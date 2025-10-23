package com.strucnjak.kindergarden.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.strucnjak.kindergarden.R
import com.strucnjak.kindergarden.data.model.Park
import com.strucnjak.kindergarden.util.Geo

class LocationService : Service() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private val PARK_NOTIFY_DISTANCE_METERS = 100.0
    private val USER_NOTIFY_DISTANCE_METERS = 50.0

    private val notifiedParkIds = mutableSetOf<String>()
    private val notifiedUserIds = mutableSetOf<String>()

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

            db.child("parks").get().addOnSuccessListener { snap ->
                val parks = snap.children.mapNotNull { it.getValue(Park::class.java) }

                parks.forEach { park ->
                    if (park.authorId == uid) return@forEach

                    val d = Geo.distanceMeters(loc.latitude, loc.longitude, park.lat, park.lng)

                    if (d <= PARK_NOTIFY_DISTANCE_METERS && !notifiedParkIds.contains(park.id)) {
                        notify("Park je u blizini", "${park.name}: ${park.desc}")
                        notifiedParkIds.add(park.id)
                    }
                }
            }

            db.child("locations").get().addOnSuccessListener { snap ->
                val others = snap.children.mapNotNull { c ->
                    val id = c.key ?: return@mapNotNull null
                    if (id == uid) return@mapNotNull null
                    val lat = (c.child("lat").value as? Number)?.toDouble() ?: return@mapNotNull null
                    val lng = (c.child("lng").value as? Number)?.toDouble() ?: return@mapNotNull null
                    id to (lat to lng)
                }

                others.forEach { (otherId, userLoc) ->
                    val d = Geo.distanceMeters(loc.latitude, loc.longitude, userLoc.first, userLoc.second)

                    if (d <= USER_NOTIFY_DISTANCE_METERS && !notifiedUserIds.contains(otherId)) {
                        notify("Korisnik u blizini", "Korisnik je ${d.toInt()}m daleko")
                        notifiedUserIds.add(otherId)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, buildNotification())
        startLocation()
        checkNearbyImmediately()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Lokacija", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kindergarden prati lokaciju")
            .setContentText("Ažuriranje lokacije da bi se pronašli parkovi i korisnici u blizini")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun checkNearbyImmediately() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        client.lastLocation.addOnSuccessListener { loc ->
            if (loc == null) return@addOnSuccessListener
            val uid = auth.currentUser?.uid ?: return@addOnSuccessListener

            db.child("parks").get().addOnSuccessListener { snap ->
                val parks = snap.children.mapNotNull { it.getValue(Park::class.java) }

                parks.forEach { park ->
                    if (park.authorId == uid) return@forEach

                    val d = Geo.distanceMeters(loc.latitude, loc.longitude, park.lat, park.lng)

                    if (d <= PARK_NOTIFY_DISTANCE_METERS && !notifiedParkIds.contains(park.id)) {
                        notify("Park je u blizini", "${park.name}: ${park.desc}")
                        notifiedParkIds.add(park.id)
                    }
                }
            }

            db.child("locations").get().addOnSuccessListener { snap ->
                val others = snap.children.mapNotNull { c ->
                    val id = c.key ?: return@mapNotNull null
                    if (id == uid) return@mapNotNull null
                    val lat = (c.child("lat").value as? Number)?.toDouble() ?: return@mapNotNull null
                    val lng = (c.child("lng").value as? Number)?.toDouble() ?: return@mapNotNull null
                    id to (lat to lng)
                }

                others.forEach { (otherId, userLoc) ->
                    val d = Geo.distanceMeters(loc.latitude, loc.longitude, userLoc.first, userLoc.second)

                    if (d <= USER_NOTIFY_DISTANCE_METERS && !notifiedUserIds.contains(otherId)) {
                        notify("Korisnik u blizini", "Korisnik je ${d.toInt()}m daleko")
                        notifiedUserIds.add(otherId)
                    }
                }
            }
        }
    }

    private fun notify(title: String, body: String) {
        val channelId = "proximity_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Blizina", NotificationManager.IMPORTANCE_DEFAULT)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % 100000).toInt(), notif)
        } catch (se: SecurityException) { }
    }

    private fun startLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(60_000L)
            .build()
        client.requestLocationUpdates(req, callback, mainLooper)
    }

    override fun onDestroy() {
        super.onDestroy()
        client.removeLocationUpdates(callback)
    }
}
