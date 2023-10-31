package eu.assistiot.selfconfig.algebras

import cats.data.NonEmptySet
import eu.assistiot.selfconfig.algebras.DefaultConditionCheckTest.*
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

// TODO: Finish tests
class DefaultConditionCheckTest extends AnyWordSpec with should.Matchers {
  "DefaultConditionCheckTest#ContainsRequirements" should {
    "return true" when {
      "fullMatch is enabled" in {
        DefaultConditionCheck.conditionMet(
          stateRequirements = Set(EmptyFunctionality, SmartHomeFunctionality),
          stateResources = Set.empty,
          maybeMessage = DummyCheckableMessage,
          messageCondition = ContainsRequirements(Set(EmptyFunctionality, SmartHomeFunctionality), true)
        ) shouldBe true
      }
      "fullMatch is disabled" in {
        DefaultConditionCheck.conditionMet(
          stateRequirements = Set(EmptyFunctionality, SmartHomeFunctionality),
          stateResources = Set.empty,
          maybeMessage = DummyCheckableMessage,
          messageCondition = ContainsRequirements(Set(EmptyFunctionality), false)
        ) shouldBe true
      }
    }
    "return false" when {
      "fullMatch is enabled" in {
        DefaultConditionCheck.conditionMet(
          stateRequirements = Set(EmptyFunctionality, SmartHomeFunctionality),
          stateResources = Set.empty,
          maybeMessage = DummyCheckableMessage,
          messageCondition = ContainsRequirements(Set(EmptyFunctionality), true)
        ) shouldBe false
      }
      "fullMatch is disabled" in {
        DefaultConditionCheck.conditionMet(
          stateRequirements = Set(EmptyFunctionality, SmartHomeFunctionality),
          stateResources = Set.empty,
          maybeMessage = DummyCheckableMessage,
          messageCondition = ContainsRequirements(Set(ThermostatFunctionality), false)
        ) shouldBe false
      }
    }
  }

  "DefaultConditionCheckTest#ContainsRequirementWithId" should {
    "return true" in {
      DefaultConditionCheck.conditionMet(
        stateRequirements = Set(EmptyFunctionality, SmartHomeFunctionality),
        stateResources = Set.empty,
        maybeMessage = DummyCheckableMessage,
        messageCondition = ContainsRequirementWithId(EmptyFunctionality.id)
      ) shouldBe true
    }
    "return false" in {
      DefaultConditionCheck.conditionMet(
        stateRequirements = Set(EmptyFunctionality, SmartHomeFunctionality),
        stateResources = Set.empty,
        maybeMessage = DummyCheckableMessage,
        messageCondition = ContainsRequirementWithId(ThermostatFunctionality.id)
      ) shouldBe false
    }
  }

  "DefaultConditionCheckTest#ContainsRequirementsWithWeight" should {
    "return true" in {
      DefaultConditionCheck.conditionMet(
        stateRequirements = Set(EmptyFunctionality, SmartHomeFunctionality),
        stateResources = Set.empty,
        maybeMessage = DummyCheckableMessage,
        messageCondition = ContainsRequirementsWithWeight(EmptyFunctionality.weight)
      ) shouldBe true
    }
    "return false" in {
      DefaultConditionCheck.conditionMet(
        stateRequirements = Set(EmptyFunctionality, SmartHomeFunctionality),
        stateResources = Set.empty,
        maybeMessage = DummyCheckableMessage,
        messageCondition = ContainsRequirementsWithWeight(ThermostatFunctionality.weight)
      ) shouldBe false
    }
  }
}

object DefaultConditionCheckTest {

  // ids
  private val SmartHomeManagerId: ConfigElementId        = ConfigElementId.unsafe("smart_home_manager")
  private val ThermostatFunctionalityId: ConfigElementId = ConfigElementId.unsafe("thermostat_functionality")
  private val SecurityFunctionalityId: ConfigElementId   = ConfigElementId.unsafe("security_functionality")
  private val WideViewCamId: ConfigElementId             = ConfigElementId.unsafe("wide_view_cam")
  private val ThermometerId: ConfigElementId             = ConfigElementId.unsafe("thermometer")
  private val AcId: ConfigElementId                      = ConfigElementId.unsafe("ac")

  val EmptyFunctionality: RequirementsModel = RequirementsModel(
    ConfigElementId.unsafe("empty_functionality"),
    LabelMap.empty,
    Set.empty,
    FunctionalityWeight.unsafe(4.0d)
  )

  val SmartHomeFunctionality: RequirementsModel = RequirementsModel(
    ConfigElementId.unsafe("smart_home_functionality"),
    LabelMap.empty,
    Set(
      IdBasedRequirement(SmartHomeManagerId),
      IdBasedRequirement(ThermostatFunctionalityId),
      IdBasedRequirement(SecurityFunctionalityId),
      LabelBasedRequirement(LabelKey.unsafe("device_type"), LabelValue.unsafe("camera"), 2, exclusive = true)
    ),
    FunctionalityWeight.unsafe(3.0d)
  )

  val SecurityFunctionality: RequirementsModel = RequirementsModel(
    SecurityFunctionalityId,
    LabelMap.empty,
    Set(
      IdBasedRequirement(WideViewCamId),
      LabelBasedRequirement(LabelKey.unsafe("device_type"), LabelValue.unsafe("camera"), 2)
    ),
    FunctionalityWeight.unsafe(2.0d)
  )

  val ThermostatFunctionality: RequirementsModel = RequirementsModel(
    ThermostatFunctionalityId,
    LabelMap.empty,
    Set(
      IdBasedRequirement(ThermometerId),
      IdBasedRequirement(AcId)
    ),
    FunctionalityWeight.unsafe(1.0d)
  )

  val SmartHomeManagerResource: Resource = Resource(
    SmartHomeManagerId,
    LabelMap(
      Map(
        LabelKey.unsafe("resource_type") -> LabelValue.unsafe("computer"),
        LabelKey.unsafe("other_id")      -> LabelValue.unsafe("destination")
      )
    )
  )

  val ThermometerResource: Resource = Resource(
    ThermometerId,
    LabelMap(Map(LabelKey.unsafe("resource_type") -> LabelValue.unsafe("sensor")))
  )

  val AcResource: Resource = Resource(AcId, LabelMap.empty)

  private val CameraLabelMap: LabelMap = LabelMap(Map(LabelKey.unsafe("device_type") -> LabelValue.unsafe("camera")))

  val WideViewCamera: Resource = Resource(WideViewCamId, CameraLabelMap)
  val FrontCamera: Resource    = Resource(ConfigElementId.unsafe("front_camera"), CameraLabelMap)
  val BackCamera: Resource     = Resource(ConfigElementId.unsafe("back_camera"), CameraLabelMap)
  val ExtraCamera1: Resource   = Resource(ConfigElementId.unsafe("extra_camera_1"), CameraLabelMap)
  val ExtraCamera2: Resource   = Resource(ConfigElementId.unsafe("extra_camera_2"), CameraLabelMap)

  val DummyCheckableMessage: Option[CheckableMessageWithResource] = Some(
    CheckableMessageWithResource(ExtraCamera2, Available)
  )
}
