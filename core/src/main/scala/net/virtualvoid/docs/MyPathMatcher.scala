package net.virtualvoid.docs

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
