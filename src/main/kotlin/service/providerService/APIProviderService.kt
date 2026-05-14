package cvut.fit.energyStats.service.providerService

import cvut.fit.energyStats.domain.dto.Entries
import cvut.fit.energyStats.domain.dto.SiteOverviewDTO
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

sealed interface APIProviderService : ProviderService {
    fun getAvailableSites(apiKey: String): List<SiteOverviewDTO>

    fun importDataFromAPIByHour(
        providerSiteId: Int,
        apiKey: String,
        dateFrom: LocalDateTime,
        dateTo: LocalDateTime,
    ): Entries

    fun fetchAvailableDatesFromAPI(
        providerSiteId: Int,
        apiKey: String,
    ): Pair<LocalDate, LocalDate>
}

inline fun <reified T : Any> fetchData(
    url: String,
    client: RestClient,
): T? =
    client
        .get()
        .uri(url)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus({ it.value() == 401 }) { _, _ ->
            throw IllegalArgumentException("Invalid API key provided")
        }.onStatus({ !it.is2xxSuccessful }) { _, response ->
            throw IllegalArgumentException(response.statusText)
        }.body<T>()
