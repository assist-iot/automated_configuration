package eu.assistiot.selfconfig.web

import cats.data.OptionT
import cats.effect.*
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.all.*
import eu.assistiot.selfconfig.algebras.RequirementsModelOps
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.*
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.RequirementsModelProtocol
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

class RequirementsRoutesTest extends AsyncWordSpec with AsyncIOSpec with Matchers with RequirementsModelProtocol {

  import RequirementsRoutesTest.*

  val routeInTest: HttpRoutes[IO] = new RequirementsRoutes(new TestRequirementsModelOps(IO.pure("Success"))).routes

  "Requirements routes" should {
    "echo a request with a body" in {
      val ioMaybeRequirementsModel: OptionT[IO, String] = routeInTest
        .run(POST(requirementsModelSampleJsonMessage, uri"/requirements-model"))
        .semiflatMap(_.as[String])
      ioMaybeRequirementsModel.value.asserting(maybeRequirementsModel => {
        maybeRequirementsModel.isDefined shouldBe true
        maybeRequirementsModel.get shouldBe "Added Requirements Model"
      })
    }
  }
}

object RequirementsRoutesTest {
  val requirementsModelSampleJsonMessage =
    json"""
      {
        "id": "element-id-1",
        "labels": {
          "key-1": "value-1"
        },
        "requirements": [{
          "id": "element-id-2",
          "exclusive": true
        }, {
          "count": 3,
          "labelKey": "key-2",
          "labelValue": "value-2",
          "exclusive": false
        }],
        "weight": 3.0
      }
      """

  class TestRequirementsModelOps(result: IO[String]) extends RequirementsModelOps[IO, String] {
    override def addRequirementsModel(requirementsModel: RequirementsModel): IO[String] = result

    override def removeRequirementsModel(id: ConfigElementId): IO[String] = result

    override def enableAutoconfiguration(): IO[String] = result

    override def disableAutoconfiguration(): IO[String] = result
  }
}
