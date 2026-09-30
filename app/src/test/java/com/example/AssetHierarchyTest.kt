package com.example

import org.junit.Assert.*
import org.junit.Test

class AssetHierarchyTest {

    @Test
    fun testSubstationEntity() {
        val substation = Substation(
            id = 10,
            substation_code = "110_GĐ",
            name = "Trạm 110kV Gò Đậu",
            address = "Thủ Dầu Một, Bình Dương",
            status = "ACTIVE"
        )
        assertEquals(10, substation.id)
        assertEquals("110_GĐ", substation.substation_code)
        assertEquals("Trạm 110kV Gò Đậu", substation.name)
        assertEquals("Thủ Dầu Một, Bình Dương", substation.address)
        assertEquals("ACTIVE", substation.status)
    }

    @Test
    fun testFeederEntity() {
        val feeder = Feeder(
            id = 101,
            substation_id = 10,
            feeder_code = "471_GĐ",
            name = "Phát tuyến 471 Gò Đậu",
            start_point = "Trạm 110kV Gò Đậu",
            end_point = "KCN Đại Đăng",
            status = "ACTIVE"
        )
        assertEquals(101, feeder.id)
        assertEquals(10, feeder.substation_id)
        assertEquals("471_GĐ", feeder.feeder_code)
        assertEquals("Phát tuyến 471 Gò Đậu", feeder.name)
        assertEquals("ACTIVE", feeder.status)
    }

    @Test
    fun testDeviceEntity() {
        val device = Device(
            id = 501,
            device_id = "REC_471_01",
            name = "Recloser 471/01",
            device_type = "REC",
            feeder_id = 101,
            substation_id = 10,
            pole_number = "T.12",
            switch_status = "CLOSED",
            scada_status = "CONNECT",
            relay_79 = "ON"
        )
        assertEquals(501, device.id)
        assertEquals("REC_471_01", device.device_id)
        assertEquals("REC", device.device_type)
        assertEquals(101, device.feeder_id)
        assertEquals(10, device.substation_id)
        assertEquals("T.12", device.pole_number)
    }

    @Test
    fun testAssetHierarchyRelationships() {
        val sub = Substation(id = 1, substation_code = "110_VT", name = "Trạm Vĩnh Trường", address = "Bình Dương")
        val feeder = Feeder(id = 11, substation_id = 1, feeder_code = "472_VT", name = "Tuyến 472 VT")
        val dev = Device(id = 101, device_id = "LBS_472_05", name = "LBS 472/05", device_type = "LBS", feeder_id = 11, substation_id = 1)

        assertEquals(sub.id, feeder.substation_id)
        assertEquals(feeder.id, dev.feeder_id)
        assertEquals(sub.id, dev.substation_id)
    }

    @Test
    fun testSubstationFeederCount() {
        val feeders = listOf(
            Feeder(id = 1, substation_id = 100, feeder_code = "471_A", name = "Tuyến A"),
            Feeder(id = 2, substation_id = 100, feeder_code = "472_A", name = "Tuyến B"),
            Feeder(id = 3, substation_id = 200, feeder_code = "471_B", name = "Tuyến C")
        )

        val countSub100 = feeders.count { it.substation_id == 100 }
        val countSub200 = feeders.count { it.substation_id == 200 }
        val countSub300 = feeders.count { it.substation_id == 300 }

        assertEquals(2, countSub100)
        assertEquals(1, countSub200)
        assertEquals(0, countSub300)
    }

    @Test
    fun testFeederDeviceCount() {
        val devices = listOf(
            Device(id = 1, feeder_id = 10, substation_id = 1, device_id = "D1", name = "Thiết bị 1", device_type = "LBS"),
            Device(id = 2, feeder_id = 10, substation_id = 1, device_id = "D2", name = "Thiết bị 2", device_type = "REC"),
            Device(id = 3, feeder_id = 20, substation_id = 1, device_id = "D3", name = "Thiết bị 3", device_type = "DS")
        )

        val countFeeder10 = devices.count { it.feeder_id == 10 }
        val countFeeder20 = devices.count { it.feeder_id == 20 }

        assertEquals(2, countFeeder10)
        assertEquals(1, countFeeder20)
    }

    @Test
    fun testSubstationDeviceCount() {
        val devices = listOf(
            Device(id = 1, feeder_id = 10, substation_id = 1, device_id = "D1", name = "Thiết bị 1", device_type = "LBS"),
            Device(id = 2, feeder_id = 11, substation_id = 1, device_id = "D2", name = "Thiết bị 2", device_type = "REC"),
            Device(id = 3, feeder_id = 20, substation_id = 2, device_id = "D3", name = "Thiết bị 3", device_type = "DS")
        )

        val countSub1 = devices.count { it.substation_id == 1 }
        val countSub2 = devices.count { it.substation_id == 2 }

        assertEquals(2, countSub1)
        assertEquals(1, countSub2)
    }

    @Test
    fun testDeviceFilterBySubstationAndFeeder() {
        val devices = listOf(
            Device(id = 1, feeder_id = 10, substation_id = 1, device_id = "D1", name = "REC 471/01", device_type = "REC"),
            Device(id = 2, feeder_id = 11, substation_id = 1, device_id = "D2", name = "LBS 472/02", device_type = "LBS"),
            Device(id = 3, feeder_id = 20, substation_id = 2, device_id = "D3", name = "REC 471/03", device_type = "REC")
        )

        val selectedSubstationId: Int? = 1
        val selectedFeederId: Int? = 10

        val filtered = devices.filter { dev ->
            val matchSubstation = selectedSubstationId == null || dev.substation_id == selectedSubstationId
            val matchFeeder = selectedFeederId == null || dev.feeder_id == selectedFeederId
            matchSubstation && matchFeeder
        }

        assertEquals(1, filtered.size)
        assertEquals("REC 471/01", filtered.first().name)
    }

    @Test
    fun testFeederFilterBySubstation() {
        val feeders = listOf(
            Feeder(id = 10, substation_id = 1, feeder_code = "471", name = "P471"),
            Feeder(id = 11, substation_id = 1, feeder_code = "472", name = "P472"),
            Feeder(id = 20, substation_id = 2, feeder_code = "471", name = "P471_B")
        )

        val selectedSubstationId: Int? = 1
        val filtered = feeders.filter { selectedSubstationId == null || it.substation_id == selectedSubstationId }

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.substation_id == 1 })
    }

    @Test
    fun testDeviceSubstationFeederMismatchDetection() {
        val feeder = Feeder(id = 10, substation_id = 1, feeder_code = "471", name = "P471")
        val devConsistent = Device(id = 1, feeder_id = 10, substation_id = 1, device_id = "D1", name = "Thiết bị 1", device_type = "LBS")
        val devInconsistent = Device(id = 2, feeder_id = 10, substation_id = 999, device_id = "D2", name = "Thiết bị 2", device_type = "REC")

        val isConsistent = devConsistent.substation_id == feeder.substation_id
        val isInconsistent = devInconsistent.substation_id != feeder.substation_id

        assertTrue(isConsistent)
        assertTrue(isInconsistent)
    }

    @Test
    fun testOrphanDataAuditReport() {
        val substations = mapOf(1 to Substation(id = 1, substation_code = "110_GĐ", name = "Gò Đậu"))
        val feeders = mapOf(
            10 to Feeder(id = 10, substation_id = 1, feeder_code = "471", name = "P471"),
            20 to Feeder(id = 20, substation_id = 999, feeder_code = "472", name = "P472 Orphan") // missing substation
        )
        val devices = listOf(
            Device(id = 1, feeder_id = 10, substation_id = 1, device_id = "D1", name = "Thiết bị 1", device_type = "LBS"),
            Device(id = 2, feeder_id = 888, substation_id = 1, device_id = "D2", name = "Thiết bị 2", device_type = "REC"), // missing feeder
            Device(id = 3, feeder_id = 10, substation_id = 2, device_id = "D3", name = "Thiết bị 3", device_type = "DS") // mismatched substation
        )

        val feedersMissingSubstation = feeders.values.count { f -> !substations.containsKey(f.substation_id) }
        val devicesMissingFeeder = devices.count { d -> d.feeder_id == null || !feeders.containsKey(d.feeder_id) }
        val devicesMismatchedSubstation = devices.count { d ->
            val parentFeeder = feeders[d.feeder_id]
            parentFeeder != null && d.substation_id != null && d.substation_id != parentFeeder.substation_id
        }

        val report = OrphanDataReport(
            feedersMissingSubstation = feedersMissingSubstation,
            devicesMissingFeeder = devicesMissingFeeder,
            devicesMismatchedSubstation = devicesMismatchedSubstation
        )

        assertEquals(1, report.feedersMissingSubstation)
        assertEquals(1, report.devicesMissingFeeder)
        assertEquals(1, report.devicesMismatchedSubstation)
    }

    @Test
    fun testScreenEnumValues() {
        val screens = Screen.values()
        assertTrue(screens.contains(Screen.STATION_DETAIL))
        assertTrue(screens.contains(Screen.FEEDER_DETAIL))
        assertTrue(screens.contains(Screen.SUBSTATIONS))
        assertTrue(screens.contains(Screen.FEEDERS))
        assertTrue(screens.contains(Screen.DEVICES))
        assertTrue(screens.contains(Screen.DEVICE_DETAIL))
    }
}
