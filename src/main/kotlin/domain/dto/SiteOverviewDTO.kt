package cvut.fit.energyStats.domain.dto

import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider

data class SiteOverviewDTO(
    val brandSiteId: Int,
    val address: String,
    val lat: Double?,
    val lon: Double?,
    val timezone: String?,
    val peakPower: Double?,
    val provider: DataProvider,
    val availableCategories: Set<DataCategory>,
)
