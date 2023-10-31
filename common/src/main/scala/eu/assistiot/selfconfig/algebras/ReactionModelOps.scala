package eu.assistiot.selfconfig.algebras

import eu.assistiot.selfconfig.configuration.model.{ReactionId, ReactionModel}

trait ReactionModelOps[F[_], Result] {

  def addReactionModel(reactionModel: ReactionModel): F[Result]

  def removeReactionModel(id: ReactionId): F[Result]

}
