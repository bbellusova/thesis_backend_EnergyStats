package service.providerService

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import cvut.fit.energyStats.service.addressToCoordinatesService.AddressToCoordinatesService
import cvut.fit.energyStats.service.providerService.SolarEdgeDataImportService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

const val EXAMPLE_IMPORT_DATA =
    "{\"energy\":{\"timeUnit\":\"HOUR\",\"unit\":\"Wh\",\"values\":[{\"date\":\"2025-02-01 00:00:00\",\"value\":null},{\"date\":\"2025-02-01 04:00:00\",\"value\":0.0},{\"date\":\"2025-02-01 07:00:00\",\"value\":174.0},{\"date\":\"2025-02-01 08:00:00\",\"value\":1193.0},{\"date\":\"2025-02-02 07:00:00\",\"value\":1.0}]}}"

const val EXAMPLE_AVAILABLE_SITES =
    "{\"sites\":{\"count\":1,\"site\":[{\"id\":1234567,\"name\":\"Name 3kW 1234567890\",\"accountId\":12345,\"status\":\"Active\",\"peakPower\":0.0,\"lastUpdateTime\":null,\"installationDate\":\"2024-04-10\",\"ptoDate\":null,\"notes\":\"24ZZS7012345000D;24ZABCDE0022364B\",\"type\":\"\",\"location\":{\"country\":\"Slovakia\",\"state\":\"Slovakia\",\"city\":\"Bratislava\",\"address\":\"Ľanová, 5236/4\",\"address2\":\"\",\"zip\":\"821 01\",\"timeZone\":\"Europe/Bratislava\",\"countryCode\":\"SK\"},\"alertQuantity\":0,\"highestImpact\":0,\"primaryModule\":{\"manufacturerName\":\"\",\"modelName\":\"\",\"maximumPower\":0.0,\"temperatureCoef\":0.0},\"uris\":{\"OVERVIEW\":\"/site/1234567/overview\",\"DATA_PERIOD\":\"/site/1234567/dataPeriod\",\"DETAILS\":\"/site/1234567/details\"},\"publicSettings\":null}]}}"

class TestSolarEdgeDataImportService :
    FunSpec({
        val mockAddressToCoordinatesService: AddressToCoordinatesService = mockk()
        lateinit var mockServer: MockRestServiceServer
        lateinit var service: SolarEdgeDataImportService

        beforeEach {
            ObjectMapper().apply {
                registerKotlinModule()
                propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
            }
            val restClientBuilder = RestClient.builder()

            mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            service =
                SolarEdgeDataImportService(mockAddressToCoordinatesService, restClientBuilder.build())
        }

        test("getAvailableSites returns correct site list") {
            val apiKey = "test-api-key"
            mockServer
                .expect(requestTo("/sites/list?size=5&api_key=$apiKey"))
                .andRespond(withSuccess(EXAMPLE_AVAILABLE_SITES, MediaType.APPLICATION_JSON))

            every { mockAddressToCoordinatesService.convertAddressToCoordinates(any()) } returns
                cvut.fit.energyStats.domain.dto.LocationDTO(
                    48.0,
                    17.0,
                    TimeZone.of("Europe/Bratislava"),
                )

            val sites = service.getAvailableSites(apiKey)

            sites.size shouldBe 1
            sites[0].brandSiteId shouldBe 1234567
            sites[0].address shouldBe "Ľanová, 5236/4, 821 01, Bratislava, Slovakia"
            sites[0].lat shouldBe 48.0
            sites[0].lon shouldBe 17.0
            mockServer.verify()
        }

        test("importDataFromAPIByHour correctly imports data") {
            val apiKey = "test-api-key"
            val siteId = 1234567
            val dateFrom = LocalDateTime(2025, 2, 1, 0, 0)
            val dateTo = LocalDateTime(2025, 2, 2, 23, 59)

            mockServer
                .expect(
                    requestTo(
                        "/site/$siteId/energy?timeUnit=HOUR&endDate=2025-02-02&startDate=2025-02-01&api_key=$apiKey",
                    ),
                ).andRespond(withSuccess(EXAMPLE_IMPORT_DATA, MediaType.APPLICATION_JSON))

            val entries = service.importDataFromAPIByHour(siteId, apiKey, dateFrom, dateTo)

            entries[LocalDateTime(2025, 2, 1, 7, 0)] shouldBe 0.174
            entries[LocalDateTime(2025, 2, 1, 8, 0)] shouldBe 1.193
            entries[LocalDateTime(2025, 2, 2, 7, 0)] shouldBe 0.001
            mockServer.verify()
        }

        test("fetchAvailableDatesFromAPI returns correct date range") {
            val apiKey = "test-api-key"
            val siteId = 1234567
            val exampleDataPeriod =
                "{\"datePeriodList\":{\"siteEnergyList\":[{\"dataPeriod\":{\"startDate\":\"2024-02-13\",\"endDate\":\"2025-05-11\"}}]}}"

            mockServer
                .expect(requestTo("/sites/$siteId/dataPeriod?api_key=$apiKey"))
                .andRespond(withSuccess(exampleDataPeriod, MediaType.APPLICATION_JSON))

            val dates = service.fetchAvailableDatesFromAPI(siteId, apiKey)

            dates.first shouldBe LocalDate(2024, 2, 13)
            dates.second shouldBe LocalDate(2025, 5, 11)
            mockServer.verify()
        }

        test("getAvailableSites throws exception on API error") {
            val apiKey = "wrong-api-key"
            mockServer
                .expect(requestTo("/sites/list?size=5&api_key=$apiKey"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

            val exception = shouldThrow<IllegalArgumentException> { service.getAvailableSites(apiKey) }
            exception.message shouldBe
                "failed to collect available sites data: Invalid API key provided"
            mockServer.verify()
        }
    })
