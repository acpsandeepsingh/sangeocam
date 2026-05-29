package com.sansoft.sangeocam

data class Feature(
    val type: FeatureType,
    val title: String,
    val description: String,
    val icon: Int
)

enum class FeatureType {
    TIMESTAMP_GEOTAG,
    MAP_VIEW,
    VIDEO_LOCATION,
    WEATHER,
    OFFLINE_MAPS,
    MULTI_CAMERA,
    SHARING
}
