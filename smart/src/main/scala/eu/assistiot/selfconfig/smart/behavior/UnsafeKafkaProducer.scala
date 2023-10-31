package eu.assistiot.selfconfig.smart.behavior

import cats.effect
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import eu.assistiot.selfconfig.smart.behavior.UnsafeKafkaProducer.KafkaConfig
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerSettings}
import io.circe.{Encoder, Json}
import io.circe.syntax.*

import java.util.UUID

class UnsafeKafkaProducer(config: KafkaConfig) {

  private val producerSettings = ProducerSettings[IO, String, String]
    .withBootstrapServers(config.bootstrapServer)
  private val kafkaProducerResource = KafkaProducer.resource(producerSettings)

  def sendMessageToKafka[M: Encoder, T: Encoder](
    topicName: String,
    trigger: T,
    content: M
  ): Unit = {
    val message = Json.obj(
      "trigger" -> trigger.asJson,
      "content" -> content.asJson
    )
    sendMessageToKafka(topicName, message)
  }

  def sendMessageToKafka[M: Encoder](
    topicName: String,
    message: M
  ): Unit = {
    kafkaProducerResource
      .use(kafkaProducer => {
        val record =
          ProducerRecord(topic = topicName, key = UUID.randomUUID().toString, value = message.asJson.spaces2)
        kafkaProducer.produceOne_(record)
      })
      .flatten
      .unsafeRunSync()
  }

}

object UnsafeKafkaProducer {
  case class KafkaConfig(
    bootstrapServer: String
  )

  def apply(bootstrapServer: String): UnsafeKafkaProducer = new UnsafeKafkaProducer(KafkaConfig(bootstrapServer)) {}
}
