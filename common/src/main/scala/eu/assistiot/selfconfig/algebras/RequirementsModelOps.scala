package eu.assistiot.selfconfig.algebras

import eu.assistiot.selfconfig.configuration.model.RequirementsModel
import eu.assistiot.selfconfig.model.ConfigElementId

trait RequirementsModelOps[F[_], Result] {

  def addRequirementsModel(requirementModel: RequirementsModel): F[Result]

  def removeRequirementsModel(id: ConfigElementId): F[Result]

  def enableAutoconfiguration(): F[Result]

  def disableAutoconfiguration(): F[Result]

}
