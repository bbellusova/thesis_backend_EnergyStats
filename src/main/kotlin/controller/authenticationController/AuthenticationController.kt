package cvut.fit.energyStats.controller.authenticationController

import cvut.fit.energyStats.controller.siteController.LoginReq
import cvut.fit.energyStats.controller.siteController.SiteResp
import cvut.fit.energyStats.controller.siteController.toSiteResp
import cvut.fit.energyStats.service.siteService.SiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.tags.Tags
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication")
@RequestMapping("auth")
class AuthenticationController(
    val service: SiteService,
) {
    @PostMapping("/login")
    @Tags(Tag(name = "Authentication"))
    @Operation(summary = "Login with the site credentials")
    @ApiResponse(responseCode = "200", description = "Site authenticated")
    @ApiResponse(
        responseCode = "401",
        description = "Incorrect site name or password",
        content = [Content()],
    )
    fun login(
        @RequestBody loginReq: LoginReq,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): SiteResp {
        val site = service.authenticateSite(loginReq.siteName, loginReq.password)

        request.getSession(true)
        request.changeSessionId()

        val auth = UsernamePasswordAuthenticationToken(site.siteName, null, ArrayList())
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = auth
        SecurityContextHolder.setContext(context)

        val repo = HttpSessionSecurityContextRepository()
        repo.saveContext(context, request, response)

        return site.toSiteResp()
    }

    @GetMapping("/validation")
    @SecurityRequirement(name = "JSESSIONID")
    @Operation(summary = "Validate a session")
    @ApiResponse(responseCode = "200", description = "Session is valid")
    @ApiResponse(responseCode = "401", description = "Session expired or invalid")
    fun validate() {}
}
