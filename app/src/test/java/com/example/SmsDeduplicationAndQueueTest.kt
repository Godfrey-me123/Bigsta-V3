package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AppNotificationEntity
import com.example.core.database.BigstaDatabase
import com.example.core.database.PendingUploadEntity
import com.example.core.database.ProcessedSmsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsDeduplicationAndQueueTest {

    private lateinit var db: BigstaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BigstaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `unique transactionRef prevents duplicate SMS processing`() = runTest {
        val dao = db.smsTransactionDao()
        val first = ProcessedSmsEntity(
            transactionRef = "MPESA998877",
            provider = "M-Pesa",
            amount = 10000.0,
            currency = "TZS",
            transactionDateTime = "14/11/26 10:30",
            transactionType = "LIPA_NAMBA",
            senderAccount = "5511223",
            userId = "user-1",
            status = "QUEUED"
        )
        val insertedFirst = dao.insertIgnore(first)
        val insertedDuplicate = dao.insertIgnore(first.copy(id = 0))

        assertTrue(insertedFirst > 0L)
        assertEquals(-1L, insertedDuplicate)
        assertEquals(1, dao.observeByUser("user-1").first().size)
    }

    @Test
    fun `pending upload queue stores and removes items cleanly`() = runTest {
        val queueDao = db.pendingUploadDao()
        val pending = PendingUploadEntity(
            idempotencyKey = "MPESA998877",
            payloadType = "PAYMENT_SMS",
            payloadJson = """{"transaction_ref":"MPESA998877"}""",
            userId = "user-1",
            status = "PENDING"
        )
        queueDao.upsert(pending)
        assertEquals(1, queueDao.getRetryableUploads().size)

        queueDao.updateStatus("MPESA998877", "SYNCED", 1, null)
        assertEquals(0, queueDao.getRetryableUploads().size)

        queueDao.deleteByKey("MPESA998877")
        assertEquals(0, queueDao.observeAll().first().size)
    }

    @Test
    fun `notifications deduplicate by eventKey and track unread count`() = runTest {
        val notifDao = db.appNotificationDao()
        val n1 = AppNotificationEntity(
            eventKey = "pay_approved_101",
            title = "Recharge Approved",
            body = "50 tokens verified",
            category = "RECHARGE",
            targetRoute = "billing"
        )
        assertTrue(notifDao.insertIfAbsent(n1) > 0L)
        assertEquals(-1L, notifDao.insertIfAbsent(n1))
        assertEquals(1, notifDao.observeUnreadCount().first())

        notifDao.markRead("pay_approved_101")
        assertEquals(0, notifDao.observeUnreadCount().first())
    }
}
