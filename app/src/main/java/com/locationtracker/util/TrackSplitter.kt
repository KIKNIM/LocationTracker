package com.locationtracker.util

import com.locationtracker.data.model.LocationRecord

/**
 * 轨迹分段器 — 虚线/实线分离
 *
 * 中断标记点将轨迹天然分段：
 * - 正常连续段 → SOLID
 * - 中断点附近（前后 500m）→ DASHED
 */
object TrackSplitter {

    enum class LineType { SOLID, DASHED }

    data class TrackSegment(val points: List<LocationRecord>, val type: LineType)

    fun split(records: List<LocationRecord>, dashedRadiusM: Double = 500.0): List<TrackSegment> {
        if (records.size < 2) return listOf(TrackSegment(records, LineType.SOLID))

        val marks = records.mapIndexedNotNull { i, r -> if (r.isInterrupted) i else null }
        if (marks.isEmpty()) return listOf(TrackSegment(records, LineType.SOLID))

        val dashed = BooleanArray(records.size)
        for (idx in marks) {
            dashed[idx] = true
            val center = records[idx]
            var j = idx + 1
            while (j < records.size && !records[j].isInterrupted &&
                DouglasPeucker.haversine(center, records[j]) <= dashedRadiusM
            ) dashed[j++] = true
            j = idx - 1
            while (j >= 0 && !records[j].isInterrupted &&
                DouglasPeucker.haversine(center, records[j]) <= dashedRadiusM
            ) dashed[j--] = true
        }

        val segments = mutableListOf<TrackSegment>()
        var start = 0
        var cur = dashed[0]
        for (i in 1 until records.size) {
            if (dashed[i] != cur) {
                segments.add(
                    TrackSegment(
                        records.subList(start, i),
                        if (cur) LineType.DASHED else LineType.SOLID
                    )
                )
                start = i; cur = dashed[i]
            }
        }
        segments.add(
            TrackSegment(
                records.subList(start, records.size),
                if (cur) LineType.DASHED else LineType.SOLID
            )
        )
        return segments.filter { it.points.size >= 2 }
    }

    fun totalDistance(records: List<LocationRecord>): Double {
        val valid = records.filter { !it.isInterrupted }
        return valid.zipWithNext().sumOf { (a, b) -> DouglasPeucker.haversine(a, b) }
    }
}
