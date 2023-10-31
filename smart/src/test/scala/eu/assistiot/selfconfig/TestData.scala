package eu.assistiot.selfconfig

import eu.assistiot.selfconfig.configuration.model.{
  ReactionId,
  ReactionModel,
  RequirementsModel,
  ResourceIsAvailable,
  SendSimpleKafkaMessage
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

trait TestData {

  val parentRequirementId1: ConfigElementId = ConfigElementId.unsafe("parentId1")
  val childId1: ConfigElementId             = ConfigElementId.unsafe("childId1")
  val childId2: ConfigElementId             = ConfigElementId.unsafe("childId2")

  val childRequirementId1: ConfigElementId = ConfigElementId.unsafe("functionality1Id")
  val resourceId1: ConfigElementId         = ConfigElementId.unsafe("resourceId1")

  val labelKey1: LabelKey     = LabelKey.unsafe("labelKey1")
  val labelValue1: LabelValue = LabelValue.unsafe("labelValue1")

  val parentRequirementsModel: RequirementsModel = RequirementsModel(
    parentRequirementId1,
    LabelMap.empty,
    Set(
      IdBasedRequirement(childId1),
      IdBasedRequirement(childRequirementId1),
      IdBasedRequirement(resourceId1),
      LabelBasedRequirement(labelKey1, labelValue1, 3)
    ),
    FunctionalityWeight.unsafe(3.0d)
  )

  val childModelReferringParentModel: RequirementsModel = RequirementsModel(
    childId1,
    LabelMap.empty,
    Set(IdBasedRequirement(parentRequirementId1)),
    FunctionalityWeight.unsafe(3.0d)
  )

  val childResource1: Resource = Resource(
    childId1,
    LabelMap(Map(labelKey1 -> labelValue1))
  )

  val childResource2: Resource = Resource(
    childId2,
    LabelMap(Map(labelKey1 -> labelValue1))
  )

  val customResource1: Resource = Resource(
    resourceId1,
    LabelMap(Map(labelKey1 -> labelValue1))
  )

  val childRequirementsModel: RequirementsModel = RequirementsModel(
    childRequirementId1,
    LabelMap.empty,
    Set(IdBasedRequirement(childId2), IdBasedRequirement(childId1)),
    FunctionalityWeight.unsafe(2.0d)
  )

  val simpleKafkaMessageReaction1: SendSimpleKafkaMessage = SendSimpleKafkaMessage("topicName", "stringmessage")

  val reactionModel1: ReactionModel = ReactionModel(
    ReactionId.unsafe("reaction-id-1"),
    ResourceIsAvailable(childResource2.id),
    simpleKafkaMessageReaction1
  )
}
