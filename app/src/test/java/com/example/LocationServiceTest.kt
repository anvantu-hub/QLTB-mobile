package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocationServiceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = Repository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBuildGoogleMapsUrl() {
        val lat = 16.0544
        val lng = 108.2022
        val url = LocationService.buildGoogleMapsUrl(lat, lng)
        assertEquals("https://www.google.com/maps/search/?api=1&query=16.0544,108.2022", url)
    }

    @Test
    fun testIsValidGoogleMapsUrl() {
        assertTrue(LocationService.isValidGoogleMapsUrl("https://www.google.com/maps/search/?api=1&query=16.0,108.0"))
        assertTrue(LocationService.isValidGoogleMapsUrl("https://maps.google.com/?q=16.0,108.0"))
        assertTrue(LocationService.isValidGoogleMapsUrl("https://maps.app.goo.gl/abcxyz123"))
        assertTrue(LocationService.isValidGoogleMapsUrl("http://goo.gl/maps/abcxyz"))

        assertFalse(LocationService.isValidGoogleMapsUrl(""))
        assertFalse(LocationService.isValidGoogleMapsUrl("ftp://maps.google.com"))
        assertFalse(LocationService.isValidGoogleMapsUrl("invalid_string_not_url"))
        assertFalse(LocationService.isValidGoogleMapsUrl("https://facebook.com/something"))
    }

    @Test
    fun testAppendLocationHistory_PreservesOldEntries() {
        val jsonParser = Json { ignoreUnknownKeys = true }

        // Initial empty history
        val h1 = LocationService.appendLocationHistory(
            existingJson = "[]",
            oldLat = null,
            oldLng = null,
            newLat = 16.01,
            newLng = 108.01,
            mapsUrl = "https://www.google.com/maps/search/?api=1&query=16.01,108.01",
            updatedBy = "user1",
            note = "Lần đầu lấy GPS"
        )
        val list1 = jsonParser.decodeFromString<List<LocationHistoryItem>>(h1)
        assertEquals(1, list1.size)
        assertNull(list1[0].old_latitude)
        assertEquals(16.01, list1[0].new_latitude!!, 0.0001)

        // Second update -> must preserve first entry
        val h2 = LocationService.appendLocationHistory(
            existingJson = h1,
            oldLat = 16.01,
            oldLng = 108.01,
            newLat = 16.02,
            newLng = 108.02,
            mapsUrl = "https://www.google.com/maps/search/?api=1&query=16.02,108.02",
            updatedBy = "user2",
            note = "Cập nhật vị trí lần 2"
        )
        val list2 = jsonParser.decodeFromString<List<LocationHistoryItem>>(h2)
        assertEquals(2, list2.size)
        assertEquals(16.01, list2[1].old_latitude!!, 0.0001)
        assertEquals(16.02, list2[1].new_latitude!!, 0.0001)
        assertEquals("user2", list2[1].updated_by)
    }

    @Test
    fun testDeviceLocationPersistence_DoesNotAlterTechnicalSpecs() = runBlocking {
        val initialDev = Device(
            id = 50,
            device_id = "REC-50",
            name = "Recloser 50",
            device_type = "REC",
            pole_number = "T22/10",
            switch_status = "CLOSED",
            scada_status = "SIGNAL",
            relay_79 = "ON"
        )
        repository.insertDevice(initialDev)

        val fetched = repository.getDeviceById(50)
        assertNotNull(fetched)
        assertNull(fetched!!.latitude)
        assertNull(fetched.longitude)
        assertEquals("", fetched.google_maps_url)

        // Update GPS location
        val newLat = 16.0789
        val newLng = 108.2234
        val newUrl = LocationService.buildGoogleMapsUrl(newLat, newLng)
        val newHistory = LocationService.appendLocationHistory(
            existingJson = fetched.locationHistoryJson,
            oldLat = fetched.latitude,
            oldLng = fetched.longitude,
            newLat = newLat,
            newLng = newLng,
            mapsUrl = newUrl,
            updatedBy = "admin"
        )

        val updatedDev = fetched.copy(
            latitude = newLat,
            longitude = newLng,
            google_maps_url = newUrl,
            locationHistoryJson = newHistory
        )
        repository.updateDevice(updatedDev)

        val fetchedAfter = repository.getDeviceById(50)
        assertNotNull(fetchedAfter)
        assertEquals(newLat, fetchedAfter!!.latitude!!, 0.0001)
        assertEquals(newLng, fetchedAfter.longitude!!, 0.0001)
        assertEquals(newUrl, fetchedAfter.google_maps_url)

        // Technical specs must be intact
        assertEquals("REC-50", fetchedAfter.device_id)
        assertEquals("Recloser 50", fetchedAfter.name)
        assertEquals("REC", fetchedAfter.device_type)
        assertEquals("T22/10", fetchedAfter.pole_number)
        assertEquals("CLOSED", fetchedAfter.switch_status)
        assertEquals("SIGNAL", fetchedAfter.scada_status)
        assertEquals("ON", fetchedAfter.relay_79)

        // Manual update of URL does not erase lat/lng
        val manualUrl = "https://maps.app.goo.gl/custom123"
        val devWithManualUrl = fetchedAfter.copy(google_maps_url = manualUrl)
        repository.updateDevice(devWithManualUrl)

        val fetchedManual = repository.getDeviceById(50)
        assertNotNull(fetchedManual)
        assertEquals(manualUrl, fetchedManual!!.google_maps_url)
        assertEquals(newLat, fetchedManual.latitude!!, 0.0001)
        assertEquals(newLng, fetchedManual.longitude!!, 0.0001)
    }

    @Test
    fun testSubstationLocationPersistence_PreservesSubstationFields() = runBlocking {
        val initialSub = Substation(
            id = 20,
            substation_code = "110_LIENCHIEU",
            name = "Trạm 110kV Liên Chiểu",
            address = "KCN Liên Chiểu",
            status = "ACTIVE",
            device_count = 8,
            feeder_count = 3
        )
        repository.insertSubstation(initialSub)

        val fetched = repository.getSubstationByIdDirect(20)
        assertNotNull(fetched)
        assertNull(fetched!!.latitude)
        assertNull(fetched.longitude)

        val subLat = 16.1234
        val subLng = 108.1567
        val subUrl = LocationService.buildGoogleMapsUrl(subLat, subLng)
        val history = LocationService.appendLocationHistory(
            existingJson = fetched.locationHistoryJson,
            oldLat = fetched.latitude,
            oldLng = fetched.longitude,
            newLat = subLat,
            newLng = subLng,
            mapsUrl = subUrl,
            updatedBy = "engineer"
        )

        val updatedSub = fetched.copy(
            latitude = subLat,
            longitude = subLng,
            google_maps_url = subUrl,
            locationHistoryJson = history
        )
        repository.updateSubstation(updatedSub)

        val fetchedAfter = repository.getSubstationByIdDirect(20)
        assertNotNull(fetchedAfter)
        assertEquals(subLat, fetchedAfter!!.latitude!!, 0.0001)
        assertEquals(subLng, fetchedAfter.longitude!!, 0.0001)
        assertEquals(subUrl, fetchedAfter.google_maps_url)

        // Substation fields must be intact
        assertEquals(20, fetchedAfter.id)
        assertEquals("110_LIENCHIEU", fetchedAfter.substation_code)
        assertEquals("Trạm 110kV Liên Chiểu", fetchedAfter.name)
        assertEquals("KCN Liên Chiểu", fetchedAfter.address)
        assertEquals("ACTIVE", fetchedAfter.status)
        assertEquals(8, fetchedAfter.device_count)
        assertEquals(3, fetchedAfter.feeder_count)
    }

    @Test
    fun testOpenMapsAndDirections_Validations() {
        // When neither coordinates nor URL exists
        assertFalse(LocationService.openGoogleMaps(context, null, null, "", "Test"))
        assertFalse(LocationService.openDirections(context, null, null, ""))

        // When coordinates exist
        assertTrue(LocationService.openGoogleMaps(context, 16.0, 108.0, "", "Test"))
        assertTrue(LocationService.openDirections(context, 16.0, 108.0, ""))

        // When only URL exists
        assertTrue(LocationService.openGoogleMaps(context, null, null, "https://maps.google.com/?q=16.0,108.0", "Test"))
        assertTrue(LocationService.openDirections(context, null, null, "https://maps.google.com/?q=16.0,108.0"))
    }
}
