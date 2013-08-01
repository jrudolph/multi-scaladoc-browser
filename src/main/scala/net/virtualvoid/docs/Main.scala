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

object IndexFormat extends DefaultJsonProtocol {
  implicit val entryFormat = jsonFormat(ScalaEntry, "name", "case class", "class", "trait", "object")
}

object Main extends App {
  implicit val system = ActorSystem()

  val docs = MultiScalaDocsRepo(args.toSeq.map(ScalaDocs.load))
  system.log.info(s"Loaded ${docs.docs.size} scaladocs")

  val service = system.actorOf(Props(classOf[DocsBrowser], docs))

  IO(Http) ! Http.Bind(service, "localhost", 8080)
}

class DocsBrowser(docs: MultiScalaDocsRepo) extends Actor with HttpService with SprayActorLogging with SprayJsonSupport {
  def actorRefFactory = context
  val templateExtraData = {
    val cl = spray.util.actorSystem(context).dynamicAccess.classLoader
    FileUtils.readAllBytes(cl.getResourceAsStream("extra-template.js"))
  }
  val mergedIndexJs = {
    import IndexFormat._
    import spray.json._
    ScalaDocs.INDEX_PRELUDE + docs.mergedPackages.toJson.toString + ScalaDocs.INDEX_SUFFIX
  }

  def receive = runRoute {
    get {
      path("") {
        complete("hello world")
      } ~
        path("docs" / "byType" / Segment) { tpe ⇒
          docs.moduleForType(tpe) match {
            case Some(m) ⇒ redirect(Uri(s"/docs/${m.module}/index.html#$tpe"), StatusCodes.Found)
            case None    ⇒ complete(StatusCodes.NotFound)
          }
        } ~
        pathPrefix("docs" / Segment) { module ⇒
          val m = docs.moduleForName(module).get

          path(Rest) { path ⇒
            implicit val bufferMarshaller = BasicMarshallers.byteArrayMarshaller(ContentTypeResolver.Default(path))

            val fileData = m.resourceForPath(path).map(FileUtils.readAllBytes)
            val data =
              if (path == "lib/template.js")
                fileData.map(_ ++ templateExtraData)
              else fileData

            complete(data)
          }
        } ~
        pathPrefix("merged") {
          import MyPathMatcher._
          path("index.js") {
            complete(mergedIndexJs)
          } ~
            path(("trait".lit | "caseClass".lit | "class".lit | "object".lit) / Segment) { (kind, tpe) ⇒
              val stream = docs.streamForType(kind, tpe)

              completeWithLinksReplacedStream(stream)
            } ~
            path(Rest.filter(_.endsWith("/package.html"))) { path ⇒
              completeWithLinksReplacedStream(docs.packageObjectStream(path))
            } ~
            path(Rest) { path ⇒
              completeWithStream(path, docs.resourceModule.resourceForPath(path))
            }
        } ~
        path(Rest) { path ⇒
          completeWithStream(path, docs.resourceModule.resourceForPath(path))
        }
    }
  }
  def completeWithLinksReplacedStream(stream: Option[InputStream]) = {
    val tpeLink = """<a href="([^"]*)" class="extype" name="([^"]*)">([^<]*)</a>""".r
    val span = """<span class="extype" name="([^"]*)">([^<]*)</span>""".r

    def replaceLinks(stream: InputStream) = {
      val withLinksReplaced = tpeLink.replaceAllIn(FileUtils.readAllText(stream), replacer = { (m: Match) ⇒
        val path = m.group(1)
        val tpe = m.group(2)
        val contents = m.group(3)

        docs.moduleForType(tpe) match {
          case Some(mod) ⇒
            val kind = mod.kindForPath(tpe, path)
            s"""<a href="/merged/${kind}/$$2" class="extype" name="$$2">$$3</a>"""
          case None ⇒ m.group(0).replaceAll("\\$", "\\\\\\$")
        }
      })
      span.replaceAllIn(withLinksReplaced, replacer = { m ⇒
        val tpe = m.group(1)

        docs.moduleForType(tpe) match {
          case Some(mod) ⇒
            val kind = mod.kindForEntry(tpe)
            s"""<a href="/merged/${kind}/$$1" class="extype" name="$$1">$$2</a>"""
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

object MyPathMatcher {
  import spray.routing._
  import shapeless._
  import spray.http.Uri.Path
  import PathMatcher._

  implicit class StringExtra(segment: String) {
    def lit: PathMatcher1[String] = PathMatcher(segment :: Path.Empty, segment :: HNil)
  }

  implicit class PathMatcher1Extra[T](matcher: PathMatcher1[T]) {
    def filter(p: T ⇒ Boolean): PathMatcher1[T] =
      matcher.transform {
        case m @ Matched(_, t :: HNil) ⇒ if (p(t)) m else Unmatched
        case x                         ⇒ x
      }
  }
}

case class ScalaEntry(name: String,
                      caseClassPath: Option[String],
                      classPath: Option[String],
                      traitPath: Option[String],
                      objectPath: Option[String]) {
  def pathForKind(kind: String): Option[String] = kind match {
    case "caseClass" ⇒ caseClassPath
    case "class"     ⇒ classPath
    case "trait"     ⇒ traitPath
    case "object"    ⇒ objectPath
  }
}

trait ScalaDocs {
  def module: String
  def path: String
  def packages: Map[String, Seq[ScalaEntry]]

  def entry(name: String): Option[ScalaEntry]
  def resourceForPath(path: String): Option[InputStream]
  def kindForPath(name: String, path: String): String = {
    val e = entry(name).get
    Seq("caseClass", "class", "trait", "object").find(k ⇒ e.pathForKind(k).isDefined).getOrElse {
      println(s"Missing kind for $e")
      throw new IllegalArgumentException(s"Missing kind for $e")
    }
  }
  def kindForEntry(name: String): String = {
    val e = entry(name).get
    Seq("caseClass", "class", "trait", "object").find(k ⇒ e.pathForKind(k).isDefined).get
  }
}

case class MultiScalaDocsRepo(docs: Seq[ScalaDocs]) {
  def resourceModule: ScalaDocs = docs.head
  def moduleForName(name: String): Option[ScalaDocs] = docs.find(_.module == name)
  def moduleForType(tpe: String): Option[ScalaDocs] = docs.find(_.entry(tpe).isDefined)

  def packageObjectStream(path: String): Option[InputStream] = {
    val packagePath = path.dropRight("/package.html".size)
    val pkg = packagePath.replaceAll("/", ".")
    docs.find(_.packages.contains(pkg)).flatMap(_.resourceForPath(path))
  }

  def mergedPackages: Map[String, Seq[ScalaEntry]] = {
    def rewritePaths(entry: ScalaEntry): ScalaEntry = {
      def path(kind: String): Option[String] = entry.pathForKind(kind).map(_ ⇒ s"$kind/${entry.name}")
      entry.copy(
        caseClassPath = path("caseClass"),
        classPath = path("class"),
        traitPath = path("trait"),
        objectPath = path("object"))
    }

    docs.flatMap(_.packages).groupBy(_._1).map {
      case (pkg, entries) ⇒ (pkg, entries.flatMap(_._2).map(rewritePaths))
    }.toMap
  }
  def streamForType(kind: String, tpe: String): Option[InputStream] =
    for {
      mod ← moduleForType(tpe)
      entry ← mod.entry(tpe)
      path ← entry.pathForKind(kind)
      stream ← mod.resourceForPath(path)
    } yield stream
}

object ScalaDocs {
  val INDEX_PRELUDE = "Index.PACKAGES = "
  val INDEX_SUFFIX = ";"

  def load(arg: String): ScalaDocs = {
    val Array(module0, path0) = arg.split(":")
    val file = new File(path0)
    require(file.exists(), s"Not found: $file")
    new ScalaDocs {
      import IndexFormat._

      val zipFile = new ZipFile(file)
      val indexJs = zipFile.getEntry("index.js")
      val packages = readIndexJs().convertTo[Map[String, Seq[ScalaEntry]]]

      def module = module0
      def path = path0

      def resourceForPath(path: String): Option[InputStream] =
        Option(zipFile.getEntry(path)).map(zipFile.getInputStream)

      def entry(name: String): Option[ScalaEntry] =
        packages.flatMap(_._2).find(_.name == name)

      def readIndexJs(): JsValue = {
        val text = FileUtils.readAllText(zipFile.getInputStream(indexJs)).drop(INDEX_PRELUDE.size).dropRight(INDEX_SUFFIX.size)
        JsonParser(text)
      }
    }
  }
}
