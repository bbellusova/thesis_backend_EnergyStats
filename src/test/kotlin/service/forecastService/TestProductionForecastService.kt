package service.forecastService

import cvut.fit.energyStats.domain.dto.ProductionForecastDataDTO
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.service.forecastService.ForecastSolarClient
import cvut.fit.energyStats.service.forecastService.ProductionForecastCacheService
import cvut.fit.energyStats.service.forecastService.ProductionForecastService
import cvut.fit.energyStats.service.siteService.SiteService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class TestProductionForecastService :
    FunSpec({
        val mockSiteService: SiteService = mockk()
        val mockCacheService: ProductionForecastCacheService = mockk()
        val mockForecastSolarClient: ForecastSolarClient = mockk()
        val mockClock: Clock = mockk()

        val service =
            ProductionForecastService(
                mockSiteService,
                mockCacheService,
                mockForecastSolarClient,
                mockClock,
            )

        val validSiteDTO =
            SiteDTO(
                siteId = 0,
                siteName = "testSite",
                password = "secret",
                timezone = TimeZone.of("UTC"),
                lat = 90.0,
                lon = -180.0,
                azm = 0.0,
                kwp = 0.1,
                dec = 90.0,
            )

        test("checkConfigurationValidity delegates to client") {
            every { mockForecastSolarClient.checkConfigurationValidity(10.0, 20.0) } returns true
            service.checkConfigurationValidity(10.0, 20.0) shouldBe true
        }

        test("getForecast returns from cache if update is not needed") {
            val currentTime = Instant.parse("2026-03-29T01:45:00Z")
            val cachedTimestamp = LocalDateTime(2026, 3, 29, 1, 0, 0, 0)
            val date = LocalDate(2026, 3, 29)
            val expectedData =
                ProductionForecastDataDTO(
                    mapOf(LocalDateTime(2026, 3, 29, 2, 0, 0, 0) to 9.0),
                    mapOf(LocalDateTime(2026, 3, 30, 2, 0, 0, 0) to 9.0),
                )

            every { mockClock.now() } returns currentTime
            every { mockSiteService.getSiteById(validSiteDTO.siteId) } returns validSiteDTO
            every { mockCacheService.getCacheTimestamp(validSiteDTO.siteId) } returns cachedTimestamp
            every { mockCacheService.getDateFromCache(validSiteDTO.siteId) } returns date
            every { mockCacheService.getFromCache(validSiteDTO.siteId) } returns expectedData

            val result = service.getForecast(validSiteDTO.siteId)

            result shouldBe expectedData
            verify(exactly = 0) { mockForecastSolarClient.getForecastFromAPI(any()) }
        }

        test("getForecast updates from API if cache is old (over 60 minutes)") {
            val currentTime = Instant.parse("2026-03-29T01:45:00Z")
            val cachedTimestamp = LocalDateTime(2026, 3, 29, 0, 44, 0, 0)

            val apiResponse = createMockApiResponse()
            val expectedResult =
                ProductionForecastDataDTO(
                    todayData =
                        buildMap {
                            for (i in 0..23) put(LocalDateTime(2026, 3, 29, i, 0), 0.0)
                            put(LocalDateTime(2026, 3, 29, 11, 0), 1.5)
                            put(LocalDateTime(2026, 3, 29, 14, 0), 0.5)
                        }.toMutableMap(),
                    tomorrowData =
                        buildMap {
                            for (i in 0..23) put(LocalDateTime(2026, 3, 30, i, 0), 0.0)
                            put(LocalDateTime(2026, 3, 30, 8, 0), 2.0)
                        }.toMutableMap(),
                )

            every { mockClock.now() } returns currentTime
            every { mockSiteService.getSiteById(validSiteDTO.siteId) } returns validSiteDTO
            every { mockCacheService.getCacheTimestamp(validSiteDTO.siteId) } returns cachedTimestamp
            every { mockCacheService.getDateFromCache(validSiteDTO.siteId) } returns
                cachedTimestamp.date
            every { mockForecastSolarClient.getForecastFromAPI(validSiteDTO) } returns apiResponse

            every { mockCacheService.removeFromCache(validSiteDTO.siteId) } just Runs
            every {
                mockCacheService.storeToCache(
                    validSiteDTO.siteId,
                    expectedResult,
                    currentTime.toLocalDateTime(validSiteDTO.timezone),
                )
            } just Runs

            val result = service.getForecast(validSiteDTO.siteId)
            assertEquals(expectedResult, result)

            verify {
                mockForecastSolarClient.getForecastFromAPI(validSiteDTO)
                mockCacheService.removeFromCache(validSiteDTO.siteId)
                mockCacheService.storeToCache(
                    validSiteDTO.siteId,
                    expectedResult,
                    currentTime.toLocalDateTime(validSiteDTO.timezone),
                )
            }
        }

        test("getForecast falls back to cache if API is unavailable and cache is up to date") {
            val currentTime = Instant.parse("2026-03-29T01:45:00Z")
            val cachedTimestamp = LocalDateTime(2026, 3, 29, 0, 44, 0, 0)

            val expectedResult =
                ProductionForecastDataDTO(
                    todayData =
                        buildMap {
                            for (i in 0..23) put(LocalDateTime(2026, 3, 29, i, 0), 0.0)
                            put(LocalDateTime(2026, 3, 29, 11, 0), 1.5)
                            put(LocalDateTime(2026, 3, 29, 14, 0), 0.5)
                        }.toMutableMap(),
                    tomorrowData =
                        buildMap {
                            for (i in 0..23) put(LocalDateTime(2026, 3, 30, i, 0), 0.0)
                            put(LocalDateTime(2026, 3, 30, 8, 0), 2.0)
                        }.toMutableMap(),
                )

            every { mockClock.now() } returns currentTime
            every { mockSiteService.getSiteById(validSiteDTO.siteId) } returns validSiteDTO
            every { mockCacheService.getCacheTimestamp(validSiteDTO.siteId) } returns cachedTimestamp
            every { mockCacheService.getDateFromCache(validSiteDTO.siteId) } returns
                cachedTimestamp.date
            every { mockCacheService.getFromCache(validSiteDTO.siteId) } returns expectedResult
            every { mockForecastSolarClient.getForecastFromAPI(validSiteDTO) } throws
                IllegalArgumentException("API Down")

            val result = service.getForecast(validSiteDTO.siteId)
            assertEquals(expectedResult, result)

            verify {
                mockForecastSolarClient.getForecastFromAPI(validSiteDTO)
                mockCacheService.getFromCache(validSiteDTO.siteId)
            }
        }

        test("getForecast throws exception when API fails and cache is not for today") {
            val currentTime = Instant.parse("2026-03-29T01:45:00Z")
            every { mockClock.now() } returns currentTime
            every { mockSiteService.getSiteById(validSiteDTO.siteId) } returns validSiteDTO
            every { mockCacheService.getCacheTimestamp(validSiteDTO.siteId) } returns null
            every { mockCacheService.getDateFromCache(validSiteDTO.siteId) } returns null
            every { mockForecastSolarClient.getForecastFromAPI(validSiteDTO) } throws
                IllegalArgumentException("API Down")

            val exception =
                shouldThrow<IllegalArgumentException> { service.getForecast(validSiteDTO.siteId) }
            exception.message shouldBe "error fetching production forecast data: API Down"
        }
    })

fun createMockApiResponse(): ForecastSolarClient.ProductionForecastResponseData {
    val mockData = mockk<ForecastSolarClient.ProductionForecastResponseData>()
    every { mockData.message.info.time } returns "2026-03-29T01:45:00+"
    every { mockData.result } returns
        mapOf(
            "2026-03-29 11:00:00" to 1500,
            "2026-03-29 14:30:00" to 500,
            "2026-03-30 08:00:00" to 2000,
        )
    return mockData
}
