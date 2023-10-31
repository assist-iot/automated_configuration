package eu.assistiot.selfconfig.algebras

import cats.Id
import com.typesafe.scalalogging.LazyLogging
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.{toMap, Resource}

trait ConditionCheckOps[F[_]]:

  def conditionMet(
    stateRequirements: Set[RequirementsModel],
    stateResources: Set[Resource],
    maybeMessage: Option[CheckableMessageWithResource],
    condition: Condition
  ): F[Boolean]

object DefaultConditionCheck extends ConditionCheckOps[Id] with LazyLogging:
  override def conditionMet(
    stateRequirements: Set[RequirementsModel],
    stateResources: Set[Resource],
    maybeMessage: Option[CheckableMessageWithResource],
    messageCondition: Condition
  ): Id[Boolean] = {
    logger.debug(
      "StateRequirements {}, StateResources {}, message {}, messageCondition {}",
      stateRequirements,
      stateResources,
      maybeMessage,
      messageCondition
    )
    val result = messageCondition match {
      case AndCondition(conditions @ _*) =>
        conditions.forall(conditionMet(stateRequirements, stateResources, maybeMessage, _))
      case OrCondition(conditions @ _*) =>
        conditions.exists(conditionMet(stateRequirements, stateResources, maybeMessage, _))
      case NotCondition(condition) => !conditionMet(stateRequirements, stateResources, maybeMessage, condition)
      case MessageContainsResource(condition) if maybeMessage.isDefined =>
        conditionMet(stateRequirements, Set(maybeMessage.get.resource), maybeMessage, condition)
      case _: MessageContainsResource => false
      case MessageContainsResourceWithEvent(condition, event) if maybeMessage.isDefined =>
        event == maybeMessage.get.event && conditionMet(
          stateRequirements,
          stateResources,
          maybeMessage,
          MessageContainsResource(condition)
        )
      case _: MessageContainsResourceWithEvent => false
      case ContainsRequirements(requirements, fullMatch) =>
        if (fullMatch) {
          requirements == stateRequirements
        } else {
          requirements.subsetOf(stateRequirements)
        }
      case ContainsRequirementWithId(id) =>
        stateRequirements.exists(_.id == id)
      case ContainsRequirementsWithLabels(labels, fullMatch) =>
        stateRequirements.exists(requirement => {
          if (fullMatch) {
            requirement.labels.toMap == labels.toMap
          } else {
            labels.toMap.forall(kv => requirement.labels.toMap.exists(_ == kv))
          }
        })
      case ContainsRequirementsWithWeight(weight) =>
        stateRequirements.exists(_.weight == weight)
      case ContainsRequirementsWithRequirements(requirements, fullMatch) =>
        stateRequirements.exists(requirement => {
          if (fullMatch) {
            requirement.requirements == requirements
          } else {
            requirements.subsetOf(requirement.requirements)
          }
        })
      case ContainsResources(resources, fullMatch) =>
        if (fullMatch) {
          resources == stateResources
        } else {
          resources.subsetOf(stateResources)
        }
      case ContainsResourceWithId(id) =>
        stateResources.exists(_.id == id)
      case ContainsResourceWithLabels(labels, fullMatch) =>
        stateResources.exists(resource => {
          if (fullMatch) {
            resource.labels.toMap == labels.toMap
          } else {
            labels.toMap.forall(kv => resource.labels.toMap.exists(_ == kv))
          }
        })
    }
    logger.debug("Result check {}", result)
    result
  }
