package cvut.fit.energyStats.service.energyDataService

import cvut.fit.energyStats.config.CategoryRepositories
import cvut.fit.energyStats.domain.AvailableUnits
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.Operator
import cvut.fit.energyStats.domain.dto.EnergyDataDTO
import cvut.fit.energyStats.domain.dto.Entries
import cvut.fit.energyStats.domain.dto.MaxDayDTO
import cvut.fit.energyStats.domain.dto.MaxHourDTO
import cvut.fit.energyStats.domain.dto.PeakHourDTO
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaMonth
import kotlinx.datetime.until
import kotlinx.datetime.yearMonth
import org.jetbrains.exposed.v1.core.SortOrder
import org.springframework.stereotype.Service
import java.time.Year.isLeap
import java.time.temporal.ChronoUnit

@Service
class EnergyDataService(
    private val dataAccess: DataAccessMethodService,
    private val energyDataImportService: EnergyDataImportService,
    private val categoryRepositories: CategoryRepositories,
) {
    suspend fun getDataByHours(
        site: Int,
        category: DataCategory,
        dateTimeFrom: LocalDateTime,
        dateTimeTo: LocalDateTime,
        fromDbOnly: Boolean = false,
    ): EnergyDataDTO {
        val expectedLen =
            ChronoUnit.HOURS.between(
                dateTimeFrom.toJavaLocalDateTime(),
                dateTimeTo.toJavaLocalDateTime(),
            )
        val dataAccessType = dataAccess.getAccessMethod(site, category).dataAccessType
        if (dataAccessType == DataAccessType.CALCULATED) {
            return this.getCalculatedDataByHours(site, category, dateTimeFrom, dateTimeTo, fromDbOnly)
        }
        return this.getData(
            site,
            category,
            dataAccessType,
            dateTimeFrom,
            dateTimeTo,
            expectedLen,
            fromDbOnly,
        ) { siteId, from, to ->
            categoryRepositories[category]?.getDataByHours(siteId, from, to)
                ?: throw IllegalArgumentException("Repository for $category not found")
        }
    }

    suspend fun getDataByDays(
        site: Int,
        category: DataCategory,
        dateFrom: LocalDate,
        dateTo: LocalDate,
        fromDbOnly: Boolean = false,
    ): EnergyDataDTO {
        val expectedLen = dateFrom.until(dateTo, DateTimeUnit.DAY) + 1
        val dataAccessType = dataAccess.getAccessMethod(site, category).dataAccessType
        if (dataAccessType == DataAccessType.CALCULATED) {
            return this.getCalculatedDataByDays(site, category, dateFrom, dateTo, fromDbOnly)
        }
        val dateTimeFrom = LocalDateTime(dateFrom, LocalTime(0, 0, 0, 0))
        val dateTimeTo = LocalDateTime(dateTo, LocalTime(23, 59, 59, 59))
        return this.getData(
            site,
            category,
            dataAccessType,
            dateTimeFrom,
            dateTimeTo,
            expectedLen,
            fromDbOnly,
        ) { siteId, from, to ->
            categoryRepositories[category]?.getDataByDays(siteId, from, to)
                ?: throw IllegalArgumentException("Repository for $category not found")
        }
    }

    private suspend fun getCalculatedDataByHours(
        site: Int,
        category: DataCategory,
        dateTimeFrom: LocalDateTime,
        dateTimeTo: LocalDateTime,
        fromDbOnly: Boolean,
    ): EnergyDataDTO {
        val calculationExpr = dataAccess.getCalculationExpr(site, category)
        val data1 =
            this.getDataByHours(
                site,
                calculationExpr.operand1,
                dateTimeFrom,
                dateTimeTo,
                fromDbOnly,
            )
        val data2 =
            this.getDataByHours(
                site,
                calculationExpr.operand2,
                dateTimeFrom,
                dateTimeTo,
                fromDbOnly,
            )
        return processData(category, data1, data2, calculationExpr.operator)
    }

    private suspend fun getCalculatedDataByDays(
        site: Int,
        category: DataCategory,
        dateFrom: LocalDate,
        dateTo: LocalDate,
        fromDbOnly: Boolean,
    ): EnergyDataDTO {
        val calculationExpr = dataAccess.getCalculationExpr(site, category)
        val data1 =
            this.getDataByDays(
                site,
                calculationExpr.operand1,
                dateFrom,
                dateTo,
                fromDbOnly,
            )
        val data2 =
            this.getDataByDays(
                site,
                calculationExpr.operand2,
                dateFrom,
                dateTo,
                fromDbOnly,
            )
        return processData(category, data1, data2, calculationExpr.operator)
    }

    private fun processData(
        category: DataCategory,
        data1: EnergyDataDTO,
        data2: EnergyDataDTO,
        operator: Operator,
    ): EnergyDataDTO {
        val allDataPresent = data1.allDataPresent && data2.allDataPresent
        var message = ""
        if (!allDataPresent) {
            message =
                "Not all data for category ${category.description} is available; data it depends on are not (yet) available"
        }
        val result = EnergyDataDTO(mutableMapOf(), allDataPresent, message)
        val allTimestamps = data1.entries.keys union data2.entries.keys
        allTimestamps.forEach {
            val data1Value = data1.entries[it]
            val data2Value = data2.entries[it]
            if (data1Value != null && data2Value != null) {
                val resultValue =
                    when (operator) {
                        Operator.PLUS -> data1Value + data2Value
                        Operator.MINUS -> data1Value - data2Value
                    }
                if (resultValue >= 0.0) result.entries[it] = resultValue
            }
        }
        return result
    }

    private suspend fun getData(
        site: Int,
        category: DataCategory,
        dataAccessType: DataAccessType,
        from: LocalDateTime,
        to: LocalDateTime,
        expectedNumberOfEntries: Long,
        fromDbOnly: Boolean,
        getDataFun: suspend (Int, LocalDateTime, LocalDateTime) -> Entries,
    ): EnergyDataDTO {
        if (dataAccessType == DataAccessType.UNAVAILABLE) {
            throw IllegalArgumentException(
                "failed to get data for ${category.description}: data for this category are unavailable",
            )
        }

        var energyData = getDataFun(site, from, to)
        var numberOfEntries = energyData.size
        var allDataPresent = true
        var message = ""
        if (numberOfEntries < expectedNumberOfEntries) {
            if (dataAccessType == DataAccessType.API && !fromDbOnly) {
                try {
                    energyDataImportService.importDataFromAPI(site, category, from, to)
                    energyData = getDataFun(site, from, to)
                    numberOfEntries = energyData.size
                    allDataPresent = numberOfEntries.toLong() >= expectedNumberOfEntries
                    if (!allDataPresent) {
                        message = "API does not provide all data for category ${category.description}"
                    }
                } catch (e: IllegalStateException) {
                    allDataPresent = false
                    message = "failed to obtain data from the API: ${e.message}"
                }
            }
            if (dataAccessType == DataAccessType.FILE) {
                allDataPresent = false
                message =
                    "Not all data for category ${category.description} is available; import data from a file if possible"
            }
        }
        return EnergyDataDTO(energyData, allDataPresent, message)
    }

    fun getAvailable(
        site: Int,
        categories: List<DataCategory>,
        sortOrder: SortOrder,
        units: AvailableUnits,
        year: Int = 0,
        month: Int = 1,
    ): List<Int> {
        var result: Set<Int> = setOf()
        var resultInitialized = false
        categories.forEach {
            val currAvailable =
                when (units) {
                    AvailableUnits.YEARS -> {
                        getAvailableYearsForCategory(site, it)
                    }

                    AvailableUnits.MONTHS_IN_YEAR -> {
                        getAvailableMonthsForYearForCategory(site, it, year)
                    }

                    AvailableUnits.DAYS_IN_MONTH -> {
                        getAvailableDaysForMonthInYearForCategory(site, it, year, month)
                    }
                }
            result = if (resultInitialized) result.intersect(currAvailable) else currAvailable
            resultInitialized = true
        }
        return if (sortOrder == SortOrder.ASC) result.sorted() else result.sortedDescending()
    }

    private fun getAvailableYearsForCategory(
        siteId: Int,
        category: DataCategory,
    ): Set<Int> {
        val dataAccessMethod = dataAccess.getAccessMethod(siteId, category)
        when (dataAccessMethod.dataAccessType) {
            DataAccessType.API -> {
                val dateRange =
                    energyDataImportService.fetchAvailableDatesFromAPI(siteId, category, dataAccessMethod)
                return (dateRange.first.year..dateRange.second.year).toSet()
            }

            DataAccessType.FILE -> {
                return categoryRepositories[category]?.getAvailableYears(siteId)?.toSet()
                    ?: throw IllegalArgumentException("Repository for $category not found")
            }

            DataAccessType.CALCULATED -> {
                val calculationExpr = dataAccess.getCalculationExpr(siteId, category)
                val data1 = getAvailableYearsForCategory(siteId, calculationExpr.operand1)
                val data2 = getAvailableYearsForCategory(siteId, calculationExpr.operand2)
                return data1.intersect(data2)
            }

            DataAccessType.UNAVAILABLE -> {
                throw IllegalArgumentException("$category unavailable for site with id $siteId")
            }
        }
    }

    private fun getAvailableMonthsForYearForCategory(
        siteId: Int,
        category: DataCategory,
        year: Int,
    ): Set<Int> {
        val dataAccessMethod = dataAccess.getAccessMethod(siteId, category)
        when (dataAccessMethod.dataAccessType) {
            DataAccessType.API -> {
                val dateRange =
                    energyDataImportService.fetchAvailableDatesFromAPI(siteId, category, dataAccessMethod)
                return when {
                    year > dateRange.first.year && year < dateRange.second.year -> {
                        (1..12).toSet()
                    }

                    year == dateRange.first.year && year == dateRange.second.year -> {
                        (dateRange.first.month.number..dateRange.second.month.number).toSet()
                    }

                    year == dateRange.first.year -> {
                        (dateRange.first.month.number..12).toSet()
                    }

                    year == dateRange.second.year -> {
                        (1..dateRange.second.month.number).toSet()
                    }

                    else -> {
                        setOf()
                    }
                }
            }

            DataAccessType.FILE -> {
                return categoryRepositories[category]?.getAvailableMonthsForYear(siteId, year)?.toSet()
                    ?: throw IllegalArgumentException("Repository for $category not found")
            }

            DataAccessType.CALCULATED -> {
                val calculationExpr = dataAccess.getCalculationExpr(siteId, category)
                val data1 =
                    getAvailableMonthsForYearForCategory(
                        siteId,
                        calculationExpr.operand1,
                        year,
                    )
                val data2 =
                    getAvailableMonthsForYearForCategory(
                        siteId,
                        calculationExpr.operand2,
                        year,
                    )
                return data1.intersect(data2)
            }

            DataAccessType.UNAVAILABLE -> {
                throw IllegalArgumentException("$category unavailable for site with id $siteId")
            }
        }
    }

    private fun getAvailableDaysForMonthInYearForCategory(
        siteId: Int,
        category: DataCategory,
        year: Int,
        month: Int,
    ): Set<Int> {
        val dataAccessMethod = dataAccess.getAccessMethod(siteId, category)
        when (dataAccessMethod.dataAccessType) {
            DataAccessType.API -> {
                val dateRange =
                    energyDataImportService.fetchAvailableDatesFromAPI(siteId, category, dataAccessMethod)
                val lastDayInMonth = Month(month).toJavaMonth().length(isLeap(year.toLong()))
                val yearMonth = YearMonth(year, month)
                return when {
                    yearMonth > dateRange.first.yearMonth && yearMonth < dateRange.second.yearMonth -> {
                        (1..lastDayInMonth).toSet()
                    }

                    (yearMonth == dateRange.first.yearMonth) &&
                        dateRange.first.yearMonth == dateRange.second.yearMonth -> {
                        (dateRange.first.day..dateRange.second.day).toSet()
                    }

                    yearMonth == dateRange.first.yearMonth -> {
                        (dateRange.first.day..lastDayInMonth).toSet()
                    }

                    yearMonth == dateRange.second.yearMonth -> {
                        (1..dateRange.second.day).toSet()
                    }

                    else -> {
                        setOf()
                    }
                }
            }

            DataAccessType.FILE -> {
                return categoryRepositories[category]
                    ?.getAvailableDaysForMonthInYear(siteId, year, month)
                    ?.toSet() ?: throw IllegalArgumentException("Repository for $category not found")
            }

            DataAccessType.CALCULATED -> {
                val calculationExpr = dataAccess.getCalculationExpr(siteId, category)
                val data1 =
                    getAvailableDaysForMonthInYearForCategory(
                        siteId,
                        calculationExpr.operand1,
                        year,
                        month,
                    )
                val data2 =
                    getAvailableDaysForMonthInYearForCategory(
                        siteId,
                        calculationExpr.operand2,
                        year,
                        month,
                    )
                return data1.intersect(data2)
            }

            DataAccessType.UNAVAILABLE -> {
                throw IllegalArgumentException("$category unavailable for site with id $siteId")
            }
        }
    }

    // returns average value for a given period
    suspend fun getDailyAverage(
        siteId: Int,
        category: DataCategory,
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ): Double {
        val dailyData = this.getDataByDays(siteId, category, dateFrom, dateTo, true).entries
        if (dailyData.isEmpty()) return 0.0
        val sum = dailyData.values.sum()
        val count = dailyData.values.count()
        return if (count == 0) 0.0 else sum / count
    }

    // returns all days during which the value was the highest during a specified period
    suspend fun getMaxDays(
        siteId: Int,
        category: DataCategory,
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ): MaxDayDTO {
        val dailyData = this.getDataByDays(siteId, category, dateFrom, dateTo, true).entries
        if (dailyData.isEmpty()) return MaxDayDTO(0.0, setOf())
        val max = dailyData.values.max()
        if (max == 0.0) return MaxDayDTO(0.0, setOf())
        val maxDays =
            dailyData
                .filter { (_, value) -> value == max }
                .keys
                .map { it.date }
                .toSet()

        return MaxDayDTO(max, maxDays)
    }

    // returns all hours of a day during which the value was the highest during a specified period
    suspend fun getMaxHours(
        siteId: Int,
        category: DataCategory,
        dateTimeFrom: LocalDateTime,
        dateTimeTo: LocalDateTime,
    ): MaxHourDTO {
        val hourlyData = this.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo, true).entries
        if (hourlyData.isEmpty()) return MaxHourDTO(0.0, setOf())
        val max = hourlyData.values.max()
        if (max == 0.0) return MaxHourDTO(0.0, setOf())
        val maxHours = hourlyData.filter { (_, value) -> value == max }.keys.toSet()
        return MaxHourDTO(max, maxHours)
    }

  /*
  returns those hours of a day which had the highest value the most times during a specified period

  example: range 12.05.2025 to 15.05.2025
  12.05.2025 - max hour was 12:00 (had most kWh in this day)
  13.05.2025 - max hour was 13:00 (had most kWh in this day)
  14.05.2025 - max hour was 12:00 (had most kWh in this day)
  15.05.2025 - max hour was 11:00 (had most kWh in this day)
  -> the result will be 12:00
   */
    suspend fun getPeakHours(
        siteId: Int,
        category: DataCategory,
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ): PeakHourDTO {
        val hours = mutableMapOf<Int, Int>()
        dateFrom.rangeTo(dateTo).forEach {
            val hourlyData = getMaxHours(siteId, category, it.atTime(0, 0), it.atTime(23, 59, 59, 59))
            hourlyData.hours.forEach { hour ->
                val h = hour.hour
                hours[h] = hours[h]?.plus(1) ?: 1
            }
        }
        val maxOccurrences = hours.values.max()
        val result = hours.keys.filter { hours[it] == maxOccurrences }
        return PeakHourDTO(result)
    }

    // returns total value for a specified period
    suspend fun getTotal(
        siteId: Int,
        category: DataCategory,
        dateTimeFrom: LocalDateTime,
        dateTimeTo: LocalDateTime,
    ): Double {
        val hourlyData = this.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo, true).entries
        if (hourlyData.isEmpty()) return 0.0
        return hourlyData.values.sum()
    }
}
