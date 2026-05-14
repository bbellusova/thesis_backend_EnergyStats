package cvut.fit.energyStats.controller.geocodingController

import cvut.fit.energyStats.service.addressToCoordinatesService.AddressToCoordinatesService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("geocoding")
@Tag(name = "Geocoding")
class GeocodingController(
    val addressToCoordinatesService: AddressToCoordinatesService,
) {
    @GetMapping("/timezoneForCoordinates/{lat}/{lon}")
    @Operation(summary = "Get IANA timezone for provided coordinates.")
    @ApiResponse(
        responseCode = "200",
        description = "Timezone returned",
    )
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    fun getTimezoneForCoordinates(
        @PathVariable lat: Double,
        @PathVariable lon: Double,
    ): String = addressToCoordinatesService.convertCoordinatesToTimezone(lat, lon).id
}
