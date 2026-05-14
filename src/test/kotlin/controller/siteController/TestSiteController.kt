package controller.siteController

import cvut.fit.energyStats.controller.siteController.CreateSiteReq
import cvut.fit.energyStats.controller.siteController.SiteController
import cvut.fit.energyStats.controller.siteController.UpdateSiteReq
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.service.siteService.SiteService
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.TimeZone
import kotlin.test.assertEquals

class TestSiteController :
    FunSpec({
        val mockSiteService: SiteService = mockk()
        val controller = SiteController(mockSiteService)

        val siteId = 1
        val siteName = "testSite"
        val timezone = "Europe/Bratislava"
        val lat = 45.0
        val lon = 17.0
        val azm = 80.0
        val kwp = 3.0
        val dec = 30.0

        beforeEach { clearAllMocks() }

        test("canUseSiteName returns service result") {
            every { mockSiteService.canUseSiteName(siteName) } returns true

            val result = controller.canUseSiteName(siteName)

            assertEquals(true, result)
            verify { mockSiteService.canUseSiteName(siteName) }
        }

        test("createSite returns siteId from service") {
            val createSiteReq =
                CreateSiteReq(
                    siteName = siteName,
                    password = "password123",
                    timezone = timezone,
                    lat = lat,
                    lon = lon,
                    azm = azm,
                    kwp = kwp,
                    dec = dec,
                )
            every { mockSiteService.createSite(any()) } returns siteId

            val result = controller.createSite(createSiteReq)

            assertEquals(siteId, result)
            verify {
                mockSiteService.createSite(
                    match {
                        it.siteName == siteName &&
                            it.timezone == TimeZone.of(timezone) &&
                            it.lat == lat &&
                            it.lon == lon &&
                            it.azm == azm &&
                            it.kwp == kwp &&
                            it.dec == dec
                    },
                )
            }
        }

        test("getSiteById returns site response") {
            val siteDTO =
                SiteDTO(
                    siteId = siteId,
                    siteName = siteName,
                    password = "",
                    timezone = TimeZone.of(timezone),
                    lat = lat,
                    lon = lon,
                    azm = azm,
                    kwp = kwp,
                    dec = dec,
                )
            every { mockSiteService.getSiteById(siteId) } returns siteDTO

            val result = controller.getSiteById(siteId)

            assertEquals(siteId, result.siteId)
            assertEquals(siteName, result.siteName)
            assertEquals(timezone, result.timezone)
            assertEquals(lat, result.lat)
            assertEquals(lon, result.lon)
            assertEquals(azm, result.azm)
            assertEquals(kwp, result.kwp)
            assertEquals(dec, result.dec)
            verify { mockSiteService.getSiteById(siteId) }
        }

        test("updateSiteParams updates site parameters") {
            val updateSiteReq =
                UpdateSiteReq(
                    timezone = "UTC",
                    lat = 50.0,
                    lon = 20.0,
                    azm = 90.0,
                    kwp = 5.0,
                    dec = 45.0,
                )
            val currentSiteDTO =
                SiteDTO(
                    siteId = siteId,
                    siteName = siteName,
                    password = "oldPassword",
                    timezone = TimeZone.of(timezone),
                    lat = lat,
                    lon = lon,
                    azm = azm,
                    kwp = kwp,
                    dec = dec,
                )
            every { mockSiteService.getSiteById(siteId) } returns currentSiteDTO
            every { mockSiteService.updateSiteParameters(siteId, any()) } returns true

            val result = controller.updateSiteParams(siteId, updateSiteReq)

            assertEquals(true, result)
            verify { mockSiteService.getSiteById(siteId) }
            verify {
                mockSiteService.updateSiteParameters(
                    siteId,
                    match {
                        it.timezone == TimeZone.of("UTC") &&
                            it.lat == 50.0 &&
                            it.lon == 20.0 &&
                            it.azm == 90.0 &&
                            it.kwp == 5.0 &&
                            it.dec == 45.0
                    },
                )
            }
        }
    })
