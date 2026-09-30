package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LoopV2Test {

    private lateinit var db: AppDatabase
    private lateinit var repository: Repository
    private lateinit var excelService: GridExcelImportService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = Repository(db)
        excelService = GridExcelImportService(db)

        runBlocking {
            // Seed base infrastructure
            repository.insertSubstation(Substation(id = 1, substation_code = "T110A", name = "Trạm 110kV A"))
            repository.insertSubstation(Substation(id = 2, substation_code = "T110B", name = "Trạm 110kV B"))

            repository.insertFeeder(Feeder(id = 101, name = "Lộ 471 A", feeder_code = "471A", substation_id = 1))
            repository.insertFeeder(Feeder(id = 201, name = "Lộ 472 B", feeder_code = "472B", substation_id = 2))

            repository.insertDevice(Device(id = 10, device_id = "REC_471_01", name = "Recloser 471/01", device_type = "REC", feeder_id = 101, substation_id = 1))
            repository.insertDevice(Device(id = 11, device_id = "REC_471_02", name = "Recloser 471/02", device_type = "REC", feeder_id = 101, substation_id = 1))

            repository.insertDevice(Device(id = 20, device_id = "REC_472_01", name = "Recloser 472/01", device_type = "REC", feeder_id = 201, substation_id = 2))

            repository.insertDevice(Device(id = 50, device_id = "LBS_BOUNDARY", name = "LBS Lien Lac 471-472", device_type = "LBS", feeder_id = 101, substation_id = 1))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBoundaryDeviceBelongsToStationB_Rejected() = runBlocking {
        // Create boundary device belonging to Station 2 (Station B)
        val devStationB = Device(id = 99, device_id = "LBS_STATION_B", name = "LBS Thuoc Tram B", device_type = "LBS", feeder_id = 201, substation_id = 2)
        repository.insertDevice(devStationB)

        val invalidLoop = LoopV2(
            id = 1,
            name = "Vòng Lỗi Điểm Dừng Phía B",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10]",
            legalBoundaryDeviceId = 99, // Belongs to Station 2!
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2,
            layout = "",
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(invalidLoop, isUpdate = false)
        assertTrue(result is LoopV2SaveResult.ValidationError)
        val msg = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(msg.contains("phải thuộc Trạm A") || msg.contains("thuộc Trạm khác Trạm A"))
    }

    @Test
    fun testBoundaryDeviceSubstationAWithParentFeederInStationBOrMissing_Rejected() = runBlocking {
        // Boundary device with station 1 but feeder 201 (Station B)
        val devContradictory = Device(id = 98, device_id = "LBS_CONTRADICTORY", name = "LBS Mau Thuan", device_type = "LBS", feeder_id = 201, substation_id = 1)
        repository.insertDevice(devContradictory)

        val loop1 = LoopV2(
            id = 1,
            name = "Vòng Lỗi Mau Thuan Feeder",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10]",
            legalBoundaryDeviceId = 98,
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2
        )

        val result1 = repository.validateAndSaveLoopV2(loop1, isUpdate = false)
        assertTrue(result1 is LoopV2SaveResult.ValidationError)

        // Boundary device with non-existent parent feeder
        val devMissingFeeder = Device(id = 97, device_id = "LBS_MISSING_FEEDER", name = "LBS Feeder Khong Ton Tai", device_type = "LBS", feeder_id = 9999, substation_id = 1)
        repository.insertDevice(devMissingFeeder)

        val loop2 = loop1.copy(legalBoundaryDeviceId = 97)
        val result2 = repository.validateAndSaveLoopV2(loop2, isUpdate = false)
        assertTrue(result2 is LoopV2SaveResult.ValidationError)
    }

    @Test
    fun testSameFeederAAndB_Rejected() = runBlocking {
        val sameFeederLoop = LoopV2(
            id = 1,
            name = "Vòng Trùng Phát Tuyến A và B",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10]",
            legalBoundaryDeviceId = 50,
            sideBDeviceIdsJson = "[11]",
            feederBId = 101, // Same as feederAId!
            stationBId = 1
        )

        val result = repository.validateAndSaveLoopV2(sameFeederLoop, isUpdate = false)
        assertTrue(result is LoopV2SaveResult.ValidationError)
        val msg = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(msg.contains("phải khác nhau"))
    }

    @Test
    fun testEmptySideAOrSideB_Rejected() = runBlocking {
        val emptySideALoop = LoopV2(
            id = 1,
            name = "Vòng Phía A Rỗng",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[]",
            legalBoundaryDeviceId = 50,
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2
        )

        val result = repository.validateAndSaveLoopV2(emptySideALoop, isUpdate = false)
        assertTrue(result is LoopV2SaveResult.ValidationError)
        val msg = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(msg.contains("không được để rỗng"))
    }

    @Test
    fun testCorruptedJsonStrings() = runBlocking {
        val invalidResult = parseJsonIdList("[1, \"sai\", 2]")
        assertTrue(invalidResult is JsonIdListResult.Invalid)

        val badJsonLoop = LoopV2(
            id = 1,
            name = "Vòng JSON Hỏng",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10, \"sai\", 11]",
            legalBoundaryDeviceId = 50,
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2
        )

        val result = repository.validateAndSaveLoopV2(badJsonLoop, isUpdate = false)
        assertTrue(result is LoopV2SaveResult.ValidationError)
        val msg = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(msg.contains("hỏng JSON"))
    }

    @Test
    fun testDeleteAndMoveDeviceOfLoop_Blocked() = runBlocking {
        // First save a valid Loop V2
        val validLoop = LoopV2(
            id = 1,
            name = "Vòng Khép Chuẩn 1",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10]",
            legalBoundaryDeviceId = 50,
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2,
            version = 1
        )

        val saveResult = repository.validateAndSaveLoopV2(validLoop, isUpdate = false)
        assertTrue(saveResult is LoopV2SaveResult.Success)

        // Attempt to delete device 10 (participating in loop)
        var deleteFailed = false
        try {
            val dev10 = db.deviceDao().getDeviceById(10)!!
            repository.deleteDevice(dev10)
        } catch (e: IllegalStateException) {
            deleteFailed = true
            assertTrue(e.message!!.contains("do đang tham gia sơ đồ Khép vòng V2"))
        }
        assertTrue("Xóa thiết bị đang tham gia loop phải bị chặn!", deleteFailed)

        // Attempt to move device 10 to feeder 201
        var moveFailed = false
        try {
            val dev10 = db.deviceDao().getDeviceById(10)!!
            repository.updateDevice(dev10.copy(feeder_id = 201))
        } catch (e: IllegalStateException) {
            moveFailed = true
            assertTrue(e.message!!.contains("Không thể chuyển Phát tuyến"))
        }
        assertTrue("Chuyển lộ thiết bị đang tham gia loop phải bị chặn!", moveFailed)
    }

    @Test
    fun testExcelImportBreaksLoop_Rollback() = runBlocking {
        // Save valid Loop V2
        val validLoop = LoopV2(
            id = 1,
            name = "Vòng Khép Excel Test",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10]",
            legalBoundaryDeviceId = 50,
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2,
            version = 1
        )
        repository.validateAndSaveLoopV2(validLoop, isUpdate = false)

        // Prepare import plan changing device 10's feeder from 101 to 201
        val updatedDev10 = Device(id = 10, device_id = "REC_471_01", name = "Recloser 471/01", device_type = "REC", feeder_id = 201, substation_id = 2)
        val plan = ImportPlan(
            fileType = GridExcelFileType.REC,
            totalRows = 1,
            validRows = 1,
            toInsertCount = 0,
            toUpdateCount = 1,
            warnings = emptyList(),
            errors = emptyList(),
            devicesToUpsert = listOf(updatedDev10)
        )

        val result = excelService.executeImport(plan)
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("làm hỏng Phía A Vòng khép V2"))

        // Verify device 10 in DB remains unchanged
        val dev10InDb = db.deviceDao().getDeviceById(10)
        assertEquals(101, dev10InDb?.feeder_id)
    }

    @Test
    fun testExcelImportChangeNameOnly_Succeeds() = runBlocking {
        // Save valid Loop V2
        val validLoop = LoopV2(
            id = 1,
            name = "Vòng Khép Excel Name Test",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10]",
            legalBoundaryDeviceId = 50,
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2,
            version = 1
        )
        repository.validateAndSaveLoopV2(validLoop, isUpdate = false)

        // Prepare import plan changing ONLY device 10's name
        val updatedDev10Name = Device(id = 10, device_id = "REC_471_01", name = "Recloser 471/01 Doi Ten", device_type = "REC", feeder_id = 101, substation_id = 1)
        val planNameOnly = ImportPlan(
            fileType = GridExcelFileType.REC,
            totalRows = 1,
            validRows = 1,
            toInsertCount = 0,
            toUpdateCount = 1,
            warnings = emptyList(),
            errors = emptyList(),
            devicesToUpsert = listOf(updatedDev10Name)
        )

        val result = excelService.executeImport(planNameOnly)
        assertTrue(result.isSuccess)

        // Verify device 10 name was updated
        val dev10InDb = db.deviceDao().getDeviceById(10)
        assertEquals("Recloser 471/01 Doi Ten", dev10InDb?.name)
    }

    @Test
    fun testOptimisticLockingConcurrentEdit_SecondFails() = runBlocking {
        val loop = LoopV2(
            id = 100,
            name = "Vòng Lock Test",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10]",
            legalBoundaryDeviceId = 50,
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2,
            version = 1
        )

        val res1 = repository.validateAndSaveLoopV2(loop, isUpdate = false)
        assertTrue("res1 was not Success, instead got: $res1", res1 is LoopV2SaveResult.Success)
        val generatedId = (res1 as LoopV2SaveResult.Success).loopId

        // User A updates version 1 -> success
        val updateA = loop.copy(id = generatedId, name = "Sửa bởi A", version = 1)
        val resA = repository.validateAndSaveLoopV2(updateA, isUpdate = true)
        assertTrue("resA was not Success, instead got: $resA", resA is LoopV2SaveResult.Success)

        // User B tries to update using stale version 1 -> fails with VersionConflict
        val updateB = loop.copy(id = generatedId, name = "Sửa bởi B", version = 1)
        val resB = repository.validateAndSaveLoopV2(updateB, isUpdate = true)
        assertTrue("resB was not VersionConflict, instead got: $resB", resB is LoopV2SaveResult.VersionConflict)
    }

    @Test
    fun testLoopPersistedAfterReopenDatabase() = runBlocking {
        val loop = LoopV2(
            id = 55,
            name = "Vòng Lồng Persistence",
            stationAId = 1,
            feederAId = 101,
            sideADeviceIdsJson = "[10, 11]",
            legalBoundaryDeviceId = 50,
            sideBDeviceIdsJson = "[20]",
            feederBId = 201,
            stationBId = 2,
            version = 1
        )

        val res = repository.validateAndSaveLoopV2(loop, isUpdate = false)
        assertTrue("res was not Success, instead got: $res", res is LoopV2SaveResult.Success)
        val generatedId = (res as LoopV2SaveResult.Success).loopId

        val loadedLoop = db.loopV2Dao().getLoopById(generatedId)
        assertNotNull(loadedLoop)
        assertEquals("Vòng Lồng Persistence", loadedLoop?.name)
        assertEquals(listOf(10, 11), loadedLoop?.getSideADeviceIds())
    }
}
