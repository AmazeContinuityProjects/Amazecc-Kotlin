package com.amazecc.app.shared.vtop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless port tests for the library-dues parser and the transport GPS-link extraction.
 *
 * The scraper keeps all interpretation in Kotlin, so header detection, book mapping and the
 * defensive fallbacks are fully verifiable without a browser.
 */
class VtopLibraryScraperTest {

    private fun payload(tables: String, kv: String = "{}", messages: String = "{}") = """
        {"ok":true,"tables":$tables,"keyValuePairs":$kv,"messages":$messages}
    """.trimIndent()

    @Test
    fun `maps a dues table by header to a book`() {
        val raw = payload(
            tables = """
            [{
              "caption": "Books issued",
              "headers": ["Sl No", "Book Title", "Author Name", "ISBN No", "Amount Due", "Days Overdue"],
              "rows": [{
                "Sl No": "1",
                "Book Title": "Data Structures and Algorithms",
                "Author Name": "Adam Drozdek",
                "ISBN No": "978-0357449237",
                "Amount Due": "250.00",
                "Days Overdue": "12 days"
              }]
            }]
            """.trimIndent()
        )

        val res = VtopLibraryScraper.parsePayload(raw)
        assertNotNull(res)
        assertTrue(res.success)
        assertEquals(1, res.booksIssued.size)
        assertEquals(1, res.total)

        val book = res.booksIssued[0]
        assertEquals("Data Structures and Algorithms", book.title)
        assertEquals("Adam Drozdek", book.author)
        assertEquals("978-0357449237", book.isbn)
        assertEquals("978-0357449237", book.bookId, "ISBN is the only stable id VTOP exposes")
        assertEquals("250.00", book.fineAmount)
        assertEquals("12", book.daysOverdue)
        assertEquals("12", book.dueDate, "daysOverdue is carried in dueDate per the lead mapping")
    }

    @Test
    fun `maps books from multiple tables and keeps their own headers`() {
        val raw = payload(
            tables = """
            [
              {
                "headers": ["Title of Book", "Author", "Fine Amount", "Overdue Days"],
                "rows": [{"Title of Book": "Operating Systems", "Author": "Silberschatz", "Fine Amount": "40", "Overdue Days": "3"}]
              },
              {
                "headers": ["Book Name", "ISBN", "Amount"],
                "rows": [{"Book Name": "Computer Networks", "ISBN": "978-0132126953", "Amount": "75"}]
              }
            ]
            """.trimIndent()
        )

        val res = VtopLibraryScraper.parsePayload(raw)
        assertNotNull(res)
        assertEquals(2, res.booksIssued.size)
        assertEquals("Operating Systems", res.booksIssued[0].title)
        assertEquals("Silberschatz", res.booksIssued[0].author)
        assertEquals("40", res.booksIssued[0].fineAmount)
        assertEquals("3", res.booksIssued[0].daysOverdue)

        assertEquals("Computer Networks", res.booksIssued[1].title)
        assertEquals("978-0132126953", res.booksIssued[1].isbn)
        assertEquals("75", res.booksIssued[1].fineAmount)
        assertNull(res.booksIssued[1].daysOverdue, "no days column in the second table")
    }

    @Test
    fun `surfaces keyValuePairs message and explicit messages`() {
        val kvOnly = payload(tables = "[]", kv = """{"Register Number":"21BCE1001","Message":"No dues outstanding"}""")
        val kvRes = VtopLibraryScraper.parsePayload(kvOnly)
        assertNotNull(kvRes)
        assertTrue(kvRes.booksIssued.isEmpty(), "no tables means no fabricated books")
        assertEquals("No dues outstanding", kvRes.message, "message-style keyValuePair is preserved")

        val withError = payload(
            tables = "[]",
            kv = """{"Message":"ignored"}""",
            messages = """{"error":"Session expired","warning":"w","success":"ok"}"""
        )
        val errRes = VtopLibraryScraper.parsePayload(withError)
        assertNotNull(errRes)
        assertEquals("Session expired", errRes.error)
        assertEquals("ok", errRes.message, "explicit success wins over warning and keyValuePairs")
    }

    @Test
    fun `reads the reference precomputed books shape too`() {
        val raw = """
            {"books":[
              {"title":"Discrete Mathematics","author":"Rosen","isbn":"978-0132439871","dueAmount":"90","daysOverdue":7}
            ]}
        """.trimIndent()
        val res = VtopLibraryScraper.parsePayload(raw)
        assertNotNull(res)
        assertEquals(1, res.booksIssued.size)
        assertEquals("Discrete Mathematics", res.booksIssued[0].title)
        assertEquals("978-0132439871", res.booksIssued[0].isbn)
        assertEquals("90", res.booksIssued[0].fineAmount)
        assertEquals("7", res.booksIssued[0].daysOverdue)
    }

    @Test
    fun `malformed or unauthorized payload returns null`() {
        assertNull(VtopLibraryScraper.parsePayload(""))
        assertNull(VtopLibraryScraper.parsePayload("not json"))
        assertNull(VtopLibraryScraper.parsePayload("""{"ok":false,"error":"not authorized"}"""))
        assertNull(VtopLibraryScraper.parsePayload("[]"))
    }

    @Test
    fun `valid but empty payload yields no fabricated rows`() {
        val empty = VtopLibraryScraper.parsePayload(payload(tables = "[]"))
        assertNotNull(empty)
        assertTrue(empty.success)
        assertTrue(empty.booksIssued.isEmpty())
        assertEquals(0, empty.total)

        // A table with no title/amount headers, or a row with no title, is dropped.
        val noise = VtopLibraryScraper.parsePayload(
            payload(
                tables = """
                [
                  {"headers":["Column A","Column B"],"rows":[{"Column A":"x","Column B":"y"}]},
                  {"headers":["Title","Amount"],"rows":[{"Title":"","Amount":"10"}]}
                ]
                """.trimIndent()
            )
        )
        assertNotNull(noise)
        assertTrue(noise.booksIssued.isEmpty(), "rows without a title are never turned into books")
    }

    @Test
    fun `transport GPS helper extracts busUrl and fails closed`() {
        val ds = VtopDataSource
        assertEquals(
            "https://example.com/track?bus=R12",
            ds.parseBusUrl("""{"busUrl":"https://example.com/track?bus=R12"}""")
        )
        assertEquals("", ds.parseBusUrl(""))
        assertEquals("", ds.parseBusUrl("not json"))
        assertEquals("", ds.parseBusUrl("""{"busUrl":""}"""))
        assertEquals("", ds.parseBusUrl("""{"other":1}"""))
        assertEquals(
            "",
            ds.parseBusUrl("<!DOCTYPE html><html><body>Unidentified OS or Browser</body></html>")
        )
    }
}
