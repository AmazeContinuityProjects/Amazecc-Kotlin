package com.amazecc.app.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class DayNode(
    val day: Int,
    var count: Int
)

@Serializable
data class MonthNode(
    val month: Int,
    val days: MutableMap<Int, DayNode> = mutableMapOf()
)

@Serializable
data class YearNode(
    val year: Int,
    val months: MutableMap<Int, MonthNode> = mutableMapOf()
)

data class HeatMapEntry(
    val date: String,
    val count: Int
)

class ActivityTree(initialData: Map<Int, YearNode>? = null) {
    val years: MutableMap<Int, YearNode> = initialData?.toMutableMap() ?: mutableMapOf()

    // Assuming we pass year, month (1-12), and day (1-31) explicitly
    // since KMP common code may not have standard date without kotlinx-datetime
    fun increment(year: Int, month: Int, day: Int) {
        val yearNode = years.getOrPut(year) { YearNode(year) }
        val monthNode = yearNode.months.getOrPut(month) { MonthNode(month) }
        val dayNode = monthNode.days.getOrPut(day) { DayNode(day, 0) }
        
        dayNode.count++
    }

    fun toHeatMap(): List<HeatMapEntry> {
        val heatMap = mutableListOf<HeatMapEntry>()

        for ((_, yearNode) in years) {
            for ((_, monthNode) in yearNode.months) {
                for ((_, dayNode) in monthNode.days) {
                    val d = "${yearNode.year}/${monthNode.month}/${dayNode.day}"
                    heatMap.add(HeatMapEntry(date = d, count = dayNode.count))
                }
            }
        }
        
        return heatMap
    }
}
