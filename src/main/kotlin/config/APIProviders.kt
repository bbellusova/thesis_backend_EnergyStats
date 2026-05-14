package cvut.fit.energyStats.config

import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.service.providerService.APIProviderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

typealias ApiProviders = Map<DataProvider, APIProviderService>

@Configuration
class ApiProvidersConfig {
    @Bean
    fun apiProviders(availableBeans: List<APIProviderService>): ApiProviders =
        availableBeans.associateBy {
            it.provider
        }
}
