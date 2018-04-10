Samatra [![Build Status](https://travis-ci.org/springernature/samatra.svg?branch=master)](https://travis-ci.org/springernature/samatra) [![](https://jitpack.io/v/springernature/samatra.svg)](https://jitpack.io/#springernature/samatra)
=======

## Decription
Minimal web framework in the spirit of [Scalatra](http://www.scalatra.org]). There's not a lot to it - you could write it yourself.

[Samatra-extras](https://github.com/springernature/samatra-extras) provides more features but has more dependencies. Documentation [here](https://github.com/springernature/samatra-extras/wiki)

## Supported platforms
- Scala 2.12
- Only dependency is servlet-api 3.1.0 - you can run with any compliant web server (only tested with Jetty 9.4.8.v20171121)

## How to install
- sbt: 
```
resolvers += "jitpack" at "https://jitpack.io",
libraryDependencies += "com.github.springernature.samatra" %% "samatra" % "v1.5.0"	
```

You may also be interested in [samatra-extras](https://github.com/springernature/samatra-extras) (which adds some dependencies but has more batteries included).
 
## Licensing
The MIT License (MIT)  http://opensource.org/licenses/MIT

Copyright © 2016, 2017 Springer Nature

## Maintenance
Submit issues and PR's to this github.

## Routing
Routes can be matched by path with params or regex. Matching on query params in not supported:

```scala
import com.springer.samatra.routing.Routings.Controller
import com.springer.samatra.routing.StandardResponses.Implicits._

object MyController extends Controller {
  get("/himynameis/:name") { req => s"hi ${req.captured("name")}" }
  get("^/year/(\\d\\d\\d\\d)$".r) { req => s"hell0 the year ${req.captured(0)}" }
}
```

HEAD, GET, POST, PUT and DELETE are supported

## What Routes return
The routes defined in controller define a block that takes a Request (a thin wrapper around HttpServletRequest) and return a HttpResp which is function from 
(HttpServletRequest, HttpServletResponse) => Unit. Several standard HttpResp implementations are supplied, along with implicit conversions for convenience. 

* Halt
* Redirect
* String
* Html
* InputStream
* Path
* Headers
* Cookies
* Futures 

Adding your own HttpResp types (json, xml, mustache) whether implicitly or explicitly should easy. 
They only haven't been added to this project so as to reduce the number of dependencies.

It's up to you whether you want to be implicit or explicit. See EndToEndTest for examples.

## Using with servlet container
See EndToEndTest for examples with jetty e.g.

```scala
import com.springer.samatra.routing.Routings.{Controller, HeadersOnly, Routes}
import com.springer.samatra.routing.StandardResponses.Halt
import com.springer.samatra.routing.StandardResponses.Implicits._
import org.eclipse.jetty.server.{Server, ServerConnector}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}


object WebApp extends App {
  private val server = new Server() {
    addConnector(new ServerConnector(this) {
      setPort(8080)
    })
  }

  server.setHandler(new ServletContextHandler() {
    addServlet(new ServletHolder(
      Routes(
        new Controller {
          get("/himynameis/:name") { req => s"hi ${req.captured("name")}" }
        },
        new Controller {

          head("/head") { _ => HeadersOnly("header" -> "value") }

          post("/post") { req => req.bodyAsStream }

          get("/getandpost") { _ => "get" }

          post("/getandpost") { _ => "post" }

          get("^/regex/year/(\\d\\d\\d\\d)$".r) { _ => Halt(500, Some(new IllegalStateException("servlet path takes precedence"))) }

        })), "/*")

    addServlet(new ServletHolder(Routes(new Controller {
      get("^/year/(\\d\\d\\d\\d)$".r) { req => s"hell0 the year ${req.captured(0)}" }
    })), "/regex/*")
  })

  server.start()
}
```

## Idiomatic use
Samatra was designed for a system that collates a number of different HTTP request (some of them in parallel) to build a web page.  
For the project that it was developed for we used Dispatch as the http client, but any other http client that support futures would work just as well. 
You could equally use Hystrix with the trick used here http://runkalrun.blogspot.co.uk/2014/12/hystrix-convert-observable-into-scala.html
 
This allows you to compose scala futures in fun and interesting ways e.g.

```scala
  get("/internal/dependencies") { _ =>
    val contentApiUri: String = s"http://$contentApiHost"
    val simUri: String = s"http://$simLoginHost"
    val identityUri: String = s"http://$identityHost"
    val coPublishersUri: String = coPublisherHost
    val slArticleMetricsUri: String = articleMetricsUrl
    val trackUri: String = s"http://$trackHost"

    val contentApiReq: Future[Response] = http(url(contentApiUri) / "internal" / "status")
    val identityReq: Future[Response] = http(url(identityUri) / "bp" / "123")
    val simReq: Future[Response] = http((url(simUri) / "institutional-token").POST
      .setHeader("X-API-Key", simAPIKey)

    val coPublishersReq: Future[Response] = http(url(s"$coPublishersUri?id=978-94-6209-356-0"))
    val slMetricsReq: Future[Response] = http(url(slArticleMetricsUri))
    val trackReq: Future[Response] = http(url(trackUri) / "internal" / "status")

    for {
      contentApi <- contentApiReq
      identity <- identityReq
      sim <- simReq
      coPublishers <- coPublishersReq
      slArticleMetrics <- slMetricsReq
      track <- trackReq
    } yield JsonResponse(
      Map("results" -> List(
        Map("url" -> contentApiUri, "status" -> contentApi.getStatusCode, "tags" -> List("required")),
        Map("url" -> identityUri, "status" -> identity.getStatusCode, "tags" -> List("required")),
        Map("url" -> simUri, "status" -> sim.getStatusCode, "tags" -> List("required")),
        Map("url" -> trackUri, "status" -> track.getStatusCode, "tags" -> List("required")),

        Map("url" -> coPublishersUri, "status" -> coPublishers.getStatusCode, "tags" -> List("optional")),
        Map("url" -> slArticleMetricsUri, "status" -> slArticleMetrics.getStatusCode, "tags" -> List("optional"))
      )))
  }
```
