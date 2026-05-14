package cvut.fit.energyStats.service.providerService

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.ProviderDataAccessType
import cvut.fit.energyStats.domain.dto.AddressDTO
import cvut.fit.energyStats.domain.dto.Entries
import cvut.fit.energyStats.domain.dto.SiteOverviewDTO
import cvut.fit.energyStats.service.addressToCoordinatesService.AddressToCoordinatesService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plusMonth
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class SolarEdgeDataImportService(
    private val addressToCoordinatesService: AddressToCoordinatesService,
    @param:Qualifier("solarEdgeAPIClient") private val apiClient: RestClient,
) : APIProviderService {
    override val provider = DataProvider.SolarEdge
    override val providedCategories =
        mapOf(
            DataCategory.ENERGY_PRODUCTION_TOTAL to setOf(ProviderDataAccessType(DataAccessType.API)),
        )

    // Site List
    override fun getAvailableSites(apiKey: String): List<SiteOverviewDTO> {
        val result = mutableListOf<SiteOverviewDTO>()
        try {
            val data =
                fetchData<SiteListResponse>("/sites/list?size=5&api_key=$apiKey", apiClient)
                    ?: throw IllegalArgumentException("failed to collect sites")
            for (site in data.sites.site) {
                val address =
                    AddressDTO(
                        site.location.country,
                        site.location.countryCode,
                        site.location.city,
                        site.location.address,
                        site.location.zip,
                    )
                val location = addressToCoordinatesService.convertAddressToCoordinates(address)
                result.add(
                    SiteOverviewDTO(
                        site.id,
                        address.street +
                            ", " +
                            address.zipCode +
                            ", " +
                            address.city +
                            ", " +
                            address.country,
                        location.lat,
                        location.lon,
                        location.timezone?.id,
                        if (site.peakPower > 0.0) site.peakPower else null,
                        DataProvider.SolarEdge,
                        setOf(DataCategory.ENERGY_PRODUCTION_TOTAL),
                    ),
                )
            }
            return result
        } catch (e: Exception) {
            throw IllegalArgumentException("failed to collect available sites data: ${e.message}")
        }
    }

    // endpoint siteEnergy
    override fun importDataFromAPIByHour(
        providerSiteId: Int,
        apiKey: String,
        dateFrom: LocalDateTime,
        dateTo: LocalDateTime,
    ): Entries = importDataByMonths(providerSiteId, apiKey, dateFrom.date, dateTo.date)

    private fun importDataByMonths(
        providerSiteId: Int,
        apiKey: String,
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ): Entries {
        val entries: Entries = mutableMapOf()
        val startMonth = YearMonth(dateFrom.year, dateFrom.month)
        val endMonth = YearMonth(dateFrom.year, dateTo.month)
        var currentMonth = startMonth

        while (currentMonth <= endMonth) {
            val firstDay = if (currentMonth == startMonth) dateFrom else currentMonth.firstDay
            val lastDay = if (currentMonth == endMonth) dateTo else currentMonth.lastDay

            try {
                val from = LocalDate.Formats.ISO.format(firstDay)
                val to = LocalDate.Formats.ISO.format(lastDay) // should be yyyy-mm-dd
                val data: SiteEnergyResponse =
                    fetchData<SiteEnergyResponse>(
                        "/site/$providerSiteId/energy?timeUnit=HOUR&endDate=$to&startDate=$from&api_key=$apiKey",
                        apiClient,
                    ) ?: throw IllegalArgumentException("failed to collect data")
                val tokWh = if (data.energy.unit == "Wh") 1000.0 else 1.0
                for (entry in data.energy.values) {
                    val timestamp = LocalDateTime.parse(entry.date.replace(' ', 'T'))
                    entries[timestamp] = (entry.value ?: 0.0) / tokWh
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("failed to import data for site: ${e.message}")
            }

            // Move to the next month
            currentMonth = currentMonth.plusMonth()
        }
        return entries
    }

    // Site Data: Start and End Dates
    override fun fetchAvailableDatesFromAPI(
        providerSiteId: Int,
        apiKey: String,
    ): Pair<LocalDate, LocalDate> {
        try {
            val data =
                fetchData<SiteDataResponse>(
                    "/sites/$providerSiteId/dataPeriod?api_key=$apiKey",
                    apiClient,
                ) ?: throw IllegalArgumentException("failed to collect available dates")
            val dates =
                data.datePeriodList.siteEnergyList
                    .first()
                    .dataPeriod
            val from = LocalDate.parse(dates.startDate.replace(' ', 'T'))
            val to = LocalDate.parse(dates.endDate.replace(' ', 'T'))
            return Pair(from, to)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("failed to get available dates for site: ${e.message}")
        }
    }

    private data class SiteListResponse(
        val sites: SiteListResponseStruct,
    )

    private data class SiteListResponseStruct(
        val count: Int,
        val site: List<SiteDetails>,
    )

    private data class SiteDetails(
        val id: Int,
        val peakPower: Double,
        val location: SiteLocation,
    )

    private data class SiteLocation(
        val country: String,
        val city: String,
        val address: String,
        val zip: String,
        val countryCode: String,
    )

    private data class SiteEnergyResponse(
        val energy: SiteEnergyResponseStruct,
    )

    private data class SiteEnergyResponseStruct(
        val timeUnit: String,
        val unit: String,
        val values: List<SiteEnergyEntry>,
    )

    private data class SiteEnergyEntry(
        val date: String,
        val value: Double?,
    )

    private data class SiteDataResponse(
        val datePeriodList: DatePeriodList,
    )

    private data class DatePeriodList(
        val siteEnergyList: List<DatePeriodStruct>,
    )

    private data class DatePeriodStruct(
        val dataPeriod: DataPeriod,
    )

    private data class DataPeriod(
        val startDate: String,
        val endDate: String,
    )
}
