package service.forecastService

import cvut.fit.energyStats.config.DatabaseConfiguration
import cvut.fit.energyStats.domain.dto.ProductionForecastDataDTO
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.repository.generationForecastCache.ProductionForecastCache
import cvut.fit.energyStats.repository.generationForecastCache.ProductionForecastCacheRepository
import cvut.fit.energyStats.repository.siteRepository.Site
import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import cvut.fit.energyStats.service.forecastService.ProductionForecastCacheService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(classes = [TestProductionForecastCacheIntegration.TestApp::class])
@TestPropertySource(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:productionforecastcache-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
        ],
)
class TestProductionForecastCacheIntegration {
    @Autowired private lateinit var service: ProductionForecastCacheService

    @Autowired private lateinit var siteRepository: SiteRepository

    @Autowired private lateinit var cacheRepository: ProductionForecastCacheRepository

    private var siteId: Int = 0

    @BeforeEach
    fun setUp() {
        transaction {
            ProductionForecastCache.deleteAll()
            Site.deleteAll()
        }

        siteId =
            siteRepository.createSite(
                SiteDTO(
                    siteId = 0,
                    siteName = "testSite",
                    password = "secret",
                    timezone = TimeZone.of("UTC"),
                    lat = 50.0,
                    lon = 14.0,
                    azm = 180.0,
                    kwp = 5.0,
                    dec = 35.0,
                ),
            )
    }

    @Test
    fun `storeToCache stores data and timestamp, removeFromCache nulls timestamp and removes data`() {
        val forecastData =
            ProductionForecastDataDTO(
                todayData =
                    mapOf(
                        LocalDateTime(2026, 5, 7, 11, 0, 0, 0) to 15.0,
                        LocalDateTime(2026, 5, 7, 13, 0, 0, 0) to 15.0,
                    ),
                tomorrowData = mutableMapOf(LocalDateTime(2026, 5, 8, 12, 0, 0, 0) to 16.0),
            )
        val currentDateAtSite = LocalDateTime(2026, 5, 7, 10, 0, 0, 0)
        val expectedDate = LocalDate(2026, 5, 7)
        val expectedDTOAfterRemove =
            ProductionForecastDataDTO(todayData = mapOf(), tomorrowData = mapOf())

        service.storeToCache(siteId, forecastData, currentDateAtSite)
        assertEquals(currentDateAtSite, service.getCacheTimestamp(siteId))
        assertEquals(expectedDate, service.getDateFromCache(siteId))
        assertEquals(forecastData, service.getFromCache(siteId))

        service.removeFromCache(siteId)
        assertEquals(null, service.getCacheTimestamp(siteId))
        assertEquals(null, service.getDateFromCache(siteId))
        assertEquals(expectedDTOAfterRemove, service.getFromCache(siteId))
    }

    @EnableAutoConfiguration
    @Import(
        DatabaseConfiguration::class,
        ProductionForecastCacheService::class,
        ProductionForecastCacheRepository::class,
        SiteRepository::class,
    )
    class TestApp
}
