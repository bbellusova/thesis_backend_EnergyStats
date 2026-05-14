package cvut.fit.energyStats.service.energyDataService

import cvut.fit.energyStats.config.ApiProviders
import cvut.fit.energyStats.config.CategoryRepositories
import cvut.fit.energyStats.config.FileProviders
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.domain.dto.Entries
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.siteService.SiteService
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import kotlin.collections.get
import kotlin.collections.set
import kotlin.time.Clock

@Service
class EnergyDataImportService(
    private val categoryRepositories: CategoryRepositories,
    private val dataAccessMethodService: DataAccessMethodService,
    private val apiDataProviders: ApiProviders,
    private val fileDataProviders: FileProviders,
    private val siteService: SiteService,
    private val clock: Clock = Clock.System,
) {
    private val availableDatesCache =
        mutableMapOf<Pair<Int, DataCategory>, Pair<LocalDate, LocalDate>>()

    private fun storeToDb(
        siteId: Int,
        entries: Entries,
        category: DataCategory,
    ) {
        val haveToBeBefore = siteService.getLastFinishedHourAtSite(siteId)
        val persistEntries = entries.filter { it.key < haveToBeBefore }.toList()
        categoryRepositories[category]?.storeToDb(siteId, persistEntries)
    }

    fun importDataFromAFile(
        siteId: Int,
        category: DataCategory,
        provider: DataProvider,
        file: MultipartFile,
    ) {
        val dataAccessMethod = dataAccessMethodService.getAccessMethod(siteId, category)
        if (dataAccessMethod.dataAccessType != DataAccessType.FILE) {
            throw IllegalArgumentException(
                "Data category $category does not accept data from file for this site",
            )
        }
        if (dataAccessMethod.dataProvider != provider) {
            throw IllegalArgumentException(
                "Data provider $provider is not set up for this site and category $category",
            )
        }
        val providerService =
            fileDataProviders[provider]
                ?: throw IllegalArgumentException("Provider $provider not supported")
        providerService.validateFile(file)
        val entries = providerService.importDataFromAFile(file)
        storeToDb(siteId, entries, category)
    }

    fun importDataFromAPI(
        siteId: Int,
        category: DataCategory,
        dateFrom: LocalDateTime,
        dateTo: LocalDateTime,
    ) {
        val dataAccessMethodDTO = dataAccessMethodService.getAccessMethod(siteId, category)
        if (dataAccessMethodDTO.dataAccessType == DataAccessType.API) {
            val apiService =
                apiDataProviders[dataAccessMethodDTO.dataProvider]
                    ?: throw IllegalStateException(
                        "${dataAccessMethodDTO.dataProvider} API not registered",
                    )
            val key =
                dataAccessMethodDTO.apiKey
                    ?: throw IllegalStateException("API key not set for $siteId and $category")
            val dataProviderSiteId =
                dataAccessMethodDTO.dataProviderSiteId
                    ?: throw IllegalStateException("No provider id defined for $siteId and $category")
            val entries =
                apiService.importDataFromAPIByHour(
                    dataProviderSiteId,
                    key,
                    dateFrom,
                    dateTo,
                )
            storeToDb(siteId, entries, category)
        } else {
            throw IllegalStateException(
                "$category cannot be imported by API for provider ${dataAccessMethodDTO.dataProvider}",
            )
        }
    }

    fun fetchAvailableDatesFromAPI(
        siteId: Int,
        category: DataCategory,
        dataAccessMethodDTO: DataAccessMethodDTO,
    ): Pair<LocalDate, LocalDate> {
        var dateRange = availableDatesCache[Pair(siteId, category)]
        val today = clock.now().toLocalDateTime(siteService.getSiteById(siteId).timezone)
        if (dateRange == null || dateRange.second < today.date) {
            val apiService =
                apiDataProviders[dataAccessMethodDTO.dataProvider]
                    ?: throw IllegalStateException("Data access method not found")
            val key =
                dataAccessMethodDTO.apiKey
                    ?: throw IllegalStateException("API key not set for $siteId and $category")
            val dataProviderSiteId =
                dataAccessMethodDTO.dataProviderSiteId
                    ?: throw IllegalStateException("No provider id defined for $siteId and $category")
            dateRange = apiService.fetchAvailableDatesFromAPI(dataProviderSiteId, key)
            availableDatesCache[Pair(siteId, category)] = dateRange
        }
        return dateRange
    }
}
