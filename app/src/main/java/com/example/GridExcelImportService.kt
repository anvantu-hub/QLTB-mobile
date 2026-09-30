package com.example

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class GridExcelImportService(
    private val database: AppDatabase
) {
    private val substationDao = database.substationDao()
    private val feederDao = database.feederDao()
    private val deviceDao = database.deviceDao()

    suspend fun prepareImport(inputStream: InputStream, filenameHint: String? = null): ImportPlan {
        return withContext(Dispatchers.IO) {
            val parsedResult = GridExcelParser.parse(inputStream, filenameHint)

            when (parsedResult.fileType) {
                GridExcelFileType.SUBSTATION -> {
                    val existing = substationDao.getAllSubstationsNow()
                    GridExcelImportValidator.validateSubstations(parsedResult, existing)
                }
                GridExcelFileType.FEEDER -> {
                    val substations = substationDao.getAllSubstationsNow()
                    val existingFeeders = feederDao.getAllFeedersNow()
                    GridExcelImportValidator.validateFeeders(parsedResult, substations, existingFeeders)
                }
                GridExcelFileType.LBS, GridExcelFileType.REC -> {
                    val substations = substationDao.getAllSubstationsNow()
                    val feeders = feederDao.getAllFeedersNow()
                    val existingDevices = deviceDao.getAllDevicesNow()
                    GridExcelImportValidator.validateDevices(
                        parsedResult,
                        substations,
                        feeders,
                        existingDevices
                    )
                }
                GridExcelFileType.UNKNOWN -> {
                    ImportPlan(
                        fileType = GridExcelFileType.UNKNOWN,
                        totalRows = parsedResult.totalRows,
                        validRows = 0,
                        toInsertCount = 0,
                        toUpdateCount = 0,
                        warnings = parsedResult.warnings,
                        errors = if (parsedResult.errors.isNotEmpty()) parsedResult.errors else listOf(
                            RowMessage(
                                rowNumber = 1,
                                message = "Không nhận diện được định dạng tệp Excel từ tiêu đề các cột.",
                                isError = true,
                                identifier = null
                            )
                        )
                    )
                }
            }
        }
    }

    suspend fun executeImport(plan: ImportPlan): ImportExecutionResult {
        return withContext(Dispatchers.IO) {
            if (!plan.canExecute) {
                return@withContext ImportExecutionResult(
                    fileType = plan.fileType,
                    insertedCount = 0,
                    updatedCount = 0,
                    isSuccess = false,
                    errorMessage = "Kế hoạch import có lỗi hoặc không có dữ liệu hợp lệ để ghi."
                )
            }

            try {
                // Execute in single transaction using Room's withTransaction
                database.withTransaction {
                    val activeLoops = database.loopV2Dao().getAllLoopsV2Now()
                    if (activeLoops.isNotEmpty()) {
                        when (plan.fileType) {
                            GridExcelFileType.FEEDER -> {
                                for (newFeeder in plan.feedersToUpsert) {
                                    val oldFeeder = feederDao.getFeederByIdDirect(newFeeder.id)
                                    if (oldFeeder != null && newFeeder.substation_id != oldFeeder.substation_id) {
                                        for (loop in activeLoops) {
                                            if (newFeeder.id == loop.feederAId && newFeeder.substation_id != loop.stationAId) {
                                                throw IllegalStateException("Dòng import thay đổi Trạm của Lộ A '${newFeeder.name}' (ID: ${newFeeder.id}) làm hỏng Vòng khép V2 '#${loop.name}' (ID: ${loop.id}).")
                                            }
                                            if (newFeeder.id == loop.feederBId && newFeeder.substation_id != loop.stationBId) {
                                                throw IllegalStateException("Dòng import thay đổi Trạm của Lộ B '${newFeeder.name}' (ID: ${newFeeder.id}) làm hỏng Vòng khép V2 '#${loop.name}' (ID: ${loop.id}).")
                                            }
                                        }
                                    }
                                }
                            }
                            GridExcelFileType.LBS, GridExcelFileType.REC -> {
                                for (newDev in plan.devicesToUpsert) {
                                    val oldDev = deviceDao.getDeviceById(newDev.id)
                                    if (oldDev != null && (newDev.feeder_id != oldDev.feeder_id || newDev.substation_id != oldDev.substation_id)) {
                                        for (loop in activeLoops) {
                                            val sideAIds = loop.getSideADeviceIds()
                                            val sideBIds = loop.getSideBDeviceIds()
                                            if (newDev.id in sideAIds && newDev.feeder_id != loop.feederAId) {
                                                throw IllegalStateException("Dòng import thay đổi Lộ của thiết bị '${newDev.name}' (ID: ${newDev.id}) làm hỏng Phía A Vòng khép V2 '#${loop.name}' (ID: ${loop.id}).")
                                            }
                                            if (newDev.id in sideBIds && newDev.feeder_id != loop.feederBId) {
                                                throw IllegalStateException("Dòng import thay đổi Lộ của thiết bị '${newDev.name}' (ID: ${newDev.id}) làm hỏng Phía B Vòng khép V2 '#${loop.name}' (ID: ${loop.id}).")
                                            }
                                            if (newDev.id == loop.legalBoundaryDeviceId) {
                                                val parentFeeder = newDev.feeder_id?.let { feederDao.getFeederByIdDirect(it) }
                                                val effectiveStation = parentFeeder?.substation_id ?: newDev.substation_id
                                                if (effectiveStation != loop.stationAId) {
                                                    throw IllegalStateException("Dòng import thay đổi Trạm/Lộ của điểm dừng '${newDev.name}' (ID: ${newDev.id}) làm hỏng Trạm A Vòng khép V2 '#${loop.name}' (ID: ${loop.id}).")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    when (plan.fileType) {
                        GridExcelFileType.SUBSTATION -> {
                            substationDao.insertAll(plan.substationsToUpsert)
                        }
                        GridExcelFileType.FEEDER -> {
                            feederDao.insertAll(plan.feedersToUpsert)
                        }
                        GridExcelFileType.LBS, GridExcelFileType.REC -> {
                            deviceDao.insertAll(plan.devicesToUpsert)
                        }
                        GridExcelFileType.UNKNOWN -> {
                            throw IllegalStateException("Loại tệp không hợp lệ")
                        }
                    }
                }

                ImportExecutionResult(
                    fileType = plan.fileType,
                    insertedCount = plan.toInsertCount,
                    updatedCount = plan.toUpdateCount,
                    isSuccess = true
                )
            } catch (e: Exception) {
                ImportExecutionResult(
                    fileType = plan.fileType,
                    insertedCount = 0,
                    updatedCount = 0,
                    isSuccess = false,
                    errorMessage = "Lỗi ghi CSDL trong giao dịch: ${e.localizedMessage}"
                )
            }
        }
    }
}
