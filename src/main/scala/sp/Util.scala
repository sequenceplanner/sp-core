package sp

import sp.domain.{JSReads, SPMessage}

object Util {
  object SPMessageSyntax {
    case class SPMessageExtractor[H, B](data: Option[(H, B)]) {
      def mapHeader[H2](f: H => H2): SPMessageExtractor[H2, B] = copy(data = data.map { case (h, b) => f(h) -> b })
      def mapBody[B2](f: B => B2): SPMessageExtractor[H, B2] = copy(data = data.map { case (h, b) => h -> f(b) })
      def flatMapBody[B2](f: PartialFunction[B, Option[B2]]): SPMessageExtractor[H, B2] = {
        copy(data = data.flatMap { case (h, b) => f.applyOrElse(b, (_: B) => None).map(b2 => (h, b2)) })
      }
      def flatMapHeader[H2](f: H => Option[H2]): SPMessageExtractor[H2, B] = {
        copy(data = data.flatMap { case (h, b) => f(h).map(h2 => (h2, b)) })
      }

      def ifHeader(f: H => Boolean): SPMessageExtractor[H, B] = copy(
        data = data.flatMap { case (h, _) => if (f(h)) data else None }
      )

      def ifBody(f: B => Boolean): SPMessageExtractor[H, B] = copy(
        data = data.flatMap { case (_, b) => if (f(b)) data else None }
      )

      def map[C](op: (H, B) => C): Option[C] = data.map { case (h, b) => op(h, b) }
      def foreach(op: (H, B) => Unit): Unit = data.foreach{ case (h, b) => op(h, b) }

      def header: Option[H] = data.map { case (h, _) => h }
      def body: Option[B] = data.map { case (_, b) => b }
      def get: Option[(H, B)] = data
    }

    implicit def extractorToOption[H, B](extractor: SPMessageExtractor[H, B]): Option[(H, B)] = extractor.get

    implicit class SPMessageExtractorSyntax(message: SPMessage) {
      def getAs[H: JSReads, B: JSReads]: SPMessageExtractor[H, B] = SPMessageExtractor(for {
        header <- message.getHeaderAs[H]
        body <- message.getBodyAs[B]
      } yield (header, body))
    }

    implicit class OptionSPMessageExtractorSyntax(message: Option[SPMessage]) {
      def getAs[H: JSReads, B: JSReads]: SPMessageExtractor[H, B] = SPMessageExtractor(for {
        msg <- message
        header <- msg.getHeaderAs[H]
        body <- msg.getBodyAs[B]
      } yield (header, body))
    }
  }

}
