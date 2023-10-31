package eu.assistiot.selfconfig.model.validation

import cats.Order
import eu.assistiot.selfconfig.model.validation.SelfConfigDomainValidation

sealed trait SelfConfigDomainValidation {
  def errorMessage: String
}

object SelfConfigDomainValidation {
  implicit val configDomainOrder: Order[SelfConfigDomainValidation] = cats.kernel.Order.allEqual
}

case class StringParameterCannotBeEmpty(parameterName: String) extends SelfConfigDomainValidation {
  def errorMessage: String = s"String parameter '$parameterName' cannot be empty."
}

case class DoubleParameterHasToBePositive(parameterName: String) extends SelfConfigDomainValidation {
  override def errorMessage: String = s"Double parameter '$parameterName' has to be positive."
}

case object ConfigElementIsNotADag extends SelfConfigDomainValidation {
  override def errorMessage: String = "Passed config element is not a Directed Acyclic Graph"
}
