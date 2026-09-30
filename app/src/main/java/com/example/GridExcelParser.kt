package com.example

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object GridExcelParser {

    fun parse(inputStream: InputStream, filenameHint: String? = null): ExcelParseResult {
        val workbook: Workbook = try {
            WorkbookFactory.create(inputStream)
        } catch (e: Exception) {
            return ExcelParseResult(
                fileType = GridExcelFileType.UNKNOWN,
                sheetName = "",
                totalRows = 0,
                errors = listOf(RowMessage(0, "Không thể đọc tệp Excel: ${e.localizedMessage}"))
            )
        }

        try {
            if (workbook.numberOfSheets == 0) {
                return ExcelParseResult(
                    fileType = GridExcelFileType.UNKNOWN,
                    sheetName = "",
                    totalRows = 0,
                    errors = listOf(RowMessage(0, "Tệp Excel không chứa sheet nào."))
                )
            }

            // Find first sheet with valid data
            for (i in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(i)
                val result = parseSheet(sheet, filenameHint)
                if (result.fileType != GridExcelFileType.UNKNOWN || result.totalRows > 0) {
                    return result
                }
            }

            // If none matched, parse first sheet anyway to return errors
            return parseSheet(workbook.getSheetAt(0), filenameHint)
        } finally {
            try {
                workbook.close()
            } catch (_: Exception) {}
        }
    }

    fun parseSheet(sheet: Sheet, filenameHint: String? = null): ExcelParseResult {
        val headerRow = findHeaderRow(sheet) ?: return ExcelParseResult(
            fileType = GridExcelFileType.UNKNOWN,
            sheetName = sheet.sheetName,
            totalRows = 0,
            errors = listOf(RowMessage(0, "Không tìm thấy hàng tiêu đề trong sheet '${sheet.sheetName}'"))
        )

        val headerMap = extractHeaderMap(headerRow)
        var fileType = GridExcelHeaderNormalizer.detectFileType(headerMap)

        if (fileType == GridExcelFileType.UNKNOWN && filenameHint != null) {
            val lower = filenameHint.lowercase()
            if (lower.contains("tram")) fileType = GridExcelFileType.SUBSTATION
            else if (lower.contains("phat") || lower.contains("tuyen")) fileType = GridExcelFileType.FEEDER
            else if (lower.contains("lbs")) fileType = GridExcelFileType.LBS
            else if (lower.contains("rec")) fileType = GridExcelFileType.REC
        }

        val warnings = mutableListOf<RowMessage>()
        val errors = mutableListOf<RowMessage>()
        val substations = mutableListOf<ParsedSubstationRow>()
        val feeders = mutableListOf<ParsedFeederRow>()
        val devices = mutableListOf<ParsedDeviceRow>()

        val startRow = headerRow.rowNum + 1
        val lastRow = sheet.lastRowNum
        var totalDataRows = 0

        for (r in startRow..lastRow) {
            val row = sheet.getRow(r) ?: continue
            if (isRowEmpty(row)) continue
            totalDataRows++

            val excelRowNum = r + 1 // 1-based line number for user

            when (fileType) {
                GridExcelFileType.SUBSTATION -> {
                    parseSubstationRow(row, excelRowNum, headerMap, substations, warnings, errors)
                }
                GridExcelFileType.FEEDER -> {
                    parseFeederRow(row, excelRowNum, headerMap, feeders, warnings, errors)
                }
                GridExcelFileType.LBS, GridExcelFileType.REC, GridExcelFileType.UNKNOWN -> {
                    // Try parsing as Device
                    parseDeviceRow(row, excelRowNum, headerMap, devices, warnings, errors)
                }
            }
        }

        // If fileType was LBS or REC, refine based on parsed devices
        if (devices.isNotEmpty()) {
            val isRec = devices.any { it.deviceType.equals("REC", ignoreCase = true) || it.deviceId.startsWith("REC") }
            fileType = if (isRec) GridExcelFileType.REC else GridExcelFileType.LBS
        }

        return ExcelParseResult(
            fileType = fileType,
            sheetName = sheet.sheetName,
            totalRows = totalDataRows,
            substations = substations,
            feeders = feeders,
            devices = devices,
            warnings = warnings,
            errors = errors
        )
    }

    private fun findHeaderRow(sheet: Sheet): Row? {
        for (r in 0..minOf(sheet.lastRowNum, 20)) {
            val row = sheet.getRow(r) ?: continue
            if (!isRowEmpty(row)) {
                return row
            }
        }
        return null
    }

    private fun extractHeaderMap(headerRow: Row): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (c in 0 until headerRow.lastCellNum) {
            val cell = headerRow.getCell(c) ?: continue
            val raw = getCellString(cell)
            val normalized = GridExcelHeaderNormalizer.normalizeHeader(raw)
            if (normalized.isNotEmpty() && !map.containsKey(normalized)) {
                map[normalized] = c
            }
        }
        return map
    }

    private fun isRowEmpty(row: Row): Boolean {
        for (c in 0 until row.lastCellNum) {
            val cell = row.getCell(c) ?: continue
            if (cell.cellType != CellType.BLANK && getCellString(cell).trim().isNotEmpty()) {
                return false
            }
        }
        return true
    }

    private fun getCellString(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue?.trim() ?: ""
            CellType.NUMERIC -> {
                val num = cell.numericCellValue
                if (num == num.toLong().toDouble()) {
                    num.toLong().toString()
                } else {
                    num.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue?.trim() ?: ""
                } catch (_: Exception) {
                    try {
                        val num = cell.numericCellValue
                        if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                    } catch (_: Exception) { "" }
                }
            }
            else -> ""
        }
    }

    private fun getValue(row: Row, headerMap: Map<String, Int>, vararg aliases: String): String {
        val col = GridExcelHeaderNormalizer.findColumnIndex(headerMap, *aliases) ?: return ""
        val cell = row.getCell(col) ?: return ""
        val value = getCellString(cell)
        if (value.equals("null", ignoreCase = true)) return ""
        return GridExcelHeaderNormalizer.removeBomAndTrim(value)
    }

    private fun parseSubstationRow(
        row: Row,
        rowNum: Int,
        headerMap: Map<String, Int>,
        out: MutableList<ParsedSubstationRow>,
        warnings: MutableList<RowMessage>,
        errors: MutableList<RowMessage>
    ) {
        val code = getValue(row, headerMap, "ma tram", "matram", "substation_code", "code")
        val name = getValue(row, headerMap, "ten tram", "tentram", "substation_name", "name")
        val address = getValue(row, headerMap, "dia chi", "diachi", "address")
        val rawStatus = getValue(row, headerMap, "trang thai", "trangthai", "status")

        if (code.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: Mã trạm không được để trống."))
            return
        }
        if (name.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: Tên trạm không được để trống.", identifier = code))
            return
        }

        val status = if (rawStatus.isNotEmpty()) rawStatus.uppercase() else "ACTIVE"
        out.add(ParsedSubstationRow(rowNum, code, name, address, status))
    }

    private fun parseFeederRow(
        row: Row,
        rowNum: Int,
        headerMap: Map<String, Int>,
        out: MutableList<ParsedFeederRow>,
        warnings: MutableList<RowMessage>,
        errors: MutableList<RowMessage>
    ) {
        val code = getValue(row, headerMap, "ma phat tuyen", "maphattuyen", "feeder_code", "code")
        val name = getValue(row, headerMap, "ten phat tuyen", "tenphattuyen", "feeder_name", "name")
        val substationRef = getValue(row, headerMap, "tram 110kv", "tram110kv", "tram", "substation", "substation_code", "ma tram")
        val rawStatus = getValue(row, headerMap, "trang thai", "trangthai", "status")

        if (code.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: Mã phát tuyến không được để trống."))
            return
        }
        if (name.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: Tên phát tuyến không được để trống.", identifier = code))
            return
        }
        if (substationRef.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: Trạm 110kV không được để trống.", identifier = code))
            return
        }

        val status = if (rawStatus.isNotEmpty()) rawStatus.uppercase() else "ACTIVE"
        out.add(ParsedFeederRow(rowNum, code, name, substationRef, status))
    }

    private fun parseDeviceRow(
        row: Row,
        rowNum: Int,
        headerMap: Map<String, Int>,
        out: MutableList<ParsedDeviceRow>,
        warnings: MutableList<RowMessage>,
        errors: MutableList<RowMessage>
    ) {
        val deviceId = getValue(row, headerMap, "device_id", "device id", "ma thiet bi", "mathietbi")
        val name = getValue(row, headerMap, "name", "ten thiet bi", "tenthietbi")
        var deviceType = getValue(row, headerMap, "device_type", "loai thiet bi", "loaithietbi").uppercase()
        val poleNumber = getValue(row, headerMap, "pole_number", "so cot", "vitritru", "tru").ifEmpty { null }
        val feederCode = getValue(row, headerMap, "ma phat tuyen", "maphattuyen", "feeder_code", "feeder")
        val substationCode = getValue(row, headerMap, "substation_code", "ma tram", "tram")
        val unit = getValue(row, headerMap, "unit", "don vi", "donvi").ifEmpty { null }
        val team = getValue(row, headerMap, "team", "doi", "doi quan ly").ifEmpty { null }
        val rawStatus = getValue(row, headerMap, "status", "trang thai")
        val rawSwitchStatus = getValue(row, headerMap, "switch_status", "trang thai dong cat")
        val rawScadaStatus = getValue(row, headerMap, "scada_status", "scada")
        var relay79 = getValue(row, headerMap, "relay_79", "relay79", "79").uppercase()
        val imageUrl = getValue(row, headerMap, "image_url", "hinh anh").ifEmpty { null }
        val googleMapsUrl = getValue(row, headerMap, "google_maps_url", "toa do", "maps").ifEmpty { null }

        if (deviceId.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: device_id không được để trống."))
            return
        }

        if (deviceType.isEmpty()) {
            deviceType = if (deviceId.startsWith("REC", ignoreCase = true)) "REC" else "LBS"
        }

        if (name.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: Tên thiết bị không được để trống.", identifier = deviceId))
            return
        }
        if (feederCode.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: Mã phát tuyến không được để trống.", identifier = deviceId))
            return
        }
        if (substationCode.isEmpty()) {
            errors.add(RowMessage(rowNum, "Dòng $rowNum: substation_code không được để trống.", identifier = deviceId))
            return
        }

        // Validate relay_79 rule
        if (relay79.isEmpty()) {
            if (deviceType == "LBS") {
                relay79 = "N_A"
                warnings.add(RowMessage(rowNum, "Dòng $rowNum ($deviceId): relay_79 trống trên thiết bị LBS, đã chuẩn hóa thành 'N_A'.", isError = false, identifier = deviceId))
            } else {
                relay79 = "OFF"
            }
        }

        // Validate URLs
        if (imageUrl != null) {
            if (!imageUrl.startsWith("https://", ignoreCase = true)) {
                errors.add(RowMessage(rowNum, "Dòng $rowNum ($deviceId): image_url phải bắt đầu bằng https://", identifier = deviceId))
                return
            }
            if (imageUrl.length > 500) {
                errors.add(RowMessage(rowNum, "Dòng $rowNum ($deviceId): image_url vượt quá giới hạn 500 ký tự.", identifier = deviceId))
                return
            }
        }

        if (googleMapsUrl != null) {
            if (!googleMapsUrl.startsWith("https://", ignoreCase = true)) {
                errors.add(RowMessage(rowNum, "Dòng $rowNum ($deviceId): google_maps_url phải bắt đầu bằng https://", identifier = deviceId))
                return
            }
            if (googleMapsUrl.length > 500) {
                errors.add(RowMessage(rowNum, "Dòng $rowNum ($deviceId): google_maps_url vượt quá giới hạn 500 ký tự.", identifier = deviceId))
                return
            }
        }

        val status = if (rawStatus.isNotEmpty()) rawStatus.uppercase() else "ACTIVE"
        val switchStatus = if (rawSwitchStatus.isNotEmpty()) rawSwitchStatus.uppercase() else "UNKNOWN"
        val scadaStatus = if (rawScadaStatus.isNotEmpty()) rawScadaStatus.uppercase() else "UNKNOWN"

        out.add(
            ParsedDeviceRow(
                rowNumber = rowNum,
                deviceId = deviceId,
                name = name,
                deviceType = deviceType,
                poleNumber = poleNumber,
                feederCode = feederCode,
                substationCode = substationCode,
                unit = unit,
                team = team,
                status = status,
                switchStatus = switchStatus,
                scadaStatus = scadaStatus,
                relay79 = relay79,
                imageUrl = imageUrl,
                googleMapsUrl = googleMapsUrl
            )
        )
    }
}
