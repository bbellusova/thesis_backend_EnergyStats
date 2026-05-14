package cvut.fit.energyStats.config

import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.service.providerService.ProviderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

typealias Providers = Map<DataProvider, ProviderService>

@Configuration
class ProvidersConfig(
    val categoryRepositories: CategoryRepositories,
) {
    @Bean
    fun providers(availableBeans: List<ProviderService>): Providers {
        val providers = availableBeans.associateBy { it.provider }
        providers.forEach {
            it.value.validateProvider(categoryRepositories.filter { cat -> cat.value != null }.keys)
        }
        DataProvider.entries.forEach {
            require(providers.keys.contains(it)) {
                "Create an inheritor of ProviderService interface for a new provider $it"
            }
        }
        return providers
    }
}
