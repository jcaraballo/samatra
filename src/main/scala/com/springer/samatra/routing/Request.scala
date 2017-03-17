package com.springer.samatra.routing

import java.io.InputStream
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import javax.servlet.http._

import scala.collection.JavaConverters
import scala.io.Source
import scala.collection.JavaConverters._

trait RequestTrait {
  def cookie(cookieName: String): Option[String]
  def captured(name: String): String
  def captured(i: Int): String
  def queryStringParamValue(name: String): String
  def queryStringParamValues(name: String): Set[String]
  def header(name: String): Option[String]
  def headers(name: String): Seq[String]
  def attribute(name: String): Option[Any]
  def setAttribute(name: String, value: Any): Unit
  def removeAttribute(name: String): Unit
  def timestamp: Long
  def toUri: String
}

class MatchableRequest private (underlying: HttpServletRequest, started: Long) {
  def method: String = underlying.getMethod

  def path: String = underlying.getRequestURI
  //relative to servlet path
  def relativePath: String =
    if (path.indexOf(underlying.getServletPath) > -1)
      path.substring(underlying.getServletPath.length)
    else
      path

  def withParams(params: collection.Map[String, String]): Request = Request(underlying, params, started)
}

object MatchableRequest {
  def start(underlying: HttpServletRequest): MatchableRequest = new MatchableRequest(underlying, System.currentTimeMillis())
}

case class Request(underlying: HttpServletRequest, params: collection.Map[String, String], timestamp: Long) extends RequestTrait {
  private val bodyRead = new AtomicBoolean(false)

  def cookie(cookieName: String): Option[String] = safeGetCookies(underlying).collectFirst {
    case c if c.getName == cookieName => c.getValue
  }

  private def safeGetCookies(r: HttpServletRequest): Array[Cookie] = Option(r.getCookies).getOrElse(Array.empty)

  def captured(name: String): String = params(name)
  def captured(i: Int): String = captured(i.toString)

  def queryStringParamValue(name: String): String = underlying.getParameter(name)

  def queryStringParamValues(name: String): Set[String] = Option(underlying.getParameterMap.get(name)).map(_.toSet).getOrElse(Set.empty)

  def header(name: String): Option[String] = Option(underlying.getHeader(name))

  def headers(name: String): Seq[String] = JavaConverters.iterableAsScalaIterableConverter(Collections.list(underlying.getHeaders(name))).asScala.toList

  def attribute(name: String): Option[Any] = Option(underlying.getAttribute(name))
  def setAttribute(name: String, value: Any): Unit = underlying.setAttribute(name, value)
  def removeAttribute(name: String): Unit = underlying.removeAttribute(name)

  def toUri: String = {
    val queryString: Option[String] = Option(underlying.getQueryString)
    if (queryString.isDefined) underlying.getRequestURL.append("?").append(queryString.get).toString else underlying.getRequestURL.toString
  }

  def queryStringMap: Map[String, Array[String]] = underlying.getParameterMap.asScala.toMap

  lazy val body: String = Source.fromInputStream(bodyAsStream).getLines().mkString("\n")

  def bodyAsStream: InputStream =
    if (bodyRead.getAndSet(true))
      throw new IllegalStateException("body already read")
    else
      underlying.getInputStream

  override def toString = toUri
}
