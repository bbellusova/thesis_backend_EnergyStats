package controller.providerController

import cvut.fit.energyStats.config.ApiProviders
import cvut.fit.energyStats.config.FileProviders
import cvut.fit.energyStats.config.Providers
import cvut.fit.energyStats.controller.providerController.ProviderController
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.ProviderDataAccessType
import cvut.fit.energyStats.domain.dto.SiteOverviewDTO
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.energyDataService.EnergyDataImportService
import cvut.fit.energyStats.service.providerService.APIProviderService
import cvut.fit.energyStats.service.providerService.FileProviderService
import cvut.fit.energyStats.service.providerService.ProviderService
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.web.multipart.MultipartFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestProviderController :
    FunSpec({
        val mockDataAccessMethodService: DataAccessMethodService = mockk()
        val mockEnergyDataImportService: EnergyDataImportService = mockk()
        val mockFileProvider: FileProviderService = mockk()
        val mockApiProvider: APIProviderService = mockk()
        val mockProvider: ProviderService = mockk()

        val fileProviders: FileProviders = mapOf(DataProvider.ZSD to mockFileProvider)
        val apiProviders: ApiProviders = mapOf(DataProvider.SolarEdge to mockApiProvider)
        val providers: Providers =
            mapOf(
                DataProvider.ZSD to mockFileProvider,
                DataProvider.SolarEdge to mockApiProvider,
                DataProvider.DUMMY to mockProvider,
            )

        val controller =
            ProviderController(
                mockDataAccessMethodService,
                mockEnergyDataImportService,
                fileProviders,
                apiProviders,
                providers,
            )

        beforeEach { clearAllMocks() }

        test("getProviders returns all providers with correct access types") {
            every { mockFileProvider.providedCategories } returns
                mapOf(
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to
                        setOf(ProviderDataAccessType(DataAccessType.FILE)),
                )
            every { mockApiProvider.providedCategories } returns
                mapOf(
                    DataCategory.ENERGY_PRODUCTION_TOTAL to
                        setOf(ProviderDataAccessType(DataAccessType.API)),
                )
            every { mockProvider.providedCategories } returns emptyMap()

            val result = controller.getProviders()

            assertEquals(3, result.size)

            val zsd = result.find { it.code == DataProvider.ZSD.name }!!
            assertEquals(DataProvider.ZSD.fullName, zsd.name)
            assertEquals(setOf(DataAccessType.FILE), zsd.accessTypes)
            assertEquals(
                listOf(DataAccessType.FILE),
                zsd.availableCategories[DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID],
            )

            val solarEdge = result.find { it.code == DataProvider.SolarEdge.name }!!
            assertEquals(DataProvider.SolarEdge.fullName, solarEdge.name)
            assertEquals(setOf(DataAccessType.API), solarEdge.accessTypes)
            assertEquals(
                listOf(DataAccessType.API),
                solarEdge.availableCategories[DataCategory.ENERGY_PRODUCTION_TOTAL],
            )

            val dummy = result.find { it.code == DataProvider.DUMMY.name }!!
            assertEquals(DataProvider.DUMMY.fullName, dummy.name)
            assertEquals(emptySet(), dummy.accessTypes)
            assertEquals(emptyMap(), dummy.availableCategories)
        }

        test("getAvailableSites returns sites for supported API provider") {
            val apiKey = "test-key"
            val sites =
                listOf(
                    SiteOverviewDTO(
                        1,
                        "Address 1",
                        10.0,
                        20.0,
                        "UTC",
                        5.0,
                        DataProvider.SolarEdge,
                        setOf(DataCategory.ENERGY_PRODUCTION_TOTAL),
                    ),
                )
            every { mockApiProvider.getAvailableSites(apiKey) } returns sites

            val result = controller.getAvailableSites(DataProvider.SolarEdge, apiKey)

            assertEquals(1, result.size)
            assertEquals(1, result[0].brandSiteId)
            assertEquals("Address 1", result[0].address)
            verify { mockApiProvider.getAvailableSites(apiKey) }
        }

        test("getAvailableSites throws exception for unsupported provider") {
            assertFailsWith<IllegalArgumentException> {
                controller.getAvailableSites(DataProvider.ZSD, "key")
            }
        }

        test("importDataFromFile calls import service") {
            val siteId = 1
            val category = DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID
            val provider = DataProvider.ZSD
            val mockFile: MultipartFile = mockk()

            every {
                mockEnergyDataImportService.importDataFromAFile(siteId, category, provider, mockFile)
            } returns Unit

            controller.importDataFromFile(siteId, category, provider, mockFile)

            verify {
                mockEnergyDataImportService.importDataFromAFile(siteId, category, provider, mockFile)
            }
        }

        test("getProvidersWithFileDataImportForSite returns correct map") {
            val siteId = 1
            val categories = setOf(DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID)
            every { mockDataAccessMethodService.getProvidersWithFileDataImportForSite(siteId) } returns
                mapOf(DataProvider.ZSD to categories)
            every { mockFileProvider.supportedFileFormats } returns listOf("csv", "xls")

            val result = controller.getProvidersWithFileDataImportForSite(siteId)

            assertEquals(1, result.size)
            assertEquals(
                listOf(DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID),
                result[DataProvider.ZSD]!!.categories,
            )
            assertEquals(listOf("csv", "xls"), result[DataProvider.ZSD]!!.supportedFileFormats)
        }
    })
