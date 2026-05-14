package controller.energyDataController

import cvut.fit.energyStats.controller.energyDataController.EnergyDataController
import cvut.fit.energyStats.domain.AvailableUnits
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.domain.dto.EnergyDataDTO
import cvut.fit.energyStats.domain.dto.MaxDayDTO
import cvut.fit.energyStats.domain.dto.MaxHourDTO
import cvut.fit.energyStats.domain.dto.PeakHourDTO
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.energyDataService.EnergyDataService
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import kotlin.test.assertEquals

class TestEnergyDataController :
    FunSpec({
        val mockEnergyDataService: EnergyDataService = mockk()
        val mockDataAccessMethodService: DataAccessMethodService = mockk()
        val controller = EnergyDataController(mockEnergyDataService, mockDataAccessMethodService)

        val siteId = 1
        val dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL
        val year = 2024
        val month = 5
        val day = 10

        beforeEach { clearAllMocks() }

        test("getDataForMonthByDays returns correct daily response") {
            val dateFrom = LocalDate(year, month, 1)
            val dateTo = LocalDate(year, month, 31)
            val energyDataDTO =
                EnergyDataDTO(
                    entries = mutableMapOf(LocalDateTime(year, month, 1, 0, 0) to 10.0),
                    allDataPresent = true,
                    message = "OK",
                )

            coEvery {
                mockEnergyDataService.getDataByDays(siteId, dataCategory, dateFrom, dateTo)
            } returns energyDataDTO

            val result = controller.getDataForMonthByDays(dataCategory, siteId, year, month)

            assertEquals(1, result.entries.size)
            assertEquals(10.0, result.entries[LocalDate(year, month, 1)])
            assertEquals(true, result.allDataPresent)
            assertEquals("OK", result.message)
        }

        test("getDataForDayByHours returns correct hourly response") {
            val dateTimeFrom = LocalDateTime(year, month, day, 0, 0, 0, 0)
            val dateTimeTo = LocalDateTime(year, month, day, 23, 59, 59, 59)
            val energyDataDTO =
                EnergyDataDTO(
                    entries = mutableMapOf(LocalDateTime(year, month, day, 10, 0) to 2.0),
                    allDataPresent = true,
                    message = "OK",
                )

            coEvery {
                mockEnergyDataService.getDataByHours(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            } returns energyDataDTO

            val result = controller.getDataForDayByHours(dataCategory, siteId, year, month, day)

            assertEquals(1, result.entries.size)
            assertEquals(2.0, result.entries[LocalDateTime(year, month, day, 10, 0)])
            assertEquals(true, result.allDataPresent)
            assertEquals("OK", result.message)
        }

        test("getStatisticsForMonth returns correct statistics and triggers update for API") {
            val dateTimeFrom = LocalDateTime(year, month, 1, 0, 0, 0, 0)
            val dateTimeTo = LocalDateTime(year, month, 31, 23, 59, 59, 59)
            val dateFrom = LocalDate(year, month, 1)
            val dateTo = LocalDate(year, month, 31)

            every { mockDataAccessMethodService.getAccessMethod(siteId, dataCategory) } returns
                DataAccessMethodDTO(siteId, dataCategory, DataAccessType.API, null, null, null)
            coEvery {
                mockEnergyDataService.getDataByHours(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            } returns mockk()
            coEvery {
                mockEnergyDataService.getTotal(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            } returns 100.0
            coEvery {
                mockEnergyDataService.getDailyAverage(siteId, dataCategory, dateFrom, dateTo)
            } returns 3.3
            coEvery {
                mockEnergyDataService.getPeakHours(siteId, dataCategory, dateFrom, dateTo)
            } returns PeakHourDTO(listOf(12))
            coEvery {
                mockEnergyDataService.getMaxHours(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            } returns MaxHourDTO(10.0, setOf(LocalDateTime(year, month, day, 12, 0)))
            coEvery { mockEnergyDataService.getMaxDays(siteId, dataCategory, dateFrom, dateTo) } returns
                MaxDayDTO(20.0, setOf(LocalDate(year, month, day)))

            val result = controller.getStatisticsForMonth(dataCategory, siteId, year, month)

            assertEquals(100.0, result.totalValue)
            assertEquals(3.3, result.dailyAverage)
            assertEquals(listOf(12), result.peakHours.hours)
            assertEquals(10.0, result.maxHours.value)
            assertEquals(20.0, result.maxDays.value)

            coVerify {
                mockEnergyDataService.getDataByHours(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            }
        }

        test("getStatisticsForDay returns correct statistics and does not trigger update for FILE") {
            val dateTimeFrom = LocalDateTime(year, month, day, 0, 0, 0, 0)
            val dateTimeTo = LocalDateTime(year, month, day, 23, 59, 59, 59)

            every { mockDataAccessMethodService.getAccessMethod(siteId, dataCategory) } returns
                DataAccessMethodDTO(siteId, dataCategory, DataAccessType.FILE, null, null, null)
            coEvery {
                mockEnergyDataService.getTotal(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            } returns 50.0
            coEvery {
                mockEnergyDataService.getMaxHours(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            } returns MaxHourDTO(5.0, setOf(LocalDateTime(year, month, day, 14, 0)))

            val result = controller.getStatisticsForDay(dataCategory, siteId, year, month, day)

            assertEquals(50.0, result.totalValue)
            assertEquals(5.0, result.maxHours.value)

            coVerify(exactly = 0) { mockEnergyDataService.getDataByHours(any(), any(), any(), any()) }
        }

        test("getStatisticsForYear returns correct statistics") {
            val dateTimeFrom = LocalDateTime(year, 1, 1, 0, 0, 0, 0)
            val dateTimeTo = LocalDateTime(year, 12, 31, 23, 59, 59, 59)

            every { mockDataAccessMethodService.getAccessMethod(siteId, dataCategory) } returns
                DataAccessMethodDTO(siteId, dataCategory, DataAccessType.UNAVAILABLE, null, null, null)
            coEvery {
                mockEnergyDataService.getTotal(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            } returns 1000.0
            coEvery {
                mockEnergyDataService.getMaxHours(siteId, dataCategory, dateTimeFrom, dateTimeTo)
            } returns MaxHourDTO(15.0, setOf(LocalDateTime(year, 6, 1, 12, 0)))

            val result = controller.getStatisticsForYear(dataCategory, siteId, year)

            assertEquals(1000.0, result.totalValue)
            assertEquals(15.0, result.maxHours.value)
        }

        test("getAvailableYears propagates result") {
            val categories = listOf(dataCategory)
            val expected = listOf(2023, 2024)
            every {
                mockEnergyDataService.getAvailable(
                    siteId,
                    categories,
                    SortOrder.ASC,
                    AvailableUnits.YEARS,
                )
            } returns expected

            val result = controller.getAvailableYears(categories, siteId, SortOrder.ASC)

            assertEquals(expected, result)
        }

        test("getAvailableMonthsForYear propagates result") {
            val categories = listOf(dataCategory)
            val expected = listOf(1, 2, 3)
            every {
                mockEnergyDataService.getAvailable(
                    siteId,
                    categories,
                    SortOrder.ASC,
                    AvailableUnits.MONTHS_IN_YEAR,
                    year,
                )
            } returns expected

            val result = controller.getAvailableMonthsForYear(categories, siteId, year, SortOrder.ASC)

            assertEquals(expected, result)
        }

        test("getAvailableDaysForMonthInYear propagates result") {
            val categories = listOf(dataCategory)
            val expected = listOf(1, 15, 30)
            every {
                mockEnergyDataService.getAvailable(
                    siteId,
                    categories,
                    SortOrder.ASC,
                    AvailableUnits.DAYS_IN_MONTH,
                    year,
                    month,
                )
            } returns expected

            val result =
                controller.getAvailableDaysForMonthInYear(
                    categories,
                    siteId,
                    year,
                    month,
                    SortOrder.ASC,
                )

            assertEquals(expected, result)
        }
    })
