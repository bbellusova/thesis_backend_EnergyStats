package cvut.fit.energyStats.service.addressToCoordinatesService

import cvut.fit.energyStats.config.OpenCageProperties
import cvut.fit.energyStats.domain.dto.AddressDTO
import cvut.fit.energyStats.domain.dto.LocationDTO
import kotlinx.datetime.TimeZone
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class AddressToCoordinatesService(
    private val properties: OpenCageProperties,
    @param:Qualifier("openCageAPIClient") private val apiClient: RestClient,
) {
    fun convertAddressToCoordinates(addressDTO: AddressDTO): LocationDTO {
        val address =
            addressDTO.street +
                ", " +
                addressDTO.zipCode +
                ", " +
                addressDTO.city +
                ", " +
                addressDTO.country
        val countryCode = addressDTO.countryCode
        val key = properties.apikey
        val req = "/json?q=$address&countrycode=$countryCode&key=$key&abbrv=1&limit=1"
        val nullDTO = LocationDTO(null, null, null)
        try {
            val response =
                apiClient
                    .get()
                    .uri(req)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body<OpenCageResponse>()
                    ?.results
                    ?.firstOrNull() ?: return nullDTO
            val lat = response.geometry.lat
            val lon = response.geometry.lng
            try {
                val timezone = TimeZone.of(response.annotations.timezone.name)
                return LocationDTO(lat, lon, timezone)
            } catch (e: Exception) {
                return LocationDTO(lat, lon, null)
            }
        } catch (e: Exception) {
            return nullDTO
        }
    }

    fun convertCoordinatesToTimezone(
        lat: Double,
        lon: Double,
    ): TimeZone {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "invalid coordinates" }
        val key = properties.apikey
        val req = "/json?q=$lat,$lon&key=$key"
        val response =
            apiClient
                .get()
                .uri(req)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus({ !it.is2xxSuccessful }) { _, response ->
                    val errorText = response.body.bufferedReader().use { it.readText() }
                    throw IllegalArgumentException("API Error: $errorText")
                }.body<OpenCageResponse>()
        val tzName =
            response
                ?.results
                ?.firstOrNull()
                ?.annotations
                ?.timezone
                ?.name
        val timezone = if (tzName != null) TimeZone.of(tzName) else null
        if (timezone == null) {
            throw IllegalArgumentException("Timezone not found for coordinates $lat, $lon")
        }
        return timezone
    }

    data class OpenCageResponse(
        val results: List<GeocodingResult>,
    )

    data class GeocodingResult(
        val geometry: Geometry,
        val annotations: Annotations,
    )

    data class Geometry(
        val lat: Double,
        val lng: Double,
    )

    data class Annotations(
        val timezone: Timezone,
    )

    data class Timezone(
        val name: String,
    )
}
