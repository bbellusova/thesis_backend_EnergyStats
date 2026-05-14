package cvut.fit.energyStats.controller.dataAccessMethodController

import cvut.fit.energyStats.config.IsSiteOwner
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@SecurityRequirement(name = "JSESSIONID")
@Tag(
    name = "Data Access Configuration",
    description =
        "Endpoints for configuring data access methods for a site and obtaining the current configuration",
)
@RequestMapping("dataAccess")
@IsSiteOwner
class DataAccessMethodController(
    val service: DataAccessMethodService,
) {
    @Operation(summary = "Get access method information for a site and data category")
    @GetMapping("/{siteId}/{category}")
    @ApiResponse(responseCode = "200", description = "Access method returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    fun getAccessMethod(
        @PathVariable siteId: Int,
        @PathVariable category: DataCategory,
    ): DataAccessMethodResp = service.getAccessMethod(siteId, category).toDataAccessMethodResp()

    @Operation(summary = "Update data access information")
    @PutMapping("/{siteId}/{category}")
    @ApiResponse(responseCode = "200", description = "Access Method changed")
    @ApiResponse(responseCode = "400", description = "Illegal argument")
    @ApiResponse(responseCode = "401", description = "Forbidden")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    fun updateAccessMethod(
        @PathVariable siteId: Int,
        @PathVariable category: DataCategory,
        @RequestBody dataAccessMethod: DataAccessMethodReq,
    ) {
        require(dataAccessMethod.dataAccessType.isProviderDataAccessType) {
            "Data access type must be a provider data access type"
        }
        require(
            service.getAccessMethod(siteId, category).dataAccessType == DataAccessType.UNAVAILABLE,
        ) {
            "Updating already configured access types is not supported"
        }
        service.updateAccessMethod(dataAccessMethod.toDataAccessMethodDTO(siteId, category))
    }

    @Operation(
        summary =
            "Get all data categories available for the site. Available categories include all categories where data access type is not UNAVAILABLE.",
    )
    @GetMapping("/{siteId}/availableCategories")
    @ApiResponse(responseCode = "200", description = "Available data categories returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    fun getAvailableCategoriesForSite(
        @PathVariable siteId: Int,
    ): Set<DataCategory> = service.getAvailableCategoriesForSite(siteId)
}
