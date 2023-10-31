package eu.assistiot.selfconfig.model

import cats.Order
import cats.data.NonEmptySet
import cats.implicits.*
import eu.assistiot.selfconfig.model
import eu.assistiot.selfconfig.model.validation.{SelfConfigDomainValidators, ValidationResult}

case class Resource(id: ConfigElementId, labels: LabelMap)

object Resource:
  implicit val resourceOrder: Order[Resource] = Order.by(_.id.toString)

opaque type ConfigElementId = String
object ConfigElementId:
  def apply(value: String): ValidationResult[ConfigElementId] =
    SelfConfigDomainValidators.nonBlankString("ConfigElementId")(value)

  def unsafe(value: String): ConfigElementId = ConfigElementId(value).toOption.get

  implicit val configElementIdOrder: Order[ConfigElementId] = (x: ConfigElementId, y: ConfigElementId) => x.compareTo(y)

opaque type LabelKey = String
object LabelKey:
  def apply(value: String): ValidationResult[LabelKey] = SelfConfigDomainValidators.nonBlankString("LabelKey")(value)
  def unsafe(value: String): LabelKey                  = LabelKey(value).toOption.get

opaque type LabelValue = String
object LabelValue:
  def apply(value: String): ValidationResult[LabelValue] =
    SelfConfigDomainValidators.nonBlankString("LabelValue")(value)
  def unsafe(value: String): LabelValue = LabelValue.apply(value).toOption.get

opaque type LabelMap = Map[LabelKey, LabelValue]
object LabelMap:
  def apply(map: Map[LabelKey, LabelValue]): LabelMap = map
  def empty: LabelMap                                 = Map.empty
extension (lm: LabelMap) def toMap: Map[LabelKey, LabelValue] = lm

opaque type FunctionalityWeight = Double
object FunctionalityWeight:
  def apply(value: Double): ValidationResult[FunctionalityWeight] =
    SelfConfigDomainValidators.positiveDouble("FunctionalityWeight")(value)
  def unsafe(value: Double): FunctionalityWeight = FunctionalityWeight(value).toOption.get
extension (fw: FunctionalityWeight) def toDouble: Double = fw

sealed trait FunctionalityRequirement {
  val exclusive: Boolean
}
case class IdBasedRequirement(id: ConfigElementId, exclusive: Boolean = false) extends FunctionalityRequirement
object IdBasedRequirement {
  import ConfigElementId.configElementIdOrder
  implicit val idBasedRequirementOrder: Order[IdBasedRequirement] =
    Order.by[IdBasedRequirement, ConfigElementId](_.id)(configElementIdOrder)
}
case class LabelBasedRequirement(labelKey: LabelKey, labelValue: LabelValue, count: Int, exclusive: Boolean = false)
    extends FunctionalityRequirement
