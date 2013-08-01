package net.virtualvoid.docs

import akka.actor._
import spray.can.Http
import spray.routing.{ PathMatchers, HttpService }
import spray.util.SprayActorLogging
import akka.io.IO
import java.io.{ InputStream, File }
import java.util.zip.{ ZipEntry, ZipFile }
import spray.json.{ JsValue, JsonParser, DefaultJsonProtocol, JsonFormat }
import org.parboiled.common.FileUtils
import spray.http.{ MediaTypes, StatusCodes, Uri }
import spray.httpx.marshalling.{ Marshaller, BasicMarshallers }
import spray.routing.directives.ContentTypeResolver
import spray.http.StatusCodes.Redirection
import spray.httpx.SprayJsonSupport
import java.util.regex.Pattern
import scala.util.matching.Regex.Match

object Main extends App {
  implicit val system = ActorSystem()

  MultiDocsBrowser.run(args.toSeq)
}

