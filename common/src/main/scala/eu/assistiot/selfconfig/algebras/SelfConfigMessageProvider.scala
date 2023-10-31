package eu.assistiot.selfconfig.algebras

import eu.assistiot.selfconfig.model.Resource
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.ReactionModelProtocol
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}

trait SelfConfigMessageProvider[F[_], Arguments] {

  def provideMessages(arg: Arguments): F[ResourceListenerMessage]
}

sealed trait ResourceListenerMessage {
  val messageType: String
}

case class RegisterResource(resource: Resource) extends ResourceListenerMessage {
  val messageType: String = "RegisterResource"
}

case class DeregisterResource(resource: Resource) extends ResourceListenerMessage {
  val messageType: String = "DeregisterResource"
}

case class CustomMessage(content: String) extends ResourceListenerMessage {
  val messageType: String = "CustomMessage"
}

object ResourceListenerMessage extends ReactionModelProtocol {

  implicit val resourceListenerMessageDecoder: Decoder[ResourceListenerMessage] = (c: HCursor) => {
    val messageType: Either[DecodingFailure, String] = c.downField("messageType").as[String]
    val result: Either[DecodingFailure, ResourceListenerMessage] = messageType.flatMap {
      case "RegisterResource" => c.downField("resource").as[Resource].map(resource => RegisterResource(resource))
      case "DeregisterResource" =>
        c.downField("resource").as[Resource].map(resource => DeregisterResource(resource))
      case "CustomMessage" => c.downField("content").as[String].map(content => CustomMessage(content))
      case other           => Left(DecodingFailure(s"could not recognize messageType: $other", List.empty))
    }
    result
  }
}
