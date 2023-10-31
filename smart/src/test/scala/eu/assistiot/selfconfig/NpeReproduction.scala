package eu.assistiot.selfconfig

import cats.data.NonEmptyMap
import eu.assistiot.selfconfig.configuration.model.{
  ReactionId,
  ReactionModel,
  ResourceIsAvailable,
  SendSimpleKafkaMessage
}
import eu.assistiot.selfconfig.model.ConfigElementId
import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpecLike

class NpeReproduction extends AnyWordSpecLike {

  import NpeReproduction.*

  "NonEmptyMap" should {
    val ne1: NonEmptyMap[ConfigElementId, ReactionModel] = NonEmptyMap.of(id1 -> reactionModel1)
    val ne2: NonEmptyMap[ConfigElementId, ReactionModel] = NonEmptyMap.of(id2 -> reactionModel2)
    "not throw an exception" when {
      "combining" in {
        ne1 ++ ne2
      }
      "retrieving" in {
        ne1(id1)
      }
    }
  }
}

object NpeReproduction {
  val id1: ConfigElementId = ConfigElementId.unsafe("configElementId1")
  val id2: ConfigElementId = ConfigElementId.unsafe("configElementId2")

  val reactionModel1: ReactionModel = ReactionModel(
    reactionId = ReactionId.unsafe("reactionId1"),
    filterExpression = ResourceIsAvailable(id1),
    action = SendSimpleKafkaMessage("asd", "asd")
  )
  val reactionModel2: ReactionModel = ReactionModel(
    reactionId = ReactionId.unsafe("reactionId2"),
    filterExpression = ResourceIsAvailable(id2),
    action = SendSimpleKafkaMessage("def", "def")
  )
}
