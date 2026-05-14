package cvut.fit.energyStats.service.providerService

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.ProviderDataAccessType

sealed interface ProviderService {
    val provider: DataProvider
    val providedCategories: Map<DataCategory, Set<ProviderDataAccessType>>

    fun validateProvider(categoriesWithRepository: Set<DataCategory>) {
        require(categoriesWithRepository.containsAll(providedCategories.keys)) {
            "Provider cannot support ${providedCategories.keys}, as repositories are only established for $categoriesWithRepository"
        }
        providedCategories.values.flatten().toSet().forEach { accessType ->
            when (accessType.dataAccess) {
                DataAccessType.API -> {
                    require(this is APIProviderService) {
                        "Provider claims to support the API access type but doesn't implement APIProviderService"
                    }
                }

                DataAccessType.FILE -> {
                    require(this is FileProviderService) {
                        "Provider claims to support the FILE access type but doesn't implement FileProviderService"
                    }
                }

                DataAccessType.CALCULATED -> {
                    require(false)
                }

                DataAccessType.UNAVAILABLE -> {
                    require(false)
                }
            }
        }
    }

    fun validateCategoryAndAccessType(
        category: DataCategory,
        accessType: DataAccessType,
    ) {
        require(category in providedCategories.keys) {
            "Category $category not supported by provider ${provider.fullName}"
        }
        require(providedCategories[category]?.contains(ProviderDataAccessType(accessType)) == true) {
            "Provider ${provider.fullName} does not support importing $category from $accessType"
        }
    }
}
