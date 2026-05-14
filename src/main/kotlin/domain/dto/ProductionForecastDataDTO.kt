package cvut.fit.energyStats.domain.dto

import kotlinx.datetime.LocalDateTime

typealias ForecastEntries = Map<LocalDateTime, Double>

data class ProductionForecastDataDTO(
    val todayData: ForecastEntries,
    val tomorrowData: ForecastEntries,
)
