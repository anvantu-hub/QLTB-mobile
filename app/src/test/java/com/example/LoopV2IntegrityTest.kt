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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LoopV2IntegrityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = Repository(db)

        runBlocking {
            // Setup base valid infrastructure
            repository.insertSubstation(Substation(id = 1, substation_code = "SUB_A", name = "Trạm A"))
            repository.insertSubstation(Substation(id = 2, substation_code = "SUB_B", name = "Trạm B"))

            repository.insertFeeder(Feeder(id = 10, name = "Lộ 471", feeder_code = "471", substation_id = 1))
            repository.insertFeeder(Feeder(id = 20, name = "Lộ 472", feeder_code = "472", substation_id = 2))

            // Devices belonging to Station A / Feeder 10
            repository.insertDevice(Device(id = 101, device_id = "REC101", name = "Recloser 101", device_type = "REC", feeder_id = 10, substation_id = 1))
            repository.insertDevice(Device(id = 102, device_id = "REC102", name = "Recloser 102", device_type = "REC", feeder_id = 10, substation_id = 1))

            // Devices belonging to Station B / Feeder 20
            repository.insertDevice(Device(id = 201, device_id = "REC201", name = "Recloser 201", device_type = "REC", feeder_id = 20, substation_id = 2))
            repository.insertDevice(Device(id = 202, device_id = "REC202", name = "Recloser 202", device_type = "REC", feeder_id = 20, substation_id = 2))

            // Legal boundary device (belongs to Station A, but sits on Feeder 10)
            repository.insertDevice(Device(id = 300, device_id = "LBS300", name = "LBS Boundary", device_type = "LBS", feeder_id = 10, substation_id = 1))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testValidLoopV2_Succeeds() = runBlocking {
        val validLoop = LoopV2(
            id = 0,
            name = "Vòng khép chuẩn V2",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[101, 102]",
            legalBoundaryDeviceId = 300,
            sideBDeviceIdsJson = "[201, 202]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(validLoop, isUpdate = false)
        assertTrue("Expected save to succeed, but got: $result", result is LoopV2SaveResult.Success)
        val generatedId = (result as LoopV2SaveResult.Success).loopId
        val savedLoop = db.loopV2Dao().getLoopById(generatedId)
        assertNotNull(savedLoop)
        assertEquals("Vòng khép chuẩn V2", savedLoop?.name)
    }

    @Test
    fun testEmptySideA_Rejected() = runBlocking {
        val invalidLoop = LoopV2(
            id = 0,
            name = "Vòng khép lỗi phía A rỗng",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[]",
            legalBoundaryDeviceId = 300,
            sideBDeviceIdsJson = "[201]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(invalidLoop, isUpdate = false)
        assertTrue("Expected failure due to empty side A", result is LoopV2SaveResult.ValidationError)
        val message = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(message.contains("phía A không được để rỗng"))
    }

    @Test
    fun testEmptySideB_Rejected() = runBlocking {
        val invalidLoop = LoopV2(
            id = 0,
            name = "Vòng khép lỗi phía B rỗng",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[101]",
            legalBoundaryDeviceId = 300,
            sideBDeviceIdsJson = "[]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(invalidLoop, isUpdate = false)
        assertTrue("Expected failure due to empty side B", result is LoopV2SaveResult.ValidationError)
        val message = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(message.contains("phía B không được để rỗng"))
    }

    @Test
    fun testSameFeeders_Rejected() = runBlocking {
        val invalidLoop = LoopV2(
            id = 0,
            name = "Vòng khép trùng phát tuyến",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[101]",
            legalBoundaryDeviceId = 300,
            sideBDeviceIdsJson = "[102]",
            feederBId = 10, // Same feeder!
            stationBId = 1,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(invalidLoop, isUpdate = false)
        assertTrue("Expected failure due to identical feeders", result is LoopV2SaveResult.ValidationError)
        val message = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(message.contains("phải khác nhau"))
    }

    @Test
    fun testFeederStationMismatchSideA_Rejected() = runBlocking {
        // Device 101 belongs to feeder 10 (Station 1), but we supply Station B (id 2) as stationAId
        val mismatchedLoop = LoopV2(
            id = 0,
            name = "Vòng khép sai trạm của lộ A",
            stationAId = 2, // Station B instead of Station A
            feederAId = 10,
            sideADeviceIdsJson = "[101]",
            legalBoundaryDeviceId = 300,
            sideBDeviceIdsJson = "[201]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(mismatchedLoop, isUpdate = false)
        assertTrue("Expected validation error due to station-feeder mismatch", result is LoopV2SaveResult.ValidationError)
        val message = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(message.contains("không thuộc Trạm"))
    }

    @Test
    fun testDeviceFeederMismatchSideA_Rejected() = runBlocking {
        // Device 201 belongs to Feeder 20, but is listed in sideADeviceIdsJson (which has feederAId = 10)
        val mismatchedLoop = LoopV2(
            id = 0,
            name = "Vòng khép thiết bị phía A sai lộ",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[201]", // Belongs to feeder 20!
            legalBoundaryDeviceId = 300,
            sideBDeviceIdsJson = "[202]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(mismatchedLoop, isUpdate = false)
        assertTrue("Expected failure due to device feeder mismatch", result is LoopV2SaveResult.ValidationError)
        val message = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(message.contains("không thuộc Phát tuyến Lộ A"))
    }

    @Test
    fun testBoundaryDeviceNotBelongingToStationA_Rejected() = runBlocking {
        // Create an LBS sitting on Feeder 20 (Station B)
        repository.insertDevice(Device(id = 301, device_id = "LBS301", name = "Boundary in Station B", device_type = "LBS", feeder_id = 20, substation_id = 2))

        val invalidLoop = LoopV2(
            id = 0,
            name = "Vòng khép điểm dừng sai trạm",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[101]",
            legalBoundaryDeviceId = 301, // Belongs to Station 2!
            sideBDeviceIdsJson = "[201]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(invalidLoop, isUpdate = false)
        assertTrue("Expected failure due to boundary device not belonging to Station A", result is LoopV2SaveResult.ValidationError)
        val message = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(message.contains("phải thuộc Trạm A"))
    }

    @Test
    fun testCorruptedJsonSideA_Rejected() = runBlocking {
        val invalidLoop = LoopV2(
            id = 0,
            name = "Vòng khép hỏng JSON phía A",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[101, invalid_id]",
            legalBoundaryDeviceId = 300,
            sideBDeviceIdsJson = "[201]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(invalidLoop, isUpdate = false)
        assertTrue("Expected failure due to bad JSON format", result is LoopV2SaveResult.ValidationError)
        val message = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(message.contains("hỏng JSON"))
    }

    @Test
    fun testDuplicateDevicesAcrossSides_Rejected() = runBlocking {
        // Device 101 cannot be in both sides or boundary
        val duplicateLoop = LoopV2(
            id = 0,
            name = "Vòng khép trùng lặp thiết bị",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[101]",
            legalBoundaryDeviceId = 101, // Duplicate!
            sideBDeviceIdsJson = "[201]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        val result = repository.validateAndSaveLoopV2(duplicateLoop, isUpdate = false)
        assertTrue("Expected failure due to duplicate device assignment", result is LoopV2SaveResult.ValidationError)
        val message = (result as LoopV2SaveResult.ValidationError).message
        assertTrue(message.contains("trùng lặp"))
    }

    @Test
    fun testConcurrentOptimisticLocking_SavesFirstThenFailsSecond() = runBlocking {
        val loop = LoopV2(
            id = 0,
            name = "Vòng khép đồng thời",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[101]",
            legalBoundaryDeviceId = 300,
            sideBDeviceIdsJson = "[201]",
            feederBId = 20,
            stationBId = 2,
            version = 1
        )

        // Save first version
        val result1 = repository.validateAndSaveLoopV2(loop, isUpdate = false)
        assertTrue(result1 is LoopV2SaveResult.Success)
        val generatedId = (result1 as LoopV2SaveResult.Success).loopId

        // Transaction A modifies loop
        val updateA = loop.copy(id = generatedId, name = "Cập nhật bởi A", version = 1)
        val resultA = repository.validateAndSaveLoopV2(updateA, isUpdate = true)
        assertTrue("Update A should succeed", resultA is LoopV2SaveResult.Success)

        // Transaction B tries to modify stale version 1
        val updateB = loop.copy(id = generatedId, name = "Cập nhật bởi B", version = 1)
        val resultB = repository.validateAndSaveLoopV2(updateB, isUpdate = true)
        assertTrue("Update B should fail with VersionConflict", resultB is LoopV2SaveResult.VersionConflict)
    }
}
