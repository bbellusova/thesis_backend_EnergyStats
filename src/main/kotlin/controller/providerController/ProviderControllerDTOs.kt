package cvut.fit.energyStats.controller.providerController

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.ProviderDataAccessType
import cvut.fit.energyStats.domain.dto.SiteOverviewDTO
import io.swagger.v3.oas.annotations.media.Schema

data class DataProviderResp(
    val code: String,
    val name: String,
    val accessTypes: Set<DataAccessType>,
    val availableCategories: Map<DataCategory, List<DataAccessType>>,
)

data class SiteOverviewResp(
    @field:Schema(description = "Unique identifier of the site in the provider's system")
    val brandSiteId: Int,
    @field:Schema(description = "Address of the site") val address: String,
    @field:Schema(description = "Latitude of the site") val lat: Double?,
    @field:Schema(description = "Longitude of the site") val lon: Double?,
    @field:Schema(description = "Timezone the site is located in") val timezone: String?,
    @field:Schema(description = "Kilowatt peak power of the site") val peakPower: Double?,
    @field:Schema(
        description =
            "Data provider's name, supported providers can be obtained from /providers endpoint",
    )
    val provider: DataProvider,
    @field:Schema(description = "Data categories the provider collects data for for this site")
    val availableCategories: Set<DataCategory>,
)

fun SiteOverviewDTO.toSiteOverviewResp(): SiteOverviewResp =
    SiteOverviewResp(
        brandSiteId,
        address,
        lat,
        lon,
        timezone,
        peakPower,
        provider,
        availableCategories,
    )

fun Map<DataCategory, Set<ProviderDataAccessType>>.toDataAccessTypeMap(): Map<DataCategory, List<DataAccessType>> {
    val result = mutableMapOf<DataCategory, List<DataAccessType>>()
    this.forEach { entry -> result[entry.key] = entry.value.map { it.dataAccess } }
    return result
}

data class ProvidersWithFileImportResp(
    @field:Schema(
        description = "Data categories with file import configured for this site and provider",
    )
    val categories: List<DataCategory>,
    @field:Schema(description = "All file formats supported by the provider")
    val supportedFileFormats: List<String>,
)
