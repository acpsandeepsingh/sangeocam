package com.sansoft.sangeocam

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val type: String
)
