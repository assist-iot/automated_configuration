package eu.assistiot.selfconfig.smart.behavior.utils

import akka.actor.typed.ActorRef
import com.typesafe.scalalogging.LazyLogging
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.{toMap, LabelKey, LabelValue, Resource}
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.{HandleReaction, ReactionHandlerMessage}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.ReplaceActiveRequirements
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigEvent.{
  CustomMessageReceived,
  ResourceAdded,
  ResourceRemoved
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.WithReactions
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.{
  SystemConfigCommand,
  SystemConfigEvent,
  SystemConfigResponse,
  SystemConfigState
}

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.runtime.LazyLong

trait SelfConfigReactionHandling extends LazyLogging:

  def handleReaction(
    reactionHandler: ActorRef[ReactionHandlerMessage],
    systemConfig: ActorRef[SystemConfigCommand],
    persistedEvent: SystemConfigEvent
  ): SystemConfigState => Unit = {
    case withReactions: WithReactions =>
      logger.debug("Received event {} in state {}", persistedEvent, withReactions)
      persistedEvent match {
        case ResourceAdded(resource) =>
          handlePerIdReaction[ResourceIsAvailable](
            reactionHandler,
            systemConfig,
            persistedEvent,
            withReactions,
            resource
          )
          handlePerLabelReaction[ResourceWithLabelIsAvailable](
            reactionHandler,
            systemConfig,
            persistedEvent,
            withReactions,
            resource
          )
        case ResourceRemoved(resource) =>
          handlePerIdReaction[ResourceIsNoLongerAvailable](
            reactionHandler,
            systemConfig,
            persistedEvent,
            withReactions,
            resource
          )
          handlePerLabelReaction[ResourceWithLabelIsNoLongerAvailable](
            reactionHandler,
            systemConfig,
            persistedEvent,
            withReactions,
            resource
          )
        case CustomMessageReceived(content) =>
          handleCustomMessage(reactionHandler, systemConfig, persistedEvent, withReactions, content)
        case _ => ()
      }
      withReactions.reactions.reactionModels.toSortedSet.toSet.collect { case rm @ ReactionModel(_, AnyEvent, _) =>
        reactionHandler ! HandleReaction(systemConfig, rm.action, withReactions, persistedEvent)
      }
    case other =>
      logger.debug("Received unhandled state {}", other)
      ()
  }

  private def handlePerIdReaction[T <: FilterExpression: ClassTag](
    reactionHandler: ActorRef[ReactionHandler.ReactionHandlerMessage],
    systemCommandRef: ActorRef[SystemConfigCommand],
    event: SystemConfigEvent,
    reactions: SystemConfigState.WithReactions,
    resource: Resource
  ): Unit = {
    logger.debug("Handling PerIdReaction, event {}, reactions {}, resource {}", event, reactions, resource)
    reactions.reactions.resourceIdToReactions
      .get(resource.id)
      .collect(rm =>
        rm.filterExpression match {
          case _: T => rm
        }
      )
      .foreach(e => reactionHandler ! HandleReaction(systemCommandRef, e.action, reactions, event))
  }

  private def handlePerLabelReaction[T <: FilterExpression: ClassTag](
    reactionHandler: ActorRef[ReactionHandlerMessage],
    systemCommandRef: ActorRef[SystemConfigCommand],
    event: SystemConfigEvent,
    reactions: WithReactions,
    resource: Resource
  ): Unit = {
    logger.debug("Handling PerPerLabelReaction, event {}, reactions {}, resource {}", event, reactions, resource)
    val reactionModels = for {
      labelKeyValue <- resource.labels.toMap.map(identity)
      labelKey   = LabelKey.unsafe(labelKeyValue._1)
      labelValue = LabelValue.unsafe(labelKeyValue._2)
      reactionModel <- reactions.reactions.labelBasedMap.get((labelKey, labelValue))
    } yield reactionModel
    reactionModels
      .collect(rm =>
        rm.filterExpression match {
          case _: T => rm
        }
      )
      .foreach(e => reactionHandler ! HandleReaction(systemCommandRef, e.action, reactions, event))
  }

  private def handleCustomMessage(
    reactionHandler: ActorRef[ReactionHandler.ReactionHandlerMessage],
    systemCommandRef: ActorRef[SystemConfigCommand],
    event: SystemConfigEvent,
    reactions: SystemConfigState.WithReactions,
    content: String
  ): Unit = {
    logger.debug("Handling CustomMessage, event {}, reactions {}, resource {}", event, reactions, content)
    reactions.reactions.contentToReaction
      .get(content)
      .foreach(e => reactionHandler ! HandleReaction(systemCommandRef, e.action, reactions, event))
  }
