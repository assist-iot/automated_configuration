package eu.assistiot.selfconfig.smart.demos

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.NonEmptySet
import cats.effect.unsafe.implicits.global
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.*
import eu.assistiot.selfconfig.smart.behavior.*
import eu.assistiot.selfconfig.smart.behavior.DefaultReactionHandler.DefaultReactionHandlerConfig
import eu.assistiot.selfconfig.smart.behavior.KafkaSendingNotifier.RequirementsMetKafkaConfig
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.ReactionHandlerMessage
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.{
  RequirementsMet,
  RequirementsMetMessage,
  RequirementsNotMet
}
import eu.assistiot.selfconfig.smart.behavior.UnsafeKafkaProducer.KafkaConfig
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.{
  AddFunctionalityModel,
  AddReactionModel,
  AddResource,
  RemoveResource
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponse
import eu.assistiot.selfconfig.smart.behavior.utils.RequirementCheck

import scala.io.StdIn.{readChar, readLine}

object ExtendedDemo extends App {

  import eu.assistiot.selfconfig.smart.demos.DemoBehaviours.*
  import eu.assistiot.selfconfig.smart.demos.DemoObjects.*

  val actorSystem          = ActorSystem("self-config-actor-system").toTyped
  val kafkaServer          = "localhost:29092"
  val requirementsMetTopic = "requirement-met-topic"
  val requirementsMetActor: ActorRef[RequirementsMetMessage] =
    actorSystem.systemActorOf(
      KafkaSendingNotifier(RequirementsMetKafkaConfig(requirementsMetTopic, kafkaServer)),
      "requirements-met-kafka"
    )
  val reactorActor =
    actorSystem.systemActorOf(DefaultReactionHandler.apply(DefaultReactionHandlerConfig(kafkaServer)), "reactor-kafka")
  val responseProbe = actorSystem.systemActorOf(ResponseProbeBehavior, "response-probe")
  val selfConfigActor =
    actorSystem.systemActorOf(SystemConfigBehavior(requirementsMetActor, reactorActor), "self-config-behavior")
  val kafkaListener1 =
    TestKafkaConsumer.apply(kafkaServer, "requirements-met-listener", requirementsMetTopic).unsafeRunAndForget()
  val kafkaListener2 =
    TestKafkaConsumer.apply(kafkaServer, "reaction-listener", requirementsMetTopic).unsafeRunAndForget()

  println(Console.BLUE + "[Step 1/Start] Defining Requirements ... ")
  readLine()

  selfConfigActor ! AddFunctionalityModel(SecurityFunctionality, responseProbe)
  selfConfigActor ! AddFunctionalityModel(ThermostatFunctionality, responseProbe)
  selfConfigActor ! AddFunctionalityModel(SmartHomeFunctionality, responseProbe)

  println(Console.BLUE + "[Step 1/End] Defined Requirements ")
  readLine()

  println(Console.BLUE + "[Step 2/Start] Set reaction model ...")
  readLine()

  selfConfigActor ! AddReactionModel(WideViewCamReactionModel, responseProbe)

  readLine()
  println(Console.BLUE + "[Step 2/End] Set reaction model")
  println(Console.BLUE + "[Step 3/Start] Add security_functionality resources...")
  readLine()

  selfConfigActor ! AddResource(WideViewCamera, responseProbe)
  selfConfigActor ! AddResource(FrontCamera, responseProbe)
  selfConfigActor ! AddResource(BackCamera, responseProbe)

  readLine()
  println(Console.BLUE + "[Step 3/End] Added security_functionality resources")

  println(Console.BLUE + "[Step 4/Start] Add rest of the resources...")
  readLine()

  selfConfigActor ! AddResource(SmartHomeManagerResource, responseProbe)
  selfConfigActor ! AddResource(ThermometerResource, responseProbe)
  selfConfigActor ! AddResource(AcResource, responseProbe)

  readLine()
  println(Console.BLUE + "[Step 4/End] Added rest of the resources")
  readLine()

  println(Console.BLUE + "[Step 5/Start] Reaction to ac going down")

  selfConfigActor ! AddReactionModel(AcGoesDownReactionModel, responseProbe)
  readLine()

  println(Console.BLUE + "[Step 5/End] Reaction to ac going down")
  readLine()

  println(Console.BLUE + "[Step 6/Start] Remove AC")
  readLine()

  selfConfigActor ! RemoveResource(AcResource, responseProbe)
  readLine()

  println(Console.BLUE + "[Step 6/End] Remove AC")

}
