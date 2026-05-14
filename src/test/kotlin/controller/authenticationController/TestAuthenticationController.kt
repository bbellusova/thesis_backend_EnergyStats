package controller.authenticationController

import cvut.fit.energyStats.controller.authenticationController.AuthenticationController
import cvut.fit.energyStats.controller.siteController.LoginReq
import cvut.fit.energyStats.controller.siteController.toSiteResp
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.service.siteService.SiteService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import kotlinx.datetime.TimeZone
import org.springframework.security.authentication.BadCredentialsException
import kotlin.test.assertEquals

class TestAuthenticationController :
    FunSpec({
        val mockSiteService: SiteService = mockk()
        val controller = AuthenticationController(mockSiteService)

        val mockRequest: HttpServletRequest = mockk()
        val mockResponse: HttpServletResponse = mockk()
        val mockSession: HttpSession = mockk()

        val siteId = 1
        val siteName = "testSite"
        val password = "password"

        val siteDTO =
            SiteDTO(
                siteId = siteId,
                siteName = siteName,
                password = password,
                timezone = TimeZone.of("Europe/Bratislava"),
                lat = 45.0,
                lon = 17.0,
                azm = 80.0,
                kwp = 3.0,
                dec = 30.0,
            )

        beforeEach { clearAllMocks() }

        test("login authenticates site and sets security context") {
            val loginReq = LoginReq(siteName, password)

            every { mockSiteService.authenticateSite(siteName, password) } returns siteDTO
            every { mockRequest.getSession(true) } returns mockSession
            every { mockRequest.getSession(false) } returns mockSession
            every { mockRequest.changeSessionId() } returns "newSessionId"
            every { mockSession.setAttribute(any(), any()) } just Runs

            val result = controller.login(loginReq, mockRequest, mockResponse)

            assertEquals(siteDTO.toSiteResp(), result)

            verify { mockSiteService.authenticateSite(siteName, password) }
            verify { mockRequest.getSession(true) }
            verify { mockRequest.changeSessionId() }
            verify { mockSession.setAttribute(any(), any()) }
        }

        test("login throws BadCredentialsException when site does not exist") {
            val loginReq = LoginReq("NonExistentSite", "password")
            every { mockSiteService.authenticateSite("NonExistentSite", "password") } throws
                BadCredentialsException("site does not exist")

            shouldThrow<BadCredentialsException> {
                controller.login(loginReq, mockRequest, mockResponse)
            }

            verify { mockSiteService.authenticateSite("NonExistentSite", "password") }
        }

        test("login throws BadCredentialsException when password is incorrect") {
            val loginReq = LoginReq(siteName, "wrongPassword")
            every { mockSiteService.authenticateSite(siteName, "wrongPassword") } throws
                BadCredentialsException("incorrect password provided")

            shouldThrow<BadCredentialsException> {
                controller.login(loginReq, mockRequest, mockResponse)
            }

            verify { mockSiteService.authenticateSite(siteName, "wrongPassword") }
        }

        test("validate returns unit") { controller.validate() }
    })
