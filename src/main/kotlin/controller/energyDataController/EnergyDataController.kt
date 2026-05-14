package cvut.fit.energyStats.controller.energyDataController

import cvut.fit.energyStats.config.IsSiteOwner
import cvut.fit.energyStats.domain.AvailableUnits
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.energyDataService.EnergyDataService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.toJavaMonth
import org.jetbrains.exposed.v1.core.SortOrder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Year

@RestController
@SecurityRequirement(name = "JSESSIONID")
@Tag(name = "Statistics and data for charts")
@RequestMapping("energyData/{siteId}")
@IsSiteOwner
class EnergyDataController(
    val service: EnergyDataService,
    val dataAccessMethodService: DataAccessMethodService,
) {
    @Operation(summary = "Get data aggregated by days for a provided month")
    @GetMapping("/{dataCategory}/forMonthByDays/{year}/{month}")
    @ApiResponse(responseCode = "200", description = "Data returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    suspend fun getDataForMonthByDays(
        @PathVariable dataCategory: DataCategory,
        @PathVariable siteId: Int,
        @PathVariable year: Int,
        @PathVariable month: Int,
    ): EnergyDataDailyResp {
        val monthPeriod = convertToMonthTimePeriod(year, month)
        return service
            .getDataByDays(siteId, dataCategory, monthPeriod.first.date, monthPeriod.second.date)
            .toEnergyDataDailyResp()
    }

    @Operation(summary = "Get data aggregated by hours for a provided day")
    @GetMapping("/{dataCategory}/forDayByHours/{year}/{month}/{day}")
    @ApiResponse(responseCode = "200", description = "Data returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    suspend fun getDataForDayByHours(
        @PathVariable dataCategory: DataCategory,
        @PathVariable siteId: Int,
        @PathVariable year: Int,
        @PathVariable month: Int,
        @PathVariable day: Int,
    ): EnergyDataHourlyResp {
        val dayPeriod = convertToDayTimePeriod(year, month, day)
        return service
            .getDataByHours(siteId, dataCategory, dayPeriod.first, dayPeriod.second)
            .toEnergyDataHourlyResp()
    }

    @Operation(summary = "Get statistics for a provided month (year, month)")
    @GetMapping("/{dataCategory}/statistics/{year}/{month}")
    @ApiResponse(responseCode = "200", description = "Statistics returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    suspend fun getStatisticsForMonth(
        @PathVariable dataCategory: DataCategory,
        @PathVariable siteId: Int,
        @PathVariable year: Int,
        @PathVariable month: Int,
    ): MonthlyStatisticsResp {
        val monthPeriod = convertToMonthTimePeriod(year, month)
        if (shouldTryDataUpdate(siteId, dataCategory)) {
            service.getDataByHours(siteId, dataCategory, monthPeriod.first, monthPeriod.second)
        }
        return coroutineScope {
            val total =
                async {
                    service.getTotal(siteId, dataCategory, monthPeriod.first, monthPeriod.second)
                }
            val dailyAverage =
                async {
                    service.getDailyAverage(
                        siteId,
                        dataCategory,
                        monthPeriod.first.date,
                        monthPeriod.second.date,
                    )
                }
            val peakHour =
                async {
                    service.getPeakHours(siteId, dataCategory, monthPeriod.first.date, monthPeriod.second.date)
                }
            val maxHour =
                async {
                    service.getMaxHours(siteId, dataCategory, monthPeriod.first, monthPeriod.second)
                }
            val maxDay =
                async {
                    service.getMaxDays(siteId, dataCategory, monthPeriod.first.date, monthPeriod.second.date)
                }
            val peakDayResp = peakHour.await().toPeakHourResp()
            val maxDayResp = maxDay.await().toMaxDayResp()
            val maxHourResp = maxHour.await().toMaxHourResp()
            return@coroutineScope MonthlyStatisticsResp(
                total.await(),
                dailyAverage.await(),
                peakDayResp,
                maxHourResp,
                maxDayResp,
            )
        }
    }

    @Operation(summary = "Get statistics for a provided date (year, month, day)")
    @GetMapping("/{dataCategory}/statistics/{year}/{month}/{day}")
    @ApiResponse(responseCode = "200", description = "Statistics returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    suspend fun getStatisticsForDay(
        @PathVariable dataCategory: DataCategory,
        @PathVariable siteId: Int,
        @PathVariable year: Int,
        @PathVariable month: Int,
        @PathVariable day: Int,
    ): DailyStatisticsResp {
        val dayPeriod = convertToDayTimePeriod(year, month, day)
        if (shouldTryDataUpdate(siteId, dataCategory)) {
            service.getDataByHours(siteId, dataCategory, dayPeriod.first, dayPeriod.second)
        }
        return coroutineScope {
            val total =
                async {
                    service.getTotal(siteId, dataCategory, dayPeriod.first, dayPeriod.second)
                }
            val maxHour =
                async {
                    service.getMaxHours(siteId, dataCategory, dayPeriod.first, dayPeriod.second)
                }

            return@coroutineScope DailyStatisticsResp(total.await(), maxHour.await().toMaxHourResp())
        }
    }

    @Operation(summary = "Get statistics for a provided year")
    @GetMapping("/{dataCategory}/statistics/{year}")
    @ApiResponse(responseCode = "200", description = "Statistics returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    suspend fun getStatisticsForYear(
        @PathVariable dataCategory: DataCategory,
        @PathVariable siteId: Int,
        @PathVariable year: Int,
    ): YearlyStatisticsResp {
        val yearPeriod = convertToYearTimePeriod(year)
        if (shouldTryDataUpdate(siteId, dataCategory)) {
            service.getDataByHours(siteId, dataCategory, yearPeriod.first, yearPeriod.second)
        }
        return coroutineScope {
            val total =
                async {
                    service.getTotal(siteId, dataCategory, yearPeriod.first, yearPeriod.second)
                }
            val maxHour =
                async {
                    service.getMaxHours(siteId, dataCategory, yearPeriod.first, yearPeriod.second)
                }

            return@coroutineScope YearlyStatisticsResp(total.await(), maxHour.await().toMaxHourResp())
        }
    }

    @Operation(summary = "Get all years for which data is available for provided categories")
    @GetMapping("/availableYears/{sortOrder}")
    @ApiResponse(responseCode = "200", description = "Available years returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    fun getAvailableYears(
        @RequestParam dataCategories: List<DataCategory>,
        @PathVariable siteId: Int,
        @PathVariable sortOrder: SortOrder,
    ): List<Int> = service.getAvailable(siteId, dataCategories, sortOrder, AvailableUnits.YEARS)

    @Operation(
        summary =
            "Get all months of a provided year for which data is available for provided categories",
    )
    @GetMapping("/availableMonths/{year}/{sortOrder}")
    @ApiResponse(responseCode = "200", description = "Available months returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    fun getAvailableMonthsForYear(
        @RequestParam dataCategories: List<DataCategory>,
        @PathVariable siteId: Int,
        @PathVariable year: Int,
        @PathVariable sortOrder: SortOrder,
    ): List<Int> = service.getAvailable(siteId, dataCategories, sortOrder, AvailableUnits.MONTHS_IN_YEAR, year)

    @Operation(
        summary =
            "Get all days of a provided month and year for which data is available for provided categories",
    )
    @GetMapping("/availableDays/{year}/{month}/{sortOrder}")
    @ApiResponse(responseCode = "200", description = "Available days returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    fun getAvailableDaysForMonthInYear(
        @RequestParam dataCategories: List<DataCategory>,
        @PathVariable siteId: Int,
        @PathVariable year: Int,
        @PathVariable month: Int,
        @PathVariable sortOrder: SortOrder,
    ): List<Int> =
        service.getAvailable(
            siteId,
            dataCategories,
            sortOrder,
            AvailableUnits.DAYS_IN_MONTH,
            year,
            month,
        )

    private fun convertToMonthTimePeriod(
        year: Int,
        month: Int,
    ): Pair<LocalDateTime, LocalDateTime> {
        val dateFrom = LocalDateTime(year, month, 1, 0, 0, 0, 0)
        val dateTo =
            LocalDateTime(
                year,
                month,
                Month(month).toJavaMonth().length(Year.of(year).isLeap),
                23,
                59,
                59,
                59,
            )
        return Pair(dateFrom, dateTo)
    }

    private fun convertToDayTimePeriod(
        year: Int,
        month: Int,
        day: Int,
    ): Pair<LocalDateTime, LocalDateTime> {
        val dateFrom = LocalDateTime(year, month, day, 0, 0, 0, 0)
        val dateTo = LocalDateTime(year, month, day, 23, 59, 59, 59)
        return Pair(dateFrom, dateTo)
    }

    private fun convertToYearTimePeriod(year: Int): Pair<LocalDateTime, LocalDateTime> {
        val dateFrom = LocalDateTime(year, 1, 1, 0, 0, 0, 0)
        val dateTo = LocalDateTime(year, 12, 31, 23, 59, 59, 59)
        return Pair(dateFrom, dateTo)
    }

    private fun shouldTryDataUpdate(
        siteId: Int,
        dataCategory: DataCategory,
    ): Boolean {
        val dataAccessType =
            dataAccessMethodService.getAccessMethod(siteId, dataCategory).dataAccessType
        return dataAccessType == DataAccessType.API || dataAccessType == DataAccessType.CALCULATED
    }
}
