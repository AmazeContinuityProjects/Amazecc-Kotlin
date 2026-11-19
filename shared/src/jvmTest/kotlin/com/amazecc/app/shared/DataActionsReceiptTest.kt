package com.amazecc.app.shared

import com.amazecc.app.shared.api.receiptResponseFromJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JVM-side checks for the receipt-detail normalisation behind `getReceiptDetail`. */
class DataActionsReceiptTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapsObjectRowsToHeaderAlignedCells() {
        val raw = json.parseToJsonElement(
            """
            {"success":true,"tables":[{
              "caption":"Receipt detail",
              "headers":["Particulars","Amount"],
              "rows":[
                {"Particulars":"Tuition fee","Amount":"50000"},
                {"Particulars":"Library","Amount":"1000"}
              ]
            }]}
            """.trimIndent()
        ).jsonObject

        val res = receiptResponseFromJson(raw)
        assertNotNull(res)
        assertTrue(res.success)
        assertEquals(1, res.tables.size)
        val table = res.tables[0]
        assertEquals(listOf("Particulars", "Amount"), table.headers)
        assertEquals(
            listOf(listOf("Tuition fee", "50000"), listOf("Library", "1000")),
            table.rows
        )
        assertEquals("Receipt detail", table.caption)
    }

    @Test
    fun fillsMissingHeaderCellsAndDropsBlankRows() {
        val raw = json.parseToJsonElement(
            """
            {"success":true,"tables":[{
              "headers":["A","B"],
              "rows":[{"A":"x"},{"A":"","B":""}]
            }]}
            """.trimIndent()
        ).jsonObject

        val res = receiptResponseFromJson(raw)
        assertNotNull(res)
        assertEquals(listOf(listOf("x", "")), res.tables[0].rows)
    }

    @Test
    fun handlesHeaderlessRowsByValueOrder() {
        val raw = json.parseToJsonElement(
            """
            {"success":true,"tables":[{"rows":[{"x":"1","y":"2"}]}]}
            """.trimIndent()
        ).jsonObject

        val res = receiptResponseFromJson(raw)
        assertNotNull(res)
        assertEquals(2, res.tables[0].rows[0].size)
        assertTrue(res.tables[0].headers.isEmpty())
    }

    @Test
    fun returnsNullOnFailureOrMissingTables() {
        val failed = json.parseToJsonElement("""{"success":false,"tables":[]}""").jsonObject
        assertNull(receiptResponseFromJson(failed))
        val noTables = json.parseToJsonElement("""{"success":true}""").jsonObject
        assertNull(receiptResponseFromJson(noTables))
        val emptyTables = json.parseToJsonElement("""{"success":true,"tables":[]}""").jsonObject
        val empty = receiptResponseFromJson(emptyTables)
        assertNotNull(empty)
        assertTrue(empty.tables.isEmpty())
    }
}
