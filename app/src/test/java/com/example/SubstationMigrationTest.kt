package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SubstationMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_substation_migration.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun testMigration6To7_PreservesExistingDataAndAddsNewFieldsWithDefaults() {
        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v6 substations table without location/image fields
                    db.execSQL(
                        """
                        CREATE TABLE `substations` (
                            `id` INTEGER NOT NULL,
                            `substation_code` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `address` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `notes` TEXT NOT NULL,
                            `created_at` TEXT NOT NULL,
                            `updated_at` TEXT NOT NULL,
                            `device_count` INTEGER NOT NULL,
                            `feeder_count` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
        val writableDb = helper.writableDatabase

        // Insert legacy substation record under version 6
        writableDb.execSQL(
            """
            INSERT INTO `substations` (
                `id`, `substation_code`, `name`, `address`, `status`,
                `notes`, `created_at`, `updated_at`, `device_count`, `feeder_count`
            ) VALUES (
                10, '110_TEST', 'Trạm 110kV Cũ', '123 Điện Biên Phủ, Đà Nẵng', 'ACTIVE',
                'Trạm trọng điểm', '2026-01-01 08:00:00', '2026-01-02 09:00:00', 14, 4
            )
            """.trimIndent()
        )

        // Execute MIGRATION_6_7
        AppDatabase.MIGRATION_6_7.migrate(writableDb)

        // Verify table still exists and query migrated record
        val cursor = writableDb.query("SELECT * FROM `substations` WHERE `id` = 10")
        assertTrue("Substations record should exist after migration", cursor.moveToFirst())

        // Verify all legacy fields are unchanged
        val idIdx = cursor.getColumnIndexOrThrow("id")
        val codeIdx = cursor.getColumnIndexOrThrow("substation_code")
        val nameIdx = cursor.getColumnIndexOrThrow("name")
        val addrIdx = cursor.getColumnIndexOrThrow("address")
        val statusIdx = cursor.getColumnIndexOrThrow("status")
        val notesIdx = cursor.getColumnIndexOrThrow("notes")
        val crIdx = cursor.getColumnIndexOrThrow("created_at")
        val upIdx = cursor.getColumnIndexOrThrow("updated_at")
        val devCountIdx = cursor.getColumnIndexOrThrow("device_count")
        val fdrCountIdx = cursor.getColumnIndexOrThrow("feeder_count")

        assertEquals(10, cursor.getInt(idIdx))
        assertEquals("110_TEST", cursor.getString(codeIdx))
        assertEquals("Trạm 110kV Cũ", cursor.getString(nameIdx))
        assertEquals("123 Điện Biên Phủ, Đà Nẵng", cursor.getString(addrIdx))
        assertEquals("ACTIVE", cursor.getString(statusIdx))
        assertEquals("Trạm trọng điểm", cursor.getString(notesIdx))
        assertEquals("2026-01-01 08:00:00", cursor.getString(crIdx))
        assertEquals("2026-01-02 09:00:00", cursor.getString(upIdx))
        assertEquals(14, cursor.getInt(devCountIdx))
        assertEquals(4, cursor.getInt(fdrCountIdx))

        // Verify all 6 new fields exist and have correct defaults
        val latIdx = cursor.getColumnIndexOrThrow("latitude")
        val lngIdx = cursor.getColumnIndexOrThrow("longitude")
        val gmapsIdx = cursor.getColumnIndexOrThrow("google_maps_url")
        val primImgIdx = cursor.getColumnIndexOrThrow("primary_image")
        val imgsIdx = cursor.getColumnIndexOrThrow("imagesJson")
        val locHistIdx = cursor.getColumnIndexOrThrow("locationHistoryJson")

        assertTrue("latitude should be NULL by default", cursor.isNull(latIdx))
        assertTrue("longitude should be NULL by default", cursor.isNull(lngIdx))
        assertEquals("", cursor.getString(gmapsIdx))
        assertEquals("", cursor.getString(primImgIdx))
        assertEquals("[]", cursor.getString(imgsIdx))
        assertEquals("[]", cursor.getString(locHistIdx))

        cursor.close()
        writableDb.close()
    }

    @Test
    fun testRoomDatabase_Version7_ReadWriteAndUpdateFieldsIndependently() = runBlocking {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = Repository(inMemoryDb)

        val initialSub = Substation(
            id = 100,
            substation_code = "110_HOAPHO",
            name = "Trạm 110kV Hòa Thọ",
            address = "Quận Cẩm Lệ",
            device_count = 5,
            feeder_count = 2
        )
        repo.insertSubstation(initialSub)

        val fetched = repo.getSubstationByIdDirect(100)
        assertNotNull(fetched)
        assertEquals(100, fetched!!.id)
        assertEquals("110_HOAPHO", fetched.substation_code)
        assertEquals("Trạm 110kV Hòa Thọ", fetched.name)
        assertNull(fetched.latitude)
        assertNull(fetched.longitude)
        assertEquals("", fetched.google_maps_url)
        assertEquals("", fetched.primary_image)
        assertEquals("[]", fetched.imagesJson)
        assertEquals("[]", fetched.locationHistoryJson)

        // Update location only, verify images are NOT lost
        val withLocation = fetched.copy(
            latitude = 16.0321,
            longitude = 108.2045,
            google_maps_url = "https://maps.google.com/?q=16.0321,108.2045"
        )
        repo.updateSubstation(withLocation)

        val fetchedAfterLoc = repo.getSubstationByIdDirect(100)
        assertNotNull(fetchedAfterLoc)
        assertEquals(16.0321, fetchedAfterLoc!!.latitude!!, 0.0001)
        assertEquals(108.2045, fetchedAfterLoc.longitude!!, 0.0001)
        assertEquals("https://maps.google.com/?q=16.0321,108.2045", fetchedAfterLoc.google_maps_url)
        assertEquals("", fetchedAfterLoc.primary_image)
        assertEquals("[]", fetchedAfterLoc.imagesJson)
        assertEquals("[]", fetchedAfterLoc.locationHistoryJson)

        // Update image only, verify latitude and longitude are NOT lost
        val withImage = fetchedAfterLoc.copy(
            primary_image = "substation_100_primary.jpg",
            imagesJson = "[{\"id\":1,\"image_url\":\"substation_100_primary.jpg\"}]"
        )
        repo.updateSubstation(withImage)

        val fetchedAfterImg = repo.getSubstationByIdDirect(100)
        assertNotNull(fetchedAfterImg)
        assertEquals(16.0321, fetchedAfterImg!!.latitude!!, 0.0001)
        assertEquals(108.2045, fetchedAfterImg.longitude!!, 0.0001)
        assertEquals("https://maps.google.com/?q=16.0321,108.2045", fetchedAfterImg.google_maps_url)
        assertEquals("substation_100_primary.jpg", fetchedAfterImg.primary_image)
        assertEquals("[{\"id\":1,\"image_url\":\"substation_100_primary.jpg\"}]", fetchedAfterImg.imagesJson)

        inMemoryDb.close()
    }
}
