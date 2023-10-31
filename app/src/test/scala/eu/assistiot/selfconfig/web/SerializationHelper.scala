//package eu.assistiot.selfconfig.web
//
//import eu.assistiot.selfconfig.algebras.RegisterResource
//import eu.assistiot.selfconfig.configuration.model.*
//import eu.assistiot.selfconfig.model.*
//import eu.assistiot.selfconfig.serialization.json.SelfConfigSerialization.ReactionModelProtocol
//import io.circe.syntax.*
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpec
//
//class SerializationHelper extends AnyWordSpec with Matchers with ReactionModelProtocol {
//
//  "test" should {
//    "succed" in {
//
////      val msConfigurationStep1 = ReactionModel(
////        ReactionId.unsafe("multi_step_configuration_1"),
////        ResourceWithLabelIsAvailable(
////          LabelKey.unsafe("configuration_step"),
////          LabelValue.unsafe("not_configured")
////        ),
////        SendSimpleKafkaMessage(
////          "agv",
////          "map_download_link: http://proper_address.com"
////        )
////      )
//
//      val rr =
//        RegisterResource(
//          Resource(
//            id = ConfigElementId.unsafe("agv_1"),
//            labels = LabelMap(
//              Map(
//                LabelKey.unsafe("configuration_step") -> LabelValue.unsafe("not_configured")
//              )
//            )
//          )
//        )
//
//      println(rr.asJson)
//    }
//  }
//}
