package com.sansoft.sangeocam

import androidx.room.*

@Dao
interface MediaDao {
    @Insert
    fun insert(mediaItem: MediaItem): Long

    @Query("SELECT * FROM media_items ORDER BY timestamp DESC")
    fun getAllMedia(): List<MediaItem>

    @Query("SELECT * FROM media_items WHERE type = :type ORDER BY timestamp DESC")
    fun getMediaByType(type: String): List<MediaItem>

    @Delete
    fun delete(mediaItem: MediaItem)
}
