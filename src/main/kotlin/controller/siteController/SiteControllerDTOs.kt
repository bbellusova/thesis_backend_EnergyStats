package cvut.fit.energyStats.controller.siteController

import cvut.fit.energyStats.domain.dto.SiteDTO
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.datetime.TimeZone

data class LoginReq(
    @field:Schema(description = "Unique identifier of the site account", example = "MySolarSite")
    val siteName: String,
    @field:Schema(
        description = "Password used to log into the site's account",
        example = "password",
    )
    val password: String,
)

data class CreateSiteReq(
    @field:Schema(description = "Unique identifier of the site account", example = "MySolarSite")
    val siteName: String,
    @field:Schema(
        description = "Password used to log into the site's account",
        example = "password",
    )
    val password: String,
    @field:Schema(
        description = "Timezone of the site, must be a valid IANA timezone identifier",
        example = "Europe/Bratislava",
    )
    val timezone: String,
    @field:Schema(description = "Latitude of the site, -90.0 <= lat <= 90.0", example = "45.0")
    val lat: Double,
    @field:Schema(description = "Longitude of the site, -180.0 <= lat <= 180.0", example = "17.0")
    val lon: Double,
    @field:Schema(description = "Azimuth of the site, -180.0 <= azm <= 180.0", example = "80.0")
    val azm: Double,
    @field:Schema(description = "Kilowatt peak power (kWp) of the site, 0.0 < kwp", example = "3.0")
    val kwp: Double,
    @field:Schema(description = "Declination of the panels, 0.0 <= dec <= 90.0", example = "30.0")
    val dec: Double,
)

data class UpdateSiteReq(
    @field:Schema(
        description = "Timezone of the site, must be a valid IANA timezone identifier",
        example = "Europe/Bratislava",
    )
    val timezone: String?,
    @field:Schema(description = "Latitude of the site, -90.0 <= lat <= 90.0", example = "45.0")
    val lat: Double?,
    @field:Schema(description = "Longitude of the site, -180.0 <= lat <= 180.0", example = "17.0")
    val lon: Double?,
    @field:Schema(description = "Azimuth of the site, -180.0 <= azm <= 180.0", example = "80.0")
    val azm: Double?,
    @field:Schema(description = "Kilowatt peak power (kWp) of the site, 0.0 < kwp", example = "3.0")
    val kwp: Double?,
    @field:Schema(description = "Declination of the panels, 0.0 <= dec <= 90.0", example = "30.0")
    val dec: Double?,
)

data class SiteResp(
    @field:Schema(description = "Numerical unique identifier of the site account", example = "1")
    val siteId: Int,
    @field:Schema(description = "Unique identifier of the site account", example = "MySolarSite")
    val siteName: String,
    @field:Schema(
        description = "Timezone of the site, is a valid IANA timezone identifier",
        example = "Europe/Bratislava",
    )
    val timezone: String,
    @field:Schema(description = "Latitude of the site, -90.0 <= lat <= 90.0", example = "45.0")
    val lat: Double,
    @field:Schema(description = "Longitude of the site, -180.0 <= lat <= 180.0", example = "17.0")
    val lon: Double,
    @field:Schema(description = "Azimuth of the site, -180.0 <= azm <= 180.0", example = "80.0")
    val azm: Double,
    @field:Schema(description = "Kilowatt peak power (kWp) of the site, 0.0 < kwp", example = "3.0")
    val kwp: Double,
    @field:Schema(description = "Declination of the panels, 0.0 <= dec <= 90.0", example = "30.0")
    val dec: Double,
)

fun CreateSiteReq.toSiteDTO(): SiteDTO = SiteDTO(0, siteName, password, timezone.toTimeZone(), lat, lon, azm, kwp, dec)

fun UpdateSiteReq.toSiteDTO(currentData: SiteDTO): SiteDTO =
    SiteDTO(
        currentData.siteId,
        currentData.siteName,
        currentData.password,
        timezone?.toTimeZone() ?: currentData.timezone,
        lat ?: currentData.lat,
        lon ?: currentData.lon,
        azm ?: currentData.azm,
        kwp ?: currentData.kwp,
        dec ?: currentData.dec,
    )

fun SiteDTO.toSiteResp(): SiteResp = SiteResp(siteId, siteName, timezone.id, lat, lon, azm, kwp, dec)

fun String.toTimeZone(): TimeZone {
    try {
        return TimeZone.of(this)
    } catch (_: Exception) {
        throw IllegalArgumentException("failed to convert \"$this\" to a valid time zone")
    }
}
