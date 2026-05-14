package cvut.fit.energyStats.config

import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.service.providerService.FileProviderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

typealias FileProviders = Map<DataProvider, FileProviderService>

@Configuration
class FileProvidersConfig {
    @Bean
    fun fileProviders(availableBeans: List<FileProviderService>): FileProviders =
        availableBeans.associateBy {
            it.provider
        }
}
