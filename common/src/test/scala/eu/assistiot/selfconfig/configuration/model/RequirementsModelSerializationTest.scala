package eu.assistiot.selfconfig.configuration.model

import eu.assistiot.selfconfig.model.*
import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.RequirementsModelProtocol
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
class RequirementsModelSerializationTest extends AnyWordSpec with should.Matchers with RequirementsModelProtocol {

  import RequirementsModelSerializationTest.*

  "FunctionalityModel serialization" should {
    "be reversible" in {
      decode[List[RequirementsModel]](
        SerializedRequirementsJson
      ).toOption.get.asJson.spaces2 shouldBe SerializedRequirementsJson
    }
  }
}

object RequirementsModelSerializationTest {

  val SerializedRequirementsJson: String =
    """[
      |  {
      |    "id" : "test1",
      |    "labels" : {
      |      "key1" : "value1"
      |    },
      |    "requirements" : [
      |      {
      |        "id" : "test2",
      |        "exclusive" : false
      |      },
      |      {
      |        "labelKey" : "key2",
      |        "labelValue" : "value2",
      |        "count" : 3,
      |        "exclusive" : true
      |      }
      |    ],
      |    "weight" : 3.0
      |  },
      |  {
      |    "id" : "test2",
      |    "labels" : {
      |      "key3" : "value3"
      |    },
      |    "requirements" : [
      |      {
      |        "id" : "test4",
      |        "exclusive" : true
      |      },
      |      {
      |        "labelKey" : "key5",
      |        "labelValue" : "value5",
      |        "count" : 5,
      |        "exclusive" : false
      |      }
      |    ],
      |    "weight" : 3.0
      |  }
      |]""".stripMargin
}
