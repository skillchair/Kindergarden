package com.strucnjak.kindergarden.data.repo

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.strucnjak.kindergarden.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UserRepo {
    private val db = FirebaseDatabase.getInstance().reference.child("users")

    fun topUsersFlow(limit: Int = 20): Flow<List<User>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { child ->
                    try {
                        child.getValue(User::class.java)?.copy(id = child.key ?: "")
                    } catch (_: Exception) {
                        null
                    }
                }.sortedByDescending { it.points }
                    .take(limit)
                trySend(users)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        awaitClose { db.removeEventListener(listener) }
    }
}

