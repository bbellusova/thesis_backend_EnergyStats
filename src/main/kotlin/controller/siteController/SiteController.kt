package cvut.fit.energyStats.controller.siteController

import cvut.fit.energyStats.config.IsSiteOwner
import cvut.fit.energyStats.service.siteService.SiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Sites")
@RequestMapping("sites")
class SiteController(
    val service: SiteService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/canUseSiteName/{name}")
    @Operation(
        summary =
            "Find out if the provided site name can be used to create new site. Returns true if the name is available, false otherwise.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Availability status returned.",
    )
    fun canUseSiteName(
        @PathVariable name: String,
    ): Boolean = service.canUseSiteName(name)

    @Operation(summary = "Create new site")
    @PostMapping("")
    @ApiResponse(responseCode = "201", description = "Site created, site ID returned")
    @ApiResponse(
        responseCode = "400",
        description =
            "Failed to create site, caused by sending invalid site configuration or using an occupied site name",
        content = [Content()],
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun createSite(
        @RequestBody siteReq: CreateSiteReq,
    ): Int = service.createSite(siteReq.toSiteDTO())

    @Operation(summary = "Get site information")
    @SecurityRequirement(name = "JSESSIONID")
    @GetMapping("/{siteId}")
    @ApiResponse(responseCode = "200", description = "Site returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    @IsSiteOwner
    fun getSiteById(
        @PathVariable siteId: Int,
    ): SiteResp = service.getSiteById(siteId).toSiteResp()

    @Operation(summary = "Update site information")
    @SecurityRequirement(name = "JSESSIONID")
    @PatchMapping("/{siteId}")
    @ApiResponse(responseCode = "200", description = "Site updated")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    @IsSiteOwner
    fun updateSiteParams(
        @PathVariable siteId: Int,
        @RequestBody siteReq: UpdateSiteReq,
    ): Boolean {
        val currentData = service.getSiteById(siteId)
        return service.updateSiteParameters(siteId, siteReq.toSiteDTO(currentData))
    }
}
