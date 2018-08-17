package sp.internal

import akka.stream.scaladsl.StreamConverters
import play.api.ApplicationLoader.Context
import play.api.http.HeaderNames.CONTENT_DISPOSITION
import play.api.http.{FileMimeTypes, HttpEntity}
import play.api.mvc.{ResponseHeader, Result, Results}

import scala.concurrent.ExecutionContext

object Resource {
  def sendFile(relativePath: String)(implicit ec: ExecutionContext, context: Context, mimeTypes: FileMimeTypes): Option[Result] = {
    context.environment.getExistingFile(relativePath)
      .map(Results.Ok.sendFile(_))
  }

  def sendResource(relativePath: String)(implicit context: Context, mimeTypes: FileMimeTypes): Option[Result] = {
    val fileName = relativePath.split('/').last

    context.environment.resourceAsStream(relativePath)
      .map { stream =>
        val source = StreamConverters.fromInputStream(() => stream)

        HttpEntity.Streamed(
          source,
          Some(stream.available()),
          mimeTypes.forFileName(fileName).orElse(Some(play.api.http.ContentTypes.BINARY))
        )
      }.map(body => Result(resourceHeader(relativePath), body))
  }

  private def resourceHeader(relativePath: String) = {
    val fileName = relativePath.split('/').last

    ResponseHeader(
      200,
      Map(
        CONTENT_DISPOSITION -> {
          val builder = new StringBuilder
          builder.append("inline")
          builder.append("; ")
          Encoding.encodeToBuilder("filename", fileName, builder)
          builder.toString
        }
      )
    )
  }
}
