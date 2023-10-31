package eu.assistiot.selfconfig.configuration.model

import cats.syntax.functor.*
import eu.assistiot.selfconfig.model.*
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.{
  ConfigElementProtocol,
  RequirementsModelProtocol
}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import io.circe.*

sealed trait Condition:
  def conditionName: String

object Condition extends RequirementsModelProtocol {

  implicit val encoder: Encoder[Condition] = Encoder.instance { c =>
    val rawJson = c match {
      case and: AndCondition                          => and.asJson
      case or: OrCondition                            => or.asJson
      case not: NotCondition                          => not.asJson
      case cr: ContainsRequirements                   => cr.asJson
      case crwid: ContainsRequirementWithId           => crwid.asJson
      case crwl: ContainsRequirementsWithLabels       => crwl.asJson
      case crww: ContainsRequirementsWithWeight       => crww.asJson
      case crwr: ContainsRequirementsWithRequirements => crwr.asJson
      case cr: ContainsResources                      => cr.asJson
      case crwid: ContainsResourceWithId              => crwid.asJson
      case crwl: ContainsResourceWithLabels           => crwl.asJson
      case mcr: MessageContainsResource               => mcr.asJson
      case mcrwe: MessageContainsResourceWithEvent    => mcrwe.asJson
    }
    rawJson.deepMerge(Json.obj("condition_name" -> c.conditionName.asJson))
  }

  implicit val decoder: Decoder[Condition] = Decoder.instance { c =>
    c.downField("condition_name").as[String].flatMap {
      case "AndCondition"                         => c.as[AndCondition]
      case "OrCondition"                          => c.as[OrCondition]
      case "NotCondition"                         => c.as[NotCondition]
      case "ContainsRequirements"                 => c.as[ContainsRequirements]
      case "ContainsRequirementWithId"            => c.as[ContainsRequirementWithId]
      case "ContainsRequirementsWithLabels"       => c.as[ContainsRequirementsWithLabels]
      case "ContainsRequirementsWithWeight"       => c.as[ContainsRequirementsWithWeight]
      case "ContainsRequirementsWithRequirements" => c.as[ContainsRequirementsWithRequirements]
      case "ContainsResources"                    => c.as[ContainsResources]
      case "ContainsResourceWithId"               => c.as[ContainsResourceWithId]
      case "ContainsResourceWithLabels"           => c.as[ContainsResourceWithLabels]
      case "MessageContainsResource"              => c.as[MessageContainsResource]
      case "MessageContainsResourceWithEvent"     => c.as[MessageContainsResourceWithEvent]
      case other => Left(DecodingFailure(s"Unknown condition name $other", List(CursorOp.DownField("condition_name"))))
    }

  }
}

// General Conditions
final case class AndCondition(conditions: Condition*) extends Condition:
  override def conditionName: String = "AndCondition"
final case class OrCondition(conditions: Condition*) extends Condition:
  override def conditionName: String = "OrCondition"
final case class NotCondition(condition: Condition) extends Condition:
  override def conditionName: String = "NotCondition"

// RequirementsModel Conditions
final case class ContainsRequirements(requirements: Set[RequirementsModel], fullMatch: Boolean) extends Condition:
  override def conditionName: String = "ContainsRequirements"
final case class ContainsRequirementWithId(id: ConfigElementId) extends Condition:
  override def conditionName: String = "ContainsRequirementWithId"
final case class ContainsRequirementsWithLabels(labels: LabelMap, fullMatch: Boolean) extends Condition:
  override def conditionName: String = "ContainsRequirementsWithLabels"
final case class ContainsRequirementsWithWeight(weight: FunctionalityWeight) extends Condition:
  override def conditionName: String = "ContainsRequirementsWithWeight"
final case class ContainsRequirementsWithRequirements(requirements: Set[FunctionalityRequirement], fullMatch: Boolean)
    extends Condition:
  override def conditionName: String = "ContainsRequirementsWithRequirements"

// Resource Conditions
sealed trait ResourceCondition extends Condition
final case class ContainsResources(resources: Set[Resource], fullMatch: Boolean) extends ResourceCondition:
  override def conditionName: String = "ContainsResources"
final case class ContainsResourceWithId(id: ConfigElementId) extends ResourceCondition:
  override def conditionName: String = "ContainsResourceWithId"
final case class ContainsResourceWithLabels(labels: LabelMap, fullMatch: Boolean) extends ResourceCondition:
  override def conditionName: String = "ContainsResourceWithLabels"

// CheckableMessageConditions
final case class MessageContainsResource(condition: ResourceCondition) extends Condition:
  override def conditionName: String = "MessageContainsResource"
final case class MessageContainsResourceWithEvent(condition: ResourceCondition, event: CheckableMessageEvent)
    extends Condition:
  override def conditionName: String = "MessageContainsResourceWithEvent"

case class CheckableMessageWithResource(resource: Resource, event: CheckableMessageEvent)

object CheckableMessageWithResource extends ConfigElementProtocol:
  implicit val codec: Codec[CheckableMessageWithResource] = deriveCodec

sealed trait CheckableMessageEvent

object CheckableMessageEvent:
  implicit val encoder: Encoder[CheckableMessageEvent] = Encoder.instance {
    case Available    => "Available".asJson
    case NotAvailable => "NotAvailable".asJson
  }
  implicit val decoder: Decoder[CheckableMessageEvent] = Decoder.decodeString.emap {
    case "Available"    => Right(Available)
    case "NotAvailable" => Right(NotAvailable)
    case other          => Left(s"Unknown type $other")
  }

case object Available    extends CheckableMessageEvent
case object NotAvailable extends CheckableMessageEvent
