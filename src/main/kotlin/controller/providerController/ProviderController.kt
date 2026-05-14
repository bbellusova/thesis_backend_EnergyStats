package cvut.fit.energyStats.controller.providerController

import cvut.fit.energyStats.config.ApiProviders
import cvut.fit.energyStats.config.FileProviders
import cvut.fit.energyStats.config.IsSiteOwner
import cvut.fit.energyStats.config.Providers
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.energyDataService.EnergyDataImportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("providers")
@Tag(name = "Providers")
class ProviderController(
    val dataAccessMethodService: DataAccessMethodService,
    val energyDataImportService: EnergyDataImportService,
    val fileProviders: FileProviders,
    private val apiProviders: ApiProviders,
    private val providers: Providers,
) {
    @Operation(summary = "Get all supported providers")
    @GetMapping("")
    @ApiResponse(responseCode = "200", description = "Providers returned")
    fun getProviders(): List<DataProviderResp> =
        DataProvider.entries.map { provider ->
            var accessTypes = setOf<DataAccessType>()
            providers[provider]!!.providedCategories.forEach { entry ->
                accessTypes = accessTypes.union(entry.value.map { it.dataAccess })
            }

            DataProviderResp(
                provider.name,
                provider.fullName,
                accessTypes,
                providers[provider]!!.providedCategories.toDataAccessTypeMap(),
            )
        }

    @Operation(
        summary =
            "Get all available sites from a provider, requires entering a provider-specific API key",
    )
    @GetMapping("/{provider}/availableSitesForAPIKey")
    @ApiResponse(responseCode = "200", description = "Available sites returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    fun getAvailableSites(
        @PathVariable provider: DataProvider,
        @RequestParam key: String,
    ): List<SiteOverviewResp> =
        apiProviders[provider]?.getAvailableSites(key)?.map { it.toSiteOverviewResp() }
            ?: throw IllegalArgumentException(
                "Provider $provider is not supported for API data import",
            )

    @Operation(
        summary =
            "Import file with data from the specified provider for the specified site and data category",
    )
    @SecurityRequirement(name = "JSESSIONID")
    @PostMapping(
        "/{provider}/importDataFromFile/{siteId}/{category}",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    @ApiResponse(responseCode = "200", description = "Data imported successfully")
    @ApiResponse(responseCode = "400", description = "Illegal argument")
    @ApiResponse(responseCode = "401", description = "Forbidden")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    @IsSiteOwner
    fun importDataFromFile(
        @PathVariable siteId: Int,
        @PathVariable category: DataCategory,
        @PathVariable provider: DataProvider,
        @RequestParam file: MultipartFile,
    ) {
        energyDataImportService.importDataFromAFile(siteId, category, provider, file)
    }

    @Operation(
        summary =
            "Get providers that support data import from file for any of the data categories for specified site",
    )
    @GetMapping("/configuredFileImportProviders/{siteId}")
    @ApiResponse(
        responseCode = "200",
        description = "Available providers and corresponding data categories found",
    )
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    @SecurityRequirement(name = "JSESSIONID")
    @IsSiteOwner
    fun getProvidersWithFileDataImportForSite(
        @PathVariable siteId: Int,
    ): Map<DataProvider, ProvidersWithFileImportResp> =
        dataAccessMethodService.getProvidersWithFileDataImportForSite(siteId).mapValues { (key, value) ->
            toProvidersWithFileImportResp(key, value)
        }

    private fun toProvidersWithFileImportResp(
        provider: DataProvider,
        categories: Set<DataCategory>,
    ) = ProvidersWithFileImportResp(
        categories.toList(),
        fileProviders[provider]?.supportedFileFormats ?: emptyList(),
    )
}
