package com.example

enum class GridExcelFileType(val displayName: String) {
    SUBSTATION("Trạm 110kV"),
    FEEDER("Phát tuyến 22kV"),
    LBS("Thiết bị đóng cắt LBS"),
    REC("Thiết bị đóng cắt REC"),
    UNKNOWN("Không xác định")
}

data class RowMessage(
    val rowNumber: Int,
    val message: String,
    val isError: Boolean = true,
    val identifier: String? = null
)

data class ParsedSubstationRow(
    val rowNumber: Int,
    val code: String,
    val name: String,
    val address: String,
    val status: String
)

data class ParsedFeederRow(
    val rowNumber: Int,
    val code: String,
    val name: String,
    val substationRef: String,
    val status: String
)

data class ParsedDeviceRow(
    val rowNumber: Int,
    val deviceId: String,
    val name: String,
    val deviceType: String,
    val poleNumber: String?,
    val feederCode: String,
    val substationCode: String,
    val unit: String?,
    val team: String?,
    val status: String,
    val switchStatus: String,
    val scadaStatus: String,
    val relay79: String,
    val imageUrl: String?,
    val googleMapsUrl: String?
)

data class ExcelParseResult(
    val fileType: GridExcelFileType,
    val sheetName: String,
    val totalRows: Int,
    val substations: List<ParsedSubstationRow> = emptyList(),
    val feeders: List<ParsedFeederRow> = emptyList(),
    val devices: List<ParsedDeviceRow> = emptyList(),
    val warnings: List<RowMessage> = emptyList(),
    val errors: List<RowMessage> = emptyList()
) {
    val isValid: Boolean get() = errors.isEmpty() && fileType != GridExcelFileType.UNKNOWN
}

data class ImportPlan(
    val fileType: GridExcelFileType,
    val totalRows: Int,
    val validRows: Int,
    val toInsertCount: Int,
    val toUpdateCount: Int,
    val warnings: List<RowMessage>,
    val errors: List<RowMessage>,
    val substationsToUpsert: List<Substation> = emptyList(),
    val feedersToUpsert: List<Feeder> = emptyList(),
    val devicesToUpsert: List<Device> = emptyList()
) {
    val canExecute: Boolean get() = errors.isEmpty() && (toInsertCount + toUpdateCount > 0)
}

data class ImportExecutionResult(
    val fileType: GridExcelFileType,
    val insertedCount: Int,
    val updatedCount: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)
