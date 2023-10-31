package eu.assistiot.selfconfig.web

import org.http4s.*

trait RouteProvider[F[_]] {

  val routes: HttpRoutes[F]

}
