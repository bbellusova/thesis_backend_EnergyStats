package service.dataAccessMethodService

import cvut.fit.energyStats.domain.CalculationExpr
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.Operator
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethodRepository
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import cvut.fit.energyStats.service.providerService.DummyProviderService
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jasypt.encryption.StringEncryptor
import service.categoriesInstance
import service.fromConsumption
import service.fromGeneration
import service.unavailableDataAccessMethodDTO

class TestDataAccessMethodService :
    FunSpec({
        val mockRepository: DataAccessMethodRepository = mockk()
        val mockEncryptor: StringEncryptor = mockk()
        val categories = categoriesInstance()
        val providers = mapOf(DataProvider.DUMMY to DummyProviderService())
        val service = DataAccessMethodService(mockRepository, providers, categories, mockEncryptor)

        fun mockRepositoryMethods(request: DataAccessMethodDTO) {
            every { mockRepository.updateAccessMethod(any()) } returns Unit
            every { mockRepository.getAccessMethod(request.siteId, request.dataCategory) } returns
                request
            every { mockRepository.getAccessMethod(any(), any()) } answers
                {
                    val siteId = firstArg<Int>()
                    val category = secondArg<DataCategory>()
                    unavailableDataAccessMethodDTO(siteId, category)
                }
            every { mockRepository.getAvailableCategoriesForSite(request.siteId) } returns setOf()
            every { mockRepository.getCalculatedCategories(request.siteId) } returns setOf()
        }

        withTests(
            mapOf(
                "success with API" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.API,
                            dataProvider = DataProvider.DUMMY,
                            dataProviderSiteId = 1,
                            apiKey = "key",
                        ),
                        null,
                    ),
                "success with FILE" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.FILE,
                            dataProvider = DataProvider.DUMMY,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        null,
                    ),
                "success with CALCULATED" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.CALCULATED,
                            dataProvider = null,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        null,
                    ),
                "success with UNAVAILABLE" to
                    Pair(
                        unavailableDataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                        ),
                        null,
                    ),
                "error when setting API without an API key" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.API,
                            dataProvider = DataProvider.DUMMY,
                            dataProviderSiteId = 1,
                            apiKey = null,
                        ),
                        "failed to update access method: the provider-specific API key must be set for API access type",
                    ),
                "error when setting file without provider" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.FILE,
                            dataProvider = null,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        "failed to update access method: data provider cannot be null for data access type FILE",
                    ),
                "error when setting API without provider site id" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.API,
                            dataProvider = DataProvider.DUMMY,
                            dataProviderSiteId = null,
                            apiKey = "key",
                        ),
                        "failed to update access method: the provider-specific site ID must be set for API access type",
                    ),
                "error when setting unknown provider" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.FILE,
                            dataProvider = DataProvider.SolarEdge,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        "failed to update access method: unknown provider",
                    ),
                "error when provider access to unsupported category" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 1,
                            dataCategory = DataCategory.ENERGY_CONSUMPTION_TOTAL,
                            dataAccessType = DataAccessType.API,
                            dataProvider = DataProvider.DUMMY,
                            dataProviderSiteId = 1,
                            apiKey = "key",
                        ),
                        "failed to update access method: ENERGY_CONSUMPTION_TOTAL does not support import from a provider",
                    ),
            ),
        ) { (request: DataAccessMethodDTO, error: String?) ->
            mockRepositoryMethods(request)
            every { mockEncryptor.encrypt(any()) } returns "encrypted"

            try {
                service.updateAccessMethod(request)
            } catch (e: Exception) {
                if (error != null) {
                    e.message shouldBe error
                } else {
                    fail("should have been updated successfully, but got ${e.message}")
                }
            }
        }

        test(
            "getCalculationExpr returns fromGeneration for immediately consumed when ENERGY_CONSUMPTION_TOTAL is calculated",
        ) {
            every { mockRepository.getCalculatedCategories(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )
            every { mockRepository.getAvailableCategoriesForSite(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )

            service.getCalculationExpr(1, DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED) shouldBe
                fromGeneration
        }

        test(
            "getCalculationExpr returns fromGeneration for immediately consumed when ENERGY_CONSUMPTION_FROM_THE_GRID is calculated",
        ) {
            every { mockRepository.getCalculatedCategories(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )
            every { mockRepository.getAvailableCategoriesForSite(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )

            service.getCalculationExpr(1, DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED) shouldBe
                fromGeneration
        }

        test(
            "getCalculationExpr returns fromConsumption for immediately consumed when ENERGY_PRODUCTION_TO_THE_GRID is not calculated",
        ) {
            every { mockRepository.getCalculatedCategories(1) } returns
                setOf(
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )
            every { mockRepository.getAvailableCategoriesForSite(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )

            service.getCalculationExpr(1, DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED) shouldBe
                fromConsumption
        }
        test(
            "getCalculationExpr returns fromConsumption for immediately consumed when ENERGY_PRODUCTION_TOTAL is calculated",
        ) {
            every { mockRepository.getCalculatedCategories(1) } returns
                setOf(
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )
            every { mockRepository.getAvailableCategoriesForSite(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )

            service.getCalculationExpr(1, DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED) shouldBe
                fromConsumption
        }

        test("getCalculationExpr returns configured expression for non-special category") {
            every { mockRepository.getCalculatedCategories(1) } returns
                setOf(DataCategory.ENERGY_PRODUCTION_TOTAL, DataCategory.ENERGY_CONSUMPTION_TOTAL)
            every { mockRepository.getAvailableCategoriesForSite(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )

            service.getCalculationExpr(1, DataCategory.ENERGY_PRODUCTION_TOTAL) shouldBe
                CalculationExpr(
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                    Operator.PLUS,
                )
        }
        test("getCalculationExpr throws exception for non-calculated category") {
            every { mockRepository.getCalculatedCategories(1) } returns
                setOf(DataCategory.ENERGY_CONSUMPTION_TOTAL)
            every { mockRepository.getAvailableCategoriesForSite(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                    DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )

            val error =
                shouldThrow<IllegalArgumentException> {
                    service.getCalculationExpr(1, DataCategory.ENERGY_PRODUCTION_TOTAL)
                }

            error.message shouldBe
                "${DataCategory.ENERGY_PRODUCTION_TOTAL.description} is not set to calculated"
        }
        test(
            "getCalculationExpr throws exception for immediately consumed when it cannot be calculated",
        ) {
            every { mockRepository.getCalculatedCategories(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )

            every { mockRepository.getAvailableCategoriesForSite(1) } returns
                setOf(
                    DataCategory.ENERGY_CONSUMPTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_TOTAL,
                    DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED,
                )

            val error =
                shouldThrow<IllegalArgumentException> {
                    service.getCalculationExpr(1, DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED)
                }

            error.message shouldBe
                "${DataCategory.ENERGY_PRODUCTION_IMMEDIATELY_CONSUMED.description} cannot be calculated"
        }
    })
