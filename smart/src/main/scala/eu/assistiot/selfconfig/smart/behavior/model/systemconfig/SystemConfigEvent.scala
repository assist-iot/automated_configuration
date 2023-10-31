package eu.assistiot.selfconfig.smart.behavior.model.systemconfig

import cats.data.NonEmptySet
import eu.assistiot.selfconfig.configuration.model.{ReactionId, ReactionModel, RequirementsModel}
import eu.assistiot.selfconfig.model.{ConfigElementId, Resource}
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.ReactionModelProtocol
import io.circe.syntax.*
import io.circe.{Encoder, Json}

sealed trait SystemConfigEvent

object SystemConfigEvent extends ReactionModelProtocol:
  implicit val encoder: Encoder[SystemConfigEvent] = Encoder.instance { e =>
    val content = e match {
      case ResourceAdded(resource)                      => resource.asJson
      case ResourceRemoved(resource)                    => resource.asJson
      case RequirementsModelAdded(model)                => model.asJson
      case RequirementsModelRemoved(id)                 => id.asJson
      case ReactionModelAdded(model)                    => model.asJson
      case ReactionModelRemoved(id)                     => id.asJson
      case RequirementsModelUpdated(requirements)       => requirements.asJson
      case AcitveRequirementsModelUpdated(requirements) => requirements.asJson
      case CustomMessageReceived(content)               => content.asJson
      case AutoConfigurationEnabled                     => Json.Null
      case AutoConfigurationDisabled                    => Json.Null

    }
    Json.obj(
      "event"   -> e.getClass.getSimpleName.asJson,
      "content" -> content.asJson
    )
  }

  final case class ResourceAdded(resource: Resource)                extends SystemConfigEvent
  final case class ResourceRemoved(resource: Resource)              extends SystemConfigEvent
  final case class RequirementsModelAdded(model: RequirementsModel) extends SystemConfigEvent

  final case class RequirementsModelRemoved(id: ConfigElementId) extends SystemConfigEvent

  final case class ReactionModelAdded(model: ReactionModel) extends SystemConfigEvent

  final case class ReactionModelRemoved(id: ReactionId) extends SystemConfigEvent

  final case class RequirementsModelUpdated(requirements: NonEmptySet[RequirementsModel]) extends SystemConfigEvent
  final case class AcitveRequirementsModelUpdated(requirements: NonEmptySet[RequirementsModel])
      extends SystemConfigEvent
  final case class CustomMessageReceived(content: String) extends SystemConfigEvent

  case object AutoConfigurationEnabled  extends SystemConfigEvent
  case object AutoConfigurationDisabled extends SystemConfigEvent
