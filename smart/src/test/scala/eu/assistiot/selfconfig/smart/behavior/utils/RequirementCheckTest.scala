package eu.assistiot.selfconfig.smart.behavior.utils

import cats.data.NonEmptySet
import eu.assistiot.selfconfig.configuration.model.*
import eu.assistiot.selfconfig.model.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class RequirementCheckTest extends AnyWordSpecLike with RequirementCheck with Matchers {

  import RequirementCheckTest.*

  "RequirementCheck" should {
    "return true" when {
      "requirements are empty" in {
        areRequirementsMet(Set(EmptyFunctionality), Set.empty) shouldBe true
      }
      "requirements are met for complex scenario" in {
        areRequirementsMet(
          Set(
            SmartHomeFunctionality,
            SecurityFunctionality,
            ThermostatFunctionality
          ),
          Set(
            SmartHomeManagerResource,
            ThermometerResource,
            AcResource,
            WideViewCamera,
            FrontCamera,
            BackCamera,
            ExtraCamera1,
            ExtraCamera2
          )
        ) shouldBe true
      }
    }
    "return false" when {
      "resources are empty" in {
        areRequirementsMet(Set(SmartHomeFunctionality), Set.empty) shouldBe false
      }
      "complex scenario but not enough resources" in {
        areRequirementsMet(
          Set(
            SmartHomeFunctionality,
            SecurityFunctionality,
            ThermostatFunctionality
          ),
          Set(
            SmartHomeManagerResource,
            ThermometerResource,
            AcResource,
            WideViewCamera,
            FrontCamera,
            BackCamera,
            ExtraCamera1
          )
        ) shouldBe false
      }
    }
  }
}

object RequirementCheckTest {

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
    FunctionalityWeight.unsafe(4.0d)
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
    LabelMap(Map(LabelKey.unsafe("resource_type") -> LabelValue.unsafe("computer")))
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

  val ReactionTopic: String = "reaction-kafka-topic"

  val WideViewCamReactionModel: ReactionModel = ReactionModel(
    ReactionId.unsafe("wide-view-cam-reaction"),
    ResourceIsAvailable(WideViewCamId),
    SendSimpleKafkaMessage(ReactionTopic, "FPS: 60")
  )

  val AcGoesDownReactionModel: ReactionModel = ReactionModel(
    ReactionId.unsafe("ac-goes-down-reaction"),
    ResourceIsNoLongerAvailable(AcId),
    ReplaceConfiguration(
      NonEmptySet.of(
        SmartHomeFunctionality.copy(
          requirements = Set(
            IdBasedRequirement(SmartHomeManagerId),
            IdBasedRequirement(SecurityFunctionalityId),
            IdBasedRequirement(WideViewCamId)
          )
        ),
        SecurityFunctionality
      )
    )
  )
}
