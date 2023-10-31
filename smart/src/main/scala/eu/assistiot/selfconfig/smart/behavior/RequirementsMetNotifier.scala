package eu.assistiot.selfconfig.smart.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.data.NonEmptySet
import cats.effect
import cats.effect.*
import cats.effect.unsafe.implicits.global
import eu.assistiot.selfconfig.configuration.model.RequirementsModel
import eu.assistiot.selfconfig.model.Resource
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.RequirementsModelProtocol
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.RequirementsMetMessage
import fs2.kafka.*
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.apache.kafka.clients.producer.RecordMetadata

import java.util.UUID
import scala.concurrent.duration.*

object RequirementsMetNotifier {
  sealed trait RequirementsMetMessage
  case class RequirementsMet(
    metRequirements: Set[RequirementsModel],
    resources: Set[Resource],
    allRequirements: Set[RequirementsModel]
  ) extends RequirementsMetMessage
  case class RequirementsNotMet(allRequirements: Set[RequirementsModel], resources: Set[Resource])
      extends RequirementsMetMessage
}

object KafkaSendingNotifier extends RequirementsModelProtocol {

  def apply(config: RequirementsMetKafkaConfig): Behavior[RequirementsMetMessage] = {
    val kafkaProducer = UnsafeKafkaProducer(config.bootstrapServer)
    Behaviors.receiveMessage[RequirementsMetMessage] {
      case rm: RequirementsMetNotifier.RequirementsMet =>
        kafkaProducer.sendMessageToKafka(
          config.topicName,
          rm.asJson.deepMerge(Json.obj("type" -> Json.fromString("RequirementsMet")))
        )
        Behaviors.same
      case rnm: RequirementsMetNotifier.RequirementsNotMet =>
        kafkaProducer.sendMessageToKafka(
          config.topicName,
          rnm.asJson.deepMerge(Json.obj("type" -> Json.fromString("RequirementsNotMet")))
        )
        Behaviors.same
    }
  }

  case class RequirementsMetKafkaConfig(
    topicName: String,
    bootstrapServer: String
  )
}
