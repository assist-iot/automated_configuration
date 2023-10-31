package eu.assistiot.selfconfig.smart.demos

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.{ActorRef, Behavior}
import cats.data.NonEmptySet
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.*
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.ReactionHandlerMessage
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.{
  RequirementsMet,
  RequirementsMetMessage,
  RequirementsNotMet
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.{
  AddFunctionalityModel,
  AddReactionModel,
  AddResource,
  RemoveResource
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponse
import eu.assistiot.selfconfig.smart.behavior.utils.RequirementCheck
import eu.assistiot.selfconfig.smart.behavior.{ReactionHandler, RequirementsMetNotifier, SystemConfigBehavior}

import scala.io.StdIn.{readChar, readLine}

object SimpleDemo extends App {

  import eu.assistiot.selfconfig.smart.demos.DemoBehaviours.*
  import eu.assistiot.selfconfig.smart.demos.DemoObjects.*

  val actorSystem = ActorSystem("self-config-actor-system").toTyped

  val requirementsMetProbe: ActorRef[RequirementsMetMessage] =
    actorSystem.systemActorOf(RequirementsMetProbeBehavior, "requirements-met-probe")
  val reactorProbe  = actorSystem.systemActorOf(ReactorProbeBehavior, "reactor-probe")
  val responseProbe = actorSystem.systemActorOf(ResponseProbeBehavior, "response-probe")
  val selfConfigActor =
    actorSystem.systemActorOf(SystemConfigBehavior(requirementsMetProbe, reactorProbe), "self-config-behavior")

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
