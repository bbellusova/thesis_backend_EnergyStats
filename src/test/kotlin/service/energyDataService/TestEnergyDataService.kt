package service.energyDataService

import cvut.fit.energyStats.config.CategoryConfig
import cvut.fit.energyStats.domain.AvailableUnits
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.domain.dto.EnergyDataDTO
import cvut.fit.energyStats.repository.energyDataRepository.EnergyConsumptionFromTheGridRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionToTheGridRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionTotalRepository
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.energyDataService.EnergyDataImportService
import cvut.fit.energyStats.service.energyDataService.EnergyDataService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import service.fromGeneration

class TestEnergyDataService :
    FunSpec({
        val mockDataAccessMethodService: DataAccessMethodService = mockk()
        val mockEnergyDataImportService: EnergyDataImportService = mockk()
        val mockEnergyConsumptionFromTheGridRepository: EnergyConsumptionFromTheGridRepository =
            mockk()
        val mockEnergyProductionToTheGridRepository: EnergyProductionToTheGridRepository = mockk()
        val mockEnergyProductionTotalRepository: EnergyProductionTotalRepository = mockk()

        val categories =
            CategoryConfig()
                .categories(
                    mockEnergyConsumptionFromTheGridRepository,
                    mockEnergyProductionToTheGridRepository,
                    mockEnergyProductionTotalRepository,
                )

        val service =
            EnergyDataService(mockDataAccessMethodService, mockEnergyDataImportService, categories)

        val siteId = 1
        val dateTimeFrom = LocalDateTime(2026, 1, 1, 0, 0)
        val dateTimeTo = LocalDateTime(2026, 1, 1, 3, 59)
        val dateFrom = LocalDate(2026, 1, 1)
        val dateTo = LocalDate(2026, 1, 3)

        fun dataAccessMethod(
            category: DataCategory,
            accessType: DataAccessType,
        ) = DataAccessMethodDTO(siteId, category, accessType, DataProvider.DUMMY, 1, "key")

        fun hourEntriesOf(vararg values: Pair<Int, Double>) =
            values
                .associate { (hour, value) -> LocalDateTime(2026, 1, 1, hour, 0) to value }
                .toMutableMap()

        fun dayEntriesOf(vararg values: Pair<Int, Double>) =
            values
                .associate { (day, value) -> LocalDateTime(2026, 1, day, 0, 0) to value }
                .toMutableMap()

        beforeTest { io.mockk.clearAllMocks() }

        test("getDataByHours returns file data when all expected hours are present") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = hourEntriesOf(0 to 1.0, 1 to 2.0, 2 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns entries

            service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo) shouldBe
                EnergyDataDTO(entries, true, "")

            coVerify(exactly = 0) {
                mockEnergyDataImportService.importDataFromAPI(any(), any(), any(), any())
            }
        }

        test("getDataByHours marks file data as incomplete when some hours are missing") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = hourEntriesOf(0 to 1.0, 2 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns entries

            service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo) shouldBe
                EnergyDataDTO(
                    entries,
                    false,
                    "Not all data for category ${category.description} is available; import data from a file if possible",
                )
        }

        test("getDataByHours imports missing API data and retries repository lookup") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val partialEntries = hourEntriesOf(0 to 1.0, 2 to 3.0)
            val completeEntries = hourEntriesOf(0 to 1.0, 1 to 2.0, 2 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.API)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns partialEntries andThen completeEntries
            every {
                mockEnergyDataImportService.importDataFromAPI(siteId, category, dateTimeFrom, dateTimeTo)
            } returns Unit

            service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo) shouldBe
                EnergyDataDTO(completeEntries, true, "")

            coVerify(exactly = 1) {
                mockEnergyDataImportService.importDataFromAPI(siteId, category, dateTimeFrom, dateTimeTo)
            }
        }

        test("getDataByHours reports API import failure when import throws") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val partialEntries = hourEntriesOf(0 to 1.0, 2 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.API)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns partialEntries
            every {
                mockEnergyDataImportService.importDataFromAPI(siteId, category, dateTimeFrom, dateTimeTo)
            } throws IllegalStateException("provider unavailable")

            service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo) shouldBe
                EnergyDataDTO(
                    partialEntries,
                    false,
                    "failed to obtain data from the API: provider unavailable",
                )
        }

        test("getDataByHours reports when API still does not provide all requested data") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val partialEntries = hourEntriesOf(0 to 1.0)
            val stillIncompleteEntries = hourEntriesOf(0 to 1.0, 1 to 2.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.API)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns partialEntries andThen stillIncompleteEntries
            every {
                mockEnergyDataImportService.importDataFromAPI(siteId, category, dateTimeFrom, dateTimeTo)
            } returns Unit

            service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo) shouldBe
                EnergyDataDTO(
                    stillIncompleteEntries,
                    false,
                    "API does not provide all data for category ${category.description}",
                )
        }

        test("getDataByHours combines calculated categories from their operands") {
            val category = DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED
            val totalEntries = hourEntriesOf(0 to 10.0, 1 to 8.0, 2 to 6.0)
            val toGridEntries = hourEntriesOf(0 to 3.0, 1 to 4.0, 2 to 1.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.CALCULATED)
            every { mockDataAccessMethodService.getCalculationExpr(siteId, category) } returns
                fromGeneration
            every {
                mockDataAccessMethodService.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL)
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TOTAL, DataAccessType.FILE)
            every {
                mockDataAccessMethodService.getAccessMethod(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                )
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns totalEntries
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns toGridEntries

            service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo) shouldBe
                EnergyDataDTO(
                    hourEntriesOf(0 to 7.0, 1 to 4.0, 2 to 5.0),
                    true,
                    "",
                )
        }

        test(
            "getDataByHours combines calculated categories from their operands and indicates not all data present",
        ) {
            val category = DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED
            val totalEntries = hourEntriesOf(0 to 10.0, 1 to 6.0, 2 to 5.0)
            val toGridEntries = hourEntriesOf(0 to 3.0, 1 to 2.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.CALCULATED)
            every { mockDataAccessMethodService.getCalculationExpr(siteId, category) } returns
                fromGeneration
            every {
                mockDataAccessMethodService.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL)
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TOTAL, DataAccessType.FILE)
            every {
                mockDataAccessMethodService.getAccessMethod(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                )
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns totalEntries
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns toGridEntries

            service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo) shouldBe
                EnergyDataDTO(
                    hourEntriesOf(0 to 7.0, 1 to 4.0),
                    false,
                    "Not all data for category ${category.description} is available; data it depends on are not (yet) available",
                )
        }

        test(
            "getDataByHours doesn't combine calculated categories from their operands if the result is < 0.0 and indicates not all data present",
        ) {
            val category = DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED
            val totalEntries = hourEntriesOf(0 to 10.0, 1 to 6.0)
            val toGridEntries = hourEntriesOf(0 to 3.0, 1 to 4.0, 2 to 1.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.CALCULATED)
            every { mockDataAccessMethodService.getCalculationExpr(siteId, category) } returns
                fromGeneration
            every {
                mockDataAccessMethodService.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL)
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TOTAL, DataAccessType.FILE)
            every {
                mockDataAccessMethodService.getAccessMethod(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                )
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns totalEntries
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns toGridEntries

            service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo) shouldBe
                EnergyDataDTO(
                    hourEntriesOf(0 to 7.0, 1 to 2.0),
                    false,
                    "Not all data for category ${category.description} is available; data it depends on are not (yet) available",
                )
        }

        test("getDataByHours throws for unavailable categories") {
            val category = DataCategory.ENERGY_CONSUMPTION_TOTAL

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.UNAVAILABLE)

            val error =
                shouldThrow<IllegalArgumentException> {
                    service.getDataByHours(siteId, category, dateTimeFrom, dateTimeTo)
                }

            error.message shouldBe
                "failed to get data for ${category.description}: data for this category are unavailable"
        }

        test("getDataByDays returns file data when all expected days are present") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = dayEntriesOf(1 to 1.0, 2 to 2.0, 3 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns entries

            service.getDataByDays(siteId, category, dateFrom, dateTo) shouldBe
                EnergyDataDTO(entries, true, "")
        }

        test("getDataByDays marks file data as incomplete when some days are missing") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = dayEntriesOf(1 to 1.0, 3 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns entries

            service.getDataByDays(siteId, category, dateFrom, dateTo) shouldBe
                EnergyDataDTO(
                    entries,
                    false,
                    "Not all data for category ${category.description} is available; import data from a file if possible",
                )
        }

        test("getDataByDays imports missing API data and retries repository lookup") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val partialEntries = dayEntriesOf(1 to 1.0, 3 to 3.0)
            val completeEntries = dayEntriesOf(1 to 1.0, 2 to 2.0, 3 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.API)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns partialEntries andThen completeEntries
            every {
                mockEnergyDataImportService.importDataFromAPI(
                    siteId,
                    category,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns Unit

            service.getDataByDays(siteId, category, dateFrom, dateTo) shouldBe
                EnergyDataDTO(completeEntries, true, "")
        }

        test("getDataByDays reports API import failure when import throws") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val partialEntries = dayEntriesOf(1 to 1.0, 3 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.API)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns partialEntries
            every {
                mockEnergyDataImportService.importDataFromAPI(
                    siteId,
                    category,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } throws IllegalStateException("provider unavailable")

            service.getDataByDays(siteId, category, dateFrom, dateTo) shouldBe
                EnergyDataDTO(
                    partialEntries,
                    false,
                    "failed to obtain data from the API: provider unavailable",
                )
        }

        test("getDataByDays reports when API still does not provide all requested data") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val partialEntries = dayEntriesOf(1 to 1.0, 3 to 3.0)
            val stillIncompleteEntries = dayEntriesOf(1 to 1.0, 2 to 2.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.API)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns partialEntries andThen stillIncompleteEntries
            every {
                mockEnergyDataImportService.importDataFromAPI(
                    siteId,
                    category,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns Unit

            service.getDataByDays(siteId, category, dateFrom, dateTo) shouldBe
                EnergyDataDTO(
                    stillIncompleteEntries,
                    false,
                    "API does not provide all data for category ${category.description}",
                )
        }

        test(
            "getDataByDays combines calculated categories from their operands and excludes unavailable days",
        ) {
            val category = DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED
            val totalEntries = dayEntriesOf(1 to 10.0, 2 to 8.0, 3 to 6.0)
            val toGridEntries = dayEntriesOf(1 to 3.0, 3 to 1.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.CALCULATED)
            every { mockDataAccessMethodService.getCalculationExpr(siteId, category) } returns
                fromGeneration
            every {
                mockDataAccessMethodService.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL)
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TOTAL, DataAccessType.FILE)
            every {
                mockDataAccessMethodService.getAccessMethod(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                )
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns totalEntries
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns toGridEntries

            service.getDataByDays(siteId, category, dateFrom, dateTo) shouldBe
                EnergyDataDTO(
                    dayEntriesOf(1 to 7.0, 3 to 5.0),
                    false,
                    "Not all data for category ${category.description} is available; data it depends on are not (yet) available",
                )
        }

        test("getDataByDays returns empty list for unavailable range") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = mutableMapOf<LocalDateTime, Double>()

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns entries

            service.getDataByDays(siteId, category, dateFrom, dateTo) shouldBe
                EnergyDataDTO(
                    entries,
                    false,
                    "Not all data for category ${category.description} is available; import data from a file if possible",
                )
        }

        test("getDataByDays throws for unavailable categories") {
            val category = DataCategory.ENERGY_CONSUMPTION_TOTAL

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.UNAVAILABLE)

            val error =
                shouldThrow<IllegalArgumentException> {
                    service.getDataByDays(siteId, category, dateFrom, dateTo)
                }

            error.message shouldBe
                "failed to get data for ${category.description}: data for this category are unavailable"
        }

        test("getAvailable returns years for a single file category") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            every { mockEnergyProductionToTheGridRepository.getAvailableYears(siteId) } returns
                listOf(2024, 2022, 2023)

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.YEARS,
            ) shouldBe listOf(2022, 2023, 2024)
        }

        test("getAvailable returns intersection of years for mixed file and api categories") {
            val fileCategory = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val apiCategory = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(apiCategory, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, fileCategory) } returns
                dataAccessMethod(fileCategory, DataAccessType.FILE)
            every { mockDataAccessMethodService.getAccessMethod(siteId, apiCategory) } returns
                apiAccessMethod
            every { mockEnergyProductionToTheGridRepository.getAvailableYears(siteId) } returns
                listOf(2022, 2023, 2024)
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(
                    siteId,
                    apiCategory,
                    apiAccessMethod,
                )
            } returns (LocalDate(2023, 5, 10) to LocalDate(2025, 2, 1))

            service.getAvailable(
                siteId,
                listOf(fileCategory, apiCategory),
                SortOrder.ASC,
                AvailableUnits.YEARS,
            ) shouldBe listOf(2023, 2024)
        }

        test("getAvailable returns descending months intersection for multiple file categories") {
            val category1 = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val category2 = DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID

            every { mockDataAccessMethodService.getAccessMethod(siteId, category1) } returns
                dataAccessMethod(category1, DataAccessType.FILE)
            every { mockDataAccessMethodService.getAccessMethod(siteId, category2) } returns
                dataAccessMethod(category2, DataAccessType.FILE)
            every {
                mockEnergyProductionToTheGridRepository.getAvailableMonthsForYear(siteId, 2024)
            } returns listOf(1, 2, 3, 5)
            every {
                mockEnergyConsumptionFromTheGridRepository.getAvailableMonthsForYear(siteId, 2024)
            } returns listOf(2, 3, 4, 5)

            service.getAvailable(
                siteId,
                listOf(category1, category2),
                SortOrder.DESC,
                AvailableUnits.MONTHS_IN_YEAR,
                year = 2024,
            ) shouldBe listOf(5, 3, 2)
        }

        test("getAvailable returns months for calculated category as intersection of operands") {
            val category = DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.CALCULATED)
            every { mockDataAccessMethodService.getCalculationExpr(siteId, category) } returns
                fromGeneration
            every {
                mockDataAccessMethodService.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL)
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TOTAL, DataAccessType.FILE)
            every {
                mockDataAccessMethodService.getAccessMethod(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                )
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID, DataAccessType.FILE)
            every {
                mockEnergyProductionTotalRepository.getAvailableMonthsForYear(siteId, 2024)
            } returns listOf(1, 2, 3, 4)
            every {
                mockEnergyProductionToTheGridRepository.getAvailableMonthsForYear(siteId, 2024)
            } returns listOf(2, 4, 5)

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.MONTHS_IN_YEAR,
                year = 2024,
            ) shouldBe listOf(2, 4)
        }

        test("getAvailable returns days intersection for api and file categories") {
            val apiCategory = DataCategory.ENERGY_PRODUCTION_TOTAL
            val fileCategory = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val apiAccessMethod = dataAccessMethod(apiCategory, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, apiCategory) } returns
                apiAccessMethod
            every { mockDataAccessMethodService.getAccessMethod(siteId, fileCategory) } returns
                dataAccessMethod(fileCategory, DataAccessType.FILE)
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(
                    siteId,
                    apiCategory,
                    apiAccessMethod,
                )
            } returns (LocalDate(2024, 2, 10) to LocalDate(2024, 2, 20))
            every {
                mockEnergyProductionToTheGridRepository.getAvailableDaysForMonthInYear(siteId, 2024, 2)
            } returns listOf(9, 10, 12, 15, 20, 21)

            service.getAvailable(
                siteId,
                listOf(apiCategory, fileCategory),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 2,
            ) shouldBe listOf(10, 12, 15, 20)
        }

        test("getAvailable returns all days in whole month inside api range") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 1, 15) to LocalDate(2024, 3, 10))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 2,
            ) shouldBe (1..29).toList()
        }

        test("getAvailable returns all years covered by api date range") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2022, 11, 15) to LocalDate(2025, 2, 10))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.YEARS,
            ) shouldBe listOf(2022, 2023, 2024, 2025)
        }

        test("getAvailable returns months in same year api range") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 3, 15) to LocalDate(2024, 7, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.MONTHS_IN_YEAR,
                year = 2024,
            ) shouldBe listOf(3, 4, 5, 6, 7)
        }

        test("getAvailable returns months from api start month to year end") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 9, 15) to LocalDate(2025, 2, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.MONTHS_IN_YEAR,
                year = 2024,
            ) shouldBe listOf(9, 10, 11, 12)
        }

        test("getAvailable returns months from year start to api end month") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 9, 15) to LocalDate(2025, 2, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.MONTHS_IN_YEAR,
                year = 2025,
            ) shouldBe listOf(1, 2)
        }

        test("getAvailable returns empty months for year outside api range") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 9, 15) to LocalDate(2025, 2, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.MONTHS_IN_YEAR,
                year = 2023,
            ) shouldBe emptyList()
        }

        test("getAvailable returns days for same month api range") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 2, 10) to LocalDate(2024, 2, 20))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 2,
            ) shouldBe (10..20).toList()
        }

        test("getAvailable returns days from api start day to end of start month") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 2, 10) to LocalDate(2024, 4, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 2,
            ) shouldBe (10..29).toList()
        }

        test("getAvailable returns days from month start to api end day") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 2, 10) to LocalDate(2024, 4, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 4,
            ) shouldBe (1..5).toList()
        }

        test("getAvailable returns empty days for month outside api range") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 2, 10) to LocalDate(2024, 4, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 5,
            ) shouldBe emptyList()
        }

        test("getAvailable returns whole month for api range month strictly inside same-year range") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 2, 10) to LocalDate(2024, 4, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 3,
            ) shouldBe (1..31).toList()
        }

        test("getAvailable returns empty days for month before api start in same year") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 2, 10) to LocalDate(2024, 4, 5))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 1,
            ) shouldBe emptyList()
        }

        test(
            "getAvailable returns exact days for api range contained within one month in same year",
        ) {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 4, 3) to LocalDate(2024, 4, 8))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 4,
            ) shouldBe listOf(3, 4, 5, 6, 7, 8)
        }

        test(
            "getAvailable returns empty days for month after api end in same year single-year range",
        ) {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 4, 3) to LocalDate(2024, 4, 8))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 5,
            ) shouldBe emptyList()
        }

        test("getAvailable return all days in a month if exactly one month is available") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 4, 1) to LocalDate(2024, 4, 30))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 4,
            ) shouldBe (1..30).toList()
        }

        test("getAvailable returns the whole monthh if in range") {
            val category = DataCategory.ENERGY_PRODUCTION_TOTAL
            val apiAccessMethod = dataAccessMethod(category, DataAccessType.API)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                apiAccessMethod
            every {
                mockEnergyDataImportService.fetchAvailableDatesFromAPI(siteId, category, apiAccessMethod)
            } returns (LocalDate(2024, 3, 30) to LocalDate(2024, 5, 1))

            service.getAvailable(
                siteId,
                listOf(category),
                SortOrder.ASC,
                AvailableUnits.DAYS_IN_MONTH,
                year = 2024,
                month = 4,
            ) shouldBe (1..30).toList()
        }

        test("getDailyAverage returns average of daily values") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = dayEntriesOf(1 to 2.0, 2 to 4.0, 3 to 6.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns entries

            service.getDailyAverage(siteId, category, dateFrom, dateTo) shouldBe 4.0
        }

        test("getDailyAverage returns average of daily values calculated category") {
            val category = DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED
            val totalEntries = dayEntriesOf(1 to 10.0, 2 to 8.0)
            val toGridEntries = dayEntriesOf(1 to 4.0, 3 to 4.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.CALCULATED)
            every { mockDataAccessMethodService.getCalculationExpr(siteId, category) } returns
                fromGeneration
            every {
                mockDataAccessMethodService.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL)
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TOTAL, DataAccessType.FILE)
            every {
                mockDataAccessMethodService.getAccessMethod(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                )
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns totalEntries
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns toGridEntries

            val result = service.getDailyAverage(siteId, category, dateFrom, dateTo)

            result shouldBe 6.0
        }

        test("getMaxDays returns max value and all matching days") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = dayEntriesOf(1 to 5.0, 2 to 8.0, 3 to 8.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByDays(
                    siteId,
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 3, 23, 59, 59, 59),
                )
            } returns entries

            val result = service.getMaxDays(siteId, category, dateFrom, dateTo)

            result.value shouldBe 8.0
            result.days shouldBe setOf(LocalDate(2026, 1, 2), LocalDate(2026, 1, 3))
        }

        test("getMaxHours returns max value and all matching hours") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = hourEntriesOf(0 to 3.0, 1 to 7.0, 2 to 7.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns entries

            val result = service.getMaxHours(siteId, category, dateTimeFrom, dateTimeTo)

            result.value shouldBe 7.0
            result.hours shouldBe
                setOf(
                    LocalDateTime(2026, 1, 1, 1, 0),
                    LocalDateTime(2026, 1, 1, 2, 0),
                )
        }

        test("getMaxHours returns max hour for calculated category") {
            val category = DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED
            val totalEntries = hourEntriesOf(0 to 10.0, 1 to 8.0, 2 to 9.0)
            val toGridEntries = hourEntriesOf(0 to 3.0, 1 to 1.0, 2 to 4.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.CALCULATED)
            every { mockDataAccessMethodService.getCalculationExpr(siteId, category) } returns
                fromGeneration
            every {
                mockDataAccessMethodService.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL)
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TOTAL, DataAccessType.FILE)
            every {
                mockDataAccessMethodService.getAccessMethod(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                )
            } returns dataAccessMethod(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionTotalRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns totalEntries
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns toGridEntries

            val result = service.getMaxHours(siteId, category, dateTimeFrom, dateTimeTo)

            result.value shouldBe 7.0
            result.hours shouldBe
                setOf(
                    LocalDateTime(2026, 1, 1, 0, 0),
                    LocalDateTime(2026, 1, 1, 1, 0),
                )
        }

        test("getPeakHours returns the hour occurring most often as daily max") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val day1From = LocalDateTime(2026, 1, 1, 0, 0)
            val day1To = LocalDateTime(2026, 1, 1, 23, 59, 59, 59)
            val day2From = LocalDateTime(2026, 1, 2, 0, 0)
            val day2To = LocalDateTime(2026, 1, 2, 23, 59, 59, 59)
            val day3From = LocalDateTime(2026, 1, 3, 0, 0)
            val day3To = LocalDateTime(2026, 1, 3, 23, 59, 59, 59)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, day1From, day1To)
            } returns
                mutableMapOf(
                    LocalDateTime(2026, 1, 1, 10, 0) to 8.0,
                    LocalDateTime(2026, 1, 1, 11, 0) to 5.0,
                )
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, day2From, day2To)
            } returns
                mutableMapOf(
                    LocalDateTime(2026, 1, 2, 10, 0) to 9.0,
                    LocalDateTime(2026, 1, 2, 12, 0) to 7.0,
                )
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, day3From, day3To)
            } returns
                mutableMapOf(
                    LocalDateTime(2026, 1, 3, 12, 0) to 10.0,
                    LocalDateTime(2026, 1, 3, 10, 0) to 4.0,
                )

            service.getPeakHours(siteId, category, dateFrom, dateTo).hours shouldBe listOf(10)
        }

        test("getTotal returns sum of hourly values") {
            val category = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID
            val entries = hourEntriesOf(0 to 1.5, 1 to 2.5, 2 to 3.0)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod(category, DataAccessType.FILE)
            coEvery {
                mockEnergyProductionToTheGridRepository.getDataByHours(siteId, dateTimeFrom, dateTimeTo)
            } returns entries

            service.getTotal(siteId, category, dateTimeFrom, dateTimeTo) shouldBe 7.0
        }
    })
