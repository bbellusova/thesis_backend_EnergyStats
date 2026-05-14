package service.siteService

import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.domain.dto.removePassword
import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import cvut.fit.energyStats.service.forecastService.ProductionForecastCacheService
import cvut.fit.energyStats.service.siteService.SiteService
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class TestSiteService :
    FunSpec({
        val mockSiteRepository: SiteRepository = mockk()
        val mockPasswordEncoder: PasswordEncoder = mockk()
        val mockCacheService: ProductionForecastCacheService = mockk()
        val mockClock: Clock = mockk()

        val service =
            SiteService(mockSiteRepository, mockPasswordEncoder, mockCacheService, mockClock)
        val validSiteDTO =
            SiteDTO(
                siteId = 0,
                siteName = "testSite",
                password = "password",
                timezone = TimeZone.of("UTC"),
                lat = 90.0,
                lon = -180.0,
                azm = 0.0,
                kwp = 0.1,
                dec = 90.0,
            )
        withTests(
            mapOf(
                "createSite success" to
                    Triple(
                        validSiteDTO,
                        false,
                        null,
                    ),
                "createSite password missing" to
                    Triple(
                        validSiteDTO.copy(password = null),
                        false,
                        "failed to create site: Site password cannot be null",
                    ),
                "createSite password too short" to
                    Triple(
                        validSiteDTO.copy(password = "short"),
                        false,
                        "failed to create site: Site password should be at least 8 characters long",
                    ),
                "createSite latitude out of bounds" to
                    Triple(
                        validSiteDTO.copy(lat = 90.1),
                        false,
                        "failed to create site: invalid coordinates",
                    ),
                "createSite longitude out of bounds" to
                    Triple(
                        validSiteDTO.copy(lon = -180.01),
                        false,
                        "failed to create site: invalid coordinates",
                    ),
                "createSite azimuth out of bounds" to
                    Triple(
                        validSiteDTO.copy(azm = 181.0),
                        false,
                        "failed to create site: invalid azimuth",
                    ),
                "createSite declination too low" to
                    Triple(
                        validSiteDTO.copy(dec = -0.001),
                        false,
                        "failed to create site: invalid declination",
                    ),
                "createSite declination too high" to
                    Triple(
                        validSiteDTO.copy(dec = 90.01),
                        false,
                        "failed to create site: invalid declination",
                    ),
                "createSite peak power zero" to
                    Triple(
                        validSiteDTO.copy(kwp = 0.0),
                        false,
                        "failed to create site: invalid peak power",
                    ),
                "createSite peak power negative" to
                    Triple(
                        validSiteDTO.copy(kwp = -0.1),
                        false,
                        "failed to create site: invalid peak power",
                    ),
                "createSite name already occupied" to
                    Triple(
                        validSiteDTO,
                        true,
                        "failed to create site: site name already exists, please choose a different name",
                    ),
            ),
        ) { (request: SiteDTO, siteNameExists: Boolean, error: String?) ->
            try {
                every { mockSiteRepository.siteNameExists(request.siteName) } returns siteNameExists
                every { mockPasswordEncoder.encode(request.password) } returns "encodedPassword"
                every {
                    mockSiteRepository.createSite(
                        SiteDTO(
                            siteId = request.siteId,
                            siteName = request.siteName,
                            password = "encodedPassword",
                            timezone = request.timezone,
                            lat = request.lat,
                            lon = request.lon,
                            azm = request.azm,
                            kwp = request.kwp,
                            dec = request.dec,
                        ),
                    )
                } returns 1
                val id = service.createSite(request)
                assertEquals(1, id)
            } catch (e: Exception) {
                if (error != null) {
                    e.message shouldBe error
                } else {
                    fail("should have been updated successfully, but got ${e.message}")
                }
            }
        }

        test(
            "getSiteById removes password",
        ) {
            every { mockSiteRepository.getSiteById(validSiteDTO.siteId) } returns validSiteDTO

            service.getSiteById(validSiteDTO.siteId) shouldBe validSiteDTO.copy(password = null)
        }

        test(
            "canUseSiteName inverts the repository result",
        ) {
            every { mockSiteRepository.siteNameExists(validSiteDTO.siteName) } returns
                false andThen
                true

            service.canUseSiteName(validSiteDTO.siteName) shouldBe true
            service.canUseSiteName(validSiteDTO.siteName) shouldBe false
        }
        test(
            "authenticateSite checks and removes password",
        ) {
            every { mockSiteRepository.getSiteByName(validSiteDTO.siteName) } returns
                validSiteDTO.copy(password = "encodedPassword")
            every { mockPasswordEncoder.matches("password", "encodedPassword") } returns true

            service.authenticateSite(validSiteDTO.siteName, "password") shouldBe
                validSiteDTO.copy(password = null)
        }
        test(
            "authenticateSite detects incorrect password",
        ) {
            every { mockSiteRepository.getSiteByName(validSiteDTO.siteName) } returns
                validSiteDTO.copy(password = "encodedPassword")
            every { mockPasswordEncoder.matches("invalidPassword", "encodedPassword") } returns false

            val error =
                shouldThrow<BadCredentialsException> {
                    service.authenticateSite(validSiteDTO.siteName, "invalidPassword")
                }

            error.message shouldBe "incorrect password provided"
        }

        withTests(
            mapOf(
                "updateSiteParameters success no change" to
                    Triple(
                        validSiteDTO,
                        false,
                        null,
                    ),
                "updateSiteParameters password can be null" to
                    Triple(
                        validSiteDTO.copy(password = null),
                        false,
                        null,
                    ),
                "updateSiteParameters success change" to
                    Triple(
                        validSiteDTO.copy(azm = 180.0),
                        true,
                        null,
                    ),
                "updateSiteParameters latitude out of bounds" to
                    Triple(
                        validSiteDTO.copy(lat = 90.1),
                        null,
                        "failed to update site: invalid coordinates",
                    ),
                "updateSiteParameters longitude out of bounds" to
                    Triple(
                        validSiteDTO.copy(lon = -180.01),
                        null,
                        "failed to update site: invalid coordinates",
                    ),
                "updateSiteParameters azimuth out of bounds" to
                    Triple(
                        validSiteDTO.copy(azm = 181.0),
                        null,
                        "failed to update site: invalid azimuth",
                    ),
                "updateSiteParameters declination too low" to
                    Triple(
                        validSiteDTO.copy(dec = -0.001),
                        null,
                        "failed to update site: invalid declination",
                    ),
                "updateSiteParameters declination too high" to
                    Triple(
                        validSiteDTO.copy(dec = 90.01),
                        null,
                        "failed to update site: invalid declination",
                    ),
                "updateSiteParameters peak power zero" to
                    Triple(
                        validSiteDTO.copy(kwp = 0.0),
                        null,
                        "failed to update site: invalid peak power",
                    ),
                "updateSiteParameters peak power negative" to
                    Triple(
                        validSiteDTO.copy(kwp = -1.0),
                        null,
                        "failed to update site: invalid peak power",
                    ),
            ),
        ) { (request: SiteDTO, shouldChange, error: String?) ->
            try {
                clearMocks(mockSiteRepository, mockCacheService)
                every { mockSiteRepository.getSiteById(request.siteId) } returns validSiteDTO
                every {
                    mockSiteRepository.updateSiteParameters(request.siteId, request.removePassword())
                } returns Unit
                every { mockCacheService.removeFromCache(request.siteId) } returns Unit
                val changed = service.updateSiteParameters(request.siteId, request)
                assertEquals(shouldChange, changed)
                val expectedCalls = if (shouldChange == true) 1 else 0

                verify(exactly = expectedCalls) { mockCacheService.removeFromCache(request.siteId) }
            } catch (e: Exception) {
                if (error != null) {
                    e.message shouldBe error
                } else {
                    fail("should have been updated successfully, but got ${e.message}")
                }
            }
        }

        withTests(
            mapOf(
                "getLastFinishedHourAtSite stays the same for UTC" to
                    Triple(
                        Instant.parse("2026-05-07T14:35:00Z"),
                        validSiteDTO.copy(timezone = TimeZone.of("UTC")),
                        LocalDateTime(2026, 5, 7, 14, 0, 0, 0),
                    ),
                "getLastFinishedHourAtSite with timezone where its one day less" to
                    Triple(
                        Instant.parse("2026-05-08T02:30:00Z"),
                        validSiteDTO.copy(timezone = TimeZone.of("America/New_York")),
                        LocalDateTime(2026, 5, 7, 22, 0, 0, 0),
                    ),
                "getLastFinishedHourAtSite with timezone where its one day more" to
                    Triple(
                        Instant.parse("2026-05-07T20:00:00Z"),
                        validSiteDTO.copy(timezone = TimeZone.of("Asia/Tokyo")),
                        LocalDateTime(2026, 5, 8, 5, 0, 0, 0),
                    ),
                "getLastFinishedHourAtSite manages year shift" to
                    Triple(
                        Instant.parse("2027-01-01T00:15:00Z"),
                        validSiteDTO.copy(timezone = TimeZone.of("America/Los_Angeles")),
                        LocalDateTime(2026, 12, 31, 16, 0, 0, 0),
                    ),
                "getLastFinishedHourAtSite manages Tokyo leap year transition" to
                    Triple(
                        Instant.parse("2028-02-28T17:45:00Z"),
                        validSiteDTO.copy(timezone = TimeZone.of("Asia/Tokyo")),
                        LocalDateTime(2028, 2, 29, 2, 0, 0, 0),
                    ),
                "getLastFinishedHourAtSite manages daylight savings - before shift" to
                    Triple(
                        Instant.parse("2026-03-29T00:45:00Z"),
                        validSiteDTO.copy(timezone = TimeZone.of("Europe/Bratislava")),
                        LocalDateTime(2026, 3, 29, 1, 0, 0, 0),
                    ),
                "getLastFinishedHourAtSite manages daylight savings - after shift" to
                    Triple(
                        Instant.parse("2026-03-29T01:15:00Z"),
                        validSiteDTO.copy(timezone = TimeZone.of("Europe/Bratislava")),
                        LocalDateTime(2026, 3, 29, 3, 0, 0, 0),
                    ),
            ),
        ) { (time: Instant, siteDTO: SiteDTO, expectedResult: LocalDateTime) ->
            every { mockSiteRepository.getSiteById(siteDTO.siteId) } returns siteDTO
            every { mockClock.now() } returns time

            val result = service.getLastFinishedHourAtSite(siteDTO.siteId)
            assertEquals(expectedResult, result)
        }
    })
