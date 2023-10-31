package eu.assistiot.selfconfig.configuration.model

import cats.Order
import cats.data.NonEmptySet
import eu.assistiot.selfconfig.model.validation.{SelfConfigDomainValidators, ValidationResult}
import eu.assistiot.selfconfig.model.{ConfigElementId, LabelKey, LabelValue}

case class ReactionModel(
  reactionId: ReactionId,
  filterExpression: FilterExpression,
  action: ReactionAction
)

object ReactionModel:
  implicit val reactionModelOrder: Order[ReactionModel] = (x: ReactionModel, y: ReactionModel) =>
    x.reactionId.toString.compareTo(y.reactionId.toString)

opaque type ReactionId = String
object ReactionId:
  def apply(value: String): ValidationResult[ReactionId] =
    SelfConfigDomainValidators.nonBlankString("ReactionId")(value)
  def unsafe(value: String): ReactionId = ReactionId(value).toOption.get

sealed trait FilterExpression

case class ResourceIsAvailable(id: ConfigElementId)                                         extends FilterExpression
case class ResourceIsNoLongerAvailable(id: ConfigElementId)                                 extends FilterExpression
case class ResourceWithLabelIsAvailable(labelKey: LabelKey, labelValue: LabelValue)         extends FilterExpression
case class ResourceWithLabelIsNoLongerAvailable(labelKey: LabelKey, labelValue: LabelValue) extends FilterExpression
case object AnyEvent                                                                        extends FilterExpression
case class CustomMessageContent(content: String)                                            extends FilterExpression

sealed trait ReactionAction

case class SendSimpleKafkaMessage(topic: String, message: String)             extends ReactionAction
case class ReplaceConfiguration(requirements: NonEmptySet[RequirementsModel]) extends ReactionAction
case class UpsertConfiguration(requirements: NonEmptySet[RequirementsModel], removeDangling: Boolean)
    extends ReactionAction
case class ConditionalAction(conditionalCheck: Condition, action: ReactionAction, fallback: ReactionAction)
    extends ReactionAction
case object KeepHighestWeightFunctionalities extends ReactionAction
case object NoAction                         extends ReactionAction
