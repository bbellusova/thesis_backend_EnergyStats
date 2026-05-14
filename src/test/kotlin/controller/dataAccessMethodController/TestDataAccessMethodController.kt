package controller.dataAccessMethodController

import cvut.fit.energyStats.controller.dataAccessMethodController.DataAccessMethodController
import cvut.fit.energyStats.controller.dataAccessMethodController.DataAccessMethodReq
import cvut.fit.energyStats.controller.dataAccessMethodController.DataAccessMethodResp
import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.service.dataAccessMethodService.DataAccessMethodService
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals

class TestDataAccessMethodController :
    FunSpec({
        val mockService: DataAccessMethodService = mockk()
        val controller = DataAccessMethodController(mockService)
        withTests(
            mapOf(
                "getAccessMethod converts DataAccessMethodDTO to DataAccessMethodResp" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.FILE,
                            dataProvider = DataProvider.DUMMY,
                            null,
                            null,
                        ),
                        DataAccessMethodResp(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.FILE,
                            dataProvider = DataProvider.DUMMY,
                            null,
                        ),
                    ),
                "getAccessMethod converts DataAccessMethodDTO to DataAccessMethodResp all optional null" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                            dataAccessType = DataAccessType.UNAVAILABLE,
                            dataProvider = null,
                            null,
                            null,
                        ),
                        DataAccessMethodResp(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TO_THE_GRID,
                            dataAccessType = DataAccessType.UNAVAILABLE,
                            dataProvider = null,
                            null,
                        ),
                    ),
                "getAccessMethod converts DataAccessMethodDTO to DataAccessMethodResp none null" to
                    Pair(
                        DataAccessMethodDTO(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_CONSUMPTION_TOTAL,
                            dataAccessType = DataAccessType.API,
                            dataProvider = DataProvider.DUMMY,
                            123,
                            "key",
                        ),
                        DataAccessMethodResp(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_CONSUMPTION_TOTAL,
                            dataAccessType = DataAccessType.API,
                            dataProvider = DataProvider.DUMMY,
                            123,
                        ),
                    ),
            ),
        ) { (dto: DataAccessMethodDTO, expectedResult: DataAccessMethodResp) ->
            clearMocks(mockService)
            every { mockService.getAccessMethod(dto.siteId, dto.dataCategory) } returns dto

            val result = controller.getAccessMethod(dto.siteId, dto.dataCategory)
            assertEquals(expectedResult, result)

            verify(exactly = 1) { mockService.getAccessMethod(any(), any()) }
        }

        data class UpdateAccessMethodTest(
            val siteId: Int,
            val dataCategory: DataCategory,
            val currentConfig: DataAccessMethodDTO,
            val request: DataAccessMethodReq,
            val expectedUpdateDTO: DataAccessMethodDTO?,
            val errorMessage: String?,
        )

        withTests(
            mapOf(
                "updateAccessMethod updates successfully" to
                    UpdateAccessMethodTest(
                        0,
                        DataCategory.ENERGY_PRODUCTION_TOTAL,
                        DataAccessMethodDTO(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.UNAVAILABLE,
                            dataProvider = null,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        DataAccessMethodReq(
                            dataAccessType = DataAccessType.API,
                            dataProvider = DataProvider.DUMMY,
                            dataProviderSiteId = 123,
                            apiKey = "key",
                        ),
                        DataAccessMethodDTO(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.API,
                            dataProvider = DataProvider.DUMMY,
                            dataProviderSiteId = 123,
                            apiKey = "key",
                        ),
                        null,
                    ),
                "updateAccessMethod rejects an update of available category" to
                    UpdateAccessMethodTest(
                        0,
                        DataCategory.ENERGY_PRODUCTION_TOTAL,
                        DataAccessMethodDTO(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_PRODUCTION_TOTAL,
                            dataAccessType = DataAccessType.FILE,
                            dataProvider = DataProvider.DUMMY,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        DataAccessMethodReq(
                            dataAccessType = DataAccessType.API,
                            dataProvider = null,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        null,
                        "Updating already configured access types is not supported",
                    ),
                "updateAccessMethod rejects non-provider data access type" to
                    UpdateAccessMethodTest(
                        0,
                        DataCategory.ENERGY_CONSUMPTION_TOTAL,
                        DataAccessMethodDTO(
                            siteId = 0,
                            dataCategory = DataCategory.ENERGY_CONSUMPTION_TOTAL,
                            dataAccessType = DataAccessType.UNAVAILABLE,
                            dataProvider = null,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        DataAccessMethodReq(
                            dataAccessType = DataAccessType.UNAVAILABLE,
                            dataProvider = null,
                            dataProviderSiteId = null,
                            apiKey = null,
                        ),
                        null,
                        "Data access type must be a provider data access type",
                    ),
            ),
        ) { test: UpdateAccessMethodTest ->
            clearMocks(mockService)
            every { mockService.getAccessMethod(test.siteId, test.dataCategory) } returns
                test.currentConfig
            if (test.expectedUpdateDTO != null) {
                every { mockService.updateAccessMethod(test.expectedUpdateDTO) } returns Unit
            }

            try {
                controller.updateAccessMethod(test.siteId, test.dataCategory, test.request)
            } catch (e: IllegalArgumentException) {
                if (test.errorMessage != null) {
                    assertEquals(test.errorMessage, e.message)
                } else {
                    assert(false)
                }
            }
            verify(atLeast = 0, atMost = 1) {
                mockService.getAccessMethod(test.siteId, test.dataCategory)
            }
            if (test.expectedUpdateDTO != null) {
                verify(exactly = 1) { mockService.updateAccessMethod(test.expectedUpdateDTO) }
            } else {
                verify(exactly = 0) { mockService.updateAccessMethod(any()) }
            }
        }

        test("getAvailableCategoriesForSite propagates result from the service") {
            val expectedResults =
                listOf(
                    setOf(DataCategory.ENERGY_PRODUCTION_TOTAL, DataCategory.ENERGY_CONSUMPTION_TOTAL),
                    setOf(),
                )
            for (expectedResult in expectedResults) {
                clearMocks(mockService)
                every { mockService.getAvailableCategoriesForSite(0) } returns expectedResult
                assertEquals(expectedResult, controller.getAvailableCategoriesForSite(0))
                verify(exactly = 1) { mockService.getAvailableCategoriesForSite(0) }
            }
        }
    })
