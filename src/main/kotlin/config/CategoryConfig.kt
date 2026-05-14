package cvut.fit.energyStats.config

import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.repository.energyDataRepository.EnergyConsumptionFromTheGridRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyDataRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionToTheGridRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionTotalRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

typealias CategoryRepositories = Map<DataCategory, EnergyDataRepository?>

@Configuration
class CategoryConfig {
    @Bean
    fun categories(
        energyConsumptionFromTheGridRepository: EnergyConsumptionFromTheGridRepository,
        energyProductionToTheGridRepository: EnergyProductionToTheGridRepository,
        energyProductionTotalRepository: EnergyProductionTotalRepository,
    ): CategoryRepositories =
        mapOf(
            DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to energyConsumptionFromTheGridRepository,
            DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to energyProductionToTheGridRepository,
            DataCategory.ENERGY_PRODUCTION_TOTAL to energyProductionTotalRepository,
            DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED to null,
            DataCategory.ENERGY_CONSUMPTION_TOTAL to null,
        )
}
