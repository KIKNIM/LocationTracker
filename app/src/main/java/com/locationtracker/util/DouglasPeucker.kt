package com.locationtracker.util

import com.locationtracker.data.model.LocationRecord
import kotlin.math.*

/**
 * Douglas-Peucker 轨迹抽稀
 *
 * 保留关键转折点，丢弃共线冗余点。
 * 中断标记点不参与抽稀（始终保留）。
 */
object DouglasPeucker {

    fun simplify(points: List<LocationRecord>, epsilon: Double = 10.0): List<LocationRecord> {
        val valid = points.filter { !it.isInterrupted }
        if (valid.size <= 2) return points

        val kept = linkedSetOf(valid.first(), valid.last())
        recurse(valid, 0, valid.size - 1, epsilon, kept)

        // 中断标记始终保留
        return (kept + points.filter { it.isInterrupted }).sortedBy { it.timestamp }
    }

    private fun recurse(
        pts: List<LocationRecord>, i: Int, j: Int, eps: Double, out: LinkedHashSet<LocationRecord>
    ) {
        var maxD = 0.0; var maxK = i
        for (k in i + 1 until j) {
            val d = pDist(pts[k], pts[i], pts[j])
            if (d > maxD) { maxD = d; maxK = k }
        }
        if (maxD > eps) {
            recurse(pts, i, maxK, eps, out); out.add(pts[maxK]); recurse(pts, maxK, j, eps, out)
        }
    }

    /** 点 P 到线段 AB 的垂直距离（Haversine 近似） */
    private fun pDist(p: LocationRecord, a: LocationRecord, b: LocationRecord): Double {
        val lat1 = Math.toRadians(a.latitude); val lon1 = Math.toRadians(a.longitude)
        val lat2 = Math.toRadians(b.latitude); val lon2 = Math.toRadians(b.longitude)
        val latP = Math.toRadians(p.latitude); val lonP = Math.toRadians(p.longitude)

        val cosAvg = cos((lat1 + lat2) / 2)
        val (x1, y1) = lon1 * cosAvg to lat1
        val (x2, y2) = lon2 * cosAvg to lat2
        val (xP, yP) = lonP * cos(latP) to latP

        val dx = x2 - x1; val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) return haversine(a, p)

        val t = ((xP - x1) * dx + (yP - y1) * dy) / (dx * dx + dy * dy)
        return when {
            t < 0 -> haversine(a, p)
            t > 1 -> haversine(b, p)
            else -> {
                val px = x1 + t * dx; val py = y1 + t * dy
                sqrt(((yP - py) * 111_320).pow(2) + ((xP - px) * 111_320 * cos(latP)).pow(2))
            }
        }
    }

    fun haversine(a: LocationRecord, b: LocationRecord): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val hv = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return 2 * R * atan2(sqrt(hv), sqrt(1 - hv))
    }
}
