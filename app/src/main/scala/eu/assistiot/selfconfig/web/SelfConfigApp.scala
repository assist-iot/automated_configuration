package eu.assistiot.selfconfig.web

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.*
import akka.util.Timeout
import cats.effect.*
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import com.comcast.ip4s.*
import eu.assistiot.selfconfig.resourcelistener.KafkaSelfConfigMessageProvider
import eu.assistiot.selfconfig.webhandling.SelfConfigSystem
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.syntax.*

import scala.concurrent.ExecutionContext.global
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.*
import scala.io.StdIn
import scala.util.Properties

object SelfConfigApp extends IOApp {

  val actorSystem          = ActorSystem("self-config-actor-system").toTyped
  val kafkaServer          = Properties.envOrElse("KAFKA_SERVER", "localhost:29092")
  val requirementsMetTopic = Properties.envOrElse("REQUIREMENTS_MET_TOPIC", "requirement-met-topic")
  val requirementsMetGroup = Properties.envOrElse("REQUIREMENTS_MET_GROUP", "requirements-met-group")
  val resourcesTopic       = Properties.envOrElse("RESOURCES_TOPIC", "resources-topic")
  val timeout              = Timeout(5.seconds)

  val selfConfigSystem = new SelfConfigSystem(
    SelfConfigSystem.SelfConfigSystemDependencies(actorSystem),
    SelfConfigSystem.SelfConfigSystemConfiguration(kafkaServer, requirementsMetTopic, timeout)
  ).initialize()

  val resourceListener = new KafkaSelfConfigMessageProvider(
    KafkaSelfConfigMessageProvider.ResourceListenerDependencies(actorSystem, selfConfigSystem.selfConfigActor),
    KafkaSelfConfigMessageProvider.ResourceListenerConfig(resourcesTopic, kafkaServer, requirementsMetGroup, timeout)
  )
  resourceListener.buildStream().unsafeRunAndForget()

  val services = new ReactionRoutes(selfConfigSystem).routes <+> new RequirementsRoutes(selfConfigSystem).routes

  val httpApp = Router("/" -> services).orNotFound

  def run(args: List[String]): IO[ExitCode] = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }

}
