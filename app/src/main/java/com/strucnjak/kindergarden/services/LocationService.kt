package com.strucnjak.kindergarden.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.getValue
import com.strucnjak.kindergarden.R
import com.strucnjak.kindergarden.data.model.Park
import com.strucnjak.kindergarden.util.Geo
import kotlin.math.min

class LocationService : Service() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val uid = auth.currentUser?.uid ?: return
            val data = mapOf(
                "lat" to loc.latitude,
                "lng" to loc.longitude,
                "timestamp" to System.currentTimeMillis()
            )
            db.child("locations").child(uid).setValue(data)

            // Proximity checks (simple local)
            db.child("parks").get().addOnSuccessListener { snap ->
                val parks = snap.children.mapNotNull { it.getValue<Park>() }
                val near = parks.minByOrNull { Geo.distanceMeters(loc.latitude, loc.longitude, it.lat, it.lng) }
                near?.let {
                    val d = Geo.distanceMeters(loc.latitude, loc.longitude, it.lat, it.lng)
                    if (d < 100) notify("You're near a park", "${it.type.ifEmpty { "Park" }}: ${it.desc}")
                }
            }
            db.child("locations").get().addOnSuccessListener { snap ->
                val others = snap.children.mapNotNull { c ->
                    val id = c.key ?: return@mapNotNull null
                    if (id == uid) return@mapNotNull null
                    val lat = (c.child("lat").value as? Number)?.toDouble() ?: return@mapNotNull null
                    val lng = (c.child("lng").value as? Number)?.toDouble() ?: return@mapNotNull null
                    lat to lng
                }
                val nearUser = others.minByOrNull { (lat, lng) -> Geo.distanceMeters(loc.latitude, loc.longitude, lat, lng) }
                nearUser?.let { (lat, lng) ->
                    val d = Geo.distanceMeters(loc.latitude, loc.longitude, lat, lng)
                    if (d < 50) notify("Nearby friend", "A user is ${(d.toInt())}m away")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, buildNotification())
        startLocation()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Location", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kindergarden tracking location")
            .setContentText("Updating your location to find nearby parks and friends")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun notify(title: String, body: String) {
        val channelId = "proximity_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Proximity", NotificationManager.IMPORTANCE_DEFAULT)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .build()
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % 100000).toInt(), notif)
    }

    private fun startLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .build()
        client.requestLocationUpdates(req, callback, mainLooper)
    }

    override fun onDestroy() {
        super.onDestroy()
        client.removeLocationUpdates(callback)
    }
}
