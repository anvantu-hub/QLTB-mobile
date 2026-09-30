package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLoggerTest {

    @Test
    fun testCreateLog_offlineParameters() {
        val user = User(
            id = "test_staff",
            employee_code = "NV001",
            full_name = "Kỹ thuật viên Hiện Trường",
            username = "kythuat_01",
            email = "kythuat@evn.vn",
            role = "STAFF"
        )

        val log = AuditLogger.createLog(
            action = AuditLogger.ACTION_DOCUMENT_ADD,
            module = AuditLogger.MODULE_DOCUMENT,
            targetId = "101",
            details = "Thêm tài liệu hướng dẫn vận hành",
            result = "SUCCESS",
            user = user
        )

        assertEquals("kythuat_01", log.username)
        assertEquals("Kỹ thuật viên Hiện Trường", log.user_fullname)
        assertEquals(AuditLogger.ACTION_DOCUMENT_ADD, log.action)
        assertEquals(AuditLogger.MODULE_DOCUMENT, log.module)
        assertEquals("101", log.target_id)
        assertEquals("SUCCESS", log.result)
        assertEquals(AuditLogger.LOCAL_IP, log.ip_address)
        assertTrue(log.created_at.isNotEmpty())
    }

    @Test
    fun testCreateLog_guestFallback() {
        val log = AuditLogger.createLog(
            action = AuditLogger.ACTION_DOCUMENT_EDIT,
            module = AuditLogger.MODULE_DOCUMENT,
            targetId = "102",
            details = "Chỉnh sửa tài liệu",
            result = "SUCCESS",
            user = null
        )

        assertEquals("OFFLINE_STAFF", log.username)
        assertEquals("Nhân viên vận hành", log.user_fullname)
        assertEquals(AuditLogger.ACTION_DOCUMENT_EDIT, log.action)
        assertNotNull(log.created_at)
    }

    @Test
    fun testActionConstants() {
        assertEquals("DOCUMENT_ADD", AuditLogger.ACTION_DOCUMENT_ADD)
        assertEquals("DOCUMENT_EDIT", AuditLogger.ACTION_DOCUMENT_EDIT)
        assertEquals("DOCUMENT_DELETE", AuditLogger.ACTION_DOCUMENT_DELETE)
        assertEquals("DOCUMENT_LINK_DEVICE", AuditLogger.ACTION_DOCUMENT_LINK_DEVICE)
        assertEquals("DOCUMENT_FAVORITE", AuditLogger.ACTION_DOCUMENT_FAVORITE)
        assertEquals("DOCUMENT_OPEN", AuditLogger.ACTION_DOCUMENT_OPEN)
        assertEquals("DOCUMENT_SHARE", AuditLogger.ACTION_DOCUMENT_SHARE)
    }
}
