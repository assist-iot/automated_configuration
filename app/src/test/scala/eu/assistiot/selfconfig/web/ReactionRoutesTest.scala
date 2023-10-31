package eu.assistiot.selfconfig.web

import cats.data.OptionT
import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.all.*
import eu.assistiot.selfconfig.algebras.ReactionModelOps
import eu.assistiot.selfconfig.configuration.model.{
  ReactionId,
  ReactionModel,
  ResourceIsAvailable,
  SendSimpleKafkaMessage
}
import eu.assistiot.selfconfig.model.ConfigElementId
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.ReactionModelProtocol
import io.circe.*
import io.circe.literal.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.client.dsl.io.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ReactionRoutesTest extends AsyncWordSpec with AsyncIOSpec with Matchers with ReactionModelProtocol {

  import ReactionRoutesTest.*

  val routeInTest: HttpRoutes[IO] = new ReactionRoutes(TestReactionModelOps(IO.pure("Success"))).routes

  "Reaction routes" should {
    "echo a request with a body" in {
      val ioMaybeReactionModel: OptionT[IO, String] = routeInTest
        .run(POST(reactionModelSampleJsonMessage, uri"/reaction-model"))
        .semiflatMap(_.as[String])
      ioMaybeReactionModel.value.asserting(maybeReactionModel => {
        maybeReactionModel.isDefined shouldBe true
        maybeReactionModel.get shouldBe "Added Reaction Model"
      })
    }
  }
}

object ReactionRoutesTest {

  val reactionModelSampleJsonMessage: Json =
    json"""
          {
            "action": {
              "message": "message",
              "topic": "topic"
            },
            "filterExpression": {
              "id": "element-id-1",
              "messageType": "ResourceIsAvailable"
            },
            "reactionId": "reaction-id-1"
          }
          """

  class TestReactionModelOps(result: IO[String]) extends ReactionModelOps[IO, String] {
    override def addReactionModel(reactionModel: ReactionModel): IO[String] = result

    override def removeReactionModel(id: ReactionId): IO[String] = result
  }
}
