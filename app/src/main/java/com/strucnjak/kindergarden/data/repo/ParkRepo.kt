package com.strucnjak.kindergarden.data.repo

import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.strucnjak.kindergarden.data.model.Park
import com.strucnjak.kindergarden.util.Geo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ParkRepo(
    private val db: DatabaseReference = com.google.firebase.database.FirebaseDatabase.getInstance().reference,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val parksRef get() = db.child("parks")
    private val usersRef get() = db.child("users")

    fun parksFlow(userLat: Double? = null, userLng: Double? = null, radiusKm: Double = 24.0): Flow<List<Park>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull {
                    val park = it.getValue(Park::class.java)
                    park?.copy(id = it.key ?: "")
                }.filter { park ->
                    if (userLat != null && userLng != null) {
                        val distance = Geo.distanceMeters(userLat, userLng, park.lat, park.lng)
                        distance <= radiusKm * 1000
                    } else {
                        true
                    }
                }
                trySend(list)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        parksRef.addValueEventListener(listener)
        awaitClose { parksRef.removeEventListener(listener) }
    }

    suspend fun addPark(park: Park): Result<Unit> = runCatching {
        val key = park.id.ifEmpty { parksRef.push().key ?: error("No key") }
        parksRef.child(key).setValue(park.copy(id = key)).await()
        val uid = park.authorId
        if (uid.isNotEmpty()) {
            val userPointsRef = usersRef.child(uid).child("points")
            val current = (userPointsRef.get().await().value as? Long) ?: 0L
            userPointsRef.setValue(current + 100).await()
        }
        Unit
    }

    suspend fun uploadImage(uri: Uri): Result<String> = runCatching {
        suspendCancellableCoroutine { cont ->
            MediaManager.get().upload(uri)
                .unsigned(com.strucnjak.kindergarden.BuildConfig.CLOUDINARY_UPLOAD_PRESET)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        val url = (resultData?.get("secure_url") as? String)
                            ?: (resultData?.get("url") as? String)
                            ?: ""
                        if (!cont.isCompleted) cont.resume(url)
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        if (!cont.isCompleted) cont.resumeWithException(RuntimeException(error?.description ?: "Upload error"))
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                    }
                })
                .dispatch()
        }
    }

    suspend fun searchByText(query: String, userLat: Double? = null, userLng: Double? = null, radiusKm: Double = 24.0): List<Park> {
        val snap = parksRef.get().await()
        val all = snap.children.mapNotNull {
            val park = it.getValue(Park::class.java)
            park?.copy(id = it.key ?: "")
        }.filter { park ->
            if (userLat != null && userLng != null) {
                val distance = Geo.distanceMeters(userLat, userLng, park.lat, park.lng)
                distance <= radiusKm * 1000
            } else {
                true
            }
        }
        val q = query.trim().lowercase()
        return if (q.isEmpty()) all else all.filter {
            it.name.lowercase().contains(q) ||
            it.desc.lowercase().contains(q) ||
            it.type.lowercase().contains(q)
        }
    }
}

