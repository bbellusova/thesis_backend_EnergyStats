package cvut.fit.energyStats.repository.dataAccessMethodRepository

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.dto.DataAccessMethodDTO
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethod.accessMethod
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethod.dataCategory
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethod.dataProvider
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethod.dataProviderAPIKey
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethod.dataProviderSiteId
import cvut.fit.energyStats.repository.dataAccessMethodRepository.DataAccessMethod.siteId
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import kotlin.collections.plus

@Repository
class DataAccessMethodRepository {
    fun updateAccessMethod(dateAccessMethodDTO: DataAccessMethodDTO) {
        transaction {
            DataAccessMethod.upsert {
                it[siteId] = dateAccessMethodDTO.siteId
                it[dataCategory] = dateAccessMethodDTO.dataCategory.name
                it[accessMethod] = dateAccessMethodDTO.dataAccessType.name
                it[dataProvider] = dateAccessMethodDTO.dataProvider?.name
                it[dataProviderSiteId] = dateAccessMethodDTO.dataProviderSiteId
                it[dataProviderAPIKey] = dateAccessMethodDTO.apiKey
            }
        }
    }

    fun getAccessMethod(
        id: Int,
        category: DataCategory,
    ): DataAccessMethodDTO {
        var result: DataAccessMethodDTO? = null
        transaction {
            DataAccessMethod
                .selectAll()
                .where { (siteId eq id) and (dataCategory eq category.name) }
                .singleOrNull()
                ?.let {
                    val provider =
                        if (it[dataProvider] != null) DataProvider.valueOf(it[dataProvider]!!) else null
                    result =
                        DataAccessMethodDTO(
                            it[siteId],
                            DataCategory.valueOf(it[dataCategory]),
                            DataAccessType.valueOf(it[accessMethod]),
                            provider,
                            it[dataProviderSiteId],
                            it[dataProviderAPIKey],
                        )
                }
        }
        if (result == null) {
            result = DataAccessMethodDTO(id, category, DataAccessType.UNAVAILABLE, null, null, null)
        }
        return result
    }

    fun getAvailableCategoriesForSite(id: Int): Set<DataCategory> {
        var result: Set<DataCategory> = setOf()
        transaction {
            result =
                DataAccessMethod
                    .select(dataCategory)
                    .where { (siteId eq id) and (accessMethod neq DataAccessType.UNAVAILABLE.name) }
                    .map { DataCategory.valueOf(it[dataCategory]) }
                    .toSet()
        }
        return result
    }

    fun getProvidersWithFileDataImport(id: Int): Map<DataProvider, Set<DataCategory>> {
        val result: MutableMap<DataProvider, Set<DataCategory>> = mutableMapOf()
        transaction {
            DataAccessMethod
                .select(dataProvider, dataCategory)
                .where { siteId eq id and (accessMethod eq DataAccessType.FILE.name) }
                .forEach {
                    val category = DataCategory.valueOf(it[dataCategory])
                    val provider =
                        if (it[dataProvider] != null) {
                            DataProvider.valueOf(it[dataProvider]!!)
                        } else {
                            throw IllegalArgumentException(
                                "Site $id and category ${category.description} have file access but undefined provider",
                            )
                        }
                    result[provider] = result[provider]?.plus(category) ?: setOf(category)
                }
        }
        return result
    }

    fun getCalculatedCategories(id: Int): Set<DataCategory> {
        val result =
            transaction {
                return@transaction DataAccessMethod
                    .select(dataCategory)
                    .where { siteId eq id and (accessMethod eq DataAccessType.CALCULATED.name) }
                    .map { DataCategory.valueOf(it[dataCategory]) }
                    .toSet()
            }
        return result
    }
}
