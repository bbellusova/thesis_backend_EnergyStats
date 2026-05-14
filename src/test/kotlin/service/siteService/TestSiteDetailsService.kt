package service.siteService

import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import cvut.fit.energyStats.service.siteService.SiteDetailsService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.TimeZone
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UsernameNotFoundException

class TestSiteDetailsService :
    FunSpec({
        val mockSiteRepository: SiteRepository = mockk()
        val service = SiteDetailsService(mockSiteRepository)

        val siteDTO =
            SiteDTO(
                siteId = 1,
                siteName = "testSite",
                password = "secret",
                timezone = TimeZone.of("UTC"),
                lat = 50.0,
                lon = 14.0,
                azm = 180.0,
                kwp = 5.0,
                dec = 35.0,
            )

        test("loadUserByUsername returns user details with SITE role") {
            every { mockSiteRepository.getSiteByName("testSite") } returns siteDTO

            val result = service.loadUserByUsername("testSite")

            result.username shouldBe "testSite"
            result.password shouldBe "secret"
            result.authorities.any { it.authority == "ROLE_SITE" } shouldBe true
        }

        test("loadUserByUsername throws when password is null") {
            every { mockSiteRepository.getSiteByName("testSite") } returns siteDTO.copy(password = null)

            val ex = shouldThrow<UsernameNotFoundException> { service.loadUserByUsername("testSite") }
            ex.message shouldBe "Site not found: testSite"
        }

        test("isSiteOwner returns false for anonymous authentication") {
            val auth =
                AnonymousAuthenticationToken(
                    "key",
                    "anonymousUser",
                    listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
                )

            service.isSiteOwner(1, auth) shouldBe false
        }

        test("isSiteOwner delegates to repository for authenticated user") {
            val auth = TestingAuthenticationToken("testSite", "pwd")
            every { mockSiteRepository.nameMatchesId(1, "testSite") } returns true
            every { mockSiteRepository.nameMatchesId(2, "testSite") } returns false

            service.isSiteOwner(1, auth) shouldBe true
            service.isSiteOwner(2, auth) shouldBe false
        }
    })
