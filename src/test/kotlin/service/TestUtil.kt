package service

import cvut.fit.energyStats.config.CategoryConfig
import cvut.fit.energyStats.config.CategoryRepositories
import cvut.fit.energyStats.domain.CalculationExpr
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.Operator
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.repository.energyDataRepository.EnergyConsumptionFromTheGridRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionToTheGridRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionTotalRepository
import io.mockk.mockk

fun categoriesInstance(): CategoryRepositories {
    val mockEnergyProductionTotalRepository: EnergyProductionTotalRepository = mockk()
    val mockEnergyConsumptionFromTheGridRepository: EnergyConsumptionFromTheGridRepository = mockk()
    val mockEnergyProductionToTheGridRepository: EnergyProductionToTheGridRepository = mockk()
    return CategoryConfig()
        .categories(
            mockEnergyConsumptionFromTheGridRepository,
            mockEnergyProductionToTheGridRepository,
            mockEnergyProductionTotalRepository,
        )
}

val fromGeneration =
    CalculationExpr(
        DataCategory.ENERGY_PRODUCTION_TOTAL,
        DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
        Operator.MINUS,
    )
val fromConsumption =
    CalculationExpr(
        DataCategory.ENERGY_CONSUMPTION_TOTAL,
        DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
        Operator.MINUS,
    )

fun unavailableDataAccessMethodDTO(
    siteId: Int,
    dataCategory: DataCategory,
) = DataAccessMethodDTO(siteId, dataCategory, DataAccessType.UNAVAILABLE, null, null, null)
