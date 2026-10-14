package com.example.voxara.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkResolverTest {

    private val a = WatchNode("a", "Galaxy Watch7", nearby = true)
    private val b = WatchNode("b", "Pixel Watch", nearby = false)
    private val c = WatchNode("c", "Galaxy Watch6", nearby = true)
    private val ok = SyncRecord(lastSuccessMs = 1_000L, lastAttemptMs = 1_000L)

    @Test
    fun `no connected watch`() {
        assertEquals(LinkState.NoWatch, LinkResolver.resolve(emptyList(), emptySet(), null, ok))
    }

    @Test
    fun `watch connected but Voxara not installed`() {
        assertEquals(LinkState.NotInstalled(listOf(a, b)), LinkResolver.resolve(listOf(a, b), emptySet(), null, ok))
    }

    @Test
    fun `one watch with the app is linked, with its last sync`() {
        assertEquals(LinkState.Linked(a, emptyList(), 1_000L), LinkResolver.resolve(listOf(a, b), setOf("a"), null, ok))
    }

    @Test
    fun `with several watches the chosen one wins, then a nearby one`() {
        val chosen = LinkResolver.resolve(listOf(a, b, c), setOf("a", "b", "c"), "b", ok) as LinkState.Linked
        assertEquals(b, chosen.watch)
        assertEquals(listOf(a, c), chosen.others)
        val nearby = LinkResolver.resolve(listOf(b, c), setOf("b", "c"), "gone", ok) as LinkState.Linked
        assertEquals(c, nearby.watch)
        val first = LinkResolver.resolve(listOf(b), setOf("b"), null, ok) as LinkState.Linked
        assertEquals(b, first.watch)
    }

    @Test
    fun `a failed sync after the last success is a problem, a later success clears it`() {
        val failed = SyncRecord(lastSuccessMs = 1_000L, lastAttemptMs = 2_000L, lastProblem = SyncProblem.UNREACHABLE)
        val p = LinkResolver.resolve(listOf(a), setOf("a"), null, failed)
        assertEquals(LinkState.Problem(a, emptyList(), SyncProblem.UNREACHABLE, 1_000L), p)
        val never = LinkResolver.resolve(listOf(a), setOf("a"), null, SyncRecord(lastAttemptMs = 5L, lastProblem = SyncProblem.INCOMPATIBLE))
        assertTrue(never is LinkState.Problem)
        val recovered = SyncRecord(lastSuccessMs = 3_000L, lastAttemptMs = 3_000L, lastProblem = SyncProblem.UNREACHABLE)
        assertTrue(LinkResolver.resolve(listOf(a), setOf("a"), null, recovered) is LinkState.Linked)
    }
}
