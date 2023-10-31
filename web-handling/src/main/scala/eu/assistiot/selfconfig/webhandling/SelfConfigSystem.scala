package eu.assistiot.selfconfig.webhandling
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.{typed, ActorSystem}
import akka.util.Timeout
import cats.effect.*
import eu.assistiot.selfconfig.algebras.{ReactionModelOps, RequirementsModelOps}
import eu.assistiot.selfconfig.configuration.model.{ReactionId, ReactionModel, RequirementsModel}
import eu.assistiot.selfconfig.model.ConfigElementId
import eu.assistiot.selfconfig.smart.behavior.DefaultReactionHandler.DefaultReactionHandlerConfig
import eu.assistiot.selfconfig.smart.behavior.KafkaSendingNotifier.RequirementsMetKafkaConfig
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.RequirementsMetMessage
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.{
  AddFunctionalityModel,
  AddReactionModel,
  DisableAutoConfiguration,
  EnableAutoConfiguration,
  RemoveFunctionalityModel,
  RemoveReactionModel
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.{SystemConfigCommand, SystemConfigResponse}
import eu.assistiot.selfconfig.smart.behavior.{
  DefaultReactionHandler,
  KafkaSendingNotifier,
  ReactionHandler,
  SystemConfigBehavior
}
import eu.assistiot.selfconfig.webhandling.SelfConfigSystem.*

class SelfConfigSystem(dependencies: SelfConfigSystemDependencies, config: SelfConfigSystemConfiguration) {
  private val typedSystem: typed.ActorSystem[Nothing] = dependencies.system

  def initialize(): InitializedSelfConfigSystem = {
    val requirementsMetActor: ActorRef[RequirementsMetMessage] =
      typedSystem.systemActorOf(
        KafkaSendingNotifier(RequirementsMetKafkaConfig(config.requirementsMetTopicName, config.kafkaBoostrapServer)),
        "requirements-met-kafka"
      )
    val reactorActor: ActorRef[ReactionHandler.ReactionHandlerMessage] = typedSystem.systemActorOf(
      DefaultReactionHandler.apply(DefaultReactionHandlerConfig(config.kafkaBoostrapServer)),
      "reactor-kafka"
    )
    val selfConfigActor: ActorRef[SystemConfigCommand] =
      typedSystem.systemActorOf(SystemConfigBehavior(requirementsMetActor, reactorActor), "self-config-behavior")

    new InitializedSelfConfigSystem(selfConfigActor)(config.timeout, typedSystem)
  }
}

class InitializedSelfConfigSystem(
  val selfConfigActor: ActorRef[SystemConfigCommand]
)(implicit val timeout: Timeout, typedSystem: typed.ActorSystem[Nothing])
    extends ReactionModelOps[IO, String]
    with RequirementsModelOps[IO, String] {
  import akka.actor.typed.scaladsl.AskPattern.*

  override def addReactionModel(reactionModel: ReactionModel): IO[String] = {
    IO.fromFuture(IO.pure(selfConfigActor.ask(ref => AddReactionModel(reactionModel, ref))))
      .map { case response: SystemConfigResponse => mapSystemConfigResponseToString(response) }
  }

  override def removeReactionModel(id: ReactionId): IO[String] = {
    IO.fromFuture(IO.pure(selfConfigActor.ask(ref => RemoveReactionModel(id, ref))))
      .map { case response: SystemConfigResponse => mapSystemConfigResponseToString(response) }
  }

  override def addRequirementsModel(requirementsModel: RequirementsModel): IO[String] = {
    IO.fromFuture(IO.pure(selfConfigActor.ask(ref => AddFunctionalityModel(requirementsModel, ref))))
      .map { case response: SystemConfigResponse => mapSystemConfigResponseToString(response) }
  }

  override def removeRequirementsModel(id: ConfigElementId): IO[String] = {
    IO.fromFuture(IO.pure(selfConfigActor.ask(ref => RemoveFunctionalityModel(id, ref))))
      .map { case response: SystemConfigResponse => mapSystemConfigResponseToString(response) }
  }

  override def enableAutoconfiguration(): IO[String] = IO
    .fromFuture(IO.pure(selfConfigActor.ask(ref => EnableAutoConfiguration(ref))))
    .map { case response: SystemConfigResponse => mapSystemConfigResponseToString(response) }

  override def disableAutoconfiguration(): IO[String] = IO
    .fromFuture(IO.pure(selfConfigActor.ask(ref => DisableAutoConfiguration(ref))))
    .map { case response: SystemConfigResponse => mapSystemConfigResponseToString(response) }

  private def mapSystemConfigResponseToString(response: SystemConfigResponse): String = response match {
    case SystemConfigResponse.SystemConfigAck(command)              => s"Successfully executed $command "
    case SystemConfigResponse.SystemConfigFailure(command, message) => s"Failed executing $command with $message"
  }
}

object SelfConfigSystem {
  case class SelfConfigSystemDependencies(system: typed.ActorSystem[Nothing])

  case class SelfConfigSystemConfiguration(
    kafkaBoostrapServer: String,
    requirementsMetTopicName: String,
    timeout: Timeout
  )
}
