package cvut.fit.energyStats.domain.dto

import kotlinx.datetime.TimeZone

data class SiteDTO(
    val siteId: Int,
    val siteName: String,
    val password: String?,
    val timezone: TimeZone,
    val lat: Double,
    val lon: Double,
    val azm: Double,
    val kwp: Double,
    val dec: Double,
)

fun SiteDTO.removePassword(): SiteDTO = SiteDTO(siteId, siteName, null, timezone, lat, lon, azm, kwp, dec)
