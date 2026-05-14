package cvut.fit.energyStats.config

import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethod
import cvut.fit.energyStats.repository.energyDataRepository.EnergyConsumptionFromTheGridO
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionToTheGridO
import cvut.fit.energyStats.repository.energyDataRepository.EnergyProductionTotalO
import cvut.fit.energyStats.repository.generationForecastCache.ProductionForecastCache
import cvut.fit.energyStats.repository.siteRepository.Site
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DatabaseConfiguration {
    @Bean fun database(dataSource: DataSource): Database = Database.connect(dataSource)

    @Bean
    fun setupDatabase(database: Database) =
        CommandLineRunner {
            transaction(database) {
                if (false) {
                    SchemaUtils.drop(
                        DataAccessMethod,
                        Site,
                        EnergyProductionTotalO,
                        EnergyProductionToTheGridO,
                        EnergyConsumptionFromTheGridO,
                        ProductionForecastCache,
                    )
                    println("Database tables dropped.")
                }

                SchemaUtils.create(
                    DataAccessMethod,
                    Site,
                    EnergyProductionTotalO,
                    EnergyProductionToTheGridO,
                    EnergyConsumptionFromTheGridO,
                    ProductionForecastCache,
                )
                println("Database tables created.")
            }
        }
}
