package eu.assistiot.selfconfig.smart.demos

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.data.NonEmptySet
import cats.effect.*
import cats.syntax.all.*
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.{Resource, *}
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.RequirementsModelProtocol
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.ReactionHandlerMessage
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.{
  RequirementsMet,
  RequirementsMetMessage,
  RequirementsNotMet
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponse
import eu.assistiot.selfconfig.smart.behavior.utils.RequirementCheck
import fs2.kafka.*

import scala.concurrent.duration.*

object DemoObjects {

  // ids
  private val SmartHomeManagerId: ConfigElementId        = ConfigElementId.unsafe("smart_home_manager")
  private val ThermostatFunctionalityId: ConfigElementId = ConfigElementId.unsafe("thermostat_functionality")
  private val SecurityFunctionalityId: ConfigElementId   = ConfigElementId.unsafe("security_functionality")
  private val WideViewCamId: ConfigElementId             = ConfigElementId.unsafe("wide_view_cam")
  private val ThermometerId: ConfigElementId             = ConfigElementId.unsafe("thermometer")
  private val AcId: ConfigElementId                      = ConfigElementId.unsafe("ac")

  val SmartHomeFunctionality: RequirementsModel = RequirementsModel(
    ConfigElementId.unsafe("smart_home_functionality"),
    LabelMap.empty,
    Set(
      IdBasedRequirement(SmartHomeManagerId),
      IdBasedRequirement(ThermostatFunctionalityId),
      IdBasedRequirement(SecurityFunctionalityId),
      IdBasedRequirement(WideViewCamId)
    ),
    FunctionalityWeight.unsafe(4.0d)
  )

  val SecurityFunctionality: RequirementsModel = RequirementsModel(
    SecurityFunctionalityId,
    LabelMap.empty,
    Set(
      IdBasedRequirement(WideViewCamId),
      LabelBasedRequirement(LabelKey.unsafe("device_type"), LabelValue.unsafe("camera"), 3)
    ),
    FunctionalityWeight.unsafe(2.0d)
  )

  val ThermostatFunctionality: RequirementsModel = RequirementsModel(
    ThermostatFunctionalityId,
    LabelMap.empty,
    Set(
      IdBasedRequirement(ThermometerId),
      IdBasedRequirement(AcId)
    ),
    FunctionalityWeight.unsafe(1.0d)
  )

  val SmartHomeManagerResource: Resource = Resource(
    SmartHomeManagerId,
    LabelMap(Map(LabelKey.unsafe("resource_type") -> LabelValue.unsafe("computer")))
  )

  val ThermometerResource: Resource = Resource(
    ThermometerId,
    LabelMap(Map(LabelKey.unsafe("resource_type") -> LabelValue.unsafe("sensor")))
  )

  val AcResource: Resource = Resource(AcId, LabelMap.empty)

  private val CameraLabelMap: LabelMap = LabelMap(Map(LabelKey.unsafe("device_type") -> LabelValue.unsafe("camera")))

  val WideViewCamera: Resource = Resource(WideViewCamId, CameraLabelMap)
  val FrontCamera: Resource    = Resource(ConfigElementId.unsafe("front_camera"), CameraLabelMap)
  val BackCamera: Resource     = Resource(ConfigElementId.unsafe("back_camera"), CameraLabelMap)

  val ReactionTopic: String = "reaction-kafka-topic"

  val WideViewCamReactionModel: ReactionModel = ReactionModel(
    ReactionId.unsafe("wide-view-cam-reaction"),
    ResourceIsAvailable(WideViewCamId),
    SendSimpleKafkaMessage(ReactionTopic, "FPS: 60")
  )

  val AcGoesDownReactionModel: ReactionModel = ReactionModel(
    ReactionId.unsafe("ac-goes-down-reaction"),
    ResourceIsNoLongerAvailable(AcId),
    ReplaceConfiguration(
      NonEmptySet.of(
        SmartHomeFunctionality.copy(
          requirements = Set(
            IdBasedRequirement(SmartHomeManagerId),
            IdBasedRequirement(SecurityFunctionalityId),
            IdBasedRequirement(WideViewCamId)
          )
        ),
        SecurityFunctionality
      )
    )
  )
}

object DemoBehaviours {

  val RequirementsMetProbeBehavior: Behavior[RequirementsMetMessage] = {
    val RequirementsMetChecker = new RequirementCheck {}
    Behaviors.receiveMessage {
      case RequirementsMet(_, _, _) =>
        println(Console.WHITE + "[RequirementsMetProbe] All requirements are met \n\n ")
        Behaviors.same
      case RequirementsNotMet(requirements, resources) =>
        println(Console.RED + "[RequirementsMetProbe] =|=|=|=|= Requirements are not met =|=|=|=|= ")
        requirements.foreach(requirementModel => {
          println(Console.RED + s"======== Start (Naive) Check for ${requirementModel.id} ========")
          if (RequirementsMetChecker.areRequirementsMet(Set(requirementModel), resources)) {
            println(
              Console.RED + s"Requirements Met for \n RequirementModel: $requirementModel \n Resources: $resources"
            )
          } else {
            println(
              Console.RED + s"Requirements Not Met for \n RequirementModel: $requirementModel \n Resources: $resources"
            )
          }
          println(Console.RED + s"======== End (Naive) Check for ${requirementModel.id} ======== \n\n\n")
        })
        Behaviors.same
    }
  }

  val ReactorProbeBehavior: Behavior[ReactionHandlerMessage] = Behaviors.receiveMessage {
    case ReactionHandler.HandleReaction(_, reactionAction, _, _) =>
      println(Console.GREEN + s"****** Received Reaction $reactionAction ******")
      reactionAction match {
        case SendSimpleKafkaMessage(topic, message) =>
          println(Console.GREEN + s"Sending Kafka Message to topic $topic with message: $message")
        case ReplaceConfiguration(requirements) =>
          println(Console.GREEN + s"Updating configuration. Will set requirements to $requirements")
      }
      Behaviors.same
  }

  val ResponseProbeBehavior: Behavior[SystemConfigResponse] = Behaviors.receiveMessage(message => {
    println(Console.YELLOW + s"[ResponseProbeBehavior] received message: $message")
    Behaviors.same
  })

}

object TestKafkaConsumer {
  def apply(bootstrapServer: String, groupId: String, topic: String): IO[Unit] = {
    val consumerSettings: ConsumerSettings[IO, String, String] = {
      ConsumerSettings[IO, String, String]
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers(bootstrapServer)
        .withGroupId(groupId)
    }
    KafkaConsumer
      .stream(consumerSettings)
      .subscribeTo(topic)
      .records
      .evalMap(committable => processRecord(committable.record))
      .compile
      .drain
  }

  private def processRecord(record: ConsumerRecord[String, String]): IO[Unit] =
    IO(println(s"[KAFKA] Processing record: $record"))

}
