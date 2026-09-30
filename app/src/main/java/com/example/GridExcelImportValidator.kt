package com.example

object GridExcelImportValidator {

    fun validateSubstations(
        parsed: ExcelParseResult,
        existingSubstations: List<Substation>
    ): ImportPlan {
        val errors = parsed.errors.toMutableList()
        val warnings = parsed.warnings.toMutableList()

        val existingByCode = existingSubstations.associateBy { it.substation_code.uppercase() }
        var maxId = existingSubstations.maxOfOrNull { it.id } ?: 0

        val seenCodes = mutableSetOf<String>()
        val substationsToUpsert = mutableListOf<Substation>()
        var insertCount = 0
        var updateCount = 0

        for (row in parsed.substations) {
            val upperCode = row.code.uppercase()
            if (seenCodes.contains(upperCode)) {
                errors.add(RowMessage(row.rowNumber, "Dòng ${row.rowNumber}: Mã trạm '${row.code}' bị trùng lặp trong tệp Excel.", identifier = row.code))
                continue
            }
            seenCodes.add(upperCode)

            val existing = existingByCode[upperCode]
            val id = if (existing != null) {
                updateCount++
                existing.id
            } else {
                insertCount++
                ++maxId
            }

            substationsToUpsert.add(
                Substation(
                    id = id,
                    substation_code = row.code,
                    name = row.name,
                    address = row.address,
                    status = row.status,
                    notes = existing?.notes ?: "",
                    created_at = existing?.created_at ?: "",
                    updated_at = existing?.updated_at ?: "",
                    device_count = existing?.device_count ?: 0,
                    feeder_count = existing?.feeder_count ?: 0,
                    latitude = existing?.latitude,
                    longitude = existing?.longitude,
                    google_maps_url = existing?.google_maps_url ?: "",
                    primary_image = existing?.primary_image ?: "",
                    imagesJson = existing?.imagesJson ?: "[]",
                    locationHistoryJson = existing?.locationHistoryJson ?: "[]"
                )
            )
        }

        return ImportPlan(
            fileType = GridExcelFileType.SUBSTATION,
            totalRows = parsed.totalRows,
            validRows = substationsToUpsert.size,
            toInsertCount = insertCount,
            toUpdateCount = updateCount,
            warnings = warnings,
            errors = errors,
            substationsToUpsert = substationsToUpsert
        )
    }

    fun validateFeeders(
        parsed: ExcelParseResult,
        existingSubstations: List<Substation>,
        existingFeeders: List<Feeder>
    ): ImportPlan {
        val errors = parsed.errors.toMutableList()
        val warnings = parsed.warnings.toMutableList()

        val substationsByCode = existingSubstations.associateBy { it.substation_code.uppercase() }
        val substationsByName = existingSubstations.associateBy {
            GridExcelHeaderNormalizer.normalizeHeader(it.name)
        }

        val existingByCode = existingFeeders.associateBy { it.feeder_code.uppercase() }
        var maxId = existingFeeders.maxOfOrNull { it.id } ?: 0

        val seenCodes = mutableSetOf<String>()
        val feedersToUpsert = mutableListOf<Feeder>()
        var insertCount = 0
        var updateCount = 0

        for (row in parsed.feeders) {
            val upperCode = row.code.uppercase()
            if (seenCodes.contains(upperCode)) {
                errors.add(RowMessage(row.rowNumber, "Dòng ${row.rowNumber}: Mã phát tuyến '${row.code}' bị trùng lặp trong tệp Excel.", identifier = row.code))
                continue
            }
            seenCodes.add(upperCode)

            // Resolve Substation
            val upperRef = row.substationRef.uppercase()
            val normalizedRef = GridExcelHeaderNormalizer.normalizeHeader(row.substationRef)
            val matchedSubstation = substationsByCode[upperRef] ?: substationsByName[normalizedRef]

            if (matchedSubstation == null) {
                errors.add(
                    RowMessage(
                        row.rowNumber,
                        "Dòng ${row.rowNumber} (${row.code}): Trạm 110kV '${row.substationRef}' không tồn tại trong cơ sở dữ liệu. Vui lòng nhập danh mục Trạm trước.",
                        identifier = row.code
                    )
                )
                continue
            }

            val existing = existingByCode[upperCode]
            val id = if (existing != null) {
                updateCount++
                existing.id
            } else {
                insertCount++
                ++maxId
            }

            feedersToUpsert.add(
                Feeder(
                    id = id,
                    feeder_code = row.code,
                    name = row.name,
                    substation_id = matchedSubstation.id,
                    status = row.status
                )
            )
        }

        return ImportPlan(
            fileType = GridExcelFileType.FEEDER,
            totalRows = parsed.totalRows,
            validRows = feedersToUpsert.size,
            toInsertCount = insertCount,
            toUpdateCount = updateCount,
            warnings = warnings,
            errors = errors,
            feedersToUpsert = feedersToUpsert
        )
    }

    fun validateDevices(
        parsed: ExcelParseResult,
        existingSubstations: List<Substation>,
        existingFeeders: List<Feeder>,
        existingDevices: List<Device>
    ): ImportPlan {
        val errors = parsed.errors.toMutableList()
        val warnings = parsed.warnings.toMutableList()

        val substationsByCode = existingSubstations.associateBy { it.substation_code.uppercase() }
        val feedersByCode = existingFeeders.associateBy { it.feeder_code.uppercase() }
        val existingByDeviceId = existingDevices.associateBy { it.device_id.uppercase() }
        var maxId = existingDevices.maxOfOrNull { it.id } ?: 0

        val seenDeviceIds = mutableSetOf<String>()
        val devicesToUpsert = mutableListOf<Device>()
        var insertCount = 0
        var updateCount = 0

        for (row in parsed.devices) {
            val upperDevId = row.deviceId.uppercase()
            if (seenDeviceIds.contains(upperDevId)) {
                errors.add(RowMessage(row.rowNumber, "Dòng ${row.rowNumber}: device_id '${row.deviceId}' bị trùng lặp trong tệp Excel.", identifier = row.deviceId))
                continue
            }
            seenDeviceIds.add(upperDevId)

            // 1. Resolve Substation
            val matchedSubstation = substationsByCode[row.substationCode.uppercase()]
            if (matchedSubstation == null) {
                errors.add(
                    RowMessage(
                        row.rowNumber,
                        "Dòng ${row.rowNumber} (${row.deviceId}): Mã trạm '${row.substationCode}' không tồn tại trong cơ sở dữ liệu.",
                        identifier = row.deviceId
                    )
                )
                continue
            }

            // 2. Resolve Feeder
            val matchedFeeder = feedersByCode[row.feederCode.uppercase()]
            if (matchedFeeder == null) {
                errors.add(
                    RowMessage(
                        row.rowNumber,
                        "Dòng ${row.rowNumber} (${row.deviceId}): Mã phát tuyến '${row.feederCode}' không tồn tại trong cơ sở dữ liệu. (Trạm: '${row.substationCode}').",
                        identifier = row.deviceId
                    )
                )
                continue
            }

            // 3. Verify Feeder belongs to Substation
            if (matchedFeeder.substation_id != matchedSubstation.id) {
                errors.add(
                    RowMessage(
                        row.rowNumber,
                        "Dòng ${row.rowNumber} (${row.deviceId}): Phát tuyến '${row.feederCode}' không thuộc trạm '${row.substationCode}'.",
                        identifier = row.deviceId
                    )
                )
                continue
            }

            val existing = existingByDeviceId[upperDevId]
            val id = if (existing != null) {
                updateCount++
                existing.id
            } else {
                insertCount++
                ++maxId
            }

            devicesToUpsert.add(
                Device(
                    id = id,
                    device_id = row.deviceId,
                    name = row.name,
                    device_type = row.deviceType,
                    pole_number = row.poleNumber ?: "",
                    feeder_id = matchedFeeder.id,
                    substation_id = matchedSubstation.id,
                    unit = row.unit ?: "ĐL Hải Châu",
                    team = row.team ?: "Tổ Thao Tác Lưu Động",
                    status = row.status,
                    switch_status = row.switchStatus,
                    scada_status = row.scadaStatus,
                    relay_79 = row.relay79,
                    primary_image = row.imageUrl ?: "",
                    google_maps_url = row.googleMapsUrl ?: ""
                )
            )
        }

        return ImportPlan(
            fileType = parsed.fileType,
            totalRows = parsed.totalRows,
            validRows = devicesToUpsert.size,
            toInsertCount = insertCount,
            toUpdateCount = updateCount,
            warnings = warnings,
            errors = errors,
            devicesToUpsert = devicesToUpsert
        )
    }
}
