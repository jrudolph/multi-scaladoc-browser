package net.virtualvoid.docs

import java.io.{ File, InputStream }
import java.util.zip.ZipFile
import spray.json.{ DefaultJsonProtocol, JsonParser, JsValue }
import org.parboiled.common.FileUtils
import DefaultJsonProtocol._

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
  def namePath: String = name.replaceAll("\\.", "/")
}

object ScalaEntry {
  implicit val entryFormat = jsonFormat(ScalaEntry.apply, "name", "case class", "class", "trait", "object")
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
  def moduleForPackage(pkg: String): Option[ScalaDocs] = docs.find(_.packages.contains(pkg))

  def pathFor(module: String, entry: ScalaEntry, kind: String) =
    entry.pathForKind(kind).map(p ⇒ s"/docs/$module/$p")

  def mergedPackages: Map[String, Seq[ScalaEntry]] = {
    def rewritePaths(module: String)(entry: ScalaEntry): ScalaEntry = {
      def path(kind: String): Option[String] = pathFor(module, entry, kind)
      entry.copy(
        caseClassPath = path("caseClass"),
        classPath = path("class"),
        traitPath = path("trait"),
        objectPath = path("object"))
    }

    docs.flatMap(_.packages).groupBy(_._1).map {
      case (pkg, entries) ⇒ (pkg, entries.flatMap(_._2).map(rewritePaths(moduleForPackage(pkg).get.module)))
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
