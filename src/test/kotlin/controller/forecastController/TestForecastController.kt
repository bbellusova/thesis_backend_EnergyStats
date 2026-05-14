package controller.forecastController

import cvut.fit.energyStats.controller.forecastController.DailyWeatherResp
import cvut.fit.energyStats.controller.forecastController.ForecastController
import cvut.fit.energyStats.controller.forecastController.HourlyWeatherResp
import cvut.fit.energyStats.controller.forecastController.ProductionForecastDataResp
import cvut.fit.energyStats.controller.forecastController.WeatherForecastResp
import cvut.fit.energyStats.domain.dto.DailyWeatherDTO
import cvut.fit.energyStats.domain.dto.HourlyWeatherDTO
import cvut.fit.energyStats.domain.dto.ProductionForecastDataDTO
import cvut.fit.energyStats.domain.dto.WeatherForecastDTO
import cvut.fit.energyStats.service.forecastService.ProductionForecastService
import cvut.fit.energyStats.service.forecastService.WeatherForecastService
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.assertEquals

class TestForecastController :
    FunSpec({
        val mockProductionService: ProductionForecastService = mockk()
        val mockWeatherService: WeatherForecastService = mockk()
        val controller = ForecastController(mockProductionService, mockWeatherService)

        val productionForecastDataDTO =
            ProductionForecastDataDTO(
                todayData =
                    mutableMapOf(
                        LocalDateTime(2026, 5, 10, 11, 0) to 4.0,
                        LocalDateTime(2026, 5, 10, 10, 0) to 5.0,
                    ),
                tomorrowData =
                    mutableMapOf(
                        LocalDateTime(2026, 5, 11, 12, 0) to 7.0,
                        LocalDateTime(2026, 5, 9, 10, 0) to 6.0,
                    ),
            )

        val weatherForecastDTO =
            WeatherForecastDTO(
                todayWeather =
                    DailyWeatherDTO(
                        LocalDate(2026, 5, 10),
                        "C",
                        10.0,
                        20.0,
                        LocalDateTime(2026, 5, 10, 6, 0),
                        LocalDateTime(2026, 5, 10, 20, 0),
                        0,
                        3600.0,
                        43200.0,
                    ),
                todayHourly = mapOf(10 to HourlyWeatherDTO(50.0, 500.0, 0, 50)),
                tomorrowWeather =
                    DailyWeatherDTO(
                        LocalDate(2026, 5, 11),
                        "C",
                        12.0,
                        22.0,
                        LocalDateTime(2026, 5, 11, 6, 0),
                        LocalDateTime(2026, 5, 11, 20, 0),
                        1,
                        3700.0,
                        43300.0,
                    ),
                tomorrowHourly = mapOf(10 to HourlyWeatherDTO(60.0, 600.0, 1, 50)),
            )

        test("getProductionForecast returns mapped response") {
            clearMocks(mockProductionService)
            every { mockProductionService.getForecast(1) } returns productionForecastDataDTO

            val expected =
                ProductionForecastDataResp(
                    todayData =
                        sortedMapOf(
                            LocalDateTime(2026, 5, 10, 11, 0) to 4.0,
                            LocalDateTime(2026, 5, 10, 10, 0) to 5.0,
                        ),
                    todayTotal = 9.0,
                    tomorrowData =
                        sortedMapOf(
                            LocalDateTime(2026, 5, 11, 12, 0) to 7.0,
                            LocalDateTime(2026, 5, 9, 10, 0) to 6.0,
                        ),
                    tomorrowTotal = 13.0,
                )
            val result = controller.getProductionForecast(1)

            assertEquals(expected, result)
            verify(exactly = 1) { mockProductionService.getForecast(1) }
        }

        withTests(
            mapOf(
                "validateConfiguration returns true" to (10.0 to 20.0 to true),
                "validateConfiguration returns false" to (30.0 to 40.0 to false),
            ),
        ) { (coords, expectedResult) ->
            val (lat, lon) = coords
            clearMocks(mockProductionService)
            every { mockProductionService.checkConfigurationValidity(lat, lon) } returns expectedResult

            val result = controller.validateConfiguration(lat, lon)
            assertEquals(expectedResult, result)
            verify(exactly = 1) { mockProductionService.checkConfigurationValidity(lat, lon) }
        }

        test("getWeatherForecast returns mapped response") {
            clearMocks(mockWeatherService)
            every { mockWeatherService.getForecastFromAPI(1) } returns weatherForecastDTO

            val expectedResult =
                WeatherForecastResp(
                    todayWeather =
                        DailyWeatherResp(
                            LocalDate(2026, 5, 10).toString(),
                            "C",
                            10.0,
                            20.0,
                            LocalDateTime(2026, 5, 10, 6, 0).toString(),
                            LocalDateTime(2026, 5, 10, 20, 0).toString(),
                            0,
                            3600.0,
                            43200.0,
                        ),
                    todayHourly = mapOf(10 to HourlyWeatherResp(50.0, 500.0, 0, 50)),
                    tomorrowWeather =
                        DailyWeatherResp(
                            LocalDate(2026, 5, 11).toString(),
                            "C",
                            12.0,
                            22.0,
                            LocalDateTime(2026, 5, 11, 6, 0).toString(),
                            LocalDateTime(2026, 5, 11, 20, 0).toString(),
                            1,
                            3700.0,
                            43300.0,
                        ),
                    tomorrowHourly = mapOf(10 to HourlyWeatherResp(60.0, 600.0, 1, 50)),
                )
            val result = controller.getWeatherForecast(1)

            assertEquals(expectedResult, result)
            verify(exactly = 1) { mockWeatherService.getForecastFromAPI(1) }
        }
    })
