package eu.assistiot.selfconfig.resourcelistener

import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import eu.assistiot.selfconfig.algebras.*
import eu.assistiot.selfconfig.configuration.model.{FilterExpression, ResourceIsAvailable, ResourceIsNoLongerAvailable}
import eu.assistiot.selfconfig.model.Resource
import eu.assistiot.selfconfig.resourcelistener.KafkaSelfConfigMessageProvider.*
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.ReactionModelProtocol
import eu.assistiot.selfconfig.smart.behavior.SystemConfigBehavior
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.{SystemConfigCommand, SystemConfigResponse}
import fs2.*
import fs2.kafka.*
import io.circe.*
import io.circe.Decoder.Result

import scala.util.Try

type IOStream[A] = Stream[IO, A]

class KafkaSelfConfigMessageProvider(dependencies: ResourceListenerDependencies, config: ResourceListenerConfig)
    extends SelfConfigMessageProvider[
      IOStream,
      ConsumerSettings[IO, String, Either[Throwable, ResourceListenerMessage]]
    ]
    with LazyLogging {

  private val consumerSettings: ConsumerSettings[IO, String, Either[Throwable, ResourceListenerMessage]] =
    ConsumerSettings[IO, String, Either[Throwable, ResourceListenerMessage]]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(config.kafkaBootstrapServer)
      .withGroupId(config.groupId)

  override def provideMessages(
    settings: ConsumerSettings[IO, String, Either[Throwable, ResourceListenerMessage]]
  ): IOStream[ResourceListenerMessage] = KafkaConsumer
    .stream(settings)
    .subscribeTo(config.topicName)
    .records
    .map(_.record.value)
    .collect { case Right(message): Right[Throwable, ResourceListenerMessage] => message }

  def buildStream(): IO[Unit] = {
    val typedSystem = dependencies.system
    provideMessages(consumerSettings)
      .mapAsync(25) { resourceListenerMessage =>
        val command: ActorRef[SystemConfigResponse] => SystemConfigCommand = resourceListenerMessage match {
          case RegisterResource(resource) =>
            (replyTo: ActorRef[SystemConfigResponse]) => SystemConfigCommand.AddResource(resource, replyTo)
          case DeregisterResource(resource) =>
            (replyTo: ActorRef[SystemConfigResponse]) => SystemConfigCommand.RemoveResource(resource, replyTo)
          case CustomMessage(content) =>
            (replyTo: ActorRef[SystemConfigResponse]) => SystemConfigCommand.CustomMessage(content, replyTo)
        }
        IO.fromFuture(IO.pure(dependencies.selfConfigActor.ask(command)(config.timeout, typedSystem.scheduler)))
      }
      .compile
      .drain
  }
}

object KafkaSelfConfigMessageProvider extends ReactionModelProtocol with LazyLogging {

  case class ResourceListenerDependencies(
    system: ActorSystem[Nothing],
    selfConfigActor: ActorRef[SystemConfigCommand]
  )

  case class ResourceListenerConfig(
    topicName: String,
    kafkaBootstrapServer: String,
    groupId: String,
    timeout: Timeout
  )

  implicit val resourceListenerMessageDeserializer: Deserializer[IO, Either[Throwable, ResourceListenerMessage]] =
    Deserializer.instance { (_, _, bytes) =>
      val tryFilterExpression = for {
        rawJson <- Try { new String(bytes) }
        _ = logger.debug("Received message with content {}", rawJson)
        filterExpression <- parser.decode[ResourceListenerMessage](rawJson).toTry
      } yield filterExpression
      IO.fromTry(tryFilterExpression)
    }.attempt
}
