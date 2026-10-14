package com.cezar.opengym.wearbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EventInboxTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun item(id: String) = EventInbox.Item(id = id, json = """{"id":"$id","type":"setDone"}""", uri = "wear://n/opengym/v1/event/$id")

    @Test fun keepsEventsInOrderOfArrivalEachIdOnce() {
        val inbox = EventInbox(File(tmp.root, "events.json"))
        assertTrue(inbox.add(item("b")))
        assertTrue(inbox.add(item("a")))
        assertFalse("the same event twice is one", inbox.add(item("b")))
        assertEquals(listOf("b", "a"), inbox.all().map { it.id })
    }

    @Test fun survivesTheProcessDyingBetweenArrivalAndDrain() {
        val f = File(tmp.root, "events.json")
        EventInbox(f).add(item("a"))
        EventInbox(f).add(item("b"))
        // a new instance over the same file: what a restarted app sees
        val again = EventInbox(f)
        assertEquals(listOf("a", "b"), again.all().map { it.id })
        assertEquals("wear://n/opengym/v1/event/a", again.all()[0].uri)
    }

    @Test fun ackTakesOnlyThoseIdsOutAndReturnsThem() {
        val inbox = EventInbox(File(tmp.root, "events.json"))
        listOf("a", "b", "c").forEach { inbox.add(item(it)) }
        val gone = inbox.remove(listOf("a", "c", "zzz"))
        assertEquals(listOf("a", "c"), gone.map { it.id })
        assertEquals(listOf("b"), inbox.all().map { it.id })
        assertTrue(inbox.remove(listOf("nope")).isEmpty())
    }

    @Test fun aBrokenFileIsSetAsideNotFatal() {
        val f = File(tmp.root, "events.json")
        f.writeText("{not json")
        val inbox = EventInbox(f)
        assertTrue(inbox.all().isEmpty())
        assertTrue(inbox.add(item("a")))
        assertEquals(1, tmp.root.listFiles()!!.count { it.name.startsWith("events.json.bad-") })
    }

    @Test fun theIdComesFromTheEventItself() {
        assertEquals("e1", EventInbox.idOf("""{"id":"e1","type":"finish"}"""))
        assertNull(EventInbox.idOf("""{"type":"finish"}"""))
        assertNull(EventInbox.idOf("""{"id":5}"""))
        assertNull(EventInbox.idOf("nope"))
    }

    @Test fun keepsAtMostTheNewestThousand() {
        val inbox = EventInbox(File(tmp.root, "events.json"))
        repeat(EventInbox.MAX + 5) { inbox.add(item("e$it")) }
        val all = inbox.all()
        assertEquals(EventInbox.MAX, all.size)
        assertEquals("e5", all.first().id)
    }
}
