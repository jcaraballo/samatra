package com.springer.samatra.routing

import javax.servlet.http._

import scala.collection.{Map, SeqView, mutable}
import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex

object Routings {

  sealed trait HttpMethod
  case object HEAD extends HttpMethod
  case object GET extends HttpMethod
  case object POST extends HttpMethod
  case object PUT extends HttpMethod
  case object DELETE extends HttpMethod

  trait HttpResp {
    def process(req: HttpServletRequest, resp: HttpServletResponse): Unit
  }

  class ControllerServlet(controller: Routes) extends HttpServlet {
    override def service(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
      val request: Request = Request(req)

      controller.matching(req) match {
        case Right((route, captured)) =>
          route.write(request.copy(params = captured), resp)

        case Left(matchingOnDifferentMethod) =>
          new DefaultRoute(matchingOnDifferentMethod).write(request, resp)
      }
    }
  }

  object Routes {
    def apply(controllers: Routes*) = new ControllerServlet(new AggregateRoutes(controllers: _*))
  }

  trait Routes {
    val routes: Seq[Route]

    def matching(req: HttpServletRequest): Either[Seq[Route], (Route, collection.Map[String, String])] = {
      val matchingByPath = routes.view.map(r => (r, r.matches(Request(req).relativePath))).collect{
        case (r, Some(captured)) => (r, captured)
      }
      matchingByPath.find(_._1.method.toString == req.getMethod) match {
        case Some(routeAndCaptured) => Right(routeAndCaptured)
        case None =>
          Left(matchingByPath.map(_._1).force)
      }
    }
  }

  class AggregateRoutes(val controllers: Routes*) extends Routes {
    override val routes: Seq[Route] = controllers.flatMap(_.routes)
  }

  abstract class Controller extends Routes {
    val routes: ArrayBuffer[Route] = new ArrayBuffer[Route]()

    def get[T](path: String)(body: Request => HttpResp): Unit = {
      getWithoutHead(path)(body)
      routes.append(PathParamsRoute(HEAD, path, NoBody(body)))
    }

    def get[T](pattern: Regex)(body: Request => HttpResp): Unit = {
      getWithoutHead(pattern)(body)
      routes.append(RegexRoute(HEAD, pattern, NoBody(body)))
    }

    //noinspection AccessorLikeMethodIsUnit
    def getWithoutHead[T](path: String)(body: Request => HttpResp): Unit = routes.append(PathParamsRoute(GET, path, body))

    //noinspection AccessorLikeMethodIsUnit
    def getWithoutHead[T](pattern: Regex)(body: Request => HttpResp): Unit = routes.append(RegexRoute(GET, pattern, body))

    def post[T](path: String)(fromRequest: Request => HttpResp): Unit = routes.append(PathParamsRoute(POST, path, fromRequest))

    def post[T](pattern: Regex)(fromRequest: Request => HttpResp): Unit = routes.append(RegexRoute(POST, pattern, fromRequest))

    def put[T](path: String)(fromRequest: Request => HttpResp): Unit = routes.append(PathParamsRoute(PUT, path, fromRequest))

    def put[T](pattern: Regex)(fromRequest: Request => HttpResp): Unit = routes.append(RegexRoute(PUT, pattern, fromRequest))

    def head(path: String)(fromRequest: Request => HeadersOnly): Unit = routes.append(PathParamsRoute(HEAD, path, fromRequest))

    def head(pattern: Regex)(fromRequest: Request => HeadersOnly): Unit = routes.append(RegexRoute(HEAD, pattern, fromRequest))

    def delete(path: String)(fromRequest: Request => HttpResp): Unit = routes.append(PathParamsRoute(DELETE, path, fromRequest))

    def delete(pattern: Regex)(fromRequest: Request => HttpResp): Unit = routes.append(RegexRoute(DELETE, pattern, fromRequest))
  }

  class HeadersOnly(val headers: (String, String)*) extends HttpResp {
    override def process(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
      headers.foreach(t => resp.setHeader(t._1, t._2))
    }
  }

  object HeadersOnly {
    def apply[T](headers: (String, String)*) = new HeadersOnly(headers: _*)
  }

  trait Route {
    def method: HttpMethod

    def matches(relativePath: String): Option[collection.Map[String, String]]

    def write(req: Request, resp: HttpServletResponse): Unit
  }

  sealed class DefaultRoute(routes: Seq[Route]) extends Route {
    override def method: HttpMethod = throw new IllegalStateException

    override def matches(relativePath: String): Option[collection.Map[String, String]] = Some(Map())

    override def write(req: Request, resp: HttpServletResponse): Unit = routes.toList match {
      case Nil => resp.sendError(404)
      case _ =>
        resp.setHeader("Allow", Set(routes.map(_.method): _*).mkString(", "))
        resp.sendError(405)
    }
  }

  sealed abstract class WritingRoute[T](method: HttpMethod, response: Request => HttpResp) extends Route {
    override def write(req: Request, resp: HttpServletResponse): Unit = {
      response(req).process(req.underlying, resp)
    }
  }

  sealed case class RegexRoute[T](method: HttpMethod, pattern: Regex, response: Request => HttpResp) extends WritingRoute(method, response) {
    override def matches(relativePath: String): Option[Map[String, String]] =
      pattern.unapplySeq(relativePath).map(_.zipWithIndex.map { case (k, v) => v.toString -> k }.toMap)

    override def toString: String = {
      s"${method.toString.padTo(4, ' ')} ${pattern.toString.padTo(32, ' ')}"
    }
  }

  sealed case class PathParamsRoute[T](method: HttpMethod, path: String, response: Request => HttpResp) extends WritingRoute(method, response) {
    override def matches(relativePath: String): Option[collection.Map[String, String]] = {
      val actual: Array[String] = relativePath.split("/")
      val pattern: Array[String] = path.split("/")

      if (actual.length != pattern.length)
        None
      else {
        val res: mutable.Map[String, String] = mutable.Map()

        for ((left, right) <- pattern.zip(actual)) {
          if (!left.equals(right))
            if (left.startsWith(":"))
              res.put(left.substring(1), right)
            else
              return None
        }

        Some(res)
      }
    }

    override def toString: String =
      s"${method.toString.padTo(4, ' ')} ${path.padTo(32, ' ')}"
  }
}
