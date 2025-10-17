package com.strucnjak.kindergarden.data.repo

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.strucnjak.kindergarden.data.model.User
import kotlinx.coroutines.tasks.await

class AuthRepo {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    suspend fun register(
        email: String,
        password: String,
        username: String,
        name: String,
        phone: String,
        photoUrl: String?
    ): Result<Unit> = runCatching {
        auth.createUserWithEmailAndPassword(email, password).await()
        val uid = auth.currentUser?.uid ?: error("No user after registration")
        val user = User(
            id = uid,
            username = username,
            name = name,
            phone = phone,
            photoUrl = photoUrl ?: "",
            points = 0
        )
        db.child("users").child(uid).setValue(user).await()
        Unit
    }

    fun logout() = auth.signOut()
}

