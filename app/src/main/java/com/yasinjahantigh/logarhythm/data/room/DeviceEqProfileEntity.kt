package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_eq_profiles")
data class DeviceEqProfileEntity(
    @PrimaryKey val deviceKey: String,
    val deviceLabel: String,
    val enabled: Boolean,
    val presetIndex: Int,
    val bandLevelsCsv: String,
    val bassStrength: Int,
    val virtualizerStrength: Int,
    val updatedAt: Long
)
