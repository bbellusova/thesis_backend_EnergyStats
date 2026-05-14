package service.dataAccessMethodService

import cvut.fit.energyStats.config.CategoryConfig
import cvut.fit.energyStats.config.DatabaseConfiguration
import cvut.fit.energyStats.config.Providers
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.domain.dto.SiteDTO
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethod
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethodRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyConsumptionFromTheGridRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionToTheGridRepository
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionTotalRepository
import cvut.fit.energyStats.repository.siteRepository.Site
import cvut.fit.energyStats.repository.siteRepository.SiteRepository
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.providerService.DummyProviderService
import kotlinx.datetime.TimeZone
import org.jasypt.encryption.StringEncryptor
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import service.unavailableDataAccessMethodDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest(classes = [TestDataAccessMethodIntegration.TestApp::class])
@TestPropertySource(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:dataaccess-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
        ],
)
class TestDataAccessMethodIntegration {
    @Autowired private lateinit var service: DataAccessMethodService

    @Autowired private lateinit var repository: DataAccessMethodRepository

    @Autowired private lateinit var siteRepository: SiteRepository

    @Autowired private lateinit var stringEncryptor: StringEncryptor

    private var siteId: Int = 0

    private val orderedCategories =
        listOf(
            DataCategory.ENERGY_PRODUCTION_TOTAL,
            DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
            DataCategory.ENERGY_CONSUMPTION_TOTAL,
            DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
            DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
        )

    @BeforeEach
    fun setUp() {
        transaction {
            DataAccessMethod.deleteAll()
            Site.deleteAll()
        }

        siteId =
            siteRepository.createSite(
                SiteDTO(
                    siteId = 0,
                    siteName = "testSite",
                    password = "secret",
                    timezone = TimeZone.of("UTC"),
                    lat = 50.0,
                    lon = 14.0,
                    azm = 180.0,
                    kwp = 5.0,
                    dec = 35.0,
                ),
            )
    }

    @Test
    fun `calculated categories are evaluated automatically`() {
        val accessMethodsToSet =
            mapOf(
                DataCategory.ENERGY_PRODUCTION_TOTAL to
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_PRODUCTION_TOTAL,
                        DataAccessType.API,
                        DataProvider.DUMMY,
                        2,
                        "key",
                    ),
                DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                        DataAccessType.FILE,
                        DataProvider.DUMMY,
                        null,
                        null,
                    ),
                DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                        DataAccessType.FILE,
                        DataProvider.DUMMY,
                        null,
                        null,
                    ),
            )

        accessMethodsToSet.forEach { (category, accessMethodDTO) ->
            service.updateAccessMethod(accessMethodDTO)
            assertEquals(accessMethodDTO, service.getAccessMethod(siteId, category))
        }

        assertEquals(
            listOf(
                DataAccessType.API,
                DataAccessType.FILE,
                DataAccessType.CALCULATED,
                DataAccessType.FILE,
                DataAccessType.CALCULATED,
            ),
            orderedCategories.map { repository.getAccessMethod(siteId, it).dataAccessType },
        )
    }

    @Test
    fun `setting third calculated category fails`() {
        val accessMethodsToSet =
            mapOf(
                DataCategory.ENERGY_PRODUCTION_TOTAL to
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_PRODUCTION_TOTAL,
                        DataAccessType.API,
                        DataProvider.DUMMY,
                        2,
                        "key",
                    ),
                DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                        DataAccessType.FILE,
                        DataProvider.DUMMY,
                        null,
                        null,
                    ),
                DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                        DataAccessType.FILE,
                        DataProvider.DUMMY,
                        null,
                        null,
                    ),
            )

        accessMethodsToSet.forEach { (category, accessMethodDTO) ->
            service.updateAccessMethod(accessMethodDTO)
            assertEquals(accessMethodDTO, service.getAccessMethod(siteId, category))
        }

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                service.updateAccessMethod(
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                        DataAccessType.CALCULATED,
                        null,
                        null,
                        null,
                    ),
                )
            }

        assertEquals(
            "failed to update access method: there cannot be more than two calculated categories",
            error.message,
        )
    }

    @Test
    fun `dependent calculated categories are also set to unavailable upon setting unavailable category`() {
        val accessMethodsToSet =
            listOf(
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.API,
                    DataProvider.DUMMY,
                    2,
                    "key",
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataAccessType.FILE,
                    DataProvider.DUMMY,
                    null,
                    null,
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataAccessType.FILE,
                    DataProvider.DUMMY,
                    null,
                    null,
                ),
            )
        val expectedResultBeforeUnavailable =
            listOf(
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.API,
                    DataProvider.DUMMY,
                    2,
                    "key",
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataAccessType.FILE,
                    DataProvider.DUMMY,
                    null,
                    null,
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataAccessType.FILE,
                    DataProvider.DUMMY,
                    null,
                    null,
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataAccessType.CALCULATED,
                    null,
                    null,
                    null,
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                    DataAccessType.CALCULATED,
                    null,
                    null,
                    null,
                ),
            )
        val expectedResultAfterUnavailable =
            listOf(
                unavailableDataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataAccessType.FILE,
                    DataProvider.DUMMY,
                    null,
                    null,
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataAccessType.FILE,
                    DataProvider.DUMMY,
                    null,
                    null,
                ),
                unavailableDataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                ),
                unavailableDataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                ),
            )

        accessMethodsToSet.forEach { service.updateAccessMethod(it) }
        expectedResultBeforeUnavailable.forEach {
            assertEquals(it, service.getAccessMethod(it.siteId, it.dataCategory))
        }
        service.updateAccessMethod(expectedResultAfterUnavailable.first())
        expectedResultAfterUnavailable.forEach {
            assertEquals(it, service.getAccessMethod(it.siteId, it.dataCategory))
        }
    }

    @Test
    fun `fields not valid for a given access types are removed on update`() {
        val accessMethodsToSet =
            listOf(
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.API,
                    DataProvider.DUMMY,
                    1,
                    "key",
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.CALCULATED,
                    DataProvider.DUMMY,
                    2,
                    "key",
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.API,
                    DataProvider.DUMMY,
                    3,
                    "key",
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.FILE,
                    DataProvider.DUMMY,
                    4,
                    "key",
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.UNAVAILABLE,
                    DataProvider.DUMMY,
                    5,
                    "key",
                ),
            )

        val expectedAccessMethods =
            listOf(
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.API,
                    DataProvider.DUMMY,
                    1,
                    "key",
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.CALCULATED,
                    null,
                    null,
                    null,
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.API,
                    DataProvider.DUMMY,
                    3,
                    "key",
                ),
                DataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataAccessType.FILE,
                    DataProvider.DUMMY,
                    null,
                    null,
                ),
                unavailableDataAccessMethodDTO(
                    siteId,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                ),
            )

        for (i in (0 until accessMethodsToSet.size - 1)) {
            val category = accessMethodsToSet[i].dataCategory
            service.updateAccessMethod(accessMethodsToSet[i])
            assertEquals(expectedAccessMethods[i], service.getAccessMethod(siteId, category))
        }
    }

    @Test
    fun `conflicting calculated categories are not set`() {
        val conflictingAccessMethods =
            listOf(
                Pair(
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_PRODUCTION_TOTAL,
                        DataAccessType.CALCULATED,
                        null,
                        null,
                        null,
                    ),
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                        DataAccessType.CALCULATED,
                        null,
                        null,
                        null,
                    ),
                ),
                Pair(
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                        DataAccessType.CALCULATED,
                        null,
                        null,
                        null,
                    ),
                    DataAccessMethodDTO(
                        siteId,
                        DataCategory.ENERGY_CONSUMPTION_TOTAL,
                        DataAccessType.CALCULATED,
                        null,
                        null,
                        null,
                    ),
                ),
            )

        conflictingAccessMethods.forEach { (access1, access2) ->
            var dto1 = access1
            var dto2 = access2
            for (i in 0 until 2) {
                service.updateAccessMethod(dto1)
                assertEquals(dto1, service.getAccessMethod(siteId, dto1.dataCategory))
                val error =
                    assertThrows(IllegalArgumentException::class.java) { service.updateAccessMethod(dto2) }

                assertEquals(
                    "failed to update access method: ${dto2.dataCategory} cannot be calculated when ${dto1.dataCategory} is calculated",
                    error.message,
                )
                dto1 = access2
                dto2 = access1
                transaction { DataAccessMethod.deleteAll() }
            }
        }
    }

    @Test
    fun `getAccessMethod returns persisted row`() {
        val expected =
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataAccessType.API,
                DataProvider.DUMMY,
                42,
                "key",
            )

        repository.updateAccessMethod(expected)

        assertEquals(expected, repository.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL))
    }

    @Test
    fun `getAccessMethod returns unavailable when row is missing`() {
        assertEquals(
            unavailableDataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_CONSUMPTION_TOTAL,
            ),
            repository.getAccessMethod(siteId, DataCategory.ENERGY_CONSUMPTION_TOTAL),
        )
    }

    @Test
    fun `getAvailableCategoriesForSite excludes unavailable categories`() {
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataAccessType.API,
                DataProvider.DUMMY,
                1,
                "key",
            ),
        )
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                DataAccessType.FILE,
                DataProvider.DUMMY,
                null,
                null,
            ),
        )
        repository.updateAccessMethod(
            unavailableDataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_CONSUMPTION_TOTAL,
            ),
        )

        assertEquals(
            setOf(
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
            ),
            repository.getAvailableCategoriesForSite(siteId).toSet(),
        )
        assertEquals(
            setOf(
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
            ),
            service.getAvailableCategoriesForSite(siteId).toSet(),
        )
    }

    @Test
    fun `getProvidersWithFileDataImport groups file categories by provider`() {
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataAccessType.FILE,
                DataProvider.DUMMY,
                null,
                null,
            ),
        )
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                DataAccessType.FILE,
                DataProvider.DUMMY,
                null,
                null,
            ),
        )
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                DataAccessType.API,
                DataProvider.DUMMY,
                7,
                "key",
            ),
        )
        repository.updateAccessMethod(
            unavailableDataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_CONSUMPTION_TOTAL,
            ),
        )

        assertEquals(
            mapOf(
                DataProvider.DUMMY to
                    setOf(
                        DataCategory.ENERGY_PRODUCTION_TOTAL,
                        DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    ),
            ),
            repository.getProvidersWithFileDataImport(siteId),
        )
    }

    @Test
    fun `getProvidersWithFileDataImportForSite throws on undefined provider`() {
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataAccessType.FILE,
                null,
                null,
                null,
            ),
        )

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                service.getProvidersWithFileDataImportForSite(siteId)
            }

        assertEquals(
            "Site $siteId and category Total energy production have file access but undefined provider",
            error.message,
        )
    }

    @Test
    fun `getCalculatedCategories returns only calculated categories`() {
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                DataAccessType.CALCULATED,
                null,
                null,
                null,
            ),
        )
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_CONSUMPTION_TOTAL,
                DataAccessType.CALCULATED,
                null,
                null,
                null,
            ),
        )
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataAccessType.FILE,
                DataProvider.DUMMY,
                null,
                null,
            ),
        )
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                DataAccessType.UNAVAILABLE,
                null,
                null,
                null,
            ),
        )
        repository.updateAccessMethod(
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                DataAccessType.API,
                DataProvider.DUMMY,
                1,
                "key",
            ),
        )

        assertEquals(
            setOf(
                DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                DataCategory.ENERGY_CONSUMPTION_TOTAL,
            ),
            repository.getCalculatedCategories(siteId),
        )
    }

    @Test
    fun `apiKey is stored encrypted and obtained decrypted`() {
        val dto =
            DataAccessMethodDTO(
                siteId,
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataAccessType.API,
                DataProvider.DUMMY,
                9,
                "key",
            )
        val expectedEncryptedKey = stringEncryptor.encrypt(dto.apiKey)
        service.updateAccessMethod(dto)
        assertEquals(dto, service.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL))
        assertEquals(
            dto.copy(apiKey = null),
            repository
                .getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL)
                .copy(apiKey = null),
        )
        assertNotEquals(
            expectedEncryptedKey,
            repository.getAccessMethod(siteId, DataCategory.ENERGY_PRODUCTION_TOTAL).apiKey,
        )
    }

    @EnableAutoConfiguration
    @Import(
        DatabaseConfiguration::class,
        CategoryConfig::class,
        DataAccessMethodService::class,
        DataAccessMethodRepository::class,
        SiteRepository::class,
        EnergyProductionTotalRepository::class,
        EnergyProductionToTheGridRepository::class,
        EnergyConsumptionFromTheGridRepository::class,
        TestBeans::class,
    )
    class TestApp

    class TestBeans {
        @Bean fun providers(): Providers = mapOf(DataProvider.DUMMY to DummyProviderService())
    }
}
