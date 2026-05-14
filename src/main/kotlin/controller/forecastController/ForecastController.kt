package cvut.fit.energyStats.controller.forecastController

import cvut.fit.energyStats.config.IsSiteOwner
import cvut.fit.energyStats.service.forecastService.ProductionForecastService
import cvut.fit.energyStats.service.forecastService.WeatherForecastService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Forecasts")
@RequestMapping("forecasts")
class ForecastController(
    val productionForecastService: ProductionForecastService,
    val weatherForecastService: WeatherForecastService,
) {
    @Operation(
        summary =
            "Get generation forecast for the current and the following day, aggregated by hours",
    )
    @GetMapping("/generation/{siteId}")
    @ApiResponse(responseCode = "200", description = "Data returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    @IsSiteOwner
    @SecurityRequirement(name = "JSESSIONID")
    fun getProductionForecast(
        @PathVariable siteId: Int,
    ): ProductionForecastDataResp = productionForecastService.getForecast(siteId).toProductionForecastDataResp()

    @Operation(
        summary =
            "Validate the location. Returns true if forecasts are available for this location, false otherwise.",
    )
    @GetMapping("/generation/validateCoordinates/{lat}/{lon}")
    @ApiResponse(
        responseCode = "200",
        description = "Validation result returned",
    )
    fun validateConfiguration(
        @PathVariable lat: Double,
        @PathVariable lon: Double,
    ): Boolean = productionForecastService.checkConfigurationValidity(lat, lon)

    @Operation(summary = "Get weather forecast for the current and the following day")
    @GetMapping("/weather/{siteId}")
    @ApiResponse(responseCode = "200", description = "Data returned")
    @ApiResponse(responseCode = "400", description = "Illegal argument", content = [Content()])
    @ApiResponse(responseCode = "401", description = "Forbidden", content = [Content()])
    @ApiResponse(responseCode = "403", description = "Forbidden", content = [Content()])
    @IsSiteOwner
    @SecurityRequirement(name = "JSESSIONID")
    fun getWeatherForecast(
        @PathVariable siteId: Int,
    ): WeatherForecastResp = weatherForecastService.getForecastFromAPI(siteId).toWeatherForecastResp()
}
