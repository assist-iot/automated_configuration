package eu.assistiot.selfconfig.smart.behavior.model.systemconfig

import akka.actor.typed.ActorRef
import cats.data.NonEmptySet
import eu.assistiot.selfconfig.configuration.model.{ReactionId, ReactionModel, RequirementsModel}
import eu.assistiot.selfconfig.model.{ConfigElementId, Resource}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponse

sealed trait SystemConfigCommand:
  def replyTo: ActorRef[SystemConfigResponse]

object SystemConfigCommand:
  case class AddResource(resource: Resource, replyTo: ActorRef[SystemConfigResponse]) extends SystemConfigCommand

  case class RemoveResource(resource: Resource, replyTo: ActorRef[SystemConfigResponse]) extends SystemConfigCommand

  case class AddFunctionalityModel(functionalityModel: RequirementsModel, replyTo: ActorRef[SystemConfigResponse])
      extends SystemConfigCommand

  case class RemoveFunctionalityModel(id: ConfigElementId, replyTo: ActorRef[SystemConfigResponse])
      extends SystemConfigCommand

  case class AddReactionModel(reactionModel: ReactionModel, replyTo: ActorRef[SystemConfigResponse])
      extends SystemConfigCommand

  case class RemoveReactionModel(id: ReactionId, replyTo: ActorRef[SystemConfigResponse]) extends SystemConfigCommand

  case class ReplaceRequirements(requirements: NonEmptySet[RequirementsModel], replyTo: ActorRef[SystemConfigResponse])
      extends SystemConfigCommand
  case class ReplaceActiveRequirements(
    requirements: NonEmptySet[RequirementsModel],
    replyTo: ActorRef[SystemConfigResponse]
  ) extends SystemConfigCommand

  case class UpsertRequirements(
    requirements: NonEmptySet[RequirementsModel],
    removeDangling: Boolean,
    replyTo: ActorRef[SystemConfigResponse]
  ) extends SystemConfigCommand

  case class CustomMessage(content: String, replyTo: ActorRef[SystemConfigResponse]) extends SystemConfigCommand

  case class EnableAutoConfiguration(replyTo: ActorRef[SystemConfigResponse]) extends SystemConfigCommand

  case class DisableAutoConfiguration(replyTo: ActorRef[SystemConfigResponse]) extends SystemConfigCommand
