package cvut.fit.energyStats.domain.dto

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider

data class DataAccessMethodDTO(
    val siteId: Int,
    val dataCategory: DataCategory,
    val dataAccessType: DataAccessType,
    val dataProvider: DataProvider?,
    val dataProviderSiteId: Int?,
    val apiKey: String?,
)
