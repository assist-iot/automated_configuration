package eu.assistiot.selfconfig.web
import cats.effect.*
import eu.assistiot.selfconfig.algebras.ReactionModelOps
import eu.assistiot.selfconfig.configuration.model.{ReactionId, ReactionModel, RequirementsModel}
import eu.assistiot.selfconfig.model.validation.ValidationResult
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.ReactionModelProtocol
import eu.assistiot.selfconfig.webhandling.InitializedSelfConfigSystem
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.dsl.io.*

class ReactionRoutes(system: ReactionModelOps[IO, String]) extends RouteProvider[IO] with ReactionModelProtocol {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "reaction-model" =>
      for {
        model <- req.as[ReactionModel]
        _ = system.addReactionModel(model)
        response <- Ok("Added Reaction Model".asJson)
      } yield response
    case DELETE -> Root / "reaction-model" / id =>
      for {
        reactionId <- IO.fromEither(ValidationResult.toThrowableEither(ReactionId(id)))
        _ = system.removeReactionModel(reactionId)
        response <- Ok(s"Removed Reaction Model With Id $id".asJson)
      } yield response
  }

}
