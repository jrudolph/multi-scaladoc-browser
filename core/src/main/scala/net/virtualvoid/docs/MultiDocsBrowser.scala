package net.virtualvoid.docs

import akka.actor.{ ActorSystem, Props, Actor }
import spray.routing.HttpService
import spray.util.SprayActorLogging
import spray.httpx.SprayJsonSupport
import org.parboiled.common.FileUtils
import spray.http.{ MediaTypes, StatusCodes, Uri }
import spray.routing.PathMatchers.Rest
import java.io.InputStream
import scala.util.matching.Regex.Match
import spray.httpx.marshalling.{ BasicMarshallers, Marshaller }
import spray.routing.directives.ContentTypeResolver
import akka.io.IO
import spray.can.Http

object MultiDocsBrowser {
  def run(scalaDocJars: Seq[String])(implicit system: ActorSystem): Unit = {
    val docs = MultiScalaDocsRepo(scalaDocJars.map(ScalaDocs.load))
    system.log.info(s"Loaded ${docs.docs.size} scaladocs")

    val service = system.actorOf(Props(classOf[MultiDocsBrowser], docs))

    IO(Http) ! Http.Bind(service, "localhost", 35891)
  }
}

class MultiDocsBrowser(docs: MultiScalaDocsRepo) extends Actor with HttpService with SprayActorLogging with SprayJsonSupport {
  def actorRefFactory = context
  val templateExtraData = {
    val cl = spray.util.actorSystem(context).dynamicAccess.classLoader
    FileUtils.readAllBytes(cl.getResourceAsStream("extra-template.js"))
  }
  val mergedIndexJs = {
    import spray.json._
    import DefaultJsonProtocol._
    ScalaDocs.INDEX_PRELUDE + docs.mergedPackages.toJson.toString + ScalaDocs.INDEX_SUFFIX
  }
  import MyPathMatcher._
  def receive = runRoute {
    get {
      path("") {
        redirect(Uri("/index"), StatusCodes.Found)
      } ~
        path("index") {
          completeWithStream("index.html", docs.resourceModule.resourceForPath("index.html"))
        } ~
        path("docs" / "byType" / Segment) { tpe ⇒
          docs.moduleForType(tpe) match {
            case Some(m) ⇒ redirect(Uri(s"/docs/${m.module}/index.html#$tpe"), StatusCodes.Found)
            case None    ⇒ complete(StatusCodes.NotFound)
          }
        } ~
        pathPrefix("docs" / Segment) { module ⇒
          val m = docs.moduleForName(module).get

          path(Rest.filter(_.endsWith(".html"))) { path ⇒
            completeWithLinksReplacedStream(m.resourceForPath(path))
          } ~
            path(Rest) { path ⇒
              completeWithStream(path, m.resourceForPath(path))
            }
        } ~
        path("index.js") {
          complete(mergedIndexJs)
        } ~
        path(Rest.filter(_.endsWith("/package.html"))) { path ⇒
          completeWithLinksReplacedStream(docs.packageObjectStream(path))
        } ~
        path(Rest) { path ⇒
          completeWithStream(path, docs.resourceModule.resourceForPath(path))
        }
    }
  }
  def completeWithLinksReplacedStream(stream: Option[InputStream]) = {
    val tpeLink = """<a href="([^"#]*)(#[^"]*)?" class="(extype|extmbr)" name="([^"]*)">([^<]*)</a>""".r
    val span = """<span class="extype" name="([^"]*)">([^<]*)</span>""".r

    def replaceLinks(stream: InputStream) = {
      val withLinksReplaced = tpeLink.replaceAllIn(FileUtils.readAllText(stream), replacer = { (m: Match) ⇒
        val path = m.group(1)
        val tpe = m.group(4)

        docs.moduleForType(tpe) match {
          case Some(mod) ⇒
            val kind = mod.kindForPath(tpe, path)
            val newPath = docs.pathFor(mod.module, mod.entry(tpe).get, kind).get.replaceAll("\\$", "\\\\\\$")
            s"""<a href="${newPath}$$2" class="$$3" name="$$4">$$5</a>"""
          case None ⇒ m.group(0).replaceAll("\\$", "\\\\\\$")
        }
      })
      span.replaceAllIn(withLinksReplaced, replacer = { m ⇒
        val tpe = m.group(1)

        docs.moduleForType(tpe) match {
          case Some(mod) ⇒
            val kind = mod.kindForEntry(tpe)
            val path = docs.pathFor(mod.module, mod.entry(tpe).get, kind).get.replaceAll("\\$", "\\\\\\$")
            s"""<a href="${path}" class="extype" name="$$1">$$2</a>"""
          case None ⇒ m.group(0).replaceAll("\\$", "\\\\\\$")
        }
      })
    }

    implicit val m = Marshaller.delegate[String, String](MediaTypes.`text/html`)
    respondWithMediaType(MediaTypes.`text/html`) {
      complete(stream.map(replaceLinks))
    }
  }
  def completeWithStream(path: String, data: Option[InputStream]) = {
    implicit val bufferMarshaller = BasicMarshallers.byteArrayMarshaller(ContentTypeResolver.Default(path))
    complete(data.map(FileUtils.readAllBytes))
  }
}
