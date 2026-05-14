package service.providerService

import cvut.fit.energyStats.domain.dto.Entries
import cvut.fit.energyStats.service.providerService.ZSDDataImportService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import org.springframework.mock.web.MockMultipartFile
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals

class TestZSDDataImportService :
    FunSpec({
        val service = ZSDDataImportService()

        test("valid file is loaded") {
            val expectedResult: Entries =
                mutableMapOf(
                    LocalDateTime(2026, 5, 1, 0, 0) to 0.195,
                    LocalDateTime(2026, 5, 1, 1, 0) to 0.183,
                    LocalDateTime(2026, 5, 1, 2, 0) to 0.197,
                    LocalDateTime(2026, 5, 1, 3, 0) to 0.228,
                    LocalDateTime(2026, 5, 1, 4, 0) to 0.303,
                    LocalDateTime(2026, 5, 1, 5, 0) to 0.179,
                    LocalDateTime(2026, 5, 1, 6, 0) to 0.26,
                    LocalDateTime(2026, 5, 1, 7, 0) to 0.331,
                    LocalDateTime(2026, 5, 1, 8, 0) to 0.236,
                    LocalDateTime(2026, 5, 1, 9, 0) to 0.493,
                    LocalDateTime(2026, 5, 1, 10, 0) to 0.908,
                    LocalDateTime(2026, 5, 1, 11, 0) to 0.878,
                    LocalDateTime(2026, 5, 1, 12, 0) to 0.128,
                    LocalDateTime(2026, 5, 1, 13, 0) to 0.065,
                    LocalDateTime(2026, 5, 1, 14, 0) to 0.062,
                    LocalDateTime(2026, 5, 1, 15, 0) to 0.014,
                    LocalDateTime(2026, 5, 1, 16, 0) to 0.145,
                    LocalDateTime(2026, 5, 1, 17, 0) to 0.4,
                    LocalDateTime(2026, 5, 1, 18, 0) to 0.776,
                    LocalDateTime(2026, 5, 1, 19, 0) to 0.401,
                    LocalDateTime(2026, 5, 1, 20, 0) to 0.596,
                    LocalDateTime(2026, 5, 1, 21, 0) to 0.63,
                    LocalDateTime(2026, 5, 1, 22, 0) to 0.345,
                    LocalDateTime(2026, 5, 1, 23, 0) to 0.25,
                    LocalDateTime(2026, 5, 2, 0, 0) to 0.185,
                    LocalDateTime(2026, 5, 2, 1, 0) to 0.178,
                )

            val filePath = "src/test/resources/export.xls"
            val file = File(filePath)

            val content = Files.readAllBytes(file.toPath())

            val multipartFile =
                MockMultipartFile("file", "export.xls", "application/vnd.ms-excel", content)
            val result = service.importDataFromAFile(multipartFile)
            assertEquals(expectedResult, result)
        }

        test("wrong file format is rejected") {
            val multipartFile =
                MockMultipartFile("file", "export.csv", "application/vnd.ms-excel", null)
            val error =
                shouldThrow<IllegalArgumentException> { service.importDataFromAFile(multipartFile) }

            error.message shouldBe "Incorrect file format, expected one of: .xls, .xlsx"
        }

        test("empty file is rejected") {
            val multipartFile =
                MockMultipartFile("file", "export.xls", "application/vnd.ms-excel", null)
            val error =
                shouldThrow<IllegalArgumentException> { service.importDataFromAFile(multipartFile) }

            error.message shouldBe "File is empty"
        }

        test("invalid file content") {
            val multipartFile =
                MockMultipartFile("file", "export.xls", "application/vnd.ms-excel", ByteArray(8))
            val error =
                shouldThrow<IllegalArgumentException> { service.importDataFromAFile(multipartFile) }

            error.message shouldBe "Invalid file content"
        }

        test("missing column") {
            val filePath = "src/test/resources/exportmissingcolumn.xls"
            val file = File(filePath)

            val content = Files.readAllBytes(file.toPath())

            val multipartFile =
                MockMultipartFile(
                    "file",
                    "exportmissingcolumn.xls",
                    "application/vnd.ms-excel",
                    content,
                )
            val error =
                shouldThrow<IllegalArgumentException> { service.importDataFromAFile(multipartFile) }

            error.message shouldBe "Invalid file content"
        }
    })
