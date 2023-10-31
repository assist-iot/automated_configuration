package eu.assistiot.selfconfig.smart.behavior.utils

import akka.actor.typed.ActorRef
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.Resource
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.{HandleReaction, ReactionHandlerMessage}
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.{
  RequirementsMet,
  RequirementsMetMessage,
  RequirementsNotMet
}
import eu.assistiot.selfconfig.smart.behavior.SystemConfigBehavior.*
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.ReplaceActiveRequirements
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigEvent.{ResourceAdded, ResourceRemoved}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.{
  StateWithRequirements,
  WithReactions,
  WithRequirements,
  WithResources
}

trait RequirementsNotificationHandler extends RequirementCheck:

  def requirementsNotification(
    requirementsMetNotifier: ActorRef[RequirementsMetMessage]
  ): SystemConfigState => Unit = {
    case state: WithRequirements =>
      val availableResources: Set[Resource] = state match {
        case wr: WithResources => wr.resources.toSortedSet
        case _                 => Set.empty
      }
      val allRequirements: Set[RequirementsModel] = state.requirements.toSortedSet
      val notifierMessage: RequirementsMetMessage = buildNotificationMessage(state, availableResources, allRequirements)
      requirementsMetNotifier ! notifierMessage
    case _ => ()
  }

  def buildNotificationMessage(
    state: SystemConfigState,
    availableResources: Set[Resource],
    allRequirements: Set[RequirementsModel]
  ): RequirementsMetMessage =
    if (state.isAutoConfigurationEnabled && state.autoConfigState.activeRequirements.nonEmpty) {
      RequirementsMet(
        state.autoConfigState.activeRequirements,
        availableResources,
        allRequirements
      )
    } else if (!state.isAutoConfigurationEnabled && areRequirementsMet(allRequirements, availableResources)) {
      RequirementsMet(
        allRequirements,
        availableResources,
        allRequirements
      )
    } else {
      RequirementsNotMet(allRequirements, availableResources)
    }
