package eu.assistiot.selfconfig.model.validation

import cats.implicits.*
import eu.assistiot.selfconfig.model.*

import scala.annotation.tailrec

object SelfConfigDomainValidators:
  def nonBlankString[T <: String](parameterName: String)(value: T): ValidationResult[T] =
    if (value.isBlank) StringParameterCannotBeEmpty(parameterName).invalidNel
    else value.validNel

  def positiveDouble[T <: Double](parameterName: String)(value: T): ValidationResult[T] =
    if (value <= 0) DoubleParameterHasToBePositive(parameterName).invalidNel
    else value.validNel
