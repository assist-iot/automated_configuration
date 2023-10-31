package eu.assistiot.selfconfig.smart.behavior

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.NonEmptySet
import cats.implicits.*
import com.typesafe.scalalogging.LazyLogging
import eu.assistiot.selfconfig.algebras.DefaultConditionCheck
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.configuration.model.RequirementsModel.configElementOrder
import eu.assistiot.selfconfig.model.{toDouble, ConfigElementId, Resource}
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.{HandleReaction, ReactionHandlerMessage}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.{
  ReplaceActiveRequirements,
  ReplaceRequirements,
  UpsertRequirements
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.{WithRequirements, WithResources}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.{
  SystemConfigCommand,
  SystemConfigEvent,
  SystemConfigResponse,
  SystemConfigState
}
import eu.assistiot.selfconfig.smart.behavior.utils.{functionalitiesAdjList, RequirementCheck}

import scala.annotation.tailrec
import scala.collection.immutable.SortedSet
object ReactionHandler {
  sealed trait ReactionHandlerMessage
  case class HandleReaction(
    respondTo: ActorRef[SystemConfigCommand],
    reactionAction: ReactionAction,
    state: SystemConfigState,
    event: SystemConfigEvent
  ) extends ReactionHandlerMessage
}

object DefaultReactionHandler extends LazyLogging {

  def apply(config: DefaultReactionHandlerConfig): Behavior[ReactionHandlerMessage] = {
    val kafkaProducer = UnsafeKafkaProducer(config.bootstrapServer)
    Behaviors.setup(context => {
      val ignoringActor = context.spawn(Behaviors.ignore, "reaction-handler-ignorer")
      Behaviors.receiveMessage[ReactionHandlerMessage] {
        case ReactionHandler.HandleReaction(respondTo, reactionAction, state, event) =>
          context.log.debug(
            "HandleReaction. respondTo {}, reactionAction {}, state {}, event {}",
            respondTo,
            reactionAction,
            state,
            event
          )
          reactionAction match {
            case SendSimpleKafkaMessage(topic, message) =>
              kafkaProducer.sendMessageToKafka(topic, event, message)
              Behaviors.same
            case ReplaceConfiguration(requirements) =>
              respondTo ! ReplaceActiveRequirements(requirements, ignoringActor)
              Behaviors.same
            case UpsertConfiguration(requirements, removeDangling) =>
              // TODO: propagate response to client
              respondTo ! UpsertRequirements(requirements, removeDangling, ignoringActor)
              Behaviors.same
            case ConditionalAction(conditionalCheck, action, fallback) =>
              val maybeCme = event match {
                case SystemConfigEvent.ResourceAdded(resource) =>
                  Some(CheckableMessageWithResource(resource, Available))
                case SystemConfigEvent.ResourceRemoved(resource) =>
                  Some(CheckableMessageWithResource(resource, NotAvailable))
                case _ => None
              }
              val stateRequirements = state match {
                case requirements: WithRequirements => requirements.requirements.toSortedSet.toSet
                case _                              => Set.empty
              }
              val stateResources = state match {
                case resources: WithResources => resources.resources.toSortedSet.toSet
                case _                        => Set.empty
              }
              if (DefaultConditionCheck.conditionMet(stateRequirements, stateResources, maybeCme, conditionalCheck)) {
                context.self ! HandleReaction(respondTo, action, state, event)
              } else {
                context.self ! HandleReaction(respondTo, fallback, state, event)
              }
              Behaviors.same
            case KeepHighestWeightFunctionalities =>
              handleKeepHighestFunctionality(ignoringActor, respondTo, state)
            case NoAction => Behaviors.same
          }
      }
    })
  }

  def handleKeepHighestFunctionality(
    ignoringActor: ActorRef[SystemConfigResponse],
    respondTo: ActorRef[SystemConfigCommand],
    state: SystemConfigState
  ): Behavior[ReactionHandlerMessage] = {
    state match {
      case requirementsAndResources: WithRequirements with WithResources =>
        val requirementsThanCanBeKept = highestWeightMet(
          requirementsAndResources.requirements,
          requirementsAndResources.resources.toSortedSet.toSet
        )
        respondTo ! ReplaceActiveRequirements(requirementsThanCanBeKept, ignoringActor)
        Behaviors.same
      case requirements: WithRequirements =>
        val requirementsThanCanBeKept = highestWeightMet(
          requirements.requirements,
          Set.empty
        )
        respondTo ! ReplaceActiveRequirements(requirementsThanCanBeKept, ignoringActor)
        Behaviors.same
      case _ => Behaviors.same
    }
  }

  def highestWeightMet(
    requirements: NonEmptySet[RequirementsModel],
    resources: Set[Resource]
  ): NonEmptySet[RequirementsModel] = {
    // TODO: more efficient check, especially "graph-path" sorting
    findHighestWeightMet(requirements, resources)
      .map(NonEmptySet.fromSetUnsafe)
      .getOrElse(requirements)
  }

  def findHighestWeightMet(
    requirements: NonEmptySet[RequirementsModel],
    resources: Set[Resource]
  ): Option[SortedSet[RequirementsModel]] = {
    (requirements.toSortedSet :: requirements.toSortedSet.subsets.toList)
      .filter(_.nonEmpty)
      .sortWith((l, r) => {
        val lSum = l.map(_.weight.toDouble).sum
        val rSum = r.map(_.weight.toDouble).sum
        lSum > rSum
      })
      .find(rs => RequirementCheck.areRequirementsMet(rs, resources))
  }

  case class DefaultReactionHandlerConfig(
    bootstrapServer: String
  )
}
