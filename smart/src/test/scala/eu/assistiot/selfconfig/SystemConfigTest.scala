package eu.assistiot.selfconfig

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.util.Timeout
import cats.data.NonEmptySet
import com.typesafe.config.ConfigFactory
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.*
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.{HandleReaction, ReactionHandlerMessage}
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.{
  RequirementsMet,
  RequirementsMetMessage,
  RequirementsNotMet
}
import eu.assistiot.selfconfig.smart.behavior.SystemConfigBehavior
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.*
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigEvent.{
  AutoConfigurationEnabled,
  ReactionModelAdded,
  RequirementsModelAdded,
  ResourceAdded,
  ResourceRemoved
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponse.{
  SystemConfigAck,
  SystemConfigFailure
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponseMessage.FunctionalitiesDoNotFormADag
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.{
  EmptyState,
  StateWithResources,
  StateWithResourcesAndRequirements,
  StateWithResourcesRequirementsAndReactions
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.{
  SystemConfigCommand,
  SystemConfigEvent,
  SystemConfigResponse,
  SystemConfigState
}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

import scala.concurrent.duration.*

class SystemConfigTest
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString("akka.actor.allow-java-serialization = on")
        .withFallback(EventSourcedBehaviorTestKit.config)
    )
    with AnyWordSpecLike
    with GivenWhenThen
    with BeforeAndAfterEach
    with TestData {

  implicit override def timeout: Timeout = 3.minutes

  private val testKit = ActorTestKit()

  private lazy val notificationsMetProbe = testKit.createTestProbe[RequirementsMetMessage]()
  private lazy val reactorProbe          = testKit.createTestProbe[ReactionHandlerMessage]()

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[
      SystemConfigCommand,
      SystemConfigEvent,
      SystemConfigState
    ](system, SystemConfigBehavior(notificationsMetProbe.ref, reactorProbe.ref))

  "We need when due to cross compilation" when {
    "SystemConfigBehavior" should {
      "handle simple fail-over scenario" in {
        Given("A message removing resource and an empty state")
        val removeNonExistingResource = RemoveResource(customResource1, _)
        When("SystemConfigBehaviour receives message")
        val emptyResult = eventSourcedTestKit.runCommand[SystemConfigResponse](removeNonExistingResource)
        emptyResult.reply shouldBe a[SystemConfigAck]
        emptyResult.event shouldBe ResourceRemoved(customResource1)
        emptyResult.state shouldBe a[EmptyState]

        Then("Nothing happens")
        Given("A message with model")
        val messageWithFirstModel = AddFunctionalityModel(parentRequirementsModel, _)
        When("SystemConfigBehaviour receives message with model")
        val firstResult = eventSourcedTestKit.runCommand[SystemConfigResponse](messageWithFirstModel)
        Then("Functionality Model should be added")
        firstResult.reply shouldBe a[SystemConfigAck]
        firstResult.event shouldBe RequirementsModelAdded(parentRequirementsModel)
        firstResult
          .stateOfType[SystemConfigState.StateWithRequirements]
          .requirements shouldBe NonEmptySet.of(
          parentRequirementsModel
        )
        And("Requirements should not be met")
        notificationsMetProbe.expectMessageType[RequirementsNotMet]
        When("Message with conflicting Functionality Model is sent")
        val messageWithSecondModel = AddFunctionalityModel(childModelReferringParentModel, _)
        val secondResult           = eventSourcedTestKit.runCommand[SystemConfigResponse](messageWithSecondModel)
        Then("Error message should be returned")
        secondResult.reply should matchPattern { case SystemConfigFailure(_, FunctionalitiesDoNotFormADag) => }
        And("There should be no events")
        secondResult.hasNoEvents shouldBe true
        And("State should be identical to first one")
        secondResult
          .stateOfType[SystemConfigState.StateWithRequirements]
          .shouldBe(firstResult.stateOfType[SystemConfigState.StateWithRequirements])
        When("Requirements are partially met")
        val thirdResult = eventSourcedTestKit.runCommand[SystemConfigResponse](AddResource(childResource1, _))
        thirdResult.reply shouldBe a[SystemConfigAck]
        thirdResult.event shouldBe ResourceAdded(childResource1)
        thirdResult
          .stateOfType[StateWithResourcesAndRequirements]
          .requirements shouldBe NonEmptySet.of(
          parentRequirementsModel
        )
        thirdResult
          .stateOfType[StateWithResourcesAndRequirements]
          .resources shouldBe NonEmptySet.of(
          childResource1
        )
        Then("Requirements should still be not met")
        notificationsMetProbe.expectMessageType[RequirementsNotMet]
        When("Reaction model is added")
        val fourthResult = eventSourcedTestKit.runCommand[SystemConfigResponse](AddReactionModel(reactionModel1, _))
        Then("Reaction Model should be added")
        fourthResult.reply shouldBe a[SystemConfigAck]
        fourthResult.event shouldBe ReactionModelAdded(reactionModel1)
        fourthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .requirements shouldBe NonEmptySet.of(
          parentRequirementsModel
        )
        fourthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .resources shouldBe NonEmptySet.of(
          childResource1
        )
        fourthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .reactions
          .resourceIdToReactions should contain(childResource2.id -> reactionModel1)
        When("Resource with assigned reaction is added")
        eventSourcedTestKit.runCommand[SystemConfigResponse](AddResource(childResource2, _))
        val receivedMessage = reactorProbe.receiveMessage()
        receivedMessage should matchPattern { case HandleReaction(_, reactionModel1, _, _) => }
        notificationsMetProbe.expectMessageType[RequirementsNotMet]
        Then("Reactor should be notified")

        When("Enough resources register to meet requirements")
        eventSourcedTestKit.runCommand[SystemConfigResponse](AddResource(customResource1, _))
        notificationsMetProbe.expectMessageType[RequirementsNotMet]
        val fifthResult =
          eventSourcedTestKit.runCommand[SystemConfigResponse](AddFunctionalityModel(childRequirementsModel, _))
        fifthResult.reply shouldBe a[SystemConfigAck]
        fifthResult.event shouldBe RequirementsModelAdded(childRequirementsModel)
        fifthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .requirements shouldBe NonEmptySet.of(parentRequirementsModel, childRequirementsModel)
        fifthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .resources shouldBe NonEmptySet.of(childResource1, childResource2, customResource1)
        fifthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .reactions
          .resourceIdToReactions should contain(childResource2.id -> reactionModel1)
        notificationsMetProbe.expectMessageType[RequirementsMet]
        When("Resource is removed and requirements are not longer met")
        val sixthResult = eventSourcedTestKit.runCommand[SystemConfigResponse](RemoveResource(customResource1, _))
        Then("Requirements should not be met anymore")
        notificationsMetProbe.expectMessageType[RequirementsNotMet]
        sixthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .requirements shouldBe NonEmptySet.of(parentRequirementsModel, childRequirementsModel)
        sixthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .resources shouldBe NonEmptySet.of(childResource1, childResource2)
        sixthResult
          .stateOfType[StateWithResourcesRequirementsAndReactions]
          .reactions
          .resourceIdToReactions should contain(childResource2.id -> reactionModel1)
        And("When resource is added again")
        eventSourcedTestKit.runCommand[SystemConfigResponse](AddResource(customResource1, _))
        Then("Requirements should be met again")
        notificationsMetProbe.expectMessageType[RequirementsMet]
      }
      "handle auto configuration" in {
        val enableAutoConfiguration = EnableAutoConfiguration(_)
        val fristResult             = eventSourcedTestKit.runCommand[SystemConfigResponse](enableAutoConfiguration)
        fristResult.reply shouldBe a[SystemConfigAck]
        fristResult.event shouldBe AutoConfigurationEnabled
        fristResult.state shouldBe a[EmptyState]

        val secondResult = eventSourcedTestKit.runCommand[SystemConfigResponse](AddResource(childResource1, _))
        secondResult.reply shouldBe a[SystemConfigAck]
        secondResult.event shouldBe ResourceAdded(childResource1)
        secondResult
          .stateOfType[StateWithResources]
          .resources shouldBe NonEmptySet.of(
          childResource1
        )

        val thirdResult =
          eventSourcedTestKit.runCommand[SystemConfigResponse](AddFunctionalityModel(childRequirementsModel, _))
        thirdResult.reply shouldBe a[SystemConfigAck]
        thirdResult.event shouldBe RequirementsModelAdded(childRequirementsModel)
        thirdResult
          .stateOfType[StateWithResourcesAndRequirements]
          .requirements shouldBe NonEmptySet.of(childRequirementsModel)
        notificationsMetProbe.expectMessageType[RequirementsNotMet]

        val fourthResult =
          eventSourcedTestKit.runCommand[SystemConfigResponse](AddFunctionalityModel(parentRequirementsModel, _))
        fourthResult.reply shouldBe a[SystemConfigAck]
        fourthResult.event shouldBe RequirementsModelAdded(parentRequirementsModel)
        fourthResult
          .stateOfType[StateWithResourcesAndRequirements]
          .requirements shouldBe NonEmptySet.of(parentRequirementsModel, childRequirementsModel)
        notificationsMetProbe.expectMessageType[RequirementsNotMet]

        val fifthResult = eventSourcedTestKit.runCommand[SystemConfigResponse](AddResource(childResource2, _))
        fifthResult.reply shouldBe a[SystemConfigAck]
        fifthResult.event shouldBe ResourceAdded(childResource2)
        fifthResult
          .stateOfType[StateWithResourcesAndRequirements]
          .resources shouldBe NonEmptySet.of(
          childResource1,
          childResource2
        )

        val firstRequirementsMetMessage = notificationsMetProbe.expectMessageType[RequirementsMet]
        firstRequirementsMetMessage.metRequirements shouldBe Set(childRequirementsModel)
        firstRequirementsMetMessage.resources shouldBe Set(childResource1, childResource2)
        firstRequirementsMetMessage.allRequirements shouldBe Set(parentRequirementsModel, childRequirementsModel)

        val sixthResult = eventSourcedTestKit.runCommand[SystemConfigResponse](AddResource(customResource1, _))
        sixthResult.reply shouldBe a[SystemConfigAck]
        sixthResult.event shouldBe ResourceAdded(customResource1)
        sixthResult
          .stateOfType[StateWithResourcesAndRequirements]
          .resources shouldBe NonEmptySet.of(
          childResource1,
          childResource2,
          customResource1
        )

        val secondRequirementsMetMessage = notificationsMetProbe.expectMessageType[RequirementsMet]
        secondRequirementsMetMessage.metRequirements shouldBe Set(parentRequirementsModel, childRequirementsModel)
        secondRequirementsMetMessage.resources shouldBe Set(childResource1, childResource2, customResource1)
        secondRequirementsMetMessage.allRequirements shouldBe Set(parentRequirementsModel, childRequirementsModel)

        val seventhResult = eventSourcedTestKit.runCommand[SystemConfigResponse](RemoveResource(customResource1, _))
        seventhResult.reply shouldBe a[SystemConfigAck]
        seventhResult.event shouldBe ResourceRemoved(customResource1)
        seventhResult.state shouldBe fifthResult.state

        val thirdRequirementsMetMessage = notificationsMetProbe.expectMessageType[RequirementsMet]
        thirdRequirementsMetMessage shouldBe firstRequirementsMetMessage

        val eigthResult = eventSourcedTestKit.runCommand[SystemConfigResponse](RemoveResource(childResource2, _))
        eigthResult
          .stateOfType[StateWithResourcesAndRequirements]
          .resources shouldBe NonEmptySet.of(
          childResource1
        )

        val fourthRequirementsMetMessage = notificationsMetProbe.expectMessageType[RequirementsNotMet]
        fourthRequirementsMetMessage.allRequirements shouldBe Set(parentRequirementsModel, childRequirementsModel)
        fourthRequirementsMetMessage.resources shouldBe Set(childResource1)
      }
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

}
