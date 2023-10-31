package eu.assistiot.selfconfig.smart.behavior.model.systemconfig

import cats.Order
import cats.data.{NonEmptyMap, NonEmptySet}
import cats.implicits.catsKernelOrderingForOrder
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.{ConfigElementId, LabelKey, LabelValue, Resource}
import eu.assistiot.selfconfig.smart.behavior.SystemConfigBehavior.removeResourceFromState
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.{AutoConfigState, EmptyState}

import scala.collection.immutable.SortedSet

sealed trait SystemConfigState:
  val autoConfigState: AutoConfigState

  def addRequirements(model: RequirementsModel): SystemConfigState
  def removeRequirements(id: ConfigElementId): SystemConfigState
  def addResource(resource: Resource): SystemConfigState
  def removeResource(resource: Resource): SystemConfigState
  def addReaction(model: ReactionModel): SystemConfigState
  def removeReaction(id: ReactionId): SystemConfigState

  def setActiveRequirments(activeRequirements: Set[RequirementsModel]): SystemConfigState
  def enableAutoConfiguration(): SystemConfigState
  def disableAutoConfiguration(): SystemConfigState
  lazy val isAutoConfigurationEnabled: Boolean = this.autoConfigState.isAutoconfigEnabled

object SystemConfigState:
  case class AutoConfigState(isAutoconfigEnabled: Boolean, activeRequirements: Set[RequirementsModel]):
    def enableAutoconfig(): AutoConfigState  = AutoConfigState(true, this.activeRequirements)
    def disableAutoconfig(): AutoConfigState = AutoConfigState(false, Set.empty)
    def setActiveRequirements(newRequirements: Set[RequirementsModel]): AutoConfigState =
      AutoConfigState(true, newRequirements)
    def setRequirimentsIfActive(reqs: Set[RequirementsModel]): AutoConfigState = if (this.isAutoconfigEnabled) {
      AutoConfigState(true, reqs)
    } else {
      this
    }

  case object AutoConfigState:
    def empty: AutoConfigState = AutoConfigState(false, Set.empty)

  case class Reactions(reactionModels: NonEmptySet[ReactionModel]):

    import ConfigElementId.configElementIdOrder
    import ReactionModel.reactionModelOrder
    import Reactions.*

    private lazy val reactionModelIterable: Iterable[ReactionModel] = reactionModels.toSortedSet.toIterable

    lazy val resourceIdToReactions: Map[ConfigElementId, ReactionModel] =
      reactionModelIterable.map(mapReactionToConfigElementIdMap).reduceLeft(_ ++ _)

    lazy val labelBasedMap: Map[(LabelKey, LabelValue), ReactionModel] =
      reactionModelIterable.map(mapReactionToLabelMap).reduceLeft(_ ++ _)

    lazy val contentToReaction: Map[String, ReactionModel] =
      reactionModelIterable.map(mapReactionToContent).reduceLeft(_ ++ _)

    def addReactionModel(reactionModel: ReactionModel): Reactions = {
      new Reactions(reactionModels.add(reactionModel))
    }

    def removeReactionModel(reactionModelId: ReactionId): Option[Reactions] = {
      val updatedReactionModels = reactionModels.filterNot(e => e.reactionId == reactionModelId)
      if (updatedReactionModels.isEmpty) {
        None
      } else {
        Some(Reactions(NonEmptySet.fromSetUnsafe(updatedReactionModels)))
      }
    }

  object Reactions:
    def apply(reactionModel: ReactionModel): Reactions = new Reactions(
      NonEmptySet.of(reactionModel)
    )

    private def mapReactionToConfigElementIdMap(model: ReactionModel): Map[ConfigElementId, ReactionModel] =
      model.filterExpression match {
        case ResourceIsAvailable(id)         => Map(id -> model)
        case ResourceIsNoLongerAvailable(id) => Map(id -> model)
        case _                               => Map.empty
      }

    private def mapReactionToLabelMap(model: ReactionModel): Map[(LabelKey, LabelValue), ReactionModel] =
      model.filterExpression match {
        case ResourceWithLabelIsAvailable(labelKey, labelValue) => Map((labelKey, labelValue) -> model)
        case ResourceWithLabelIsNoLongerAvailable(labelKey, labelValue) =>
          Map((labelKey, labelValue) -> model)
        case _ => Map.empty
      }

    private def mapReactionToContent(model: ReactionModel): Map[String, ReactionModel] =
      model.filterExpression match {
        case CustomMessageContent(content) => Map(content -> model)
        case _                             => Map.empty
      }

  case class EmptyState(autoConfigState: AutoConfigState) extends SystemConfigState:
    def addRequirements(requirements: RequirementsModel): StateWithRequirements = StateWithRequirements(
      NonEmptySet.of(requirements),
      this.autoConfigState.setRequirimentsIfActive(Set(requirements))
    )

    override def setActiveRequirments(
      activeRequirements: Set[RequirementsModel]
    ): EmptyState | StateWithRequirements = {
      val updatedAcs = autoConfigState.setActiveRequirements(activeRequirements)
      if (activeRequirements.isEmpty) {
        EmptyState(updatedAcs)
      } else {
        StateWithRequirements(
          NonEmptySet.fromSetUnsafe[RequirementsModel](SortedSet.from(activeRequirements)),
          updatedAcs
        )
      }
    }
    def removeRequirements(id: ConfigElementId): EmptyState = this
    def addResource(resource: Resource): StateWithResources =
      StateWithResources(NonEmptySet.of(resource), this.autoConfigState)
    def removeResource(resource: Resource): EmptyState = this
    def addReaction(reaction: ReactionModel): StateWithReactions =
      StateWithReactions(Reactions(reaction), this.autoConfigState)
    def removeReaction(id: ReactionId): EmptyState = this
    def enableAutoConfiguration(): EmptyState      = EmptyState(this.autoConfigState.enableAutoconfig())
    def disableAutoConfiguration(): EmptyState     = EmptyState(this.autoConfigState.disableAutoconfig())

  sealed trait WithRequirements extends SystemConfigState:
    def requirements: NonEmptySet[RequirementsModel]
    def getOperatingRequirements: Set[RequirementsModel] = if (this.autoConfigState.isAutoconfigEnabled) {
      this.autoConfigState.activeRequirements
    } else {
      this.requirements.toSortedSet
    }

  sealed trait WithResources extends SystemConfigState:
    def resources: NonEmptySet[Resource]

  sealed trait WithReactions extends SystemConfigState:
    def reactions: Reactions

  case class StateWithRequirements(
    requirements: NonEmptySet[RequirementsModel],
    autoConfigState: AutoConfigState
  ) extends WithRequirements:
    def addRequirements(requirement: RequirementsModel): StateWithRequirements = {
      val updatedRequirements = this.requirements.add(requirement)
      StateWithRequirements(
        updatedRequirements,
        this.autoConfigState.setRequirimentsIfActive(updatedRequirements.toSortedSet.toSet)
      )
    }
    override def setActiveRequirments(activeRequirements: Set[RequirementsModel]): StateWithRequirements = {
      val updatedAcs = autoConfigState.setActiveRequirements(activeRequirements)
      StateWithRequirements(
        this.requirements,
        updatedAcs
      )
    }
    def removeRequirements(
      id: ConfigElementId
    ): StateWithRequirements | EmptyState = {
      val updatedRequirements = requirements.filterNot(e => e.id == id)
      if (updatedRequirements.isEmpty) {
        EmptyState(this.autoConfigState)
      } else {
        StateWithRequirements(NonEmptySet.fromSetUnsafe(updatedRequirements), this.autoConfigState)
      }
    }
    def addResource(resource: Resource): StateWithResourcesAndRequirements =
      StateWithResourcesAndRequirements(NonEmptySet.of(resource), requirements, this.autoConfigState)
    def removeResource(resource: Resource): StateWithRequirements = this
    def addReaction(reaction: ReactionModel): StateWithRequirementsAndReactions = StateWithRequirementsAndReactions(
      requirements,
      Reactions(reaction),
      this.autoConfigState
    )
    def removeReaction(id: ReactionId): StateWithRequirements = this
    def enableAutoConfiguration(): StateWithRequirements =
      StateWithRequirements(this.requirements, this.autoConfigState.enableAutoconfig())
    def disableAutoConfiguration(): StateWithRequirements =
      StateWithRequirements(this.requirements, this.autoConfigState.disableAutoconfig())

  case class StateWithResources(
    resources: NonEmptySet[Resource],
    autoConfigState: AutoConfigState
  ) extends WithResources:
    def addRequirements(requirement: RequirementsModel): StateWithResourcesAndRequirements =
      StateWithResourcesAndRequirements(
        resources,
        NonEmptySet.of(requirement),
        this.autoConfigState.setRequirimentsIfActive(Set(requirement))
      )
    override def setActiveRequirments(
      activeRequirements: Set[RequirementsModel]
    ): StateWithResources | StateWithResourcesAndRequirements = {
      val updatedAcs = autoConfigState.setActiveRequirements(activeRequirements)
      if (activeRequirements.isEmpty) {
        StateWithResources(this.resources, updatedAcs)
      } else {
        StateWithResourcesAndRequirements(
          resources,
          NonEmptySet.fromSetUnsafe(SortedSet.from(activeRequirements)),
          updatedAcs
        )
      }
    }
    def removeRequirements(id: ConfigElementId): StateWithResources = this
    def addResource(resource: Resource): StateWithResources =
      StateWithResources(resources.add(resource), this.autoConfigState)
    def removeResource(resource: Resource): StateWithResources | EmptyState = {
      val existingResourceWithoutResource = resources - resource
      if (existingResourceWithoutResource.isEmpty) {
        EmptyState(this.autoConfigState)
      } else {
        // It is safe, because argument is not empty
        StateWithResources(NonEmptySet.fromSetUnsafe(existingResourceWithoutResource), this.autoConfigState)
      }
    }
    def addReaction(reaction: ReactionModel): SystemConfigState =
      StateWithResourcesAndReactions(resources, Reactions(reaction), this.autoConfigState)
    def removeReaction(id: ReactionId): StateWithResources = this
    def enableAutoConfiguration(): StateWithResources =
      StateWithResources(this.resources, this.autoConfigState.enableAutoconfig())
    def disableAutoConfiguration(): StateWithResources =
      StateWithResources(this.resources, this.autoConfigState.disableAutoconfig())

  case class StateWithReactions(reactions: Reactions, autoConfigState: AutoConfigState) extends WithReactions:
    def addRequirements(requirement: RequirementsModel): StateWithRequirementsAndReactions =
      StateWithRequirementsAndReactions(
        NonEmptySet.of(requirement),
        reactions,
        this.autoConfigState.setRequirimentsIfActive(Set(requirement))
      )
    override def setActiveRequirments(
      activeRequirements: Set[RequirementsModel]
    ): StateWithReactions | StateWithRequirementsAndReactions = {
      val updatedAcs = autoConfigState.setActiveRequirements(activeRequirements)
      if (activeRequirements.isEmpty) {
        StateWithReactions(this.reactions, updatedAcs)
      } else {
        StateWithRequirementsAndReactions(
          NonEmptySet.fromSetUnsafe(SortedSet.from(activeRequirements)),
          this.reactions,
          updatedAcs
        )
      }
    }
    def removeRequirements(id: ConfigElementId): StateWithReactions = this
    def addResource(resource: Resource): StateWithResourcesAndReactions =
      StateWithResourcesAndReactions(NonEmptySet.of(resource), reactions, this.autoConfigState)
    def removeResource(resource: Resource): StateWithReactions = this
    def addReaction(reaction: ReactionModel): SystemConfigState = StateWithReactions(
      reactions.addReactionModel(reaction),
      this.autoConfigState
    )
    def removeReaction(id: ReactionId): StateWithReactions | EmptyState =
      reactions
        .removeReactionModel(id)
        .fold(EmptyState(this.autoConfigState))(StateWithReactions(_, this.autoConfigState))
    def enableAutoConfiguration(): StateWithReactions =
      StateWithReactions(this.reactions, this.autoConfigState.enableAutoconfig())
    def disableAutoConfiguration(): StateWithReactions =
      StateWithReactions(this.reactions, this.autoConfigState.disableAutoconfig())

  object StateWithReactions:
    def apply(reactionModels: NonEmptySet[ReactionModel]): StateWithReactions = StateWithReactions(
      Reactions(reactionModels),
      AutoConfigState.empty
    )

  case class StateWithResourcesAndRequirements(
    resources: NonEmptySet[Resource],
    requirements: NonEmptySet[RequirementsModel],
    autoConfigState: AutoConfigState
  ) extends WithResources
      with WithRequirements:
    def addRequirements(requirement: RequirementsModel): StateWithResourcesAndRequirements =
      StateWithResourcesAndRequirements(
        resources,
        requirements.add(requirement),
        this.autoConfigState.setRequirimentsIfActive(Set(requirement))
      )
    override def setActiveRequirments(
      activeRequirements: Set[RequirementsModel]
    ): StateWithResourcesAndRequirements = {
      val updatedAcs = autoConfigState.setActiveRequirements(activeRequirements)
      StateWithResourcesAndRequirements(
        resources,
        requirements,
        updatedAcs
      )
    }
    def removeRequirements(id: ConfigElementId): StateWithResourcesAndRequirements | StateWithResources = {
      val updatedRequirements = requirements.filterNot(e => e.id == id)
      if (updatedRequirements.isEmpty) {
        StateWithResources(resources, this.autoConfigState)
      } else {
        StateWithResourcesAndRequirements(
          resources,
          NonEmptySet.fromSetUnsafe(updatedRequirements),
          this.autoConfigState
        )
      }
    }
    def addResource(resource: Resource): StateWithResourcesAndRequirements =
      StateWithResourcesAndRequirements(resources.add(resource), requirements, this.autoConfigState)
    def removeResource(resource: Resource): StateWithResourcesAndRequirements | StateWithRequirements =
      removeResourceFromState(
        this,
        StateWithRequirements(requirements, this.autoConfigState),
        StateWithResourcesAndRequirements(_, requirements, this.autoConfigState)
      )(resource)
    def addReaction(reaction: ReactionModel): StateWithResourcesRequirementsAndReactions =
      StateWithResourcesRequirementsAndReactions(
        resources,
        requirements,
        Reactions(reaction),
        this.autoConfigState
      )
    def removeReaction(id: ReactionId): StateWithResourcesAndRequirements = this
    def enableAutoConfiguration(): StateWithResourcesAndRequirements =
      StateWithResourcesAndRequirements(this.resources, this.requirements, this.autoConfigState.enableAutoconfig())
    def disableAutoConfiguration(): StateWithResourcesAndRequirements =
      StateWithResourcesAndRequirements(this.resources, this.requirements, this.autoConfigState.disableAutoconfig())

  case class StateWithResourcesAndReactions(
    resources: NonEmptySet[Resource],
    reactions: Reactions,
    autoConfigState: AutoConfigState
  ) extends WithResources
      with WithReactions:
    def addRequirements(requirement: RequirementsModel): StateWithResourcesRequirementsAndReactions =
      StateWithResourcesRequirementsAndReactions(
        resources,
        NonEmptySet.of(requirement),
        reactions,
        this.autoConfigState.setRequirimentsIfActive(Set(requirement))
      )
    override def setActiveRequirments(
      activeRequirements: Set[RequirementsModel]
    ): StateWithResourcesAndReactions | StateWithResourcesRequirementsAndReactions = {
      val updatedAcs = autoConfigState.setActiveRequirements(activeRequirements)
      if (activeRequirements.isEmpty) {
        StateWithResourcesAndReactions(this.resources, this.reactions, updatedAcs)
      } else {
        StateWithResourcesRequirementsAndReactions(
          this.resources,
          NonEmptySet.fromSetUnsafe(SortedSet.from(activeRequirements)),
          this.reactions,
          updatedAcs
        )
      }
    }
    def removeRequirements(id: ConfigElementId): StateWithResourcesAndReactions = this
    def addResource(resource: Resource): StateWithResourcesAndReactions =
      StateWithResourcesAndReactions(resources.add(resource), reactions, this.autoConfigState)
    def removeResource(resource: Resource): StateWithResourcesAndReactions | StateWithReactions =
      removeResourceFromState(
        this,
        StateWithReactions(reactions, this.autoConfigState),
        StateWithResourcesAndReactions(_, reactions, this.autoConfigState)
      )(resource)
    def addReaction(reaction: ReactionModel): StateWithResourcesAndReactions =
      StateWithResourcesAndReactions(resources, reactions.addReactionModel(reaction), this.autoConfigState)
    def removeReaction(id: ReactionId): StateWithResourcesAndReactions | StateWithResources = reactions
      .removeReactionModel(id)
      .fold(StateWithResources(resources, this.autoConfigState))(
        StateWithResourcesAndReactions(resources, _, this.autoConfigState)
      )
    def enableAutoConfiguration(): StateWithResourcesAndReactions =
      StateWithResourcesAndReactions(this.resources, this.reactions, this.autoConfigState.enableAutoconfig())
    def disableAutoConfiguration(): StateWithResourcesAndReactions =
      StateWithResourcesAndReactions(this.resources, this.reactions, this.autoConfigState.disableAutoconfig())

  case class StateWithRequirementsAndReactions(
    requirements: NonEmptySet[RequirementsModel],
    reactions: Reactions,
    autoConfigState: AutoConfigState
  ) extends WithRequirements
      with WithReactions:
    def addRequirements(requirement: RequirementsModel): StateWithRequirementsAndReactions = {
      val updatedRequirements = requirements.add(requirement)
      StateWithRequirementsAndReactions(
        updatedRequirements,
        reactions,
        this.autoConfigState.setRequirimentsIfActive(updatedRequirements.toSortedSet.toSet)
      )
    }
    override def setActiveRequirments(
      activeRequirements: Set[RequirementsModel]
    ): StateWithRequirementsAndReactions = {
      val updatedAcs = autoConfigState.setActiveRequirements(activeRequirements)
      StateWithRequirementsAndReactions(this.requirements, this.reactions, updatedAcs)
    }
    def removeRequirements(id: ConfigElementId): StateWithRequirementsAndReactions | StateWithReactions = {
      val updatedRequirements = requirements.filterNot(e => e.id == id)
      if (updatedRequirements.isEmpty) {
        StateWithReactions(reactions, this.autoConfigState)
      } else {
        StateWithRequirementsAndReactions(
          NonEmptySet.fromSetUnsafe(updatedRequirements),
          reactions,
          this.autoConfigState
        )
      }
    }
    def addResource(resource: Resource): StateWithResourcesRequirementsAndReactions =
      StateWithResourcesRequirementsAndReactions(
        NonEmptySet.of(resource),
        requirements,
        reactions,
        this.autoConfigState
      )
    def removeResource(resource: Resource): StateWithRequirementsAndReactions = this
    def addReaction(reaction: ReactionModel): StateWithRequirementsAndReactions =
      StateWithRequirementsAndReactions(requirements, reactions.addReactionModel(reaction), this.autoConfigState)
    def removeReaction(id: ReactionId): StateWithRequirementsAndReactions | StateWithRequirements =
      reactions
        .removeReactionModel(id)
        .fold(StateWithRequirements(requirements, this.autoConfigState))(
          StateWithRequirementsAndReactions(requirements, _, this.autoConfigState)
        )
    def enableAutoConfiguration(): StateWithRequirementsAndReactions =
      StateWithRequirementsAndReactions(this.requirements, this.reactions, this.autoConfigState.enableAutoconfig())
    def disableAutoConfiguration(): StateWithRequirementsAndReactions =
      StateWithRequirementsAndReactions(this.requirements, this.reactions, this.autoConfigState.disableAutoconfig())

  case class StateWithResourcesRequirementsAndReactions(
    resources: NonEmptySet[Resource],
    requirements: NonEmptySet[RequirementsModel],
    reactions: Reactions,
    autoConfigState: AutoConfigState
  ) extends WithResources
      with WithReactions
      with WithRequirements:
    def addRequirements(requirement: RequirementsModel): StateWithResourcesRequirementsAndReactions = {
      val updatedRequirements = requirements.add(requirement)
      StateWithResourcesRequirementsAndReactions(
        resources,
        updatedRequirements,
        reactions,
        this.autoConfigState.setRequirimentsIfActive(updatedRequirements.toSortedSet.toSet)
      )
    }
    override def setActiveRequirments(
      activeRequirements: Set[RequirementsModel]
    ): StateWithResourcesRequirementsAndReactions = {
      val updatedAcs = autoConfigState.setActiveRequirements(activeRequirements)
      StateWithResourcesRequirementsAndReactions(
        this.resources,
        this.requirements,
        this.reactions,
        updatedAcs
      )
    }
    def removeRequirements(
      id: ConfigElementId
    ): StateWithResourcesRequirementsAndReactions | StateWithResourcesAndReactions = {
      val updatedRequirements = requirements.filterNot(e => e.id == id)
      if (updatedRequirements.isEmpty) {
        StateWithResourcesAndReactions(resources, reactions, this.autoConfigState)
      } else {
        StateWithResourcesRequirementsAndReactions(
          resources,
          NonEmptySet.fromSetUnsafe(updatedRequirements),
          reactions,
          this.autoConfigState
        )
      }
    }
    def addResource(resource: Resource): StateWithResourcesRequirementsAndReactions =
      StateWithResourcesRequirementsAndReactions(resources.add(resource), requirements, reactions, this.autoConfigState)
    def removeResource(
      resource: Resource
    ): StateWithResourcesRequirementsAndReactions | StateWithRequirementsAndReactions = removeResourceFromState(
      this,
      StateWithRequirementsAndReactions(requirements, reactions, this.autoConfigState),
      StateWithResourcesRequirementsAndReactions(_, requirements, reactions, this.autoConfigState)
    )(resource)
    def addReaction(reaction: ReactionModel): StateWithResourcesRequirementsAndReactions =
      StateWithResourcesRequirementsAndReactions(
        resources,
        requirements,
        reactions.addReactionModel(reaction),
        this.autoConfigState
      )
    def removeReaction(id: ReactionId): StateWithResourcesRequirementsAndReactions | StateWithResourcesAndRequirements =
      reactions
        .removeReactionModel(id)
        .fold(StateWithResourcesAndRequirements(resources, requirements, this.autoConfigState))(
          StateWithResourcesRequirementsAndReactions(resources, requirements, _, this.autoConfigState)
        )
    def enableAutoConfiguration(): StateWithResourcesRequirementsAndReactions =
      StateWithResourcesRequirementsAndReactions(
        this.resources,
        this.requirements,
        this.reactions,
        this.autoConfigState.enableAutoconfig()
      )
    def disableAutoConfiguration(): StateWithResourcesRequirementsAndReactions =
      StateWithResourcesRequirementsAndReactions(
        this.resources,
        this.requirements,
        this.reactions,
        this.autoConfigState.disableAutoconfig()
      )

  private def removeResourceFromState[CS <: WithResources, FS <: SystemConfigState](
    currentState: CS,
    fallbackState: => FS,
    sameStateFunction: NonEmptySet[Resource] => CS
  )(
    removedResource: Resource
  ): CS | FS = {
    val existingResourceWithoutResource = currentState.resources - removedResource
    if (existingResourceWithoutResource.isEmpty) {
      fallbackState
    } else {
      // It is safe, because argument is not empty
      sameStateFunction(NonEmptySet.fromSetUnsafe(existingResourceWithoutResource))
    }
  }
