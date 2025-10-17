package com.strucnjak.kindergarden.util

import kotlin.math.*

object Geo {
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    fun within(lat1: Double, lon1: Double, lat2: Double, lon2: Double, radiusMeters: Double): Boolean =
        distanceMeters(lat1, lon1, lat2, lon2) <= radiusMeters
}
