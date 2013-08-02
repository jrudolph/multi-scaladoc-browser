A webserver that integrates scaladoc from different jars.

Instructions
------------

 * Clone: `git clone https://github.com/jrudolph/multi-scaladoc-browser.git`
 * Assemble: `sbt assembly`
 * Run: `java -jar core/target/scala-2.10/multidoc-core-assembly-0.1.0.jar <scaladoc-descriptor>*` where each
   `<scaladoc-descriptor>` is of the form `<module-name>:<path to scaladoc.jar>`. E.g.

```
java -jar core/target/scala-2.10/multidoc-core-assembly-0.1.0.jar \
spray-json:/home/johannes/.ivy2/local/io.spray/spray-json_2.10/1.2.5/docs/spray-json_2.10-javadoc.jar \
scala-library:/home/johannes/.ivy2/cache/org.scala-lang/scala-library/docs/scala-library-2.10.2-javadoc.jar \
parboiled-scala:/home/johannes/.ivy2/cache/org.parboiled/parboiled-scala_2.10/docs/parboiled-scala_2.10-1.1.5-javadoc.jar
```
 * Browse: [http://localhost:35891](http://localhost:35891)
