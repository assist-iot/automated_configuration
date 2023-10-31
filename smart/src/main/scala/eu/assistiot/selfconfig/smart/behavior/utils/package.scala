package eu.assistiot.selfconfig.smart.behavior

import cats.data.NonEmptySet
import eu.assistiot.selfconfig.configuration.model.RequirementsModel
import eu.assistiot.selfconfig.model.{ConfigElementId, IdBasedRequirement}

package object utils {
  def topologicalSortReversed(
    adj: Map[ConfigElementId, List[ConfigElementId]]
  ) = {
    val k  = adj.head._1
    val ks = adj.tail.keys

    def doDfs(
      v: ConfigElementId,
      seen: Set[ConfigElementId],
      acc: List[ConfigElementId]
    ): (List[ConfigElementId], Set[ConfigElementId]) = {
      if (seen(v)) (acc, seen)
      else {
        val nonVisitedNeighbours = adj.get(v).fold(List.empty[ConfigElementId])(identity).filterNot(seen.contains)
        val (childrenAcc, childrenSeen) =
          nonVisitedNeighbours.foldLeft((acc, seen + v)) { case ((accWithoutV, seenWithV), e) =>
            doDfs(e, seenWithV, accWithoutV)
          }
        (childrenAcc.appended(v), childrenSeen)
      }
    }

    val initialState = doDfs(k, Set.empty, List.empty)
    ks.foldLeft(initialState) { case ((acc, seen), id) => doDfs(id, seen, acc) }._1
  }

  def existingFunctionalitiesAdjList(
    requirements: Set[RequirementsModel]
  ): Map[ConfigElementId, List[ConfigElementId]] = {
    val requirementsIds = requirements.map(_.id)
    requirements
      .map(rm =>
        rm.id -> rm.requirements.collect { case ibr: IdBasedRequirement if requirementsIds(ibr.id) => ibr.id }.toList
      )
      .toMap
  }

  def functionalitiesAdjList(
    requirements: NonEmptySet[RequirementsModel]
  ): Map[ConfigElementId, List[ConfigElementId]] =
    requirements.toSortedSet.toSet
      .map(rm => rm.id -> rm.requirements.collect { case ibr: IdBasedRequirement => ibr.id }.toList)
      .toMap

}
