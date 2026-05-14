package service.providerService

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.ProviderDataAccessType
import cvut.fit.energyStats.service.providerService.APIProviderService
import cvut.fit.energyStats.service.providerService.FileProviderService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class TestProviderService :
    FunSpec({
        test("validateProvider succeeds for valid API provider") {
            val provider =
                apiProviderMock(
                    mapOf(
                        DataCategory.ENERGY_PRODUCTION_TOTAL to
                            setOf(ProviderDataAccessType(DataAccessType.API)),
                    ),
                )

            provider.validateProvider(setOf(DataCategory.ENERGY_PRODUCTION_TOTAL))
        }

        test("validateProvider succeeds for valid file provider") {
            val provider =
                fileProviderMock(
                    mapOf(
                        DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to
                            setOf(ProviderDataAccessType(DataAccessType.FILE)),
                    ),
                )

            provider.validateProvider(setOf(DataCategory.ENERGY_PRODUCTION_TO_THE_GRID))
        }

        test("validateProvider throws when category has no repository") {
            val provider =
                apiProviderMock(
                    mapOf(
                        DataCategory.ENERGY_CONSUMPTION_TOTAL to
                            setOf(ProviderDataAccessType(DataAccessType.API)),
                    ),
                )

            val error =
                shouldThrow<IllegalArgumentException> {
                    provider.validateProvider(setOf(DataCategory.ENERGY_PRODUCTION_TOTAL))
                }

            error.message shouldBe
                "Provider cannot support [ENERGY_CONSUMPTION_TOTAL], as repositories are only established for [ENERGY_PRODUCTION_TOTAL]"
        }

        test("validateCategoryAndAccessType succeeds for supported category and access type") {
            val provider =
                apiProviderMock(
                    mapOf(
                        DataCategory.ENERGY_PRODUCTION_TOTAL to
                            setOf(ProviderDataAccessType(DataAccessType.API)),
                    ),
                )

            provider.validateCategoryAndAccessType(
                DataCategory.ENERGY_PRODUCTION_TOTAL,
                DataAccessType.API,
            )
        }

        test("validateCategoryAndAccessType throws for unsupported category") {
            val provider =
                apiProviderMock(
                    mapOf(
                        DataCategory.ENERGY_PRODUCTION_TOTAL to
                            setOf(ProviderDataAccessType(DataAccessType.API)),
                    ),
                )

            val error =
                shouldThrow<IllegalArgumentException> {
                    provider.validateCategoryAndAccessType(
                        DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID,
                        DataAccessType.API,
                    )
                }

            error.message shouldBe
                "Category ENERGY_CONSUMPTION_FROM_THE_GRID not supported by provider ${DataProvider.DUMMY.fullName}"
        }

        test("validateCategoryAndAccessType throws for unsupported access type") {
            val provider =
                apiProviderMock(
                    mapOf(
                        DataCategory.ENERGY_PRODUCTION_TOTAL to
                            setOf(ProviderDataAccessType(DataAccessType.API)),
                    ),
                )

            val error =
                shouldThrow<IllegalArgumentException> {
                    provider.validateCategoryAndAccessType(
                        DataCategory.ENERGY_PRODUCTION_TOTAL,
                        DataAccessType.FILE,
                    )
                }

            error.message shouldBe
                "Provider ${DataProvider.DUMMY.fullName} does not support importing ENERGY_PRODUCTION_TOTAL from FILE"
        }
    })

private fun apiProviderMock(categories: Map<DataCategory, Set<ProviderDataAccessType>>): APIProviderService =
    mockk<APIProviderService> {
        every { provider } returns DataProvider.DUMMY
        every { providedCategories } returns categories
        every { validateProvider(any()) } answers { callOriginal() }
        every { validateCategoryAndAccessType(any(), any()) } answers { callOriginal() }
    }

private fun fileProviderMock(categories: Map<DataCategory, Set<ProviderDataAccessType>>): FileProviderService =
    mockk<FileProviderService> {
        every { provider } returns DataProvider.DUMMY
        every { providedCategories } returns categories
        every { validateProvider(any()) } answers { callOriginal() }
        every { validateCategoryAndAccessType(any(), any()) } answers { callOriginal() }
    }
