package controller.geocodingController

import cvut.fit.energyStats.controller.geocodingController.GeocodingController
import cvut.fit.energyStats.service.addressToCoordinatesService.AddressToCoordinatesService
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.TimeZone
import kotlin.test.assertEquals

class TestGeocodingController :
    FunSpec({
        val mockAddressToCoordinatesService: AddressToCoordinatesService = mockk()
        val controller = GeocodingController(mockAddressToCoordinatesService)

        beforeEach { clearAllMocks() }

        test("getTimezoneForCoordinates returns correct timezone ID") {
            val lat = 50.0
            val lon = 14.0
            val expectedTimeZone = TimeZone.of("Europe/Prague")

            every { mockAddressToCoordinatesService.convertCoordinatesToTimezone(lat, lon) } returns
                expectedTimeZone

            val result = controller.getTimezoneForCoordinates(lat, lon)

            assertEquals("Europe/Prague", result)
        }
    })
