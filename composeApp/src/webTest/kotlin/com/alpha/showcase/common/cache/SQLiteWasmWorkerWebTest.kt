package com.alpha.showcase.common.cache

import com.alpha.showcase.worker.createSQLiteWasmWorker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SQLiteWasmWorkerWebTest {
    @Test
    fun closingAStatementDoesNotPoisonTheNextRequest() = runTest {
        val connection = createSQLiteWasmWorker().open("sqlite_worker_protocol_test.db")

        try {
            val firstStatement = connection.prepare("SELECT 1")
            assertTrue(firstStatement.step())
            assertEquals(1, firstStatement.getInt(0))
            firstStatement.close()

            // Statement close is one-way. The worker must ignore its null databaseId instead of
            // reporting an error that cancels this subsequent pending request.
            val nextStatement = connection.prepare("SELECT 2")
            try {
                assertTrue(nextStatement.step())
                assertEquals(2, nextStatement.getInt(0))
            } finally {
                nextStatement.close()
            }
        } finally {
            connection.close()
        }
    }
}
