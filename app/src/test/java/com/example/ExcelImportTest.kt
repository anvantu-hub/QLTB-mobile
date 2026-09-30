package com.example

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelImportTest {

    private fun createWorkbook(
        sheetName: String,
        headers: List<String>,
        rows: List<List<String>>
    ): ByteArrayInputStream {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(sheetName)

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }

        rows.forEachIndexed { rIndex, rowData ->
            val row = sheet.createRow(rIndex + 1)
            rowData.forEachIndexed { cIndex, cellVal ->
                row.createCell(cIndex).setCellValue(cellVal)
            }
        }

        val out = ByteArrayOutputStream()
        workbook.write(out)
        workbook.close()
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun testParseSubstations_18Records() {
        val headers = listOf("Mã trạm", "Tên trạm", "Địa chỉ", "Trạng thái")
        val data = listOf(
            listOf("110_GĐ", "GÒ ĐẬU", "Bình Dương", "ACTIVE"),
            listOf("110_BB", "BÀU BÈO", "Bình Dương", "ACTIVE"),
            listOf("110_VT", "VĨNH TRƯỜNG", "Bình Dương", "ACTIVE"),
            listOf("110_VH", "VĨNH HIỆP", "Bình Dương", "ACTIVE"),
            listOf("110_VL", "VĨNH LỢI", "Bình Dương", "ACTIVE"),
            listOf("110_HP", "HÒA PHÚ", "Bình Dương", "ACTIVE"),
            listOf("110_HL", "HÒA LỢI", "Bình Dương", "ACTIVE"),
            listOf("110_HB", "HÒA BÌNH", "Bình Dương", "ACTIVE"),
            listOf("110_TP", "THẠNH PHƯỚC", "Bình Dương", "ACTIVE"),
            listOf("110_KV", "KHÁNH VÂN", "Bình Dương", "ACTIVE"),
            listOf("110_KB", "KHÁNH BÌNH", "Bình Dương", "ACTIVE"),
            listOf("110_TU", "TÂN UYÊN", "Bình Dương", "ACTIVE"),
            listOf("110_ĐC", "ĐẤT CUỐC", "Bình Dương", "ACTIVE"),
            listOf("110_TG", "THUẬN GIAO", "Bình Dương", "ACTIVE"),
            listOf("110_HĐ", "HƯNG ĐỊNH", "Bình Dương", "ACTIVE"),
            listOf("110_MH", "MỸ HÒA", "Bình Dương", "ACTIVE"),
            listOf("110_TH", "THỚI HÒA", "Bình Dương", "ACTIVE"),
            listOf("220_BH", "BÌNH HÒA", "Bình Dương", "ACTIVE")
        )

        val stream = createWorkbook("Tram110kV", headers, data)
        val result = GridExcelParser.parse(stream)

        assertEquals(GridExcelFileType.SUBSTATION, result.fileType)
        assertEquals(18, result.substations.size)
        assertEquals(0, result.errors.size)

        val plan = GridExcelImportValidator.validateSubstations(result, emptyList())
        assertEquals(18, plan.toInsertCount)
        assertEquals(0, plan.toUpdateCount)
        assertEquals(0, plan.errors.size)
        assertTrue(plan.canExecute)
    }

    @Test
    fun testParseFeeders_145Records() {
        val substations = listOf(
            Substation(1, "110_GĐ", "GÒ ĐẬU"),
            Substation(2, "110_BB", "BÀU BÈO"),
            Substation(3, "110_VT", "VĨNH TRƯỜNG"),
            Substation(4, "110_VH", "VĨNH HIỆP"),
            Substation(5, "110_VL", "VĨNH LỢI"),
            Substation(6, "110_HP", "HÒA PHÚ"),
            Substation(7, "110_HL", "HÒA LỢI"),
            Substation(8, "110_HB", "HÒA BÌNH"),
            Substation(9, "110_TP", "THẠNH PHƯỚC"),
            Substation(10, "110_KV", "KHÁNH VÂN"),
            Substation(11, "110_KB", "KHÁNH BÌNH"),
            Substation(12, "110_TU", "TÂN UYÊN"),
            Substation(13, "110_ĐC", "ĐẤT CUỐC"),
            Substation(14, "110_TG", "THUẬN GIAO"),
            Substation(15, "110_HĐ", "HƯNG ĐỊNH"),
            Substation(16, "110_MH", "MỸ HÒA"),
            Substation(17, "110_TH", "THỚI HÒA"),
            Substation(18, "220_BH", "BÌNH HÒA")
        )

        val headers = listOf("Mã phát tuyến", "Tên phát tuyến", "Trạm 110kV", "Trạng thái")
        val data = mutableListOf<List<String>>()
        val feedersPerStation = 145
        for (i in 1..feedersPerStation) {
            val station = substations[(i - 1) % substations.size]
            data.add(listOf("47${i % 10}-$i", "Tuyến $i", station.name, "ACTIVE"))
        }

        val stream = createWorkbook("PhatTuyen", headers, data)
        val result = GridExcelParser.parse(stream)

        assertEquals(GridExcelFileType.FEEDER, result.fileType)
        assertEquals(145, result.feeders.size)
        assertEquals(0, result.errors.size)

        val plan = GridExcelImportValidator.validateFeeders(result, substations, emptyList())
        assertEquals(145, plan.toInsertCount)
        assertEquals(0, plan.errors.size)
        assertTrue(plan.canExecute)
    }

    @Test
    fun testParseLBS_EmptyRelay79GeneratesWarning() {
        val headers = listOf(
            "device_id", "name", "device_type", "pole_number", "Mã phát tuyến",
            "substation_code", "unit", "team", "status", "switch_status", "scada_status",
            "relay_79", "image_url", "google_maps_url"
        )
        val data = listOf(
            listOf("LBS-471GĐ-001", "LBS-01", "LBS", "TRỤ 01", "471-GĐ", "110_GĐ", "ĐL BD", "ĐỘI 1", "ACTIVE", "CLOSED", "SIGNAL", "N_A", "", ""),
            listOf("LBS-476TP-147", "LBS-147", "LBS", "TRỤ 03", "476-TP", "110_TP", "ĐL BD", "ĐỘI 1", "ACTIVE", "CLOSED", "SIGNAL", "", "", "") // Empty relay_79
        )

        val stream = createWorkbook("ThietBi", headers, data)
        val result = GridExcelParser.parse(stream)

        assertEquals(2, result.devices.size)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.first().message.contains("relay_79 trống"))
        assertEquals("N_A", result.devices[1].relay79)
    }

    @Test
    fun testValidateLBS_UnknownFeeder479HD_GeneratesErrorAndBlocks() {
        val substations = listOf(Substation(1, "110_HĐ", "HƯNG ĐỊNH"))
        val feeders = listOf(Feeder(1, "474-HĐ", "474 THIÊN HÒA", 1))

        val headers = listOf(
            "device_id", "name", "device_type", "pole_number", "Mã phát tuyến",
            "substation_code", "unit", "team", "status", "switch_status", "scada_status",
            "relay_79", "image_url", "google_maps_url"
        )
        val data = listOf(
            listOf("LBS-474HĐ-188", "LBS-22 SUỐI CÁT", "LBS", "TRỤ 22", "474-HĐ", "110_HĐ", "ĐL BD", "ĐỘI 1", "ACTIVE", "CLOSED", "SIGNAL", "N_A", "", ""),
            listOf("LBS-474HĐ-190", "LBS-72 THẠNH HÒA", "LBS", "TRỤ 72", "479-HĐ", "110_HĐ", "ĐL BD", "ĐỘI 1", "ACTIVE", "OPEN", "SIGNAL", "N_A", "", "")
        )

        val stream = createWorkbook("ThietBi", headers, data)
        val result = GridExcelParser.parse(stream)
        val plan = GridExcelImportValidator.validateDevices(result, substations, feeders, emptyList())

        assertFalse(plan.canExecute)
        assertEquals(1, plan.errors.size)
        assertTrue(plan.errors.first().message.contains("479-HĐ"))
        assertEquals("LBS-474HĐ-190", plan.errors.first().identifier)
    }

    @Test
    fun testColumnReorderingAndHeaderBOM() {
        // Re-ordered columns and added BOM
        val headers = listOf("\uFEFF  Tên trạm  ", " Trạng thái ", "Mã trạm", "Địa chỉ")
        val data = listOf(
            listOf("GÒ ĐẬU", "ACTIVE", "110_GĐ", "Bình Dương")
        )

        val stream = createWorkbook("Tram110kV", headers, data)
        val result = GridExcelParser.parse(stream)

        assertEquals(GridExcelFileType.SUBSTATION, result.fileType)
        assertEquals(1, result.substations.size)
        assertEquals("110_GĐ", result.substations.first().code)
        assertEquals("GÒ ĐẬU", result.substations.first().name)
    }

    @Test
    fun testUpsertIdPreservationAndIdempotency() {
        val existingSubstations = listOf(
            Substation(100, "110_GĐ", "GÒ ĐẬU CŨ", "Bình Dương", "ACTIVE")
        )

        val headers = listOf("Mã trạm", "Tên trạm", "Địa chỉ", "Trạng thái")
        val data = listOf(
            listOf("110_GĐ", "GÒ ĐẬU MỚI", "Bình Dương", "ACTIVE"),
            listOf("110_BB", "BÀU BÈO", "Bình Dương", "ACTIVE")
        )

        val stream = createWorkbook("Tram110kV", headers, data)
        val result = GridExcelParser.parse(stream)
        val plan = GridExcelImportValidator.validateSubstations(result, existingSubstations)

        assertEquals(1, plan.toUpdateCount)
        assertEquals(1, plan.toInsertCount)
        assertEquals(100, plan.substationsToUpsert.first { it.substation_code == "110_GĐ" }.id)
        assertEquals(101, plan.substationsToUpsert.first { it.substation_code == "110_BB" }.id)
    }
}
