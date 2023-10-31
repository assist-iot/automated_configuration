package eu.assistiot.selfconfig.smart.behavior.utils

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import cats.data.NonEmptySet
import cats.implicits.*
import eu.assistiot.selfconfig.TestData
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.{LabelMap, Resource}
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.{HandleReaction, ReactionHandlerMessage}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.CustomMessage
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigEvent.{
  CustomMessageReceived,
  RequirementsModelUpdated,
  ResourceAdded
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.{
  AutoConfigState,
  EmptyState,
  StateWithReactions
}
import eu.assistiot.selfconfig.smart.behavior.utils.SelfConfigReactionHandling
import org.scalatest.wordspec.AnyWordSpecLike

class SelfConfigReactionHandlingTest extends ScalaTestWithActorTestKit() with AnyWordSpecLike with TestData:

  private val testKit         = ActorTestKit()
  private val reactionHandler = new SelfConfigReactionHandling {}

  private val reactionHandlerProbe = testKit.createTestProbe[ReactionHandlerMessage]()
  private val systemConfigProbe    = testKit.createTestProbe[SystemConfigCommand]()

  "Reaction Handler" should {
    "do nothing" when {
      "Other than `WithReactions` state is provided" in {
        reactionHandler.handleReaction(
          reactionHandlerProbe.ref,
          systemConfigProbe.ref,
          ResourceAdded(childResource1)
        )(EmptyState(AutoConfigState.empty))
        reactionHandlerProbe.expectNoMessage()
        systemConfigProbe.expectNoMessage()
      }
    }
    "react" when {
      "State with per label and per id reaction was provided" in {
        val firstReactionModel = ReactionModel(
          ReactionId.unsafe("reaction-id-1"),
          ResourceIsAvailable(childResource1.id),
          SendSimpleKafkaMessage("topic", "message")
        )
        val state = StateWithReactions(
          NonEmptySet.of(
            firstReactionModel,
            ReactionModel(
              ReactionId.unsafe("reaction-id-1"),
              ResourceWithLabelIsAvailable(labelKey1, labelValue1),
              ReplaceConfiguration(NonEmptySet.one(parentRequirementsModel))
            )
          )
        )
        reactionHandler.handleReaction(
          reactionHandlerProbe.ref,
          systemConfigProbe.ref,
          ResourceAdded(childResource1)
        )(state)
        reactionHandlerProbe.expectMessage(
          HandleReaction(systemConfigProbe.ref, firstReactionModel.action, state, ResourceAdded(childResource1))
        )
        systemConfigProbe.expectNoMessage()
      }
      "any event is provided" in {
        val firstReactionModel = ReactionModel(
          ReactionId.unsafe("reaction-id-1"),
          AnyEvent,
          SendSimpleKafkaMessage("topic", "message")
        )
        val state = StateWithReactions(
          NonEmptySet.of(
            firstReactionModel
          )
        )
        reactionHandler.handleReaction(
          reactionHandlerProbe.ref,
          systemConfigProbe.ref,
          RequirementsModelUpdated(NonEmptySet.of(childRequirementsModel))
        )(state)
        reactionHandlerProbe.expectMessage(
          HandleReaction(
            systemConfigProbe.ref,
            firstReactionModel.action,
            state,
            RequirementsModelUpdated(NonEmptySet.of(childRequirementsModel))
          )
        )
        systemConfigProbe.expectNoMessage()
      }
      "custom messageis provided" in {
        val firstReactionModel = ReactionModel(
          ReactionId.unsafe("reaction-id-1"),
          CustomMessageContent("custom message"),
          SendSimpleKafkaMessage("topic", "message")
        )
        val state = StateWithReactions(
          NonEmptySet.of(
            firstReactionModel
          )
        )
        reactionHandler.handleReaction(
          reactionHandlerProbe.ref,
          systemConfigProbe.ref,
          CustomMessageReceived("custom message")
        )(state)
        reactionHandlerProbe.expectMessage(
          HandleReaction(
            systemConfigProbe.ref,
            firstReactionModel.action,
            state,
            CustomMessageReceived("custom message")
          )
        )
        systemConfigProbe.expectNoMessage()
      }
    }
  }
