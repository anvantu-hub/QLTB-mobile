package com.example

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        User::class,
        Substation::class,
        Feeder::class,
        Device::class,
        Loop::class,
        AuditLog::class,
        LoopV2::class,
        DocCategory::class,
        LibraryDocument::class,
        com.example.library.DocumentEntity::class,
        com.example.camera.EquipmentImageEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun substationDao(): SubstationDao
    abstract fun feederDao(): FeederDao
    abstract fun deviceDao(): DeviceDao
    abstract fun loopDao(): LoopDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun loopV2Dao(): LoopV2Dao
    abstract fun docCategoryDao(): DocCategoryDao
    abstract fun libraryDocumentDao(): LibraryDocumentDao
    abstract fun documentDao(): com.example.library.DocumentDao
    abstract fun equipmentImageDao(): com.example.camera.EquipmentImageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun getColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
            val columns = mutableSetOf<String>()
            try {
                val cursor = db.query("PRAGMA table_info(`$tableName`)")
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1) {
                        columns.add(cursor.getString(nameIndex))
                    }
                }
                cursor.close()
            } catch (_: Exception) {}
            return columns
        }

        private fun ensureSchemaSynchronized(db: SupportSQLiteDatabase) {
            // 1. Users
            val userCols = getColumns(db, "users")
            val expectedUserCols = setOf("id", "employee_code", "full_name", "username", "email", "phone", "unit", "team", "title", "status", "created_at", "role")
            if (userCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users` (
                        `id` TEXT NOT NULL,
                        `employee_code` TEXT NOT NULL,
                        `full_name` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `team` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            } else if (!userCols.containsAll(expectedUserCols)) {
                db.execSQL(
                    """
                    CREATE TABLE `users_new` (
                        `id` TEXT NOT NULL,
                        `employee_code` TEXT NOT NULL,
                        `full_name` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `team` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val idCol = if (userCols.contains("id")) "`id`" else "''"
                val empCol = if (userCols.contains("employee_code")) "`employee_code`" else "''"
                val nameCol = if (userCols.contains("full_name")) "`full_name`" else "''"
                val userCol = if (userCols.contains("username")) "`username`" else "''"
                val emailCol = if (userCols.contains("email")) "`email`" else "''"
                val phoneCol = if (userCols.contains("phone")) "`phone`" else "''"
                val unitCol = if (userCols.contains("unit")) "`unit`" else "''"
                val teamCol = if (userCols.contains("team")) "`team`" else "''"
                val titleCol = if (userCols.contains("title")) "`title`" else "''"
                val statusCol = if (userCols.contains("status")) "`status`" else "'ACTIVE'"
                val createdCol = if (userCols.contains("created_at")) "`created_at`" else "''"
                val roleCol = if (userCols.contains("role")) "`role`" else "'STAFF'"
                db.execSQL("INSERT INTO `users_new` SELECT $idCol, $empCol, $nameCol, $userCol, $emailCol, $phoneCol, $unitCol, $teamCol, $titleCol, $statusCol, $createdCol, $roleCol FROM `users`")
                db.execSQL("DROP TABLE `users`")
                db.execSQL("ALTER TABLE `users_new` RENAME TO `users`")
            }

            // 2. Substations
            val subCols = getColumns(db, "substations")
            val expectedSubCols = setOf(
                "id", "substation_code", "name", "address", "status", "notes",
                "created_at", "updated_at", "device_count", "feeder_count",
                "latitude", "longitude", "google_maps_url", "primary_image",
                "imagesJson", "locationHistoryJson"
            )
            if (subCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `substations` (
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
                        `latitude` REAL,
                        `longitude` REAL,
                        `google_maps_url` TEXT NOT NULL DEFAULT '',
                        `primary_image` TEXT NOT NULL DEFAULT '',
                        `imagesJson` TEXT NOT NULL DEFAULT '[]',
                        `locationHistoryJson` TEXT NOT NULL DEFAULT '[]',
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            } else if (!subCols.containsAll(expectedSubCols)) {
                db.execSQL(
                    """
                    CREATE TABLE `substations_new` (
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
                        `latitude` REAL,
                        `longitude` REAL,
                        `google_maps_url` TEXT NOT NULL DEFAULT '',
                        `primary_image` TEXT NOT NULL DEFAULT '',
                        `imagesJson` TEXT NOT NULL DEFAULT '[]',
                        `locationHistoryJson` TEXT NOT NULL DEFAULT '[]',
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val idCol = if (subCols.contains("id")) "`id`" else "1"
                val codeCol = if (subCols.contains("substation_code")) "`substation_code`" else if (subCols.contains("code")) "`code`" else "''"
                val nameCol = if (subCols.contains("name")) "`name`" else "''"
                val addrCol = if (subCols.contains("address")) "`address`" else "''"
                val statusCol = if (subCols.contains("status")) "`status`" else "'ACTIVE'"
                val notesCol = if (subCols.contains("notes")) "`notes`" else "''"
                val createdCol = if (subCols.contains("created_at")) "`created_at`" else "''"
                val updatedCol = if (subCols.contains("updated_at")) "`updated_at`" else "''"
                val devCountCol = if (subCols.contains("device_count")) "`device_count`" else "0"
                val fdrCountCol = if (subCols.contains("feeder_count")) "`feeder_count`" else "0"
                val latCol = if (subCols.contains("latitude")) "`latitude`" else "NULL"
                val lngCol = if (subCols.contains("longitude")) "`longitude`" else "NULL"
                val gmapsCol = if (subCols.contains("google_maps_url")) "`google_maps_url`" else "''"
                val primImgCol = if (subCols.contains("primary_image")) "`primary_image`" else "''"
                val imgsCol = if (subCols.contains("imagesJson")) "`imagesJson`" else "'[]'"
                val locHistCol = if (subCols.contains("locationHistoryJson")) "`locationHistoryJson`" else "'[]'"
                db.execSQL("INSERT INTO `substations_new` SELECT $idCol, $codeCol, $nameCol, $addrCol, $statusCol, $notesCol, $createdCol, $updatedCol, $devCountCol, $fdrCountCol, $latCol, $lngCol, $gmapsCol, $primImgCol, $imgsCol, $locHistCol FROM `substations`")
                db.execSQL("DROP TABLE `substations`")
                db.execSQL("ALTER TABLE `substations_new` RENAME TO `substations`")
            }

            // 3. Feeders
            val feederCols = getColumns(db, "feeders")
            val expectedFeederCols = setOf("id", "feeder_code", "name", "substation_id", "start_point", "end_point", "notes", "status", "created_at", "updated_at")
            if (feederCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `feeders` (
                        `id` INTEGER NOT NULL,
                        `feeder_code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `substation_id` INTEGER NOT NULL,
                        `start_point` TEXT NOT NULL,
                        `end_point` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            } else if (!feederCols.containsAll(expectedFeederCols)) {
                db.execSQL(
                    """
                    CREATE TABLE `feeders_new` (
                        `id` INTEGER NOT NULL,
                        `feeder_code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `substation_id` INTEGER NOT NULL,
                        `start_point` TEXT NOT NULL,
                        `end_point` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val idCol = if (feederCols.contains("id")) "`id`" else "1"
                val codeCol = if (feederCols.contains("feeder_code")) "`feeder_code`" else if (feederCols.contains("code")) "`code`" else "''"
                val nameCol = if (feederCols.contains("name")) "`name`" else "''"
                val subIdCol = if (feederCols.contains("substation_id")) "`substation_id`" else "0"
                val startCol = if (feederCols.contains("start_point")) "`start_point`" else "''"
                val endCol = if (feederCols.contains("end_point")) "`end_point`" else "''"
                val notesCol = if (feederCols.contains("notes")) "`notes`" else "''"
                val statusCol = if (feederCols.contains("status")) "`status`" else "'ACTIVE'"
                val createdCol = if (feederCols.contains("created_at")) "`created_at`" else "''"
                val updatedCol = if (feederCols.contains("updated_at")) "`updated_at`" else "''"
                db.execSQL("INSERT INTO `feeders_new` SELECT $idCol, $codeCol, $nameCol, $subIdCol, $startCol, $endCol, $notesCol, $statusCol, $createdCol, $updatedCol FROM `feeders`")
                db.execSQL("DROP TABLE `feeders`")
                db.execSQL("ALTER TABLE `feeders_new` RENAME TO `feeders`")
            }

            // 4. Devices
            val deviceCols = getColumns(db, "devices")
            val expectedDeviceCols = setOf("id", "device_id", "device_code", "name", "device_type", "pole_number", "feeder_id", "substation_id", "unit", "team", "status", "switch_status", "scada_status", "relay_79", "battery_status", "latitude", "longitude", "google_maps_url", "notes", "current_setting", "primary_image", "imagesJson", "statusHistoryJson", "locationHistoryJson", "created_at", "updated_at")
            if (deviceCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `devices` (
                        `id` INTEGER NOT NULL,
                        `device_id` TEXT NOT NULL,
                        `device_code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `device_type` TEXT NOT NULL,
                        `pole_number` TEXT NOT NULL,
                        `feeder_id` INTEGER,
                        `substation_id` INTEGER,
                        `unit` TEXT NOT NULL,
                        `team` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `switch_status` TEXT NOT NULL,
                        `scada_status` TEXT NOT NULL,
                        `relay_79` TEXT NOT NULL,
                        `battery_status` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `google_maps_url` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `current_setting` TEXT NOT NULL,
                        `primary_image` TEXT NOT NULL,
                        `imagesJson` TEXT NOT NULL,
                        `statusHistoryJson` TEXT NOT NULL,
                        `locationHistoryJson` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            } else if (!deviceCols.containsAll(expectedDeviceCols)) {
                db.execSQL(
                    """
                    CREATE TABLE `devices_new` (
                        `id` INTEGER NOT NULL,
                        `device_id` TEXT NOT NULL,
                        `device_code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `device_type` TEXT NOT NULL,
                        `pole_number` TEXT NOT NULL,
                        `feeder_id` INTEGER,
                        `substation_id` INTEGER,
                        `unit` TEXT NOT NULL,
                        `team` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `switch_status` TEXT NOT NULL,
                        `scada_status` TEXT NOT NULL,
                        `relay_79` TEXT NOT NULL,
                        `battery_status` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `google_maps_url` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `current_setting` TEXT NOT NULL,
                        `primary_image` TEXT NOT NULL,
                        `imagesJson` TEXT NOT NULL,
                        `statusHistoryJson` TEXT NOT NULL,
                        `locationHistoryJson` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val idCol = if (deviceCols.contains("id")) "`id`" else "1"
                val devIdCol = if (deviceCols.contains("device_id")) "`device_id`" else "''"
                val devCodeCol = if (deviceCols.contains("device_code")) "`device_code`" else "''"
                val nameCol = if (deviceCols.contains("name")) "`name`" else "''"
                val devTypeCol = if (deviceCols.contains("device_type")) "`device_type`" else "'LBS'"
                val poleCol = if (deviceCols.contains("pole_number")) "`pole_number`" else "''"
                val fdrIdCol = if (deviceCols.contains("feeder_id")) "`feeder_id`" else "NULL"
                val subIdCol = if (deviceCols.contains("substation_id")) "`substation_id`" else "NULL"
                val unitCol = if (deviceCols.contains("unit")) "`unit`" else "'ĐL Hải Châu'"
                val teamCol = if (deviceCols.contains("team")) "`team`" else "'Tổ Thao Tác Lưu Động'"
                val statusCol = if (deviceCols.contains("status")) "`status`" else "'ACTIVE'"
                val swStatusCol = if (deviceCols.contains("switch_status")) "`switch_status`" else "'CLOSED'"
                val scadaCol = if (deviceCols.contains("scada_status")) "`scada_status`" else "'SIGNAL'"
                val relayCol = if (deviceCols.contains("relay_79")) "`relay_79`" else "'ON'"
                val battCol = if (deviceCols.contains("battery_status")) "`battery_status`" else "'GOOD'"
                val latCol = if (deviceCols.contains("latitude")) "`latitude`" else "NULL"
                val lngCol = if (deviceCols.contains("longitude")) "`longitude`" else "NULL"
                val gmapsCol = if (deviceCols.contains("google_maps_url")) "`google_maps_url`" else "''"
                val notesCol = if (deviceCols.contains("notes")) "`notes`" else "''"
                val curSetCol = if (deviceCols.contains("current_setting")) "`current_setting`" else "''"
                val primImgCol = if (deviceCols.contains("primary_image")) "`primary_image`" else "''"
                val imgsCol = if (deviceCols.contains("imagesJson")) "`imagesJson`" else "'[]'"
                val stHistCol = if (deviceCols.contains("statusHistoryJson")) "`statusHistoryJson`" else "'[]'"
                val locHistCol = if (deviceCols.contains("locationHistoryJson")) "`locationHistoryJson`" else "'[]'"
                val createdCol = if (deviceCols.contains("created_at")) "`created_at`" else "''"
                val updatedCol = if (deviceCols.contains("updated_at")) "`updated_at`" else "''"
                db.execSQL("INSERT INTO `devices_new` SELECT $idCol, $devIdCol, $devCodeCol, $nameCol, $devTypeCol, $poleCol, $fdrIdCol, $subIdCol, $unitCol, $teamCol, $statusCol, $swStatusCol, $scadaCol, $relayCol, $battCol, $latCol, $lngCol, $gmapsCol, $notesCol, $curSetCol, $primImgCol, $imgsCol, $stHistCol, $locHistCol, $createdCol, $updatedCol FROM `devices`")
                db.execSQL("DROP TABLE `devices`")
                db.execSQL("ALTER TABLE `devices_new` RENAME TO `devices`")
            }

            // 5. Loops
            val loopCols = getColumns(db, "loops")
            val expectedLoopCols = setOf("id", "loop_id", "name", "substation_id_a", "feeder_id_a", "device_id_a", "substation_id_b", "feeder_id_b", "device_id_b", "status", "notes", "created_at", "updated_at")
            if (loopCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loops` (
                        `id` INTEGER NOT NULL,
                        `loop_id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `substation_id_a` INTEGER NOT NULL,
                        `feeder_id_a` INTEGER NOT NULL,
                        `device_id_a` TEXT NOT NULL,
                        `substation_id_b` INTEGER NOT NULL,
                        `feeder_id_b` INTEGER NOT NULL,
                        `device_id_b` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            } else if (!loopCols.containsAll(expectedLoopCols)) {
                db.execSQL(
                    """
                    CREATE TABLE `loops_new` (
                        `id` INTEGER NOT NULL,
                        `loop_id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `substation_id_a` INTEGER NOT NULL,
                        `feeder_id_a` INTEGER NOT NULL,
                        `device_id_a` TEXT NOT NULL,
                        `substation_id_b` INTEGER NOT NULL,
                        `feeder_id_b` INTEGER NOT NULL,
                        `device_id_b` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL,
                        `updated_at` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val idCol = if (loopCols.contains("id")) "`id`" else "1"
                val loopIdCol = if (loopCols.contains("loop_id")) "`loop_id`" else "''"
                val nameCol = if (loopCols.contains("name")) "`name`" else "''"
                val subACol = if (loopCols.contains("substation_id_a")) "`substation_id_a`" else "0"
                val fdrACol = if (loopCols.contains("feeder_id_a")) "`feeder_id_a`" else "0"
                val devACol = if (loopCols.contains("device_id_a")) "`device_id_a`" else "''"
                val subBCol = if (loopCols.contains("substation_id_b")) "`substation_id_b`" else "0"
                val fdrBCol = if (loopCols.contains("feeder_id_b")) "`feeder_id_b`" else "0"
                val devBCol = if (loopCols.contains("device_id_b")) "`device_id_b`" else "''"
                val statusCol = if (loopCols.contains("status")) "`status`" else "'OPEN'"
                val notesCol = if (loopCols.contains("notes")) "`notes`" else "''"
                val createdCol = if (loopCols.contains("created_at")) "`created_at`" else "''"
                val updatedCol = if (loopCols.contains("updated_at")) "`updated_at`" else "''"
                db.execSQL("INSERT INTO `loops_new` SELECT $idCol, $loopIdCol, $nameCol, $subACol, $fdrACol, $devACol, $subBCol, $fdrBCol, $devBCol, $statusCol, $notesCol, $createdCol, $updatedCol FROM `loops`")
                db.execSQL("DROP TABLE `loops`")
                db.execSQL("ALTER TABLE `loops_new` RENAME TO `loops`")
            }

            // 6. Audit Logs
            val auditCols = getColumns(db, "audit_logs")
            val expectedAuditCols = setOf("id", "username", "user_fullname", "action", "module", "target_id", "details", "result", "ip_address", "created_at")
            if (auditCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `username` TEXT NOT NULL,
                        `user_fullname` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `module` TEXT NOT NULL,
                        `target_id` TEXT,
                        `details` TEXT NOT NULL,
                        `result` TEXT NOT NULL,
                        `ip_address` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            } else if (!auditCols.containsAll(expectedAuditCols)) {
                db.execSQL(
                    """
                    CREATE TABLE `audit_logs_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `username` TEXT NOT NULL,
                        `user_fullname` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `module` TEXT NOT NULL,
                        `target_id` TEXT,
                        `details` TEXT NOT NULL,
                        `result` TEXT NOT NULL,
                        `ip_address` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                val idCol = if (auditCols.contains("id")) "`id`" else "NULL"
                val userCol = if (auditCols.contains("username")) "`username`" else "''"
                val nameCol = if (auditCols.contains("user_fullname")) "`user_fullname`" else "''"
                val actCol = if (auditCols.contains("action")) "`action`" else "''"
                val modCol = if (auditCols.contains("module")) "`module`" else "''"
                val targetCol = if (auditCols.contains("target_id")) "`target_id`" else "NULL"
                val detCol = if (auditCols.contains("details")) "`details`" else "''"
                val resCol = if (auditCols.contains("result")) "`result`" else "'SUCCESS'"
                val ipCol = if (auditCols.contains("ip_address")) "`ip_address`" else "'127.0.0.1'"
                val createdCol = if (auditCols.contains("created_at")) "`created_at`" else "''"
                db.execSQL("INSERT INTO `audit_logs_new` SELECT $idCol, $userCol, $nameCol, $actCol, $modCol, $targetCol, $detCol, $resCol, $ipCol, $createdCol FROM `audit_logs`")
                db.execSQL("DROP TABLE `audit_logs`")
                db.execSQL("ALTER TABLE `audit_logs_new` RENAME TO `audit_logs`")
            }

            // 7. Loops V2
            val loopV2Cols = getColumns(db, "loops_v2")
            val expectedLoopV2Cols = setOf("id", "name", "stationAId", "feederAId", "sideADeviceIdsJson", "legalBoundaryDeviceId", "sideBDeviceIdsJson", "feederBId", "stationBId", "layout", "version", "createdAt", "updatedAt")
            if (loopV2Cols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loops_v2` (
                        `id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `stationAId` INTEGER NOT NULL,
                        `feederAId` INTEGER NOT NULL,
                        `sideADeviceIdsJson` TEXT NOT NULL,
                        `legalBoundaryDeviceId` INTEGER NOT NULL,
                        `sideBDeviceIdsJson` TEXT NOT NULL,
                        `feederBId` INTEGER NOT NULL,
                        `stationBId` INTEGER NOT NULL,
                        `layout` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            } else if (!loopV2Cols.containsAll(expectedLoopV2Cols)) {
                db.execSQL(
                    """
                    CREATE TABLE `loops_v2_new` (
                        `id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `stationAId` INTEGER NOT NULL,
                        `feederAId` INTEGER NOT NULL,
                        `sideADeviceIdsJson` TEXT NOT NULL,
                        `legalBoundaryDeviceId` INTEGER NOT NULL,
                        `sideBDeviceIdsJson` TEXT NOT NULL,
                        `feederBId` INTEGER NOT NULL,
                        `stationBId` INTEGER NOT NULL,
                        `layout` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val idCol = if (loopV2Cols.contains("id")) "`id`" else "1"
                val nameCol = if (loopV2Cols.contains("name")) "`name`" else "''"
                val stACol = if (loopV2Cols.contains("stationAId")) "`stationAId`" else "0"
                val fdrACol = if (loopV2Cols.contains("feederAId")) "`feederAId`" else "0"
                val sideACol = if (loopV2Cols.contains("sideADeviceIdsJson")) "`sideADeviceIdsJson`" else "'[]'"
                val legBoundCol = if (loopV2Cols.contains("legalBoundaryDeviceId")) "`legalBoundaryDeviceId`" else if (loopV2Cols.contains("openDeviceId")) "`openDeviceId`" else "0"
                val sideBCol = if (loopV2Cols.contains("sideBDeviceIdsJson")) "`sideBDeviceIdsJson`" else "'[]'"
                val fdrBCol = if (loopV2Cols.contains("feederBId")) "`feederBId`" else "0"
                val stBCol = if (loopV2Cols.contains("stationBId")) "`stationBId`" else "0"
                val layoutCol = if (loopV2Cols.contains("layout")) "`layout`" else "''"
                val verCol = if (loopV2Cols.contains("version")) "`version`" else "1"
                val crCol = if (loopV2Cols.contains("createdAt")) "`createdAt`" else "''"
                val upCol = if (loopV2Cols.contains("updatedAt")) "`updatedAt`" else "''"
                db.execSQL("INSERT INTO `loops_v2_new` SELECT $idCol, $nameCol, $stACol, $fdrACol, $sideACol, $legBoundCol, $sideBCol, $fdrBCol, $stBCol, $layoutCol, $verCol, $crCol, $upCol FROM `loops_v2`")
                db.execSQL("DROP TABLE `loops_v2`")
                db.execSQL("ALTER TABLE `loops_v2_new` RENAME TO `loops_v2`")
            }

            // 8. Doc Categories
            val docCatCols = getColumns(db, "doc_categories")
            val expectedDocCatCols = setOf("id", "name")
            if (docCatCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `doc_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            } else if (!docCatCols.containsAll(expectedDocCatCols)) {
                db.execSQL(
                    """
                    CREATE TABLE `doc_categories_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                val idCol = if (docCatCols.contains("id")) "`id`" else "NULL"
                val nameCol = if (docCatCols.contains("name")) "`name`" else "''"
                db.execSQL("INSERT INTO `doc_categories_new` SELECT $idCol, $nameCol FROM `doc_categories`")
                db.execSQL("DROP TABLE `doc_categories`")
                db.execSQL("ALTER TABLE `doc_categories_new` RENAME TO `doc_categories`")
            }

            // 9. Library Documents
            val docCols = getColumns(db, "library_documents")
            val expectedDocCols = setOf("id", "displayName", "originalName", "mimeType", "category", "description", "tags", "localPath", "fileSize", "checksumSHA256", "favorite", "deviceId", "createdAt", "updatedAt")
            if (docCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_documents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `originalName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `checksumSHA256` TEXT NOT NULL,
                        `favorite` INTEGER NOT NULL,
                        `deviceId` INTEGER,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            } else if (!docCols.containsAll(expectedDocCols)) {
                db.execSQL(
                    """
                    CREATE TABLE `library_documents_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `originalName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `checksumSHA256` TEXT NOT NULL,
                        `favorite` INTEGER NOT NULL,
                        `deviceId` INTEGER,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                val idCol = if (docCols.contains("id")) "`id`" else "NULL"
                val dispNameCol = if (docCols.contains("displayName")) "`displayName`" else if (docCols.contains("name")) "`name`" else "''"
                val origNameCol = if (docCols.contains("originalName")) "`originalName`" else "''"
                val mimeCol = if (docCols.contains("mimeType")) "`mimeType`" else "'application/octet-stream'"
                val catCol = if (docCols.contains("category")) "`category`" else "''"
                val descCol = if (docCols.contains("description")) "`description`" else "''"
                val tagsCol = if (docCols.contains("tags")) "`tags`" else "''"
                val pathCol = if (docCols.contains("localPath")) "`localPath`" else "''"
                val sizeCol = if (docCols.contains("fileSize")) "`fileSize`" else "0"
                val checkCol = if (docCols.contains("checksumSHA256")) "`checksumSHA256`" else "''"
                val favCol = if (docCols.contains("favorite")) "`favorite`" else "0"
                val devIdCol = if (docCols.contains("deviceId")) "`deviceId`" else "NULL"
                val crCol = if (docCols.contains("createdAt")) "`createdAt`" else "''"
                val upCol = if (docCols.contains("updatedAt")) "`updatedAt`" else "''"
                db.execSQL("INSERT INTO `library_documents_new` SELECT $idCol, $dispNameCol, $origNameCol, $mimeCol, $catCol, $descCol, $tagsCol, $pathCol, $sizeCol, $checkCol, $favCol, $devIdCol, $crCol, $upCol FROM `library_documents`")
                db.execSQL("DROP TABLE `library_documents`")
                db.execSQL("ALTER TABLE `library_documents_new` RENAME TO `library_documents`")
            }

            // 10. Documents (Library Module)
            val docEntityCols = getColumns(db, "documents")
            if (docEntityCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `documents` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `fileUrl` TEXT NOT NULL,
                        `localPath` TEXT,
                        `fileSize` INTEGER NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `isDownloaded` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }

            // 11. Equipment Images (Camera & Equipment Gallery Module)
            val equipImgCols = getColumns(db, "equipment_images")
            if (equipImgCols.isEmpty()) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `equipment_images` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `stationId` INTEGER NOT NULL,
                        `stationName` TEXT NOT NULL,
                        `equipmentCode` TEXT NOT NULL,
                        `equipmentName` TEXT NOT NULL,
                        `imagePath` TEXT NOT NULL,
                        `capturedAt` TEXT NOT NULL,
                        `capturedBy` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `isSynced` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaSynchronized(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaSynchronized(db)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaSynchronized(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaSynchronized(db)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaSynchronized(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `substations` ADD COLUMN `latitude` REAL")
                db.execSQL("ALTER TABLE `substations` ADD COLUMN `longitude` REAL")
                db.execSQL("ALTER TABLE `substations` ADD COLUMN `google_maps_url` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `substations` ADD COLUMN `primary_image` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `substations` ADD COLUMN `imagesJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `substations` ADD COLUMN `locationHistoryJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `documents` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `fileUrl` TEXT NOT NULL,
                        `localPath` TEXT,
                        `fileSize` INTEGER NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `isDownloaded` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `equipment_images` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `stationId` INTEGER NOT NULL,
                        `stationName` TEXT NOT NULL,
                        `equipmentCode` TEXT NOT NULL,
                        `equipmentName` TEXT NOT NULL,
                        `imagePath` TEXT NOT NULL,
                        `capturedAt` TEXT NOT NULL,
                        `capturedBy` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `isSynced` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qltb_mobile.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
