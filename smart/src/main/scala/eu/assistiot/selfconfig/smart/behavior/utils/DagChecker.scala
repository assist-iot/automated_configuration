package eu.assistiot.selfconfig.smart.behavior.utils

import eu.assistiot.selfconfig.configuration.model.RequirementsModel
import eu.assistiot.selfconfig.model.{ConfigElementId, IdBasedRequirement}

import scala.annotation.tailrec

trait DagChecker:
  def doFunctionalityModelsFormADag(models: List[RequirementsModel]): Boolean = {
    val rootsToChildren: Map[ConfigElementId, List[ConfigElementId]] =
      models
        .map(fm => (fm.id, fm.requirements.collect { case ibr: IdBasedRequirement => ibr.id }.toList))
        .toMap
    type VisitedPath = Set[ConfigElementId]

    @tailrec
    def dagCheck(
      toVisit: List[(ConfigElementId, VisitedPath)],
      parentChildrenMap: Map[ConfigElementId, List[ConfigElementId]]
    ): Boolean = {
      toVisit match
        case Nil                                       => true
        case (elementId, path) :: _ if path(elementId) => false
        case (elementId, path) :: rest =>
          val pathWithCurrentElementId: Set[ConfigElementId] = path + elementId
          val additionalToVisit: List[(ConfigElementId, VisitedPath)] = parentChildrenMap
            .get(elementId)
            .fold(List.empty)(_.map((_, pathWithCurrentElementId)))
          dagCheck(additionalToVisit ++ rest, parentChildrenMap)

    }

    dagCheck(rootsToChildren.keys.map((_, Set.empty)).toList, rootsToChildren)
  }
