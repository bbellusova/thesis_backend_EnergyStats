package service.forecastService

import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.service.forecastService.OpenMeteoClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

const val DAILY_FORECAST =
    """{"latitude":47.02,"longitude":17.999998,"generationtime_ms":0.10371208190917969,"utc_offset_seconds":7200,"timezone":"Europe/Budapest","timezone_abbreviation":"GMT+2","elevation":105.0,"daily_units":{"time":"iso8601","temperature_2m_min":"°C","temperature_2m_max":"°C","sunrise":"iso8601","sunset":"iso8601","weather_code":"wmo code","sunshine_duration":"s","daylight_duration":"s"},"daily":{"time":["2026-05-11","2026-05-12"],"temperature_2m_min":[16.0,8.9],"temperature_2m_max":[23.4,15.8],"sunrise":["2026-05-11T05:17","2026-05-12T05:16"],"sunset":["2026-05-11T20:11","2026-05-12T20:12"],"weather_code":[80,80],"sunshine_duration":[41501.82,22408.52],"daylight_duration":[53613.48,53771.46]}}"""

const val HOURLY_FORECAST =
    """{"latitude":47.02,"longitude":17.999998,"generationtime_ms":0.27239322662353516,"utc_offset_seconds":0,"timezone":"GMT","timezone_abbreviation":"GMT","elevation":105.0,"hourly_units":{"time":"iso8601","global_tilted_irradiance":"W/m²","cloud_cover":"%","weather_code":"wmo code","relative_humidity_2m":"%"},"hourly":{"time":["2026-05-13T00:00","2026-05-13T01:00","2026-05-13T02:00","2026-05-13T03:00","2026-05-13T04:00","2026-05-13T05:00","2026-05-13T06:00","2026-05-13T07:00","2026-05-13T08:00","2026-05-13T09:00","2026-05-13T10:00","2026-05-13T11:00","2026-05-13T12:00","2026-05-13T13:00","2026-05-13T14:00","2026-05-13T15:00","2026-05-13T16:00","2026-05-13T17:00","2026-05-13T18:00","2026-05-13T19:00","2026-05-13T20:00","2026-05-13T21:00","2026-05-13T22:00","2026-05-13T23:00","2026-05-14T00:00","2026-05-14T01:00","2026-05-14T02:00","2026-05-14T03:00","2026-05-14T04:00","2026-05-14T05:00","2026-05-14T06:00","2026-05-14T07:00","2026-05-14T08:00","2026-05-14T09:00","2026-05-14T10:00","2026-05-14T11:00","2026-05-14T12:00","2026-05-14T13:00","2026-05-14T14:00","2026-05-14T15:00","2026-05-14T16:00","2026-05-14T17:00","2026-05-14T18:00","2026-05-14T19:00","2026-05-14T20:00","2026-05-14T21:00","2026-05-14T22:00","2026-05-14T23:00","2026-05-15T00:00","2026-05-15T01:00","2026-05-15T02:00","2026-05-15T03:00","2026-05-15T04:00","2026-05-15T05:00","2026-05-15T06:00","2026-05-15T07:00","2026-05-15T08:00","2026-05-15T09:00","2026-05-15T10:00","2026-05-15T11:00","2026-05-15T12:00","2026-05-15T13:00","2026-05-15T14:00","2026-05-15T15:00","2026-05-15T16:00","2026-05-15T17:00","2026-05-15T18:00","2026-05-15T19:00","2026-05-15T20:00","2026-05-15T21:00","2026-05-15T22:00","2026-05-15T23:00","2026-05-16T00:00","2026-05-16T01:00","2026-05-16T02:00","2026-05-16T03:00","2026-05-16T04:00","2026-05-16T05:00","2026-05-16T06:00","2026-05-16T07:00","2026-05-16T08:00","2026-05-16T09:00","2026-05-16T10:00","2026-05-16T11:00","2026-05-16T12:00","2026-05-16T13:00","2026-05-16T14:00","2026-05-16T15:00","2026-05-16T16:00","2026-05-16T17:00","2026-05-16T18:00","2026-05-16T19:00","2026-05-16T20:00","2026-05-16T21:00","2026-05-16T22:00","2026-05-16T23:00","2026-05-17T00:00","2026-05-17T01:00","2026-05-17T02:00","2026-05-17T03:00","2026-05-17T04:00","2026-05-17T05:00","2026-05-17T06:00","2026-05-17T07:00","2026-05-17T08:00","2026-05-17T09:00","2026-05-17T10:00","2026-05-17T11:00","2026-05-17T12:00","2026-05-17T13:00","2026-05-17T14:00","2026-05-17T15:00","2026-05-17T16:00","2026-05-17T17:00","2026-05-17T18:00","2026-05-17T19:00","2026-05-17T20:00","2026-05-17T21:00","2026-05-17T22:00","2026-05-17T23:00","2026-05-18T00:00","2026-05-18T01:00","2026-05-18T02:00","2026-05-18T03:00","2026-05-18T04:00","2026-05-18T05:00","2026-05-18T06:00","2026-05-18T07:00","2026-05-18T08:00","2026-05-18T09:00","2026-05-18T10:00","2026-05-18T11:00","2026-05-18T12:00","2026-05-18T13:00","2026-05-18T14:00","2026-05-18T15:00","2026-05-18T16:00","2026-05-18T17:00","2026-05-18T18:00","2026-05-18T19:00","2026-05-18T20:00","2026-05-18T21:00","2026-05-18T22:00","2026-05-18T23:00","2026-05-19T00:00","2026-05-19T01:00","2026-05-19T02:00","2026-05-19T03:00","2026-05-19T04:00","2026-05-19T05:00","2026-05-19T06:00","2026-05-19T07:00","2026-05-19T08:00","2026-05-19T09:00","2026-05-19T10:00","2026-05-19T11:00","2026-05-19T12:00","2026-05-19T13:00","2026-05-19T14:00","2026-05-19T15:00","2026-05-19T16:00","2026-05-19T17:00","2026-05-19T18:00","2026-05-19T19:00","2026-05-19T20:00","2026-05-19T21:00","2026-05-19T22:00","2026-05-19T23:00"],"global_tilted_irradiance":[0.0,0.0,0.0,0.0,11.4,52.2,218.2,433.3,643.0,804.3,782.3,864.7,910.0,637.6,303.7,417.1,370.9,168.1,30.6,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,12.4,54.0,215.9,426.3,637.2,815.9,945.5,1009.8,1000.6,917.8,768.3,531.0,276.0,140.3,31.2,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,6.6,25.6,63.4,259.7,535.0,775.9,767.0,777.5,556.2,236.9,243.3,295.0,264.9,92.3,23.7,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,2.8,18.0,57.7,83.3,96.5,168.7,156.3,230.2,130.8,116.6,102.6,84.5,62.5,39.5,16.1,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,4.7,19.9,37.9,62.4,93.8,124.2,150.8,170.7,178.1,166.6,144.8,120.2,98.5,69.2,29.4,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,12.3,54.6,199.8,340.7,547.7,743.7,849.5,866.6,833.3,770.3,667.4,526.1,355.4,177.3,25.5,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,14.4,63.2,172.6,275.2,361.5,441.2,527.5,608.5,646.5,632.8,572.6,458.5,302.2,152.4,40.1,0.0,0.0,0.0,0.0,0.0],"cloud_cover":[0,0,0,0,0,0,0,7,7,46,70,51,53,100,100,36,35,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,9,42,77,96,100,100,96,12,2,13,97,100,100,100,100,100,100,100,100,100,100,5,93,100,100,100,100,100,100,100,99,100,100,99,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,100,99,99,98,97,97,96,93,90,87,81,74,68,63,58,53,45,38,33,51,42,33,45,57,69,48,27,6,5,5,4,7,9,12,8,4,0,11,23,34,47,60,73,73,74,74,80,86,92,94,96,98,75,53,30,53,77,100,94,89],"weather_code":[0,0,0,0,0,0,0,1,1,2,2,2,3,3,3,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,2,3,3,3,3,80,1,0,1,3,61,61,3,3,61,61,61,61,61,3,1,1,3,3,95,3,3,3,3,3,61,61,3,61,61,61,63,80,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,61,3,3,3,3,3,3,2,2,2,2,2,2,2,2,2,1,1,1,2,2,2,0,0,0,0,0,0,1,1,1,0,0,0,1,1,1,2,2,2,2,2,2,3,3,3,3,3,3,1,1,1,3,3,3,3,3],"relative_humidity_2m":[63,60,61,60,60,64,67,65,56,48,45,42,42,41,44,44,47,48,52,56,62,65,68,70,69,69,71,72,73,68,63,59,54,47,42,41,37,36,36,39,41,52,57,60,62,62,64,66,70,71,74,81,86,85,79,77,71,69,70,71,72,83,72,70,69,80,82,80,79,78,78,78,86,90,92,92,90,86,84,85,86,83,80,79,80,81,83,84,82,79,77,77,78,79,79,78,78,79,79,80,80,80,80,80,79,79,79,79,79,80,80,80,78,74,72,71,71,72,74,77,78,78,76,74,72,69,68,65,60,56,53,50,48,48,48,49,50,52,54,57,60,62,63,63,63,63,63,63,62,61,60,58,56,54,50,46,44,44,45,48,55,64,72,77,81,84,85,84]}}"""

class TestOpenMeteoClient :
    FunSpec({
        val objectMapper = ObjectMapper()
        val siteDTO =
            SiteDTO(
                siteId = 1,
                siteName = "testSite",
                password = "secret",
                timezone = TimeZone.of("Europe/Budapest"),
                lat = 47.02,
                lon = 18.0,
                azm = 180.0,
                kwp = 5.0,
                dec = 35.0,
            )

        test("getDailyWeatherForecastFromAPI returns correct data on success") {
            val restClientBuilder = RestClient.builder()
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val client = OpenMeteoClient(restClientBuilder.build(), objectMapper)

            mockServer
                .expect(
                    requestTo(
                        "?latitude=47.02&longitude=18.0&daily=temperature_2m_min,temperature_2m_max,sunrise,sunset,weather_code,sunshine_duration,daylight_duration&timezone=Europe/Budapest&forecast_days=2",
                    ),
                ).andRespond(withSuccess(DAILY_FORECAST, MediaType.APPLICATION_JSON))

            val result = client.getDailyWeatherForecastFromAPI(siteDTO)

            result.daily.time[0] shouldBe java.time.LocalDate.of(2026, 5, 11)
            result.daily.tempMin[0] shouldBe 16.0
            result.dailyUnits["temperature_2m_min"] shouldBe "°C"
            mockServer.verify()
        }

        test("getHourlyWeatherForecastFromAPI returns correct data on success") {
            val restClientBuilder = RestClient.builder()
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val client = OpenMeteoClient(restClientBuilder.build(), objectMapper)

            mockServer
                .expect(
                    requestTo(
                        "?latitude=47.02&longitude=18.0&hourly=global_tilted_irradiance,cloud_cover,weather_code,relative_humidity_2m&tilt=35.0&azimuth=180.0",
                    ),
                ).andRespond(withSuccess(HOURLY_FORECAST, MediaType.APPLICATION_JSON))

            val result = client.getHourlyWeatherForecastFromAPI(siteDTO)

            result.hourly.time[0].hour shouldBe 0
            result.hourlyUnits["global_tilted_irradiance"] shouldBe "W/m²"
            mockServer.verify()
        }

        test("callAPI throws exception on non-2xx error") {
            val restClientBuilder = RestClient.builder()
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val client = OpenMeteoClient(restClientBuilder.build(), objectMapper)

            val errorJson = "{\"reason\":\"Invalid parameters\"}"

            mockServer
                .expect(
                    requestTo(
                        "?latitude=47.02&longitude=18.0&daily=temperature_2m_min,temperature_2m_max,sunrise,sunset,weather_code,sunshine_duration,daylight_duration&timezone=Europe/Budapest&forecast_days=2",
                    ),
                ).andRespond(
                    withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson),
                )

            val exception =
                shouldThrow<IllegalArgumentException> { client.getDailyWeatherForecastFromAPI(siteDTO) }
            exception.message shouldBe "failed to get weather forecast data"
            mockServer.verify()
        }
    })
