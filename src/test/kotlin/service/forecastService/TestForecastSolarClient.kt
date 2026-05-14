package service.forecastService

import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.service.forecastService.ForecastSolarClient
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

const val EXAMPLE_FORECAST =
    "{\"result\":{\"2026-05-11 05:17:30\":0,\"2026-05-11 06:00:00\":39,\"2026-05-11 07:00:00\":183,\"2026-05-11 08:00:00\":359,\"2026-05-11 09:00:00\":710,\"2026-05-11 10:00:00\":1137,\"2026-05-11 11:00:00\":1150,\"2026-05-11 12:00:00\":1010,\"2026-05-11 13:00:00\":970,\"2026-05-11 14:00:00\":878,\"2026-05-11 15:00:00\":840,\"2026-05-11 16:00:00\":751,\"2026-05-11 17:00:00\":566,\"2026-05-11 18:00:00\":370,\"2026-05-11 19:00:00\":204,\"2026-05-11 20:00:00\":79,\"2026-05-11 20:11:16\":3,\"2026-05-12 05:16:11\":0,\"2026-05-12 06:00:00\":42,\"2026-05-12 07:00:00\":186,\"2026-05-12 08:00:00\":332,\"2026-05-12 09:00:00\":489,\"2026-05-12 10:00:00\":668,\"2026-05-12 11:00:00\":860,\"2026-05-12 12:00:00\":1004,\"2026-05-12 13:00:00\":1063,\"2026-05-12 14:00:00\":1027,\"2026-05-12 15:00:00\":886,\"2026-05-12 16:00:00\":701,\"2026-05-12 17:00:00\":532,\"2026-05-12 18:00:00\":370,\"2026-05-12 19:00:00\":210,\"2026-05-12 20:00:00\":94,\"2026-05-12 20:12:33\":6},\"message\":{\"code\":0,\"type\":\"success\",\"text\":\"\",\"pid\":\"1CZYN9ir\",\"info\":{\"latitude\":47,\"longitude\":18,\"distance\":0,\"place\":\"8226 Alsóörs, Almádi utca ., Hungary\",\"timezone\":\"Europe/Budapest\",\"time\":\"2026-05-11T18:34:00+02:00\",\"time_utc\":\"2026-05-11T16:34:00+00:00\"},\"ratelimit\":{\"zone\":\"IP 185.112.167.16\",\"period\":3600,\"limit\":12,\"remaining\":11}}}"
const val EXAMPLE_CHECK =
    "{\"result\":{\"latitude\":\"12° 00' 00\\\" N\",\"longitude\":\"25° 00' 00\\\" E\",\"place\":\"Um Bilil, Sudan\",\"timezone\":\"Africa/Khartoum\"},\"message\":{\"code\":0,\"type\":\"success\",\"text\":\"\",\"pid\":\"F2806KcP\",\"info\":{\"latitude\":12,\"longitude\":25,\"distance\":0,\"place\":\"Um Bilil, Sudan\",\"timezone\":\"Africa/Khartoum\",\"time\":\"2026-05-11T18:35:53+02:00\",\"time_utc\":\"2026-05-11T16:35:53+00:00\"},\"ratelimit\":null}}"

const val EXAMPLE_RATE_LIMIT_REACHED =
    "{\"result\":\"rate limit reached\",\"message\":{\"code\":429,\"type\":\"error\",\"text\":\"Too many requests\",\"pid\":\"pid\",\"info\":[],\"ratelimit\":{\"zone\":\"zone\",\"period\":3600,\"limit\":12,\"retry-at\":\"2026-05-11T19:00:00+02:00\"}}}"

const val EXAMPLE_ERROR =
    "{\"result\":\"some error\",\"message\":{\"code\":400,\"type\":\"error\",\"text\":\"bad request\"}}"

class TestForecastSolarClient :
    FunSpec({
        val objectMapper = ObjectMapper()
        val siteDTO =
            SiteDTO(
                siteId = 1,
                siteName = "testSite",
                password = "secret",
                timezone = TimeZone.of("Europe/Budapest"),
                lat = 47.0,
                lon = 18.0,
                azm = 180.0,
                kwp = 5.0,
                dec = 35.0,
            )

        test("getForecastFromAPI returns correct data on success") {
            val restClientBuilder = RestClient.builder()
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val client = ForecastSolarClient(restClientBuilder.build(), objectMapper)

            mockServer
                .expect(requestTo("/estimate/watthours/period/47.0/18.0/35.0/180.0/5.0"))
                .andRespond(withSuccess(EXAMPLE_FORECAST, MediaType.APPLICATION_JSON))

            val result = client.getForecastFromAPI(siteDTO)

            result.result["2026-05-11 06:00:00"] shouldBe 39
            result.message.pid shouldBe "1CZYN9ir"
            result.message.info.place shouldBe "8226 Alsóörs, Almádi utca ., Hungary"
            mockServer.verify()
        }

        test("getForecastFromAPI throws exception on 429 error") {
            val restClientBuilder = RestClient.builder()
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val client = ForecastSolarClient(restClientBuilder.build(), objectMapper)

            mockServer
                .expect(requestTo("/estimate/watthours/period/47.0/18.0/35.0/180.0/5.0"))
                .andRespond(
                    withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(EXAMPLE_RATE_LIMIT_REACHED),
                )

            val exception = shouldThrow<IllegalArgumentException> { client.getForecastFromAPI(siteDTO) }
            exception.message shouldBe
                "failed to collect forecast data: rate limit reached. Retry at 2026-05-11T19:00"
            mockServer.verify()
        }

        test("getForecastFromAPI throws exception on non-2xx error") {
            val restClientBuilder = RestClient.builder()
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val client = ForecastSolarClient(restClientBuilder.build(), objectMapper)

            mockServer
                .expect(requestTo("/estimate/watthours/period/47.0/18.0/35.0/180.0/5.0"))
                .andRespond(
                    withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(EXAMPLE_ERROR),
                )

            val exception = shouldThrow<IllegalArgumentException> { client.getForecastFromAPI(siteDTO) }
            exception.message shouldBe "failed to collect forecast data: some error"
            mockServer.verify()
        }

        test("checkConfigurationValidity returns true on success") {
            val restClientBuilder = RestClient.builder()
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val client = ForecastSolarClient(restClientBuilder.build(), objectMapper)

            mockServer
                .expect(requestTo("/check/47.0/18.0"))
                .andRespond(withSuccess(EXAMPLE_CHECK, MediaType.APPLICATION_JSON))

            val result = client.checkConfigurationValidity(47.0, 18.0)

            result shouldBe true
            mockServer.verify()
        }

        test("checkConfigurationValidity returns false on error") {
            val restClientBuilder = RestClient.builder()
            val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            val client = ForecastSolarClient(restClientBuilder.build(), objectMapper)

            mockServer
                .expect(requestTo("/check/47.0/18.0"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST))

            val result = client.checkConfigurationValidity(47.0, 18.0)

            result shouldBe false
            mockServer.verify()
        }
    })
