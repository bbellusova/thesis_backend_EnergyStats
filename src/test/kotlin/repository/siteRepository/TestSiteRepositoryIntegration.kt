package repository.siteRepository

import cvut.fit.energyStats.config.DatabaseConfiguration
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.repository.siteRepository.Site
import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.test.context.TestPropertySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(classes = [TestSiteRepositoryIntegration.TestApp::class])
@TestPropertySource(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:site-repository-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
        ],
)
class TestSiteRepositoryIntegration {
    @Autowired private lateinit var repository: SiteRepository

    private lateinit var site1: SiteDTO
    private lateinit var site2: SiteDTO

    @BeforeEach
    fun setUp() {
        transaction { Site.deleteAll() }

        site1 =
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
            )
        site2 =
            SiteDTO(
                siteId = 0,
                siteName = "testSite2",
                password = "secret2",
                timezone = TimeZone.of("Europe/Bratislava"),
                lat = 48.0,
                lon = 17.0,
                azm = 170.0,
                kwp = 6.0,
                dec = 30.0,
            )
    }

    @Test
    fun `createSite and getSiteById persist and load site`() {
        val id = repository.createSite(site1)

        val loaded = repository.getSiteById(id)

        assertEquals(
            site1.copy(siteId = id),
            loaded,
        )
    }

    @Test
    fun `getSiteByName returns site and throws for missing name`() {
        repository.createSite(site1)

        val loaded = repository.getSiteByName(site1.siteName)
        assertEquals(site1.copy(siteId = loaded.siteId), loaded)

        assertThrows(BadCredentialsException::class.java) { repository.getSiteByName("missing-site") }
    }

    @Test
    fun `siteNameExists reflects stored names`() {
        assertFalse(repository.siteNameExists(site1.siteName))

        repository.createSite(site1)

        assertTrue(repository.siteNameExists(site1.siteName))
        assertFalse(repository.siteNameExists(site2.siteName))
    }

    @Test
    fun `updateSiteParameters updates mutable site fields`() {
        val id = repository.createSite(site1)
        val update =
            SiteDTO(
                siteId = id,
                siteName = site1.siteName,
                password = null,
                timezone = TimeZone.of("Asia/Tokyo"),
                lat = 35.0,
                lon = 139.0,
                azm = 160.0,
                kwp = 7.5,
                dec = 20.0,
            )

        repository.updateSiteParameters(id, update)
        val loaded = repository.getSiteById(id)

        assertEquals(site1.siteName, loaded.siteName)
        assertEquals(site1.password, loaded.password)
        assertEquals(update.timezone, loaded.timezone)
        assertEquals(update.lat, loaded.lat)
        assertEquals(update.lon, loaded.lon)
        assertEquals(update.azm, loaded.azm)
        assertEquals(update.kwp, loaded.kwp)
        assertEquals(update.dec, loaded.dec)
    }

    @Test
    fun `cache timestamp can be set read and reset`() {
        val id = repository.createSite(site1)
        val ts = LocalDateTime(2026, 5, 12, 11, 0)

        assertEquals(null, repository.getCacheTimestamp(id))

        repository.setCacheTimestamp(id, ts)
        assertEquals(ts, repository.getCacheTimestamp(id))

        repository.setCacheTimestamp(id, null)
        assertEquals(null, repository.getCacheTimestamp(id))
    }

    @Test
    fun `nameMatchesId matches exact pair only`() {
        val id1 = repository.createSite(site1)
        val id2 = repository.createSite(site2)

        assertTrue(repository.nameMatchesId(id1, site1.siteName))
        assertTrue(repository.nameMatchesId(id2, site2.siteName))
        assertFalse(repository.nameMatchesId(id1, site2.siteName))
        assertFalse(repository.nameMatchesId(999999, site1.siteName))
    }

    @Test
    fun `getSiteById throws for missing site`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { repository.getSiteById(123456) }
        assertEquals("site does not exist", ex.message)
    }

    @EnableAutoConfiguration
    @Import(
        DatabaseConfiguration::class,
        SiteRepository::class,
    )
    class TestApp
}
