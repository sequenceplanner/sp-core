package spgui.communication

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.scalajs.js.timers._
import sp.domain._
import sp.domain.Logic._

import scala.language.higherKinds
import scala.util.{Try, Success, Failure }

import fs2._
import fs2.async
import fs2.async.mutable.Queue
import scala.concurrent.ExecutionContext.Implicits.global
import cats.effect.{ Effect, IO }
import cats.implicits._

object APIComm {
  implicit class StreamHelper[RST](s: Stream[IO, (SPHeader, Either[APISP.SPError, RST])]) {
    def takeFirstResponse = takeNSuccessfulBySender(1).map(_.headOption.flatMap(_._2.headOption).getOrElse(
      throw new RuntimeException("Didn't get an answer!"))): Future[(SPHeader,RST)]

    def takeNSuccessfulBySender(n: Int): Future[Map[String,List[(SPHeader,RST)]]] = {
      s.bySender.collect {
        case (from, v) if v.forall(_._2.isRight) =>
          (from, v.collect{ case(h,Right(rst))=>(h,rst) })}.take(n).runLog.unsafeToFuture().
        map(v=>if(v.size < n) throw new RuntimeException(s"Got fewer than ${n} answers!") else v).
        map { _.toList.map ({case (g,v) => (g,v.toList) }).toMap }
    }

    def bySender = s.groupBy(_._1.from)
    def doit = s.run.unsafeToFuture()
  }
}

class APIComm[RQT,RST](requestTopic: String, responseTopic: String, from: String, to: String,
  onChannelUp: Option[() => Unit], onMessage: Option[(SPHeader,RST) => Unit])
  (implicit val writeRequest: JSFormat[RQT], implicit val readResponse: JSFormat[RST]) {
  import spgui.communication.{BackendCommunication => bc }
  type ResponseStream = Stream[IO, (SPHeader, Either[APISP.SPError, RST])]

  private var reqs: Map[ID, (ManualOuterSource, Map[String,(ManualSource, ResponseStream, SetTimeoutHandle)], FiniteDuration)] = Map() // order of declaration important!

  private val topicHandler = bc.getMessageObserver(topicHandlerCB, responseTopic)
  private val channelObserver = bc.getWebSocketStatusObserver(up => if(up) onChannelUp.foreach(_.apply), responseTopic)

  private def topicHandlerCB(mess: SPMessage): Unit = {
    for {
      cb <- onMessage
      h <- mess.header.to[SPHeader].toOption
      b <- mess.body.to[RST](readResponse).toOption
    } cb.apply(h,b)

    for {
      h <- mess.header.to[SPHeader].toOption
      (source, inner, timeout) <- reqs.get(h.reqID)
    } yield {
      mess.body.to[APISP].foreach {
        case APISP.SPACK() =>
          val innerSource = new ManualSource
          val innerStream = createStream(innerSource)
          val innerTimeout = setTimeout(timeout) {
            val errMsg = s"No SPDone to request ${h.reqID} (after ACK) in ${timeout.toMillis}ms."
            innerSource.error(h, APISP.SPError(errMsg))
            innerSource.done()
          }
          val newInner = inner + (h.from -> (innerSource, innerStream, innerTimeout))
          source.post(innerStream)
          reqs += h.reqID -> (source, newInner, timeout)
        case err@APISP.SPError(message, _) =>
          inner.get(h.from).foreach { case (innerSource, innerStream, innerTimeout) =>
            clearTimeout(innerTimeout)
            innerSource.error(h, err)
            innerSource.done()
          }
        case APISP.SPDone() =>
          inner.get(h.from).foreach { case (innerSource, innerStream, innerTimeout) =>
            clearTimeout(innerTimeout)
            innerSource.done()
          }

        case _ => // do nothing
      }

      mess.body.to[RST](readResponse).foreach { rst =>
        inner.get(h.from).foreach { case (innerSource, innerStream, innerTimeout) => innerSource.post(h,rst) }
      }
    }
  }

  def request(body: RQT): ResponseStream = request(SPHeader(from = from, to = to), body)

  def request(header: SPHeader, body: RQT, ackTimeout: FiniteDuration = 500 millis,
    innerTimeout: FiniteDuration = 12500 millis): ResponseStream = {
    val msg = SPMessage.make[SPHeader, RQT](header, body)(implicitly, writeRequest)
    bc.publish(msg, requestTopic)
    val source = new ManualOuterSource

    setTimeout(ackTimeout) {
      if(reqs.get(header.reqID).map(_._2.isEmpty).getOrElse(false)) {
        val errMsg = s"No reply to request ${header.reqID} from service in ${ackTimeout.toMillis}ms."
        source.error(new java.util.concurrent.TimeoutException(errMsg))
      } else {
        source.done()
      }
    }

    def addReq = IO(reqs += (header.reqID -> (source, Map(), innerTimeout)))
    def removeReq = IO(reqs -= header.reqID)

    Stream.bracket(addReq)(_ => createOuterStream(source).join(10), _ => removeReq)
  }

  def kill = topicHandler.kill()

  private class ManualSource {
    var _cb: Option[Option[(SPHeader, Either[APISP.SPError, RST])] => Unit] = None
    def registerPostFunction(cb: Option[(SPHeader, Either[APISP.SPError, RST])] => Unit): Unit = {
      _cb = Some(cb)
    }
    def post(header: SPHeader, body: RST): Unit = _cb.foreach(_(Some((header, Right(body)))))
    def error(header: SPHeader, err: APISP.SPError): Unit = _cb.foreach(_(Some((header, Left(err)))))
    def done(): Unit = _cb.foreach(_(None))
  }

  private def createStream(h: ManualSource): ResponseStream = {
    for {
      q <- Stream.eval(async.boundedQueue[IO, Option[(SPHeader, Either[APISP.SPError, RST])]](1))
      _ <- Stream.suspend {
        h.registerPostFunction { e => async.unsafeRunAsync(q.enqueue1(e))(_ => IO.unit) }; Stream.emit(())
      }
      row <- q.dequeue.unNoneTerminate
    } yield row
  }

  private class ManualOuterSource {
    var _cb: Option[Either[Throwable, Option[ResponseStream]] => Unit] = None
    def registerPostFunction(cb: Either[Throwable, Option[ResponseStream]] => Unit): Unit = {
      _cb = Some(cb)
    }
    def post(inner: ResponseStream): Unit = _cb.foreach(_(Right(Some(inner))))
    def done(): Unit = _cb.foreach(_(Right(None)))
    def error(err: Exception): Unit = _cb.foreach(_(Left(err)))
  }

  private def createOuterStream(h: ManualOuterSource): Stream[IO, ResponseStream] = {
    for {
      q <- Stream.eval(async.boundedQueue[IO, Either[Throwable, Option[ResponseStream]]](1))
      _ <- Stream.suspend {
        h.registerPostFunction { e => async.unsafeRunAsync(q.enqueue1(e))(_ => IO.unit) }; Stream.emit(())
      }
      row <- q.dequeue.rethrow.unNoneTerminate
    } yield row
  }
}
