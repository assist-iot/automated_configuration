package eu.assistiot.selfconfig.smart.behavior.utils

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import cats.data.NonEmptySet
import eu.assistiot.selfconfig.TestData
import eu.assistiot.selfconfig.configuration.model.{
  AnyEvent,
  KeepHighestWeightFunctionalities,
  ReactionId,
  ReactionModel,
  RequirementsModel
}
import eu.assistiot.selfconfig.model.{
  ConfigElementId,
  FunctionalityWeight,
  IdBasedRequirement,
  LabelBasedRequirement,
  LabelKey,
  LabelMap,
  LabelValue,
  Resource
}
import eu.assistiot.selfconfig.smart.behavior.DefaultReactionHandler
import eu.assistiot.selfconfig.smart.behavior.DefaultReactionHandler.DefaultReactionHandlerConfig
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.HandleReaction
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.ReplaceActiveRequirements
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigEvent.CustomMessageReceived
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.{
  AutoConfigState,
  EmptyState,
  StateWithRequirements,
  StateWithResourcesAndRequirements
}
import eu.assistiot.selfconfig.smart.behavior.utils.DefaultReactionHandlerTest.{
  Req1,
  Req2,
  Req3,
  Req4,
  Req5,
  Req6,
  ResL1V1_1,
  ResL1V1_2,
  ResL1V1_3,
  ResL2V2_1,
  ResL2V2_2,
  ResL2V2_3
}
import org.scalatest.wordspec.AnyWordSpecLike

class DefaultReactionHandlerTest extends ScalaTestWithActorTestKit() with AnyWordSpecLike with TestData:

  private val testKit: ActorTestKit = ActorTestKit()
  private val systemConfigProbe     = testKit.createTestProbe[SystemConfigCommand]()

  "Reaction handler#KeepHighestWeightFunctionalities" should {
    val reactionHandlerBehavior = DefaultReactionHandler(DefaultReactionHandlerConfig("do-not-care"))
    val reactionHandlerActor    = testKit.spawn(reactionHandlerBehavior)
    "do nothing" when {
      "State with no requirements and no resources" in {
        reactionHandlerActor ! HandleReaction(
          systemConfigProbe.ref,
          KeepHighestWeightFunctionalities,
          EmptyState(AutoConfigState.empty),
          CustomMessageReceived("")
        )
        systemConfigProbe.expectNoMessage()
      }
    }
    "Return same requirements" when {
      "there are enough resources for all requirements" in {
        val state = StateWithResourcesAndRequirements(
          NonEmptySet.of(customResource1, childResource1, childResource2),
          NonEmptySet.of(parentRequirementsModel, childRequirementsModel),
          AutoConfigState.empty
        )
        reactionHandlerActor ! HandleReaction(
          systemConfigProbe.ref,
          KeepHighestWeightFunctionalities,
          state,
          CustomMessageReceived("")
        )
        systemConfigProbe
          .receiveMessage()
          .asInstanceOf[ReplaceActiveRequirements]
          .requirements shouldBe state.requirements
      }
      "no requirements would be met" in {
        val state = StateWithRequirements(
          NonEmptySet.of(parentRequirementsModel, childRequirementsModel),
          AutoConfigState.empty
        )
        reactionHandlerActor ! HandleReaction(
          systemConfigProbe.ref,
          KeepHighestWeightFunctionalities,
          state,
          CustomMessageReceived("")
        )
        systemConfigProbe
          .receiveMessage()
          .asInstanceOf[ReplaceActiveRequirements]
          .requirements shouldBe state.requirements
      }
    }
    "return requirements with highest possible values" when {
      /*
      Available Requirements, their dependencies and weight
      Req1 (10)
       - Req2 (5)
         - L1
         - Req3 (3)
          - L2
        - Req4 (3)
          - L1
          - Req5 (2)
            - L2
            - Req6 (10)
              - L1
              - L2
       */
      "Req 6 should be met before Req2 and Req3" in {
        val state = StateWithResourcesAndRequirements(
          NonEmptySet.of(ResL1V1_1, ResL2V2_1),
          NonEmptySet.of(Req1, Req2, Req3, Req4, Req5, Req6),
          AutoConfigState.empty
        )
        reactionHandlerActor ! HandleReaction(
          systemConfigProbe.ref,
          KeepHighestWeightFunctionalities,
          state,
          CustomMessageReceived("")
        )
        val expectedRequirements: NonEmptySet[RequirementsModel] = NonEmptySet.of(Req6)
        systemConfigProbe
          .receiveMessage()
          .asInstanceOf[ReplaceActiveRequirements]
          .requirements shouldBe expectedRequirements
      }
      "Req6 + Req3 should be met before Req5" in {
        val state = StateWithResourcesAndRequirements(
          NonEmptySet.of(ResL1V1_1, ResL2V2_1, ResL2V2_2),
          NonEmptySet.of(Req1, Req2, Req3, Req4, Req5, Req6),
          AutoConfigState.empty
        )
        reactionHandlerActor ! HandleReaction(
          systemConfigProbe.ref,
          KeepHighestWeightFunctionalities,
          state,
          CustomMessageReceived("")
        )
        val expectedRequirements: NonEmptySet[RequirementsModel] = NonEmptySet.of(Req6, Req3)
        systemConfigProbe
          .receiveMessage()
          .asInstanceOf[ReplaceActiveRequirements]
          .requirements shouldBe expectedRequirements
      }
      "All requirements would be met" in {
        val state = StateWithResourcesAndRequirements(
          NonEmptySet.of(ResL1V1_1, ResL1V1_2, ResL1V1_3, ResL2V2_1, ResL2V2_2, ResL2V2_3),
          NonEmptySet.of(Req1, Req2, Req3, Req4, Req5, Req6),
          AutoConfigState.empty
        )
        reactionHandlerActor ! HandleReaction(
          systemConfigProbe.ref,
          KeepHighestWeightFunctionalities,
          state,
          CustomMessageReceived("")
        )
        systemConfigProbe
          .receiveMessage()
          .asInstanceOf[ReplaceActiveRequirements]
          .requirements shouldBe state.requirements
      }

    }

  }

object DefaultReactionHandlerTest:

  val Req1Id: ConfigElementId = ConfigElementId.unsafe("req1-id")
  val Req2Id: ConfigElementId = ConfigElementId.unsafe("req2-id")
  val Req3Id: ConfigElementId = ConfigElementId.unsafe("req3-id")
  val Req4Id: ConfigElementId = ConfigElementId.unsafe("req4-id")
  val Req5Id: ConfigElementId = ConfigElementId.unsafe("req5-id")
  val Req6Id: ConfigElementId = ConfigElementId.unsafe("req6-id")
  val LK1: LabelKey           = LabelKey.unsafe("lk1")
  val LK2: LabelKey           = LabelKey.unsafe("lk2")
  val LV1: LabelValue         = LabelValue.unsafe("lv1")
  val LV2: LabelValue         = LabelValue.unsafe("lv2")
  val ResL1V1_1: Resource = Resource(
    ConfigElementId.unsafe("res-l1"),
    LabelMap.apply(
      Map(LK1 -> LV1)
    )
  )
  val ResL1V1_2: Resource = Resource(
    ConfigElementId.unsafe("res-l1_2"),
    LabelMap.apply(
      Map(LK1 -> LV1)
    )
  )
  val ResL1V1_3: Resource = Resource(
    ConfigElementId.unsafe("res-l1_3"),
    LabelMap.apply(
      Map(LK1 -> LV1)
    )
  )
  val ResL2V2_1: Resource = Resource(
    ConfigElementId.unsafe("res-l2"),
    LabelMap.apply(
      Map(LK2 -> LV2)
    )
  )
  val ResL2V2_2: Resource = Resource(
    ConfigElementId.unsafe("res-l2_2"),
    LabelMap.apply(
      Map(LK2 -> LV2)
    )
  )
  val ResL2V2_3: Resource = Resource(
    ConfigElementId.unsafe("res-l2_3"),
    LabelMap.apply(
      Map(LK2 -> LV2)
    )
  )

  val Req1: RequirementsModel = RequirementsModel(
    Req1Id,
    LabelMap.empty,
    Set(
      IdBasedRequirement(Req2Id),
      IdBasedRequirement(Req4Id)
    ),
    FunctionalityWeight.unsafe(10.0d)
  )
  val Req2: RequirementsModel = RequirementsModel(
    Req2Id,
    LabelMap.empty,
    Set(
      IdBasedRequirement(Req3Id),
      LabelBasedRequirement(LK1, LV1, 1, true)
    ),
    FunctionalityWeight.unsafe(5.0d)
  )
  val Req3: RequirementsModel = RequirementsModel(
    Req3Id,
    LabelMap.empty,
    Set(
      LabelBasedRequirement(LK2, LV2, 1, true)
    ),
    FunctionalityWeight.unsafe(3.0d)
  )
  val Req4: RequirementsModel = RequirementsModel(
    Req4Id,
    LabelMap.empty,
    Set(
      IdBasedRequirement(Req5Id),
      LabelBasedRequirement(LK1, LV1, 1, true)
    ),
    FunctionalityWeight.unsafe(3.0d)
  )
  val Req5: RequirementsModel = RequirementsModel(
    Req5Id,
    LabelMap.empty,
    Set(
      IdBasedRequirement(Req6Id),
      LabelBasedRequirement(LK2, LV2, 1, true)
    ),
    FunctionalityWeight.unsafe(2.0d)
  )
  val Req6: RequirementsModel = RequirementsModel(
    Req6Id,
    LabelMap.empty,
    Set(
      LabelBasedRequirement(LK1, LV1, 1, true),
      LabelBasedRequirement(LK2, LV2, 1, true)
    ),
    FunctionalityWeight.unsafe(10.0d)
  )
