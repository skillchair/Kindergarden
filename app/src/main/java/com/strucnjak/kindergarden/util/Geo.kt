package com.strucnjak.kindergarden.util

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.math.*

object Geo {
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return SphericalUtil.computeDistanceBetween(
            LatLng(lat1, lon1),
            LatLng(lat2, lon2)
        )
    }

    fun within(lat1: Double, lon1: Double, lat2: Double, lon2: Double, radiusMeters: Double): Boolean =
        distanceMeters(lat1, lon1, lat2, lon2) <= radiusMeters

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
}
