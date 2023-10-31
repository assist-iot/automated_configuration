package eu.assistiot.selfconfig.web

import cats.effect.IO
import eu.assistiot.selfconfig.algebras.RequirementsModelOps
import eu.assistiot.selfconfig.configuration.model.RequirementsModel
import eu.assistiot.selfconfig.model.ConfigElementId
import eu.assistiot.selfconfig.model.validation.ValidationResult
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.RequirementsModelProtocol
import eu.assistiot.selfconfig.webhandling.InitializedSelfConfigSystem
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.dsl.io.*

class RequirementsRoutes(system: RequirementsModelOps[IO, String])
    extends RouteProvider[IO]
    with RequirementsModelProtocol {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "requirements-model" =>
      for {
        model <- req.as[RequirementsModel]
        _ = system.addRequirementsModel(model)
        response <- Ok("Added Requirements Model".asJson)
      } yield response
    case DELETE -> Root / "requirements-model" / id =>
      for {
        requirementModelId <- IO.fromEither(ValidationResult.toThrowableEither(ConfigElementId(id)))
        _ = system.removeRequirementsModel(requirementModelId)
        response <- Ok(s"Removed Reaction Model With Id $id".asJson)
      } yield response
    case req @ POST -> Root / "requirements" / "enable-auto" =>
      for {
        _        <- system.enableAutoconfiguration()
        response <- Ok("Enabled autoconfiguration".asJson)
      } yield response
    case req @ POST -> Root / "requirements" / "disable-auto" =>
      for {
        _        <- system.disableAutoconfiguration()
        response <- Ok("Disabled autoconfiguration".asJson)
      } yield response
  }

}
