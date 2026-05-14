package cvut.fit.energyStats.controller.forecastController

import cvut.fit.energyStats.domain.dto.ProductionForecastDataDTO
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.datetime.LocalDateTime
import java.util.SortedMap

data class ProductionForecastDataResp(
    @field:Schema(description = "Prediction for the current day")
    val todayData: SortedMap<LocalDateTime, Double>,
    @field:Schema(description = "Total expected production for the current day in kWh")
    val todayTotal: Double,
    @field:Schema(description = "Prediction for the following day")
    val tomorrowData: SortedMap<LocalDateTime, Double>,
    @field:Schema(description = "Total expected production for the following day in kWh")
    val tomorrowTotal: Double,
)

fun ProductionForecastDataDTO.toProductionForecastDataResp(): ProductionForecastDataResp =
    ProductionForecastDataResp(
        todayData.toSortedMap(),
        todayData.values.sum(),
        tomorrowData.toSortedMap(),
        tomorrowData.values.sum(),
    )
