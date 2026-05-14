package cvut.fit.energyStats.service.dataAccessMethodService

import cvut.fit.energyStats.config.CategoryRepositories
import cvut.fit.energyStats.config.Providers
import cvut.fit.energyStats.domain.CalculationExpr
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.Operator
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethodRepository
import org.jasypt.encryption.StringEncryptor
import org.springframework.stereotype.Service

@Service
class DataAccessMethodService(
    private val dataAccessMethodRepository: DataAccessMethodRepository,
    private val providers: Providers,
    private val categoryRepositories: CategoryRepositories,
    private val stringEncryptor: StringEncryptor,
) {
    fun getAvailableCategoriesForSite(siteId: Int): Set<DataCategory> = dataAccessMethodRepository.getAvailableCategoriesForSite(siteId)

    fun getProvidersWithFileDataImportForSite(siteId: Int): Map<DataProvider, Set<DataCategory>> =
        dataAccessMethodRepository.getProvidersWithFileDataImport(siteId)

    fun getAccessMethod(
        siteId: Int,
        category: DataCategory,
    ): DataAccessMethodDTO {
        val dto = dataAccessMethodRepository.getAccessMethod(siteId, category)
        return if (dto.apiKey != null) {
            dto.copy(apiKey = stringEncryptor.decrypt(dto.apiKey))
        } else {
            dto
        }
    }

    fun updateAccessMethod(
        dataAccessMethodDTO: DataAccessMethodDTO,
        loopThroughCategories: Set<DataCategory> = DataCategory.entries.toSet(),
    ) {
        try {
            checkRequestConsistency(dataAccessMethodDTO)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("failed to update access method: ${e.message}")
        }
        val persistDataAccessMethod = removeInvalidFields(dataAccessMethodDTO)
        val encryptedDataAccessMethodDTO =
            if (persistDataAccessMethod.apiKey != null) {
                persistDataAccessMethod.copy(
                    apiKey = stringEncryptor.encrypt(persistDataAccessMethod.apiKey),
                )
            } else {
                persistDataAccessMethod
            }
        dataAccessMethodRepository.updateAccessMethod(encryptedDataAccessMethodDTO)
        setCalculatedCategoriesForSite(
            dataAccessMethodDTO.siteId,
            loopThroughCategories.minus(dataAccessMethodDTO.dataCategory),
        )
    }

    fun getCalculationExpr(
        siteId: Int,
        category: DataCategory,
    ): CalculationExpr {
        val available = getAvailableCategoriesForSite(siteId)
        val calculated = dataAccessMethodRepository.getCalculatedCategories(siteId)
        require(calculated.contains(category)) { "${category.description} is not set to calculated" }
        val availableNotCalculated = available.minus(calculated)
        if (category == DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED) {
            if (
                availableNotCalculated.contains(DataCategory.ENERGY_PRODUCTION_TOTAL) &&
                availableNotCalculated.contains(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID)
            ) {
                return fromGeneration
            }
            if (
                availableNotCalculated.contains(DataCategory.ENERGY_CONSUMPTION_TOTAL) &&
                availableNotCalculated.contains(DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID)
            ) {
                return fromConsumption
            }
            // if this happens it means setting calculated categories collapsed
            throw IllegalArgumentException(
                "${DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED.description} cannot be calculated",
            )
        }
        return calculationExpressions[category]!!.first()
    }

    private fun checkRequestConsistency(dateAccessMethodDTO: DataAccessMethodDTO) {
        require(DataCategory.entries.toSet().contains(dateAccessMethodDTO.dataCategory)) {
            "invalid data category"
        }
        require(DataAccessType.entries.toSet().contains(dateAccessMethodDTO.dataAccessType)) {
            "invalid access method"
        }
        if (dateAccessMethodDTO.dataAccessType.isProviderDataAccessType) {
            require(categoryRepositories[dateAccessMethodDTO.dataCategory] != null) {
                "${dateAccessMethodDTO.dataCategory} does not support import from a provider"
            }
            require(dateAccessMethodDTO.dataProvider != null) {
                "data provider cannot be null" +
                    " for data access type ${dateAccessMethodDTO.dataAccessType}"
            }
            providers[dateAccessMethodDTO.dataProvider]?.validateCategoryAndAccessType(
                dateAccessMethodDTO.dataCategory,
                dateAccessMethodDTO.dataAccessType,
            ) ?: throw IllegalArgumentException("unknown provider")
        }
        if (dateAccessMethodDTO.dataAccessType == DataAccessType.API) {
            require(
                dateAccessMethodDTO.apiKey != null,
            ) {
                "the provider-specific API key must be set for API access type"
            }
            require(
                dateAccessMethodDTO.dataProviderSiteId != null,
            ) {
                "the provider-specific site ID must be set for API access type"
            }
        }
        if (dateAccessMethodDTO.dataAccessType == DataAccessType.CALCULATED) {
            require(getNumberOfCalculatedCategories(dateAccessMethodDTO.siteId) < 2) {
                "there cannot be more than two calculated categories"
            }
            val cannotBeSetToCalculatedWith =
                cannotBeSetToCalculatedWithCalculated[dateAccessMethodDTO.dataCategory]
            if (cannotBeSetToCalculatedWith != null) {
                val accessTypeOfOther =
                    getAccessMethod(dateAccessMethodDTO.siteId, cannotBeSetToCalculatedWith).dataAccessType
                require(accessTypeOfOther != DataAccessType.CALCULATED) {
                    "${dateAccessMethodDTO.dataCategory} cannot be calculated when $cannotBeSetToCalculatedWith is calculated"
                }
            }
        }
    }

    private fun setCalculatedCategoriesForSite(
        siteId: Int,
        loopThroughCategories: Set<DataCategory>,
    ) {
        outer@ for (cat in loopThroughCategories) {
            for (expr in calculationExpressions[cat]!!) {
                val setNewCategory =
                    verifyAndSetCalculatedCategory(
                        siteId,
                        cat,
                        Pair(
                            expr.operand1,
                            expr.operand2,
                        ),
                    )
                if (setNewCategory) {
                    break@outer
                }
            }
        }
    }

    // verifyAndSetCalculatedCategory sets category to CALCULATED if possible to calculate it, and to
    // UNAVAILABLE if not possible to calculate it
    private fun verifyAndSetCalculatedCategory(
        siteId: Int,
        categoryToCalculate: DataCategory,
        needsAvailable: Pair<DataCategory, DataCategory>,
    ): Boolean {
        val currAccess = getAccessMethod(siteId, categoryToCalculate).dataAccessType
        if (currAccess.isProviderDataAccessType) {
            return false
        }
        if (canBeSetToCalculated(siteId, categoryToCalculate, needsAvailable)) {
            val loopThrough =
                DataCategory.entries
                    .minus(needsAvailable.toList().toSet())
                    .minus(categoryToCalculate)
                    .toSet()
            updateAccessMethod(
                DataAccessMethodDTO(
                    siteId,
                    categoryToCalculate,
                    DataAccessType.CALCULATED,
                    null,
                    null,
                    null,
                ),
                loopThrough,
            )
            return true
        } else {
            dataAccessMethodRepository.updateAccessMethod(
                DataAccessMethodDTO(
                    siteId,
                    categoryToCalculate,
                    DataAccessType.UNAVAILABLE,
                    null,
                    null,
                    null,
                ),
            )
        }
        return false
    }

    private fun canBeSetToCalculated(
        siteId: Int,
        categoryToCalculate: DataCategory,
        needsAvailable: Pair<DataCategory, DataCategory>,
    ): Boolean {
        val availableCategories = getAvailableCategoriesForSite(siteId)
        if (availableCategories.containsAll(needsAvailable.toList())) {
            val currentlyCalculated = dataAccessMethodRepository.getCalculatedCategories(siteId)
            if (currentlyCalculated.size >= 2) return false
            val cannotBeSetToCalculatedWith = cannotBeSetToCalculatedWithCalculated[categoryToCalculate]
            if (currentlyCalculated.contains(cannotBeSetToCalculatedWith)) return false

            if (categoryToCalculate == DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED) {
                val canBeCalculatedFromGeneration =
                    !currentlyCalculated.contains(fromGeneration.operand1) &&
                        !currentlyCalculated.contains(fromGeneration.operand2)
                val canBeCalculatedFromConsumption =
                    !currentlyCalculated.contains(fromConsumption.operand1) &&
                        !currentlyCalculated.contains(fromConsumption.operand2)
                return canBeCalculatedFromGeneration || canBeCalculatedFromConsumption
            }
            return true
        }
        return false
    }

    private fun getNumberOfCalculatedCategories(siteId: Int): Int = dataAccessMethodRepository.getCalculatedCategories(siteId).size

    private fun removeInvalidFields(dto: DataAccessMethodDTO): DataAccessMethodDTO =
        DataAccessMethodDTO(
            dto.siteId,
            dto.dataCategory,
            dto.dataAccessType,
            if (dto.dataAccessType.isProviderDataAccessType) dto.dataProvider else null,
            if (dto.dataAccessType == DataAccessType.API) dto.dataProviderSiteId else null,
            if (dto.dataAccessType == DataAccessType.API) dto.apiKey else null,
        )

    private val cannotBeSetToCalculatedWithCalculated =
        mapOf(
            DataCategory.ENERGY_CONSUMPTION_TOTAL to DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
            DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to DataCategory.ENERGY_CONSUMPTION_TOTAL,
            DataCategory.ENERGY_PRODUCTION_TOTAL to DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
            DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to DataCategory.ENERGY_PRODUCTION_TOTAL,
        )

    private val fromConsumption =
        CalculationExpr(
            DataCategory.ENERGY_CONSUMPTION_TOTAL,
            DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
            Operator.MINUS,
        )

    private val fromGeneration =
        CalculationExpr(
            DataCategory.ENERGY_PRODUCTION_TOTAL,
            DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
            Operator.MINUS,
        )

    private val calculationExpressions =
        mapOf(
            DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to
                setOf(
                    CalculationExpr(
                        DataCategory.ENERGY_CONSUMPTION_TOTAL,
                        DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                        Operator.MINUS,
                    ),
                ),
            DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to
                setOf(
                    CalculationExpr(
                        DataCategory.ENERGY_PRODUCTION_TOTAL,
                        DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                        Operator.MINUS,
                    ),
                ),
            DataCategory.ENERGY_PRODUCTION_TOTAL to
                setOf(
                    CalculationExpr(
                        DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                        DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                        Operator.PLUS,
                    ),
                ),
            DataCategory.ENERGY_CONSUMPTION_TOTAL to
                setOf(
                    CalculationExpr(
                        DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                        DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                        Operator.PLUS,
                    ),
                ),
            DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED to
                setOf(fromConsumption, fromGeneration),
        )
}
