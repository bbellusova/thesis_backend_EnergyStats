package cvut.fit.energyStats.service.providerService

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.ProviderDataAccessType
import cvut.fit.energyStats.domain.dto.Entries
import cvut.fit.energyStats.domain.dto.SiteOverviewDTO
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DummyProviderService :
    APIProviderService,
    FileProviderService {
    override val provider = DataProvider.DUMMY
    override val providedCategories =
        mapOf(
            DataCategory.ENERGY_PRODUCTION_TOTAL to
                setOf(
                    ProviderDataAccessType(DataAccessType.API),
                    ProviderDataAccessType(DataAccessType.FILE),
                ),
            DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to
                setOf(
                    ProviderDataAccessType(DataAccessType.API),
                    ProviderDataAccessType(DataAccessType.FILE),
                ),
            DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to
                setOf(
                    ProviderDataAccessType(DataAccessType.API),
                    ProviderDataAccessType(DataAccessType.FILE),
                ),
        )
    override val supportedFileFormats = listOf("dummy")

    override fun importDataFromAFile(file: MultipartFile): Entries = mutableMapOf()

    override fun getAvailableSites(apiKey: String): List<SiteOverviewDTO> =
        listOf(
            SiteOverviewDTO(
                1,
                "nowhere",
                90.0,
                180.0,
                null,
                null,
                DataProvider.DUMMY,
                setOf(
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                ),
            ),
            SiteOverviewDTO(
                2,
                "everywhere",
                0.0,
                0.0,
                null,
                null,
                DataProvider.DUMMY,
                setOf(
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                ),
            ),
        )

    override fun importDataFromAPIByHour(
        providerSiteId: Int,
        apiKey: String,
        dateFrom: LocalDateTime,
        dateTo: LocalDateTime,
    ): Entries = mutableMapOf()

    override fun fetchAvailableDatesFromAPI(
        providerSiteId: Int,
        apiKey: String,
    ): Pair<LocalDate, LocalDate> = Pair(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1))
}
