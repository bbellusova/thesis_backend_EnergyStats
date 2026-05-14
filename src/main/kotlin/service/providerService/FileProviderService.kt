package cvut.fit.energyStats.service.providerService

import cvut.fit.energyStats.domain.dto.Entries
import org.springframework.web.multipart.MultipartFile

sealed interface FileProviderService : ProviderService {
    val supportedFileFormats: List<String>

    fun importDataFromAFile(file: MultipartFile): Entries

    fun validateFile(file: MultipartFile) {
        var validFormat = false
        for (format in supportedFileFormats) {
            if (file.originalFilename?.endsWith(format, ignoreCase = true) ?: false) {
                validFormat = true
                break
            }
        }

        require(validFormat) {
            "Incorrect file format, expected one of: ${supportedFileFormats.joinToString(", ")}"
        }

        require(!file.isEmpty) { "File is empty" }
    }
}
