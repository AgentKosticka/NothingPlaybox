package com.agentkosticka.playbox.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaseCounterTest {
    private enum class Owner { APP, TOY }

    @Test
    fun overlappingAppOwnersCannotReleaseEachOther() {
        val leases = LeaseCounter<Owner>()
        leases.acquire(Owner.APP)
        leases.acquire(Owner.APP)
        leases.acquire(Owner.TOY)

        val firstAppRelease = leases.release(Owner.APP)
        assertTrue(firstAppRelease.released)
        assertFalse(firstAppRelease.lastForOwner)
        assertEquals(2, firstAppRelease.total)
        assertEquals(1, leases.count(Owner.APP))
        assertEquals(1, leases.count(Owner.TOY))
    }

    @Test
    fun lastToyLeaseIsDetectableWhileAppStillOwnsConnection() {
        val leases = LeaseCounter<Owner>()
        leases.acquire(Owner.APP)
        leases.acquire(Owner.TOY)

        val toyRelease = leases.release(Owner.TOY)
        assertTrue(toyRelease.lastForOwner)
        assertEquals(1, toyRelease.total)
        assertEquals(0, leases.count(Owner.TOY))
    }

    @Test
    fun unknownReleaseIsNoOp() {
        val leases = LeaseCounter<Owner>()
        val result = leases.release(Owner.APP)
        assertFalse(result.released)
        assertEquals(0, result.total)
    }
}
