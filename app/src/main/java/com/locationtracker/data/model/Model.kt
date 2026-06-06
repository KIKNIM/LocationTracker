package com.locationtracker.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 位置记录
 *
 * date 字段配合索引实现按日隔离查询（等效按日期分表）
 */
@Entity(
    tableName = "location_records",
    indices = [
        Index("timestamp", name = "idx_ts"),
        Index("date", name = "idx_date"),
        Index("date", "timestamp", name = "idx_date_ts")
    ]
)
data class LocationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val date: String,            // yyyy-MM-dd
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val provider: String = "fused",
    val isInterrupted: Boolean = false,
    val batteryLevel: Int? = null
)

/** 用户活动状态 — Activity Recognition 检测 */
enum class UserActivityState {
    STATIONARY, WALKING, RUNNING, DRIVING, BICYCLING, UNKNOWN
}
