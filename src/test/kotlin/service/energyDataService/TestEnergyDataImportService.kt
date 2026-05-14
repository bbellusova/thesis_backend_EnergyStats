package service.energyDataService

import cvut.fit.energyStats.config.ApiProviders
import cvut.fit.energyStats.config.CategoryRepositories
import cvut.fit.energyStats.config.FileProviders
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.repository.energyDataRepository.EnergyDataRepository
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.energyDataService.EnergyDataImportService
import cvut.fit.energyStats.service.providerService.APIProviderService
import cvut.fit.energyStats.service.providerService.FileProviderService
import cvut.fit.energyStats.service.siteService.SiteService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.springframework.web.multipart.MultipartFile
import kotlin.time.Clock
import kotlin.time.Instant

class TestEnergyDataImportService :
    FunSpec({
        val mockCategoryRepositories: CategoryRepositories = mockk()
        val mockDataAccessMethodService: DataAccessMethodService = mockk()
        val mockApiDataProviders: ApiProviders = mockk()
        val mockFileDataProviders: FileProviders = mockk()
        val mockSiteService: SiteService = mockk()
        val mockClock: Clock = mockk()

        val energyDataImportService =
            EnergyDataImportService(
                mockCategoryRepositories,
                mockDataAccessMethodService,
                mockApiDataProviders,
                mockFileDataProviders,
                mockSiteService,
                mockClock,
            )

        val siteId = 1
        val category = DataCategory.ENERGY_PRODUCTION_TOTAL
        val provider = DataProvider.DUMMY
        val apiKey = "test-api-key"
        val providerSiteId = 1
        val lastFinishedHour = LocalDateTime(2026, 5, 10, 12, 0)

        beforeEach { clearAllMocks() }

        test("importDataFromAFile successful import") {
            val file = mockk<MultipartFile>()
            val dataAccessMethod =
                DataAccessMethodDTO(siteId, category, DataAccessType.FILE, provider, null, null)
            val fileProviderService = mockk<FileProviderService>()
            val repository = mockk<EnergyDataRepository>()

            val entries =
                mutableMapOf(
                    LocalDateTime(2026, 5, 10, 10, 0) to 10.0,
                    LocalDateTime(2026, 5, 10, 11, 0) to 11.0,
                    LocalDateTime(2026, 5, 10, 13, 0) to 13.0,
                )

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod
            every { mockFileDataProviders[provider] } returns fileProviderService
            every { fileProviderService.validateFile(file) } returns Unit
            every { fileProviderService.importDataFromAFile(file) } returns entries
            every { mockSiteService.getLastFinishedHourAtSite(siteId) } returns lastFinishedHour
            every { mockCategoryRepositories[category] } returns repository
            every { repository.storeToDb(siteId, any()) } returns Unit

            energyDataImportService.importDataFromAFile(siteId, category, provider, file)

            verify {
                repository.storeToDb(
                    siteId,
                    match { it.size == 2 && it.all { entry -> entry.first < lastFinishedHour } },
                )
            }
        }

        test("importDataFromAFile throws when access type is not FILE") {
            val file = mockk<MultipartFile>()
            val dataAccessMethod =
                DataAccessMethodDTO(siteId, category, DataAccessType.API, provider, null, null)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod

            val exception =
                shouldThrow<IllegalArgumentException> {
                    energyDataImportService.importDataFromAFile(siteId, category, provider, file)
                }
            exception.message shouldBe
                "Data category $category does not accept data from file for this site"
        }

        test("importDataFromAFile throws when provider mismatch") {
            val file = mockk<MultipartFile>()
            val dataAccessMethod =
                DataAccessMethodDTO(siteId, category, DataAccessType.FILE, DataProvider.ZSD, null, null)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod

            val exception =
                shouldThrow<IllegalArgumentException> {
                    energyDataImportService.importDataFromAFile(siteId, category, provider, file)
                }
            exception.message shouldBe
                "Data provider $provider is not set up for this site and category $category"
        }

        test("importDataFromAPI successful import") {
            val dateFrom = LocalDateTime(2026, 5, 1, 0, 0)
            val dateTo = LocalDateTime(2026, 5, 10, 23, 0)
            val dataAccessMethod =
                DataAccessMethodDTO(
                    siteId,
                    category,
                    DataAccessType.API,
                    provider,
                    providerSiteId,
                    apiKey,
                )
            val apiProviderService = mockk<APIProviderService>()
            val repository = mockk<EnergyDataRepository>()

            val entries =
                mutableMapOf(
                    LocalDateTime(2026, 5, 10, 10, 0) to 10.0,
                    LocalDateTime(2026, 5, 10, 11, 0) to 11.0,
                    LocalDateTime(2026, 5, 10, 13, 0) to 13.0, // Should be filtered out
                )

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod
            every { mockApiDataProviders[provider] } returns apiProviderService
            every {
                apiProviderService.importDataFromAPIByHour(providerSiteId, apiKey, dateFrom, dateTo)
            } returns entries
            every { mockSiteService.getLastFinishedHourAtSite(siteId) } returns lastFinishedHour
            every { mockCategoryRepositories[category] } returns repository
            every { repository.storeToDb(siteId, any()) } returns Unit

            energyDataImportService.importDataFromAPI(siteId, category, dateFrom, dateTo)

            verify { repository.storeToDb(siteId, match { it.size == 2 }) }
        }

        test("importDataFromAPI throws when access type is not API") {
            val dateFrom = LocalDateTime(2026, 5, 1, 0, 0)
            val dateTo = LocalDateTime(2026, 5, 10, 23, 0)
            val dataAccessMethod =
                DataAccessMethodDTO(siteId, category, DataAccessType.FILE, provider, null, null)

            every { mockDataAccessMethodService.getAccessMethod(siteId, category) } returns
                dataAccessMethod

            val exception =
                shouldThrow<IllegalStateException> {
                    energyDataImportService.importDataFromAPI(siteId, category, dateFrom, dateTo)
                }
            exception.message shouldBe "$category cannot be imported by API for provider $provider"
        }

        test("fetchAvailableDatesFromAPI successful fetch and cache") {
            val currentTime = Instant.parse("2026-05-10T01:45:00Z")
            val currentTimeDayLater = Instant.parse("2026-05-11T00:00:00Z")
            val dataAccessMethod =
                DataAccessMethodDTO(
                    siteId,
                    category,
                    DataAccessType.API,
                    provider,
                    providerSiteId,
                    apiKey,
                )
            val apiProviderService = mockk<APIProviderService>()
            val dateRange = Pair(LocalDate(2026, 1, 1), LocalDate(2026, 5, 10))
            val dateRangeDayLater = Pair(LocalDate(2026, 1, 1), LocalDate(2026, 5, 11))

            val siteDTO = mockk<SiteDTO>()

            every { mockSiteService.getSiteById(siteId) } returns siteDTO
            every { siteDTO.timezone } returns TimeZone.UTC
            every { mockApiDataProviders[provider] } returns apiProviderService
            every { apiProviderService.fetchAvailableDatesFromAPI(providerSiteId, apiKey) } returns
                dateRange andThen
                dateRangeDayLater
            every { mockClock.now() } returns
                currentTime andThen
                currentTime andThen
                currentTimeDayLater

            // Should take from API
            val result1 =
                energyDataImportService.fetchAvailableDatesFromAPI(siteId, category, dataAccessMethod)
            result1 shouldBe dateRange
            verify(exactly = 1) {
                apiProviderService.fetchAvailableDatesFromAPI(providerSiteId, apiKey)
            }

            // Should take from cache
            val result2 =
                energyDataImportService.fetchAvailableDatesFromAPI(siteId, category, dataAccessMethod)
            result2 shouldBe dateRange
            verify(exactly = 1) {
                apiProviderService.fetchAvailableDatesFromAPI(providerSiteId, apiKey)
            }

            // Should take from API when cache not up to date
            val result3 =
                energyDataImportService.fetchAvailableDatesFromAPI(siteId, category, dataAccessMethod)
            result3 shouldBe dateRangeDayLater
            verify(exactly = 2) {
                apiProviderService.fetchAvailableDatesFromAPI(providerSiteId, apiKey)
            }
        }
    })
