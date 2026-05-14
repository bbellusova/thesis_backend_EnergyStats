package cvut.fit.energyStats.controller.energyDataController

import cvut.fit.energyStats.domain.dto.EnergyDataDTO
import cvut.fit.energyStats.domain.dto.MaxDayDTO
import cvut.fit.energyStats.domain.dto.MaxHourDTO
import cvut.fit.energyStats.domain.dto.PeakHourDTO
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.util.SortedMap

data class EnergyDataDailyResp(
    @field:Schema(description = "Produced or consumed energy, map of ISO date string to value")
    val entries: SortedMap<LocalDate, Double>,
    @field:Schema(description = "True if all data is collected, false otherwise")
    val allDataPresent: Boolean,
    @field:Schema(description = "States the reason why not all data is present")
    val message: String,
)

data class EnergyDataHourlyResp(
    @field:Schema(description = "Produced or consumed energy, map of ISO date time string to value")
    val entries: SortedMap<LocalDateTime, Double>,
    @field:Schema(description = "True if all data is collected, false otherwise")
    val allDataPresent: Boolean,
    @field:Schema(description = "States the reason why not all data is present")
    val message: String,
)

fun EnergyDataDTO.toEnergyDataDailyResp(): EnergyDataDailyResp =
    EnergyDataDailyResp(entries.mapKeys { (k, _) -> k.date }.toSortedMap(), allDataPresent, message)

fun EnergyDataDTO.toEnergyDataHourlyResp(): EnergyDataHourlyResp = EnergyDataHourlyResp(entries.toSortedMap(), allDataPresent, message)

data class MaxHourResp(
    @field:Schema(description = "The highest produced or consumed energy value") val value: Double,
    @field:Schema(
        description = "The hours during which this value was achieved, ISO date time string",
    )
    val hours: List<String>,
)

fun MaxHourDTO.toMaxHourResp(): MaxHourResp = MaxHourResp(this.value, this.hours.map { it.toString() }.sorted())

data class PeakHourResp(
    @field:Schema(
        description = "The hours which had the highest value the most times during set period",
    )
    val hours: List<Int>,
)

fun PeakHourDTO.toPeakHourResp(): PeakHourResp = PeakHourResp(this.hours.sorted())

data class MaxDayResp(
    @field:Schema(description = "The highest produced/ consumed energy value") val value: Double,
    @field:Schema(description = "The days during which this value was achieved, ISO date string")
    val days: List<String>,
)

fun MaxDayDTO.toMaxDayResp(): MaxDayResp = MaxDayResp(this.value, this.days.map { it.toString() }.sorted())

data class MonthlyStatisticsResp(
    @field:Schema(description = "Total value for a provided month") val totalValue: Double,
    @field:Schema(description = "Daily average for a provided month") val dailyAverage: Double,
    @field:Schema(description = "Peak hour(s) for a provided month") val peakHours: PeakHourResp,
    @field:Schema(description = "Max hour(s) for a provided month") val maxHours: MaxHourResp,
    @field:Schema(description = "Peak day(s) for a provided month") val maxDays: MaxDayResp,
)

data class DailyStatisticsResp(
    @field:Schema(description = "Total value for a provided day") val totalValue: Double,
    @field:Schema(description = "Peak hour(s) for a provided day") val maxHours: MaxHourResp,
)

data class YearlyStatisticsResp(
    @field:Schema(description = "Total value for a provided year") val totalValue: Double,
    @field:Schema(description = "Peak hour(s) for a provided year") val maxHours: MaxHourResp,
)
