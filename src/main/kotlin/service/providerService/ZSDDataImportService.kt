package cvut.fit.energyStats.service.providerService

import cvut.fit.energyStats.domain.DataAccessType
import cvut.fit.energyStats.domain.DataCategory
import cvut.fit.energyStats.domain.DataProvider
import cvut.fit.energyStats.domain.ProviderDataAccessType
import cvut.fit.energyStats.domain.dto.Entries
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.io.readExcel
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ZSDDataImportService : FileProviderService {
    override val provider = DataProvider.ZSD
    override val supportedFileFormats = listOf(".xls", ".xlsx")
    override val providedCategories =
        mapOf(
            DataCategory.ENERGY_CONSUMPTION_FROM_THE_GRID to
                setOf(ProviderDataAccessType(DataAccessType.FILE)),
            DataCategory.ENERGY_PRODUCTION_TO_THE_GRID to
                setOf(ProviderDataAccessType(DataAccessType.FILE)),
        )

    private val sheetName = "Nameraná hodinová práca"
    private val skipRows = 9
    private val columns = "B,C,D"
    private val dateColName = "Dátum"
    private val timeColName = "Čas od"
    private val energyColName = "Vypočítaná nameraná práca"

    override fun importDataFromAFile(file: MultipartFile): Entries {
        validateFile(file)
        try {
            val df =
                DataFrame.readExcel(
                    file.inputStream,
                    sheetName = sheetName,
                    skipRows = skipRows,
                    columns = columns,
                )
            require(
                df.containsColumn(dateColName) &&
                    df.containsColumn(timeColName) &&
                    df.containsColumn(energyColName),
            ) {
                "Invalid file content"
            }
            val entries: Entries = mutableMapOf()
            for (row in df) {
                if (row[dateColName] != null && row[timeColName] != null && row[energyColName] != null) {
                    val date = row[dateColName].toString()
                    val time = row[timeColName].toString()
                    val kwString = row[energyColName].toString()
                    if (date != "" && time != "" && kwString != "") {
                        val kw = kwString.replace(',', '.').toDouble()
                        val ldatetime = parseStringToLocalDateTime(date, time)
                        entries[ldatetime] = kw
                    }
                }
            }
            return entries
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid file content")
        }
    }

  /*
  parses date and time strings to LocalDateTime format
  dateString - contains date in format DD.MM.YYYY
  timeString - contains time in the format hh:mm:ss
   */
    private fun parseStringToLocalDateTime(
        dateString: String,
        timeString: String,
    ): LocalDateTime {
        require(
            dateString.isNotEmpty() &&
                dateString.contains(regex = "[0-9][0-9].[0-9][0-9].[0-9][0-9][0-9][0-9]".toRegex()),
        ) {
            "Date format incorrect"
        }
        var year: Int
        var month: Int
        var day: Int
        val hour: Int = LocalTime.parse(timeString).hour
        val minute: Int = LocalTime.parse(timeString).minute
        val second: Int = LocalTime.parse(timeString).second
        dateString.split(".").let {
            day = it[0].toInt()
            month = it[1].toInt()
            year = it[2].toInt()
        }
        return LocalDateTime(year, month, day, hour, minute, second)
    }
}
