package repository.energyDataRepository

import cvut.fit.energyStats.config.DatabaseConfiguration
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionTotalO
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionTotalRepository
import cvut.fit.energyStats.repository.siteRepository.Site
import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import kotlinx.coroutines.runBlocking
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

@SpringBootTest(classes = [TestEnergyDataRepositoryIntegration.TestApp::class])
@TestPropertySource(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:energy-data-repository-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
        ],
)
class TestEnergyDataRepositoryIntegration {
    @Autowired private lateinit var repository: EnergyProductionTotalRepository

    @Autowired private lateinit var siteRepository: SiteRepository

    private var site1: Int = 0
    private var site2: Int = 0

    @BeforeEach
    fun setUp() {
        transaction {
            EnergyProductionTotalO.deleteAll()
            Site.deleteAll()
        }

        site1 =
            siteRepository.createSite(
                SiteDTO(
                    siteId = 0,
                    siteName = "testSite1",
                    password = "secret1",
                    timezone = TimeZone.of("UTC"),
                    lat = 50.0,
                    lon = 14.0,
                    azm = 180.0,
                    kwp = 5.0,
                    dec = 35.0,
                ),
            )
        site2 =
            siteRepository.createSite(
                SiteDTO(
                    siteId = 0,
                    siteName = "testSite2",
                    password = "secret2",
                    timezone = TimeZone.of("UTC"),
                    lat = 51.0,
                    lon = 15.0,
                    azm = 180.0,
                    kwp = 6.0,
                    dec = 35.0,
                ),
            )
    }

    @Test
    fun `storeToDb upserts and getDataByHours aggregates by hour and site`() =
        runBlocking {
            repository.storeToDb(
                site1,
                listOf(
                    LocalDateTime(2026, 1, 10, 10, 5) to 1.0,
                    LocalDateTime(2026, 1, 10, 10, 30) to 2.0,
                    LocalDateTime(2026, 1, 10, 11, 0) to 4.0,
                ),
            )

            val result1 =
                repository.getDataByHours(
                    site1,
                    LocalDateTime(2026, 1, 10, 0, 0),
                    LocalDateTime(2026, 1, 10, 23, 59),
                )

            assertEquals(2, result1.size)
            assertEquals(3.0, result1[LocalDateTime(2026, 1, 10, 10, 0)])
            assertEquals(4.0, result1[LocalDateTime(2026, 1, 10, 11, 0)])

            repository.storeToDb(
                site2,
                listOf(
                    LocalDateTime(2026, 1, 10, 10, 10) to 50.0,
                    LocalDateTime(2026, 1, 11, 11, 10) to 51.0,
                ),
            )
            repository.storeToDb(
                site1,
                listOf(
                    LocalDateTime(2026, 1, 10, 10, 5) to 1.5,
                ),
            )

            val result2 =
                repository.getDataByHours(
                    site1,
                    LocalDateTime(2026, 1, 10, 0, 0),
                    LocalDateTime(2026, 1, 10, 23, 59),
                )

            assertEquals(2, result2.size)
            assertEquals(3.5, result2[LocalDateTime(2026, 1, 10, 10, 0)])
            assertEquals(4.0, result2[LocalDateTime(2026, 1, 10, 11, 0)])

            val result3 =
                repository.getDataByHours(
                    site1,
                    LocalDateTime(2026, 1, 11, 0, 0),
                    LocalDateTime(2026, 1, 11, 23, 59),
                )
            assertEquals(0, result3.size)
        }

    @Test
    fun `getDataByDays aggregates by day and date range`() =
        runBlocking {
            repository.storeToDb(
                site1,
                listOf(
                    LocalDateTime(2026, 2, 1, 8, 0) to 1.2,
                    LocalDateTime(2026, 2, 1, 16, 0) to 0.8,
                    LocalDateTime(2026, 2, 2, 9, 0) to 2.5,
                    LocalDateTime(2026, 2, 3, 9, 0) to 9.9,
                ),
            )

            val result =
                repository.getDataByDays(
                    site1,
                    LocalDateTime(2026, 2, 1, 0, 0),
                    LocalDateTime(2026, 2, 2, 23, 59),
                )

            assertEquals(2, result.size)
            assertEquals(2.0, result[LocalDateTime(2026, 2, 1, 0, 0)])
            assertEquals(2.5, result[LocalDateTime(2026, 2, 2, 0, 0)])
        }

    @Test
    fun `getAvailable returns all available years,months and days for site`() {
        repository.storeToDb(
            site1,
            listOf(
                LocalDateTime(2025, 12, 31, 23, 0) to 1.0,
                LocalDateTime(2026, 1, 1, 1, 0) to 2.0,
                LocalDateTime(2026, 1, 2, 1, 0) to 3.0,
                LocalDateTime(2026, 2, 3, 1, 0) to 4.0,
                LocalDateTime(2026, 2, 3, 5, 0) to 5.0,
            ),
        )
        repository.storeToDb(
            site2,
            listOf(
                LocalDateTime(2030, 7, 7, 7, 0) to 7.0,
            ),
        )

        assertEquals(setOf(2025, 2026), repository.getAvailableYears(site1).toSet())
        assertEquals(setOf(1, 2), repository.getAvailableMonthsForYear(site1, 2026).toSet())
        assertEquals(setOf(12), repository.getAvailableMonthsForYear(site1, 2025).toSet())
        assertEquals(setOf(), repository.getAvailableMonthsForYear(site1, 2024).toSet())
        assertEquals(setOf(1, 2), repository.getAvailableDaysForMonthInYear(site1, 2026, 1).toSet())
        assertEquals(setOf(3), repository.getAvailableDaysForMonthInYear(site1, 2026, 2).toSet())
        assertEquals(setOf(31), repository.getAvailableDaysForMonthInYear(site1, 2025, 12).toSet())
        assertEquals(setOf(), repository.getAvailableDaysForMonthInYear(site1, 2025, 1).toSet())
    }

    @EnableAutoConfiguration
    @Import(
        DatabaseConfiguration::class,
        SiteRepository::class,
        EnergyProductionTotalRepository::class,
    )
    class TestApp
}
