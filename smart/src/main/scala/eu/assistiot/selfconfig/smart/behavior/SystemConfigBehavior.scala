package eu.assistiot.selfconfig.smart.behavior

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import cats.data.{NonEmptyMap, NonEmptySet}
import cats.implicits.*
import eu.assistiot.selfconfig.configuration.model
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.{ConfigElementId, IdBasedRequirement, Resource}
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.{HandleReaction, ReactionHandlerMessage}
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.{
  RequirementsMet,
  RequirementsMetMessage,
  RequirementsNotMet
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.*
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigEvent.*
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponse.*
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponseMessage.*
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.*
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.{
  SystemConfigCommand,
  SystemConfigEvent,
  SystemConfigResponse,
  SystemConfigState
}
import eu.assistiot.selfconfig.smart.behavior.utils.{
  DagChecker,
  RequirementCheck,
  RequirementsNotificationHandler,
  SelfConfigReactionHandling
}

import scala.annotation.tailrec
import scala.collection.immutable.{HashMap, SortedSet}
import scala.util.{Properties, Try}

object SystemConfigBehavior
    extends RequirementCheck
    with SelfConfigReactionHandling
    with RequirementsNotificationHandler
    with DagChecker:

  type SelfConfigCommandHandler =
    (SystemConfigState, SystemConfigCommand) => ReplyEffect[SystemConfigEvent, SystemConfigState]
  type SelfConfigEventHandler = (SystemConfigState, SystemConfigEvent) => SystemConfigState

  def apply(
    requirementsMetNotifier: ActorRef[RequirementsMetMessage],
    reactionHandler: ActorRef[ReactionHandlerMessage]
  ): Behavior[SystemConfigCommand] =
    Behaviors.setup(context => {
      EventSourcedBehavior.withEnforcedReplies(
        persistenceId = PersistenceId.ofUniqueId(
          Properties.envOrElse("SYSTEM_CONFIG_NAME", "system-config-behavior")
        ),
        emptyState = EmptyState(AutoConfigState.empty),
        commandHandler = selfConfigCommandHandler(requirementsMetNotifier, reactionHandler, context),
        eventHandler = selfConfigEventHandler(context, reactionHandler)
      )
    })

  private def selfConfigCommandHandler(
    requirementsMetNotifier: ActorRef[RequirementsMetMessage],
    reactionHandler: ActorRef[ReactionHandlerMessage],
    context: ActorContext[SystemConfigCommand]
  ): SelfConfigCommandHandler =
    (state, cmd) =>
      (state, cmd) match {
        case (_, msg @ AddResource(resource, replyTo)) =>
          val resourceAdded = ResourceAdded(resource)
          Effect
            .persist(resourceAdded)
            .thenRun(handleReaction(reactionHandler, context.self, resourceAdded))
            .thenRun(requirementsNotification(requirementsMetNotifier))
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
        case (_, msg @ RemoveResource(resource, replyTo)) =>
          val removed = ResourceRemoved(resource)
          Effect
            .persist(removed)
            .thenRun(handleReaction(reactionHandler, context.self, removed))
            .thenRun(requirementsNotification(requirementsMetNotifier))
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
        case (_, msg @ AddReactionModel(reactionModel, replyTo)) =>
          Effect
            .persist(ReactionModelAdded(reactionModel))
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
        case (_, msg @ RemoveReactionModel(id, replyTo)) =>
          Effect
            .persist(ReactionModelRemoved(id))
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
        case (wm: WithRequirements, msg @ AddFunctionalityModel(functionalityModel, replyTo)) if {
              !doFunctionalityModelsFormADag(wm.requirements.add(functionalityModel).toList)
            } =>
          Effect.reply(replyTo)(SystemConfigFailure(msg, FunctionalitiesDoNotFormADag))
        case (_, msg @ AddFunctionalityModel(requirementsModel, replyTo)) =>
          Effect
            .persist(RequirementsModelAdded(requirementsModel))
            .thenRun(requirementsNotification(requirementsMetNotifier))
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
        case (_, msg @ RemoveFunctionalityModel(id, replyTo)) =>
          Effect
            .persist(RequirementsModelRemoved(id))
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
        case (_, msg @ ReplaceActiveRequirements(requirements, replyTo)) =>
          if (!doFunctionalityModelsFormADag(requirements.toList)) {
            Effect.reply(replyTo)(SystemConfigFailure(msg, FunctionalitiesDoNotFormADag))
          } else {
            Effect
              .persist(AcitveRequirementsModelUpdated(requirements))
              .thenRun(requirementsNotification(requirementsMetNotifier))
              .thenReply(replyTo)(_ => SystemConfigAck(msg))
          }
        case (_, msg @ ReplaceRequirements(requirements, replyTo)) =>
          if (!doFunctionalityModelsFormADag(requirements.toList)) {
            Effect.reply(replyTo)(SystemConfigFailure(msg, FunctionalitiesDoNotFormADag))
          } else {
            Effect
              .persist(RequirementsModelUpdated(requirements))
              .thenRun(requirementsNotification(requirementsMetNotifier))
              .thenReply(replyTo)(_ => SystemConfigAck(msg))
          }
        case (s, msg @ UpsertRequirements(updatedRequirements, removeDangling, replyTo)) =>
          val updatedModelRequirements: RequirementsModelUpdated = s match {
            case wr: WithRequirements =>
              val updatedRequirementsId = updatedRequirements.map(_.id)
              val commonIds             = wr.requirements.map(_.id).intersect(updatedRequirementsId)
              if (commonIds.isEmpty) {
                RequirementsModelUpdated(updatedRequirements ++ wr.requirements)
              } else {
                // Get dangling children ids
                val existingChildrenRequirementsIds = wr.requirements
                  .filter(r => commonIds(r.id))
                  .toSet
                  .flatMap(_.requirements)
                  .collect { case IdBasedRequirement(id, _) =>
                    id
                  }
                val updatedChildrenRequirementsIds = updatedRequirements.toSortedSet.toSet
                  .flatMap(_.requirements)
                  .collect { case IdBasedRequirement(id, _) =>
                    id
                  }
                val danglingChildrenIds = existingChildrenRequirementsIds.diff(updatedChildrenRequirementsIds)
                val resultingRequirements = wr.requirements
                  .filter(r => !updatedRequirementsId(r.id)) // Remove existing requirements with same ids as new ones
                  .filter(r => !(removeDangling && danglingChildrenIds(r.id)))
                RequirementsModelUpdated(
                  NonEmptySet.fromSetUnsafe(updatedRequirements.toSortedSet ++ resultingRequirements)
                )
              }
            case _ => RequirementsModelUpdated(updatedRequirements)
          }
          if (!doFunctionalityModelsFormADag(updatedModelRequirements.requirements.toList)) {
            Effect.reply(replyTo)(SystemConfigFailure(msg, FunctionalitiesDoNotFormADag))
          } else {
            Effect
              .persist(updatedModelRequirements)
              .thenRun(requirementsNotification(requirementsMetNotifier))
              .thenReply(replyTo)(_ => SystemConfigAck(msg))
          }
        case (_, msg @ CustomMessage(content, replyTo)) =>
          val cmr = CustomMessageReceived(content)
          Effect
            .persist(cmr)
            .thenRun(handleReaction(reactionHandler, context.self, cmr))
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
        case (_, msg @ EnableAutoConfiguration(replyTo)) =>
          Effect
            .persist(AutoConfigurationEnabled)
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
        case (_, msg @ DisableAutoConfiguration(replyTo)) =>
          Effect
            .persist(AutoConfigurationDisabled)
            .thenReply(replyTo)(_ => SystemConfigAck(msg))
      }

  private def selfConfigEventHandler(
    context: ActorContext[SystemConfigCommand],
    reactionHandler: ActorRef[ReactionHandlerMessage]
  ): SelfConfigEventHandler =
    (state, evt) =>
      context.log.debug("selfConfigEventHandler: Received Event {} in State {}", state, evt)
      val result = evt match {
        case ResourceAdded(resource) =>
          val updatedState = state.addResource(resource)
          updateStateToOneWithHighestWeights(context, reactionHandler, updatedState, evt)
        case ResourceRemoved(resource) =>
          val updatedState = state.removeResource(resource)
          updateStateToOneWithHighestWeights(context, reactionHandler, updatedState, evt)
        case RequirementsModelAdded(requirement) =>
          val updatedState = state.addRequirements(requirement)
          updateStateToOneWithHighestWeights(context, reactionHandler, updatedState, evt)
        case RequirementsModelRemoved(id) =>
          val updatedState = state.removeRequirements(id)
          updateStateToOneWithHighestWeights(context, reactionHandler, updatedState, evt)
        case ReactionModelAdded(reaction) => state.addReaction(reaction)
        case ReactionModelRemoved(id)     => state.removeReaction(id)

        case AutoConfigurationEnabled  => state.enableAutoConfiguration()
        case AutoConfigurationDisabled => state.disableAutoConfiguration()

        case RequirementsModelUpdated(updatedRequierements) =>
          handleRequirementsModelUpdated(state, updatedRequierements)
        case AcitveRequirementsModelUpdated(updatedRequierements) =>
          if (state.autoConfigState.isAutoconfigEnabled) {
            state.setActiveRequirments(updatedRequierements.toSortedSet)
          } else {
            handleRequirementsModelUpdated(state, updatedRequierements)
          }

        case _: CustomMessageReceived => state
      }
      context.log.debug("selfConfigEventHandler: Returning result {}", result)
      result

  private def updateStateToOneWithHighestWeights(
    context: ActorContext[SystemConfigCommand],
    reactionHandler: ActorRef[ReactionHandlerMessage],
    state: SystemConfigState,
    evt: SystemConfigEvent
  ): SystemConfigState = state match {
    case s: WithRequirements if state.autoConfigState.isAutoconfigEnabled =>
      val availableResources = s match {
        case wr: WithResources => wr.resources.toSortedSet.toSet
        case _                 => Set.empty
      }
      val activeRequirements = DefaultReactionHandler
        .findHighestWeightMet(
          s.requirements,
          availableResources
        )
        .map(_.toSet)
        .getOrElse(Set.empty)
      state.setActiveRequirments(activeRequirements)
    case other => other
  }

  private def handleRequirementsModelUpdated(
    state: SystemConfigState,
    updatedRequierements: NonEmptySet[RequirementsModel]
  ) = {
    state match {
      case swr: WithRequirements =>
        val withoutRequirements =
          swr.requirements.foldLeft(swr: SystemConfigState)((acc, r) => acc.removeRequirements(r.id))
        updatedRequierements.foldLeft(withoutRequirements)((acc, r) => acc.addRequirements(r))
      case other => updatedRequierements.foldLeft(other)((acc, r) => acc.addRequirements(r))
    }
  }

  private def removeResourceFromState[CS <: WithResources, FS <: SystemConfigState](
    currentState: CS,
    fallbackState: => FS,
    sameStateFunction: NonEmptySet[Resource] => CS
  )(
    removedResource: Resource
  ): SystemConfigState = {
    val existingResourceWithoutResource = currentState.resources - removedResource
    if (existingResourceWithoutResource.isEmpty) {
      fallbackState
    } else {
      // It is safe, because argument is not empty
      sameStateFunction(NonEmptySet.fromSetUnsafe(existingResourceWithoutResource))
    }
  }
