package eu.assistiot.selfconfig.configuration.model

import cats.Order
import eu.assistiot.selfconfig.model.*
import io.circe.*
import io.circe.generic.semiauto.*

case class RequirementsModel(
  id: ConfigElementId,
  labels: LabelMap,
  requirements: Set[FunctionalityRequirement],
  weight: FunctionalityWeight
)

object RequirementsModel {
  implicit val configElementOrder: Order[RequirementsModel] = (x: RequirementsModel, y: RequirementsModel) =>
    x.id.toString.compareTo(y.id.toString)
}
