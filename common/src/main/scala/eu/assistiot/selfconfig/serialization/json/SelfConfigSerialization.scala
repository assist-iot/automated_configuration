package eu.assistiot.selfconfig.serialization.json

import cats.data.NonEmptySet
import cats.syntax.functor.*
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.*
import eu.assistiot.selfconfig.model.validation.ValidationResult.*
import io.circe.*
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import io.circe.syntax.*

object SelfConfigSerialization {

  trait ReactionModelProtocol extends RequirementsModelProtocol {

    implicit val reactionIdDecoder: Decoder[ReactionId] =
      Decoder.decodeString.emap(raw => toStringEither(ReactionId.apply(raw)))
    implicit val reactionIdEncoder: Encoder[ReactionId] = Encoder.encodeString.contramap(_.toString)

    implicit val sendSimpleKafkaMessageCodec: Codec[SendSimpleKafkaMessage] = deriveCodec
    implicit val replaceConfigurationCodec: Codec[ReplaceConfiguration]     = deriveCodec
    implicit val upsertConfigurationCodec: Codec[UpsertConfiguration]       = deriveCodec
    implicit val conditionalActionCodec: Codec[ConditionalAction]           = deriveCodec
    implicit val highestFunctionalityCodec: Codec[KeepHighestWeightFunctionalities.type] = Codec.from(
      Decoder.decodeString.emap {
        case "KeepHighestWeightFunctionalities" => Right(KeepHighestWeightFunctionalities)
        case other                              => Left(s"Unknown string: $other")
      },
      Encoder.encodeString.contramap(_ => "KeepHighestWeightFunctionalities")
    )
    implicit val noActionCodec: Codec[NoAction.type] = Codec.from(
      Decoder.decodeString.emap {
        case "NoAction" => Right(NoAction)
        case other      => Left(s"Unknown string: $other")
      },
      Encoder.encodeString.contramap(_ => "NoAction")
    )

    implicit val reactionActionCodec: Codec[ReactionAction] = Codec.from(
      List[Decoder[ReactionAction]](
        Decoder[SendSimpleKafkaMessage].widen,
        Decoder[ReplaceConfiguration].widen,
        Decoder[UpsertConfiguration].widen,
        Decoder[ConditionalAction].widen,
        Decoder[KeepHighestWeightFunctionalities.type].widen,
        Decoder[NoAction.type].widen
      ).reduceLeft(_ or _),
      Encoder.instance {
        case sskm: SendSimpleKafkaMessage     => sskm.asJson
        case uc: UpsertConfiguration          => uc.asJson
        case rc: ReplaceConfiguration         => rc.asJson
        case ca: ConditionalAction            => ca.asJson
        case KeepHighestWeightFunctionalities => KeepHighestWeightFunctionalities.asJson
        case NoAction                         => NoAction.asJson
      }
    )

    implicit val resourceIsAvailableCodec: Codec[ResourceIsAvailable]                                   = deriveCodec
    implicit val resourceIsNoLongerAvailableCodec: Codec[ResourceIsNoLongerAvailable]                   = deriveCodec
    implicit val resourceWithLabelIsAvailableCodec: Codec[ResourceWithLabelIsAvailable]                 = deriveCodec
    implicit val resourceWithLabelIsNoLongerAvailableCodec: Codec[ResourceWithLabelIsNoLongerAvailable] = deriveCodec
    implicit val anyEventCodec: Codec[AnyEvent.type]                                                    = deriveCodec
    implicit val customMessageContentCodec: Codec[CustomMessageContent]                                 = deriveCodec

    implicit val filterExpressionCodec: Codec[FilterExpression] = Codec.from(
      Decoder.instance(c => {
        for {
          messageType <- c.downField("messageType").as[String]
          result <- messageType match {
            case "ResourceIsAvailable"                  => c.as[ResourceIsAvailable]
            case "ResourceIsNoLongerAvailable"          => c.as[ResourceIsNoLongerAvailable]
            case "ResourceWithLabelIsAvailable"         => c.as[ResourceWithLabelIsAvailable]
            case "ResourceWithLabelIsNoLongerAvailable" => c.as[ResourceWithLabelIsNoLongerAvailable]
            case "AnyEvent"                             => c.as[AnyEvent.type]
            case "CustomMessageContent"                 => c.as[CustomMessageContent]
          }
        } yield result
      }),
      Encoder.instance {
        case ria: ResourceIsAvailable =>
          ria.asJson.deepMerge(Json.obj("messageType" -> Json.fromString("ResourceIsAvailable")))
        case rinla: ResourceIsNoLongerAvailable =>
          rinla.asJson.deepMerge(Json.obj("messageType" -> Json.fromString("ResourceIsNoLongerAvailable")))
        case rwlia: ResourceWithLabelIsAvailable =>
          rwlia.asJson.deepMerge(Json.obj("messageType" -> Json.fromString("ResourceWithLabelIsAvailable")))
        case rwlinla: ResourceWithLabelIsNoLongerAvailable =>
          rwlinla.asJson.deepMerge(Json.obj("messageType" -> Json.fromString("ResourceWithLabelIsNoLongerAvailable")))
        case AnyEvent => AnyEvent.asJson.deepMerge(Json.obj("messageType" -> Json.fromString("AnyEvent")))
        case cmc: CustomMessageContent =>
          cmc.asJson.deepMerge(Json.obj("messageType" -> Json.fromString("CustomMessageContent")))
      }
    )

    implicit val reactionModelCodec: Codec[ReactionModel] = deriveCodec

  }

  trait RequirementsModelProtocol extends ConfigElementProtocol {

    implicit val functionalityModelDecoder: Decoder[RequirementsModel] = deriveDecoder[RequirementsModel]
    implicit val functionalityModelEncoder: Encoder[RequirementsModel] = deriveEncoder[RequirementsModel]
  }

  trait ConfigElementProtocol {

    implicit val configElementIdDecoder: Decoder[ConfigElementId] =
      Decoder.decodeString.emap(raw => toStringEither(ConfigElementId.apply(raw)))
    implicit val configElementIdEncoder: Encoder[ConfigElementId] = Encoder.encodeString.contramap(_.toString)

    implicit val labelKeyDecoder: Decoder[LabelKey] =
      Decoder.decodeString.emap(raw => toStringEither(LabelKey(raw)))
    implicit val labelKeyEncoder: Encoder[LabelKey] = Encoder.encodeString.contramap(_.toString)

    implicit val labelKeyAsKeyDecoder: KeyDecoder[LabelKey] = (key: String) => LabelKey(key).toOption
    implicit val labelKeyAsKeyEncoder: KeyEncoder[LabelKey] = (key: LabelKey) => key.toString

    implicit val labelValueDecoder: Decoder[LabelValue] =
      Decoder.decodeString.emap(raw => toStringEither(LabelValue.apply(raw)))
    implicit val labelValueEncoder: Encoder[LabelValue] = Encoder.encodeString.contramap(_.toString)

    implicit val idBasedRequirementDecoder: Decoder[IdBasedRequirement] = deriveDecoder
    implicit val idBasedRequirementEncoder: Encoder[IdBasedRequirement] = deriveEncoder

    implicit val labelBasedRequirementDecoder: Decoder[LabelBasedRequirement] = deriveDecoder
    implicit val labelBasedRequirementEncoder: Encoder[LabelBasedRequirement] = deriveEncoder

    implicit val functionalityRequirementDecoder: Decoder[FunctionalityRequirement] =
      List[Decoder[FunctionalityRequirement]](
        Decoder[IdBasedRequirement].widen,
        Decoder[LabelBasedRequirement].widen
      ).reduceLeft(_ or _)
    implicit val functionalityRequirementEncoder: Encoder[FunctionalityRequirement] = Encoder.instance {
      case ibr: IdBasedRequirement    => ibr.asJson
      case lbr: LabelBasedRequirement => lbr.asJson
    }

    implicit val labelMapDecoder: Decoder[LabelMap] = Decoder.decodeMap[LabelKey, LabelValue].map(LabelMap.apply)
    implicit val labelMapEncoder: Encoder[LabelMap] = Encoder.encodeMap[LabelKey, LabelValue].contramap(_.toMap)

    implicit val functionalityWeightDecoder: Decoder[FunctionalityWeight] =
      Decoder.decodeDouble.emap(raw => toStringEither(FunctionalityWeight(raw)))
    implicit val functionalityWeightEncoder: Encoder[FunctionalityWeight] = Encoder.encodeDouble.contramap(_.toDouble)

    implicit val resourceCodec: Codec[Resource] = deriveCodec

  }

}
