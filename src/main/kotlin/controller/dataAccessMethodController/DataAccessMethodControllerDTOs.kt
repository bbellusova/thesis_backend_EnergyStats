package cvut.fit.energyStats.controller.dataAccessMethodController

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import io.swagger.v3.oas.annotations.media.Schema

data class DataAccessMethodReq(
    @field:Schema(
        description = "How the data for the category is provided",
        allowableValues = ["API", "FILE"],
    )
    val dataAccessType: DataAccessType,
    @field:Schema(description = "Data provider for the category") val dataProvider: DataProvider?,
    @field:Schema(description = "Provider-specific ID of the site") val dataProviderSiteId: Int?,
    @field:Schema(description = "Provider-specific API key for the site") val apiKey: String?,
)

data class DataAccessMethodResp(
    @field:Schema(description = "ID of the site") val siteId: Int,
    @field:Schema(description = "Data category") val dataCategory: DataCategory,
    @field:Schema(description = "How the data for the category is provided")
    val dataAccessType: DataAccessType,
    @field:Schema(description = "Data provider for the category") val dataProvider: DataProvider?,
    @field:Schema(description = "Provider-specific ID of the site") val dataProviderSiteId: Int?,
)

fun DataAccessMethodReq.toDataAccessMethodDTO(
    siteId: Int,
    dataCategory: DataCategory,
): DataAccessMethodDTO =
    DataAccessMethodDTO(
        siteId,
        dataCategory,
        dataAccessType,
        dataProvider,
        dataProviderSiteId,
        apiKey,
    )

fun DataAccessMethodDTO.toDataAccessMethodResp(): DataAccessMethodResp =
    DataAccessMethodResp(siteId, dataCategory, dataAccessType, dataProvider, dataProviderSiteId)
