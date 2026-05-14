package cvut.fit.energyStats.repository.energyDataRepository

import cvut.fit.energyStats.domain.dto.Entries
import cvut.fit.energyStats.repository.siteRepository.Site.siteId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.day
import org.jetbrains.exposed.v1.datetime.hour
import org.jetbrains.exposed.v1.datetime.month
import org.jetbrains.exposed.v1.datetime.year
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository
import kotlin.collections.forEach
import kotlin.collections.set

@Repository
abstract class EnergyDataRepository(
    private val table: EnergyDataTable,
) {
    fun storeToDb(
        site: Int,
        entries: List<Pair<LocalDateTime, Double>>,
    ) {
        transaction {
            val t = table
            t.batchUpsert(entries) {
                this[t.siteId] = site
                this[t.timestamp] = it.first
                this[t.valuekWh] = it.second
            }
        }
    }

    suspend fun getDataByHours(
        site: Int,
        dateTimeFrom: LocalDateTime,
        dateTimeTo: LocalDateTime,
    ): Entries =
        withContext(Dispatchers.IO) {
            val result: Entries = mutableMapOf()
            val alias = table.valuekWh.sum().alias("kWhSum")

            transaction {
                val t = table
                val q =
                    table
                        .select(t.timestamp.date(), t.timestamp.hour(), alias)
                        .where {
                            (t.siteId eq site) and
                                (t.timestamp greaterEq dateTimeFrom) and
                                (t.timestamp lessEq dateTimeTo)
                        }.groupBy(t.timestamp.date(), t.timestamp.hour())

                q.forEach {
                    val hour = it[t.timestamp.hour()]
                    val hourTimestamp = it[t.timestamp.date()].atTime(hour, 0, 0, 0)
                    result[hourTimestamp] = it[alias] ?: 0.0
                }
            }
            result
        }

    suspend fun getDataByDays(
        site: Int,
        dateFrom: LocalDateTime,
        dateTo: LocalDateTime,
    ): Entries =
        withContext(Dispatchers.IO) {
            val result: Entries = mutableMapOf()
            val alias = table.valuekWh.sum().alias("kWhSum")

            transaction {
                val t = table
                val q =
                    table
                        .select(t.timestamp.date(), alias)
                        .where {
                            (t.siteId eq site) and
                                (t.timestamp.date() greaterEq dateFrom.date) and
                                (t.timestamp.date() lessEq dateTo.date)
                        }.groupBy(t.timestamp.date())
                        .orderBy(t.timestamp.date())

                q.forEach {
                    val dateTimestamp = it[t.timestamp.date()].atTime(0, 0, 0, 0)
                    result[dateTimestamp] = it[alias] ?: 0.0
                }
            }
            result
        }

    fun getAvailableYears(site: Int): List<Int> {
        var result = listOf<Int>()
        transaction {
            val t = table
            val q = table.select(t.timestamp.year()).where { t.siteId eq site }.withDistinct()

            result = q.map { it[t.timestamp.year()].toString().toInt() }
        }
        return result
    }

    fun getAvailableMonthsForYear(
        site: Int,
        year: Int,
    ): List<Int> {
        var result = listOf<Int>()
        transaction {
            val t = table
            val q =
                table
                    .select(t.timestamp.month())
                    .where { (t.siteId eq site) and (t.timestamp.year() eq year) }
                    .withDistinct()

            result = q.map { it[t.timestamp.month()].toString().toInt() }
        }
        return result
    }

    fun getAvailableDaysForMonthInYear(
        site: Int,
        year: Int,
        month: Int,
    ): List<Int> {
        var result = listOf<Int>()
        transaction {
            val t = table
            val q =
                table
                    .select(t.timestamp.day())
                    .where(
                        (t.siteId eq site) and
                            (t.timestamp.year() eq year) and
                            (t.timestamp.month() eq month),
                    ).withDistinct()

            result = q.map { it[t.timestamp.day()].toString().toInt() }
        }
        return result
    }
}
