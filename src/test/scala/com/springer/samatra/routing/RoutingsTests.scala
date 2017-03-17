package com.springer.samatra.routing

import com.springer.samatra.routing.Routings.{GET, PathParamsRoute, RegexRoute}
import com.springer.samatra.routing.StandardResponses.Implicits.fromString
import org.scalatest.FunSpec
import org.scalatest.Matchers._

import scala.util.matching.Regex

class RoutingsTests extends FunSpec {

  describe("parameter path matching") {
    it("should match simple path") {
      pathPattern("/a/b").matches(requestWithPath("/a/b")) shouldBe 'defined
    }

    it("should match simple path with trailing /") {
      pathPattern("/a/b").matches(requestWithPath("/a/b/")) shouldBe 'defined
    }

    it("partial path match should not match") {
      pathPattern("/a/b").matches(requestWithPath("/a")) shouldBe 'empty
    }

    it("should capture path parameters") {
      pathPattern("/a/:b/:c/d").matches(requestWithPath("/a/hello/springer/d")).get shouldBe Map("b" -> "hello", "c" -> "springer")
    }
  }

  describe("parameter regex matching") {

    it("should only match a pattern exactly") {
      regexPattern("/exact/(\\d\\d)".r).matches(requestWithPath("/something/exact/12")) shouldBe 'empty
    }

    it("should match a pattern") {
      regexPattern("^/exact/(\\d\\d)$".r).matches(requestWithPath("/exact/1")) shouldBe 'empty

      regexPattern("^/exact/(\\d\\d)/(.*)$".r).matches(requestWithPath("/exact/12/something/with/slashes")) shouldBe
        Some(Map("0" -> "12", "1" -> "something/with/slashes"))
    }
  }

  def pathPattern(pattern: String): PathParamsRoute[String] = PathParamsRoute(GET, pattern, { _ => "" })

  def regexPattern(pattern: Regex): RegexRoute[String] = RegexRoute(GET, pattern, { _ => "" })

  def requestWithPath(p: String): String = p
}
