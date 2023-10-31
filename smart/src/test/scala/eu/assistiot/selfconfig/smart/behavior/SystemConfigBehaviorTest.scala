package eu.assistiot.selfconfig.smart.behavior

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import cats.data.NonEmptySet
import com.typesafe.config.ConfigFactory
import eu.assistiot.selfconfig.TestData
import eu.assistiot.selfconfig.configuration.model.RequirementsModel
import eu.assistiot.selfconfig.model.{FunctionalityWeight, IdBasedRequirement, LabelBasedRequirement, LabelMap}
import eu.assistiot.selfconfig.smart.behavior.ReactionHandler.ReactionHandlerMessage
import eu.assistiot.selfconfig.smart.behavior.RequirementsMetNotifier.RequirementsMetMessage
import eu.assistiot.selfconfig.smart.behavior.SystemConfigBehaviorTest.simplifiedparentRequirementsModel
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigCommand.{
  ReplaceActiveRequirements,
  UpsertRequirements
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigEvent.RequirementsModelUpdated
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponse.{
  SystemConfigAck,
  SystemConfigFailure
}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigResponseMessage.FunctionalitiesDoNotFormADag
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.SystemConfigState.{EmptyState, StateWithRequirements}
import eu.assistiot.selfconfig.smart.behavior.model.systemconfig.{
  SystemConfigCommand,
  SystemConfigEvent,
  SystemConfigResponse,
  SystemConfigState
}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

class SystemConfigBehaviorTest
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString("akka.actor.allow-java-serialization = on")
        .withFallback(EventSourcedBehaviorTestKit.config)
    )
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with TestData {

  private val testKit = ActorTestKit()

  private lazy val notificationsMetProbe = testKit.createTestProbe[RequirementsMetMessage]()
  private lazy val reactorProbe          = testKit.createTestProbe[ReactionHandlerMessage]()

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[
      SystemConfigCommand,
      SystemConfigEvent,
      SystemConfigState
    ](system, SystemConfigBehavior(notificationsMetProbe.ref, reactorProbe.ref))

  "UpsertConfiguration#UpsertRequirements" should {
    "reject" when {
      "New configuration does not form a DAG" in {
        val result = eventSourcedTestKit.runCommand[SystemConfigResponse](
          UpsertRequirements(NonEmptySet.of(parentRequirementsModel, childModelReferringParentModel), false, _)
        )
        result.reply should matchPattern { case SystemConfigFailure(_, FunctionalitiesDoNotFormADag) => }
        result.hasNoEvents shouldBe true
        result.state shouldBe a[EmptyState]
      }
    }
    "update" when {
      "Current configuration is empty" in {
        val requirementsTobeUpserted = NonEmptySet.of(parentRequirementsModel, childRequirementsModel)
        val result = eventSourcedTestKit.runCommand[SystemConfigResponse](
          UpsertRequirements(requirementsTobeUpserted, false, _)
        )
        result.reply shouldBe a[SystemConfigAck]
        result.event shouldBe RequirementsModelUpdated(requirementsTobeUpserted)
        result
          .stateOfType[StateWithRequirements]
          .requirements shouldBe requirementsTobeUpserted
      }
      "Current configuration is non-empty" when {
        "Current configuration is not overlapping" in {
          // Non overlapping config
          val initialRequirements = NonEmptySet.of(childModelReferringParentModel)
          eventSourcedTestKit.runCommand[SystemConfigResponse](
            ReplaceActiveRequirements(initialRequirements, _)
          )

          // Actual test
          val requirementsTobeUpserted = NonEmptySet.of(childRequirementsModel)
          val result = eventSourcedTestKit.runCommand[SystemConfigResponse](
            UpsertRequirements(requirementsTobeUpserted, false, _)
          )
          result.reply shouldBe a[SystemConfigAck]
          result.event shouldBe RequirementsModelUpdated(requirementsTobeUpserted ++ initialRequirements)
          result
            .stateOfType[StateWithRequirements]
            .requirements shouldBe (requirementsTobeUpserted ++ initialRequirements)
        }
        "Current configuration is overlapping" when {
          val existingConfiguration     = NonEmptySet.of(parentRequirementsModel, childRequirementsModel)
          val updatedConfigWithDangling = NonEmptySet.of(simplifiedparentRequirementsModel)
          "Remove dangling is disabled" in {
            // Overlapping config
            eventSourcedTestKit.runCommand[SystemConfigResponse](
              ReplaceActiveRequirements(existingConfiguration, _)
            )

            // Actual test
            val result = eventSourcedTestKit.runCommand[SystemConfigResponse](
              UpsertRequirements(updatedConfigWithDangling, false, _)
            )
            val expectedRequirements = NonEmptySet.of(simplifiedparentRequirementsModel, childRequirementsModel)
            result.reply shouldBe a[SystemConfigAck]
            result.event shouldBe RequirementsModelUpdated(expectedRequirements)
            result
              .stateOfType[StateWithRequirements]
              .requirements shouldBe expectedRequirements
          }
          "Remove dangling is enabled" when {
            "There are no dangling children to remove" in {
              // Overlapping config
              eventSourcedTestKit.runCommand[SystemConfigResponse](
                ReplaceActiveRequirements(NonEmptySet.of(simplifiedparentRequirementsModel), _)
              )

              // Actual test
              val result = eventSourcedTestKit.runCommand[SystemConfigResponse](
                UpsertRequirements(NonEmptySet.of(parentRequirementsModel), true, _)
              )
              val expectedRequirements = NonEmptySet.of(parentRequirementsModel)
              result.reply shouldBe a[SystemConfigAck]
              result.event shouldBe RequirementsModelUpdated(expectedRequirements)
              result
                .stateOfType[StateWithRequirements]
                .requirements shouldBe expectedRequirements
            }
            "There are dangling children to remove" in {
              // Overlapping config
              eventSourcedTestKit.runCommand[SystemConfigResponse](
                ReplaceActiveRequirements(existingConfiguration, _)
              )

              // Actual test
              val result = eventSourcedTestKit.runCommand[SystemConfigResponse](
                UpsertRequirements(updatedConfigWithDangling, true, _)
              )
              val expectedRequirements = NonEmptySet.of(simplifiedparentRequirementsModel)
              result.reply shouldBe a[SystemConfigAck]
              result.event shouldBe RequirementsModelUpdated(expectedRequirements)
              result
                .stateOfType[StateWithRequirements]
                .requirements shouldBe expectedRequirements
            }
          }
        }
      }
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }
}

object SystemConfigBehaviorTest extends TestData {

  val simplifiedparentRequirementsModel: RequirementsModel = RequirementsModel(
    parentRequirementId1,
    LabelMap.empty,
    Set(
      LabelBasedRequirement(labelKey1, labelValue1, 3)
    ),
    FunctionalityWeight.unsafe(3.0d)
  )

}
