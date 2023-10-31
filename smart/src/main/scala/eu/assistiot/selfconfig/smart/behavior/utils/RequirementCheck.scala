package eu.assistiot.selfconfig.smart.behavior.utils

import cats.data.NonEmptySet
import cats.implicits.*
import com.typesafe.scalalogging.LazyLogging
import eu.assistiot.selfconfig.configuration.model.RequirementsModel
import eu.assistiot.selfconfig.model.*

trait RequirementCheck extends LazyLogging {

  def areRequirementsMet(
    requirements: Set[RequirementsModel],
    resources: Set[Resource]
  ): Boolean = {
    if (requirements.isEmpty) {
      true
    } else {
      val adj                   = existingFunctionalitiesAdjList(requirements)
      val sortedFunctionalities = topologicalSortReversed(adj)
      val requirementsMap       = requirements.toList.map(rm => rm.id -> rm).toMap
      val idsMap                = IdMaps(requirementsMap, resources.map(r => (r.id -> r)).toMap)
      val result                = doCheckRequirements(sortedFunctionalities, idsMap)
      result
    }
  }

  private def doCheckRequirements(
    sortedFunctionalities: List[ConfigElementId],
    idMaps: IdMaps
  ) = {
    if (sortedFunctionalities.isEmpty) {
      true
    } else {
      def doCheck(
        start: ConfigElementId,
        rest: List[ConfigElementId],
        markableState: MarkedEntitiesState
      ): Boolean = {
        val rm: RequirementsModel = idMaps.requirementsMap(start)
        val requiredIds: Set[IdBasedRequirement] =
          rm.requirements
            .collect { case ibr: IdBasedRequirement => ibr }
        val requiredLabels: Set[LabelBasedRequirement] = rm.requirements
          .collect { case l: LabelBasedRequirement => l }
        val (idsMet, updatedState) =
          checkIdBasedRequirements(requiredIds, markableState)
        val (labelsMet, finalState) =
          checkLabelBasedRequirements(requiredLabels, updatedState)
        if (labelsMet && idsMet) {
          rest match
            case r :: rs =>
              val me: MarkableEntity = MarkableEntity(rm.id, rm.labels.toMap.toSet)
              val updatedState       = finalState.add(me)
              doCheck(r, rs, updatedState)
            case Nil => true
        } else {
          false
        }
      }
      doCheck(
        sortedFunctionalities.head,
        sortedFunctionalities.tail,
        MarkedEntitiesState.fromResources(idMaps.resources)
      )
    }
  }

  private def checkIdBasedRequirements(
    requiredIds: Set[IdBasedRequirement],
    markableState: MarkedEntitiesState
  ) = checkRequirement(markableState, requiredIds)(
    enoughResourcesCheck = (state, ibr) => state(ibr.id),
    enoughExclusiveResourcesCheck = (state, ibr) => state.isNonMarked(ibr.id),
    remover = (state, ibr) => state.remove(ibr.id),
    marker = (state, ibr) => state.mark(ibr.id)
  )

  private def checkLabelBasedRequirements(
    requiredLabels: Set[LabelBasedRequirement],
    markableState: MarkedEntitiesState
  ) = checkRequirement(markableState, requiredLabels)(
    enoughResourcesCheck = (state, lbr) => state.labelCountGraterEqualThan((lbr.labelKey, lbr.labelValue), lbr.count),
    enoughExclusiveResourcesCheck =
      (state, lbr) => state.labelNonMarkedCountGraterEqualThan((lbr.labelKey, lbr.labelValue), lbr.count),
    remover = (state, lbr) => state.reduceCountBy((lbr.labelKey, lbr.labelValue), lbr.count),
    marker = (state, lbr) => state.mark((lbr.labelKey, lbr.labelValue), lbr.count)
  )

  private def checkRequirement[T <: FunctionalityRequirement](markableState: MarkedEntitiesState, requirements: Set[T])(
    enoughResourcesCheck: (MarkedEntitiesState, T) => Boolean,
    enoughExclusiveResourcesCheck: (MarkedEntitiesState, T) => Boolean,
    remover: (MarkedEntitiesState, T) => MarkedEntitiesState,
    marker: (MarkedEntitiesState, T) => MarkedEntitiesState
  ) = requirements.foldLeft((true, markableState)) { case ((result, state), e) =>
    if (!result) {
      (result, state)
    } else if (!enoughResourcesCheck(state, e) || (e.exclusive && !enoughExclusiveResourcesCheck(state, e))) {
      (false, state)
    } else {
      val (resultingState) = if (e.exclusive) {
        remover(state, e)
      } else {
        marker(state, e)
      }
      (result, resultingState)
    }
  }

  case class MarkableEntity(
    id: ConfigElementId,
    labels: Set[(LabelKey, LabelValue)],
    takenByAny: Boolean = false
  ) {
    def mark(): MarkableEntity = this.copy(takenByAny = true)
  }

  case class MarkedEntitiesState(
    idtoEntity: Map[ConfigElementId, MarkableEntity],
    labelToEntity: Map[(LabelKey, LabelValue), List[MarkableEntity]]
  ) {
    def apply(id: ConfigElementId): Boolean = this.idtoEntity.contains(id)
    def remove(id: ConfigElementId): MarkedEntitiesState = {
      val maybeUpdatedState = for {
        me <- this.idtoEntity.get(id)
        updatedIdToEntityMap = this.idtoEntity - id
        labels               = me.labels
        updatedLabelToEntityMap = labels.foldLeft(this.labelToEntity)((acc, keyValue) =>
          acc.get(keyValue).map(mes => acc + (keyValue -> mes.filterNot(_.id == id))).getOrElse(acc)
        )
      } yield MarkedEntitiesState(updatedIdToEntityMap, updatedLabelToEntityMap)
      maybeUpdatedState.getOrElse(this)
    }
    def add(entity: MarkableEntity): MarkedEntitiesState = {
      val updatedIdToEntity = idtoEntity + (entity.id -> entity)
      val updatedLabelsToEntity =
        entity.labels.foldLeft(this.labelToEntity)((acc, keyValue) => acc.combine(Map(keyValue -> List(entity))))
      MarkedEntitiesState(updatedIdToEntity, updatedLabelsToEntity)
    }
    def mark(id: ConfigElementId): MarkedEntitiesState = {
      val maybeUpdatedState = for {
        me <- this.idtoEntity.get(id)
        updatedIdToEntityMap = this.idtoEntity + (me.id -> me.mark())
        labels               = me.labels
        updatedLabelToEntityMap = labels.foldLeft(this.labelToEntity)((acc, keyValue) =>
          acc
            .get(keyValue)
            .map(mes =>
              acc + (keyValue -> mes.collect {
                case willChange: MarkableEntity if willChange.id == id => willChange.mark()
                case willNotChange: MarkableEntity                     => willNotChange
              })
            )
            .getOrElse(acc)
        )
      } yield MarkedEntitiesState(updatedIdToEntityMap, updatedLabelToEntityMap)
      maybeUpdatedState.getOrElse(this)
    }
    def isNonMarked(id: ConfigElementId): Boolean         = this.idtoEntity.get(id).exists(me => !me.takenByAny)
    def getCount(kv: (LabelKey, LabelValue)): Option[Int] = this.labelToEntity.get(kv).map(_.size)
    def apply(kv: (LabelKey, LabelValue)): Int            = this.labelToEntity(kv).size
    def nonMarked(kv: (LabelKey, LabelValue)): Int        = this.labelToEntity(kv).filterNot(_.takenByAny).size
    def labelCountGraterEqualThan(kv: (LabelKey, LabelValue), count: Int): Boolean =
      this.getCount(kv).exists(_ >= count)
    def labelNonMarkedCountGraterEqualThan(kv: (LabelKey, LabelValue), count: Int): Boolean =
      this.labelToEntity.get(kv).map(_.filterNot(_.takenByAny).size).exists(_ >= count)
    def reduceCountBy(kv: (LabelKey, LabelValue), count: Int): MarkedEntitiesState = {
      val maybeUpdatedState = for {
        mes <- this.labelToEntity.get(kv)
        sortedMes            = mes.sortBy(_.takenByAny).reverse
        reducedMes           = sortedMes.drop(count)
        removedMes           = sortedMes.take(count)
        updatedLabelToEntity = this.labelToEntity + (kv -> reducedMes)
        updatedIdToEntity    = removedMes.foldLeft(this.idtoEntity)((acc, me) => acc - me.id)
      } yield MarkedEntitiesState(updatedIdToEntity, updatedLabelToEntity)
      maybeUpdatedState.getOrElse(this)
    }
    def mark(kv: (LabelKey, LabelValue), count: Int): MarkedEntitiesState = {
      val maybeUpdatedState = for {
        mes <- this.labelToEntity.get(kv)
        sortedMes            = mes.sortBy(_.takenByAny)
        nonChangedTail       = sortedMes.drop(count)
        changedHeads         = sortedMes.take(count).map(_.mark())
        updatedLabelToEntity = this.labelToEntity + (kv -> (changedHeads ++ nonChangedTail))
        updatedIdToEntity    = changedHeads.foldLeft(this.idtoEntity)((acc, me) => acc + (me.id -> me))
      } yield MarkedEntitiesState(updatedIdToEntity, updatedLabelToEntity)
      maybeUpdatedState.getOrElse(this)
    }
  }

  object MarkedEntitiesState {
    def fromResources(resources: Set[Resource]): MarkedEntitiesState = {
      val mes = resources.map(r => MarkableEntity(r.id, r.labels.toMap.toSet))

      val idToEntityMap = mes.map(me => (me.id -> me)).toMap
      val labelsToEntityMap = (for {
        me       <- mes
        keyValue <- me.labels
      } yield (keyValue, me)).groupMap(e => e._1)(e => e._2).mapValues(_.toList).toMap
      MarkedEntitiesState(idToEntityMap, labelsToEntityMap)
    }
  }

  private case class IdMaps(
    requirementsMap: Map[ConfigElementId, RequirementsModel],
    resourcesMap: Map[ConfigElementId, Resource]
  ) {
    lazy val resources: Set[Resource]             = this.resourcesMap.values.toSet
    lazy val requirements: Set[RequirementsModel] = this.requirementsMap.values.toSet
  }
}

object RequirementCheck extends RequirementCheck
