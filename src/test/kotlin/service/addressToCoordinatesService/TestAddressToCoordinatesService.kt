package service.addressToCoordinatesService

import cvut.fit.energyStats.config.OpenCageProperties
import cvut.fit.energyStats.domain.dto.AddressDTO
import cvut.fit.energyStats.service.addressToCoordinatesService.AddressToCoordinatesService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

const val EXAMPLE_COORDINATES_TO_TIMEZONE =
    "{\"documentation\":\"https://opencagedata.com/api\",\"licenses\":[{\"name\":\"see attribution guide\",\"url\":\"https://opencagedata.com/credits\"}],\"rate\":{\"limit\":2500,\"remaining\":2498,\"reset\":1778544000},\"results\":[{\"annotations\":{\"DMS\":{\"lat\":\"16\\u00b0 0' 0.00000'' N\",\"lng\":\"26\\u00b0 0' 0.00000'' E\"},\"MGRS\":\"35QLT9300369192\",\"Maidenhead\":\"KK36aa00aa\",\"Mercator\":{\"x\":2894306.761,\"y\":1792951.697},\"OSM\":{\"edit_url\":\"https://www.openstreetmap.org/edit?relation=3774668#map=16/16.00000/26.00000\",\"note_url\":\"https://www.openstreetmap.org/note/new#map=16/16.00000/26.00000&layers=N\",\"url\":\"https://www.openstreetmap.org/?mlat=16.00000&mlon=26.00000#map=16/16.00000/26.00000\"},\"UN_M49\":{\"regions\":{\"AFRICA\":\"002\",\"NORTHERN_AFRICA\":\"015\",\"SD\":\"729\",\"WORLD\":\"001\"},\"statistical_groupings\":[\"LDC\",\"LEDC\"]},\"callingcode\":249,\"currency\":{\"alternate_symbols\":[],\"decimal_mark\":\".\",\"disambiguate_symbol\":\"SDG\",\"html_entity\":\"\",\"iso_code\":\"SDG\",\"iso_numeric\":\"938\",\"name\":\"Sudanese Pound\",\"smallest_denomination\":1,\"subunit\":\"Piastre\",\"subunit_to_unit\":100,\"symbol\":\"\\u00a3\",\"symbol_first\":1,\"thousands_separator\":\",\"},\"flag\":\"\\ud83c\\uddf8\\ud83c\\udde9\",\"geohash\":\"sdf7p8swgbjjsyp33jxb\",\"qibla\":65.38,\"roadinfo\":{\"drive_on\":\"right\",\"speed_in\":\"km/h\"},\"sun\":{\"rise\":{\"apparent\":1778471280,\"astronomical\":1778466600,\"civil\":1778469900,\"nautical\":1778468280},\"set\":{\"apparent\":1778517420,\"astronomical\":1778522100,\"civil\":1778518800,\"nautical\":1778520420}},\"timezone\":{\"name\":\"Africa/Khartoum\",\"now_in_dst\":0,\"offset_sec\":7200,\"offset_string\":\"+0200\",\"short_name\":\"CAT\"},\"what3words\":{\"words\":\"crumb.electrify.thereafter\"},\"wikidata\":\"Q688306\"},\"bounds\":{\"northeast\":{\"lat\":20.0013631,\"lng\":27.5274506},\"southwest\":{\"lat\":11.7217143,\"lng\":22.7810744}},\"components\":{\"ISO_3166-1_alpha-2\":\"SD\",\"ISO_3166-1_alpha-3\":\"SDN\",\"ISO_3166-2\":[\"SD-DN\"],\"_category\":\"place\",\"_type\":\"state\",\"continent\":\"Africa\",\"country\":\"Sudan\",\"country_code\":\"sd\",\"state\":\"North Darfur\"},\"confidence\":1,\"distance_from_q\":{\"meters\":154599},\"formatted\":\"North Darfur, Sudan\",\"geometry\":{\"lat\":16.0,\"lng\":26.0}}],\"status\":{\"code\":200,\"message\":\"OK\"},\"stay_informed\":{\"blog\":\"https://blog.opencagedata.com\",\"mastodon\":\"https://en.osm.town/@opencage\"},\"thanks\":\"For using an OpenCage API\",\"timestamp\":{\"created_http\":\"Mon, 11 May 2026 16:30:54 GMT\",\"created_unix\":1778517054},\"total_results\":1}"
const val EXAMPLE_ADRRESS_TO_COORDINATES =
    "{\"documentation\":\"https://opencagedata.com/api\",\"licenses\":[{\"name\":\"see attribution guide\",\"url\":\"https://opencagedata.com/credits\"}],\"rate\":{\"limit\":2500,\"remaining\":2496,\"reset\":1778544000},\"results\":[{\"annotations\":{\"DMS\":{\"lat\":\"48\\u00b0 5' 12.12000'' N\",\"lng\":\"17\\u00b0 16' 18.12000'' E\"},\"MGRS\":\"33UXP6916928432\",\"Maidenhead\":\"JN88pc20ot\",\"Mercator\":{\"x\":1922676.849,\"y\":6089477.663},\"NUTS\":{\"NUTS0\":{\"code\":\"SK\"},\"NUTS1\":{\"code\":\"SK0\"},\"NUTS2\":{\"code\":\"SK01\"},\"NUTS3\":{\"code\":\"SK010\"}},\"OSM\":{\"note_url\":\"https://www.openstreetmap.org/note/new#map=17/48.08670/17.27170&layers=N\",\"url\":\"https://www.openstreetmap.org/?mlat=48.08670&mlon=17.27170#map=17/48.08670/17.27170\"},\"UN_M49\":{\"regions\":{\"EASTERN_EUROPE\":\"151\",\"EUROPE\":\"150\",\"SK\":\"703\",\"WORLD\":\"001\"},\"statistical_groupings\":[\"MEDC\"]},\"callingcode\":421,\"currency\":{\"alternate_symbols\":[],\"decimal_mark\":\",\",\"html_entity\":\"\\u20ac\",\"iso_code\":\"EUR\",\"iso_numeric\":\"978\",\"name\":\"Euro\",\"smallest_denomination\":1,\"subunit\":\"Cent\",\"subunit_to_unit\":100,\"symbol\":\"\\u20ac\",\"symbol_first\":0,\"thousands_separator\":\".\"},\"flag\":\"\\ud83c\\uddf8\\ud83c\\uddf0\",\"geohash\":\"u2s391bv7f8fwwy2yxk6\",\"qibla\":137.94,\"roadinfo\":{\"drive_on\":\"right\",\"speed_in\":\"km/h\"},\"sun\":{\"rise\":{\"apparent\":1778469420,\"astronomical\":1778461020,\"civil\":1778467260,\"nautical\":1778464440},\"set\":{\"apparent\":1778523480,\"astronomical\":1778532000,\"civil\":1778525700,\"nautical\":1778528520}},\"timezone\":{\"name\":\"Europe/Bratislava\",\"now_in_dst\":1,\"offset_sec\":7200,\"offset_string\":\"+0200\",\"short_name\":\"CEST\"},\"what3words\":{\"words\":\"password.isolator.trunk\"}},\"components\":{\"ISO_3166-1_alpha-2\":\"SK\",\"ISO_3166-1_alpha-3\":\"SVK\",\"_category\":\"postcode\",\"_type\":\"postcode\",\"continent\":\"Europe\",\"country\":\"Slovakia\",\"country_code\":\"sk\",\"political_union\":\"European Union\",\"postcode\":\"821 01\"},\"confidence\":7,\"formatted\":\"821 01, Slovakia\",\"geometry\":{\"lat\":48.0867,\"lng\":17.2717}}],\"status\":{\"code\":200,\"message\":\"OK\"},\"stay_informed\":{\"blog\":\"https://blog.opencagedata.com\",\"mastodon\":\"https://en.osm.town/@opencage\"},\"thanks\":\"For using an OpenCage API\",\"timestamp\":{\"created_http\":\"Mon, 11 May 2026 16:31:58 GMT\",\"created_unix\":1778517118},\"total_results\":1}"

class TestAddressToCoordinatesService :
    FunSpec({
        val properties = OpenCageProperties(apikey = "test-api-key")
        lateinit var mockServer: MockRestServiceServer
        lateinit var service: AddressToCoordinatesService

        beforeEach {
            val restClientBuilder = RestClient.builder()
            mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
            service = AddressToCoordinatesService(properties, restClientBuilder.build())
        }

        test("convertAddressToCoordinates returns location with timezone") {
            val address = AddressDTO("Slovakia", "SK", "Bratislava", "street", "821 01")

            mockServer
                .expect(
                    requestTo(
                        "/json?q=street,%20821%2001,%20Bratislava,%20Slovakia&countrycode=SK&key=test-api-key&abbrv=1&limit=1",
                    ),
                ).andRespond(withSuccess(EXAMPLE_ADRRESS_TO_COORDINATES, MediaType.APPLICATION_JSON))

            val result = service.convertAddressToCoordinates(address)

            result.lat shouldBe 48.0867
            result.lon shouldBe 17.2717
            result.timezone shouldBe TimeZone.of("Europe/Bratislava")
            mockServer.verify()
        }

        test("convertCoordinatesToTimezone returns timezone for coordinates") {
            val lat = 16.0
            val lon = 26.0
            mockServer
                .expect(requestTo("/json?q=16.0,26.0&key=test-api-key"))
                .andRespond(withSuccess(EXAMPLE_COORDINATES_TO_TIMEZONE, MediaType.APPLICATION_JSON))

            val timezone = service.convertCoordinatesToTimezone(lat, lon)

            timezone shouldBe TimeZone.of("Africa/Khartoum")
            mockServer.verify()
        }

        test("convertCoordinatesToTimezone throws on invalid coordinates") {
            shouldThrow<IllegalArgumentException> { service.convertCoordinatesToTimezone(91.0, 26.0) }
                .message shouldBe "invalid coordinates"
        }
    })
