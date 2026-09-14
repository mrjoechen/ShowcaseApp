package com.alpha.showcase.common

import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest

class NetworkConnectionPoolTest {
    @Test fun closingPoolRetiresActiveLeaseOnlyAfterItsOwnerReturnsIt() = runTest {
        var destroyed = 0
        val pool = NetworkConnectionPool(1, { Any() }, { true }, { destroyed++ })
        val lease = pool.borrow()
        pool.close()
        assertEquals(0, destroyed, "Shutdown must not destroy a resource while its owner is using it")
        lease.release()
        lease.release()
        pool.close()
        assertEquals(1, destroyed)
        assertFailsWith<IllegalStateException> { pool.borrow() }
    }

    @Test fun closingPoolDuringValidationCannotReturnAnotherLease() = runTest {
        var destroyed = 0
        lateinit var pool: NetworkConnectionPool<Any>
        pool = NetworkConnectionPool(1, { Any() }, { pool.close(); true }, { destroyed++ })
        pool.borrow().release()
        assertFailsWith<IllegalStateException> { pool.borrow() }
        assertEquals(1, destroyed)
    }

    @Test fun waitingBorrowerDoesNotBlockReturn() = runTest {
        val pool = NetworkConnectionPool(1, { Any() }, { true }, {})
        val first = pool.borrow()
        val waiting = async { pool.borrow() }
        yield()
        assertFalse(waiting.isCompleted)
        first.release()
        val second = withTimeout(1000) { waiting.await() }
        assertSame(first.value, second.value)
        second.release()
        pool.close()
    }

    @Test fun cancelledBorrowerDoesNotConsumeCapacity() = runTest {
        val pool = NetworkConnectionPool(1, { Any() }, { true }, {})
        val first = pool.borrow()
        val waiting = async { pool.borrow() }
        yield()
        waiting.cancelAndJoin()
        first.release()
        withTimeout(1000) { pool.borrow().release() }
        pool.close()
    }

    @Test fun failedStreamCloseStillReleasesLeaseExactlyOnce() = runTest {
        var destroyed = 0
        val pool = NetworkConnectionPool(1, { Any() }, { true }, { destroyed++ })
        val first = pool.borrow()
        val stream = ReleasingInputStream(object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { throw IOException("closed by server") }
        }) { healthy -> first.release(healthy) }
        assertFailsWith<IOException> { stream.close() }
        stream.close()
        assertEquals(1, destroyed)
        val next = withTimeout(1000) { pool.borrow() }
        assertNotSame(first.value, next.value)
        next.release()
        pool.close()
    }

    @Test fun cleanupNeverProbesOrClosesAnActiveTransfer() = runTest {
        var probes = 0
        var destroyed = 0
        val pool = NetworkConnectionPool(1, { Any() }, { probes++; true }, { destroyed++ })
        val lease = pool.borrow()
        pool.cleanupIdle(Long.MAX_VALUE, 1)
        assertEquals(0, probes)
        assertEquals(0, destroyed)
        lease.release()
        pool.cleanupIdle(Long.MAX_VALUE, 1)
        assertEquals(1, destroyed)
        pool.close()
    }

    @Test fun failedConnectionCreationDoesNotConsumeCapacity() = runTest {
        var attempts = 0
        val pool = NetworkConnectionPool(1, { if (++attempts == 1) throw IOException("connect failed"); Any() }, { true }, {})
        assertFailsWith<IOException> { pool.borrow() }
        withTimeout(1000) { pool.borrow().release() }
        pool.close()
    }
}
