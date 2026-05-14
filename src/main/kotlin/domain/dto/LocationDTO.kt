package cvut.fit.energyStats.domain.dto

import kotlinx.datetime.TimeZone

data class LocationDTO(
    val lat: Double?,
    val lon: Double?,
    val timezone: TimeZone?,
)
