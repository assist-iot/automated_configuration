package eu.assistiot.selfconfig.model

import cats.data.ValidatedNel
import cats.implicits.*

package object validation:
  type ValidationResult[A] = ValidatedNel[SelfConfigDomainValidation, A]

  object ValidationResult:
    def toStringEither[A](validationResult: ValidationResult[A]): Either[String, A] =
      validationResult.toEither.leftMap(nel => nel.toList.map(_.errorMessage).mkString(","))

    def toThrowableEither[A](validationResult: ValidationResult[A]): Either[Throwable, A] =
      toStringEither(validationResult).left.map(new IllegalArgumentException(_))
