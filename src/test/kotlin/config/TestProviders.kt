package config

import cvut.fit.energyStats.config.ApiProvidersConfig
import cvut.fit.energyStats.config.CategoryRepositories
import cvut.fit.energyStats.config.FileProvidersConfig
import cvut.fit.energyStats.config.ProvidersConfig
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.repository.energyDataRepository.EnergyDataRepository
import cvut.fit.energyStats.service.addressToCoordinatesService.AddressToCoordinatesService
import cvut.fit.energyStats.service.providerService.DummyProviderService
import cvut.fit.energyStats.service.providerService.SolarEdgeDataImportService
import cvut.fit.energyStats.service.providerService.ZSDDataImportService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.web.client.RestClient

class TestProviders :
    FunSpec({
        val mockAddressToCoordinatesService: AddressToCoordinatesService = mockk()
        val mockRestClient: RestClient = mockk()
        val solarEdgeService =
            SolarEdgeDataImportService(mockAddressToCoordinatesService, mockRestClient)
        val zsdService = ZSDDataImportService()
        val dummyService = DummyProviderService()

        val categoryRepositories: CategoryRepositories =
            mapOf(
                DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to mockk<EnergyDataRepository>(),
                DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to mockk<EnergyDataRepository>(),
                DataCategory.ENERGY_PRODUCTION_TOTAL to mockk<EnergyDataRepository>(),
                DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED to null,
                DataCategory.ENERGY_CONSUMPTION_TOTAL to null,
            )

        test("providers contains all providers inheriting ProviderService") {
            val providersConfig = ProvidersConfig(categoryRepositories)

            val providers =
                providersConfig.providers(listOf(solarEdgeService, zsdService, dummyService))

            providers.keys shouldContainExactlyInAnyOrder DataProvider.entries
            providers[DataProvider.SolarEdge] shouldBe solarEdgeService
            providers[DataProvider.ZSD] shouldBe zsdService
            providers[DataProvider.DUMMY] shouldBe dummyService
        }

        test("providers throws when some DataProvider implementation is missing") {
            val config = ProvidersConfig(categoryRepositories)

            val ex =
                shouldThrow<IllegalArgumentException> {
                    config.providers(listOf(solarEdgeService, dummyService))
                }

            ex.message shouldBe
                "Create an inheritor of ProviderService interface for a new provider ZSD"
        }

        test("fileProviders contains all providers with FileProviderService") {
            val config = FileProvidersConfig()

            val fileProviders = config.fileProviders(listOf(zsdService, dummyService))

            fileProviders.keys shouldContainExactlyInAnyOrder
                listOf(DataProvider.ZSD, DataProvider.DUMMY)
            fileProviders[DataProvider.ZSD] shouldBe zsdService
            fileProviders[DataProvider.DUMMY] shouldBe dummyService
        }

        test("apiProviders contains all providers with APIProviderService") {
            val config = ApiProvidersConfig()

            val apiProviders = config.apiProviders(listOf(solarEdgeService, dummyService))

            apiProviders.keys shouldContainExactlyInAnyOrder
                listOf(DataProvider.SolarEdge, DataProvider.DUMMY)
            apiProviders[DataProvider.SolarEdge] shouldBe solarEdgeService
            apiProviders[DataProvider.DUMMY] shouldBe dummyService
        }
    })
