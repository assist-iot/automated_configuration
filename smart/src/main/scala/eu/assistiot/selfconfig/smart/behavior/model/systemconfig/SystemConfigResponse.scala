package eu.assistiot.selfconfig.smart.behavior.model.systemconfig

sealed trait SystemConfigResponse

object SystemConfigResponse:
  case class SystemConfigAck(command: SystemConfigCommand) extends SystemConfigResponse

  case class SystemConfigFailure(command: SystemConfigCommand, message: SystemConfigResponseMessage)
      extends SystemConfigResponse

sealed trait SystemConfigResponseMessage

object SystemConfigResponseMessage:
  case object FunctionalitiesDoNotFormADag extends SystemConfigResponseMessage
