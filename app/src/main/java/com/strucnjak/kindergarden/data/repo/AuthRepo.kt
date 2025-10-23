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

    suspend fun checkUsernameExists(username: String): Boolean {
        val snapshot = db.child("users").get().await()
        return snapshot.children.any {
            val user = it.getValue(User::class.java)
            user?.username?.equals(username, ignoreCase = true) == true
        }
    }

    suspend fun checkPhoneExists(phone: String): Boolean {
        val snapshot = db.child("users").get().await()
        return snapshot.children.any {
            val user = it.getValue(User::class.java)
            user?.phone == phone
        }
    }

    suspend fun register(
        email: String,
        password: String,
        username: String,
        name: String,
        phone: String,
        photoUrl: String?
    ): Result<Unit> = runCatching {
        try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (e: Exception) {
            if (e.message?.contains("email address is already in use", ignoreCase = true) == true) {
                error("Email adresa je zauzeta")
            } else if (e.message?.contains("badly formatted", ignoreCase = true) == true) {
                error("Email adresa nije validna")
            } else if (e.message?.contains("weak password", ignoreCase = true) == true) {
                error("Lozinka mora imati najmanje 6 karaktera")
            } else {
                error("Greška pri kreiranju naloga: ${e.message}")
            }
        }

        if (checkUsernameExists(username)) {
            auth.currentUser?.delete()?.await()
            error("Korisničko ime je već zauzeto")
        }

        if (phone.isNotEmpty()) {
            if (checkPhoneExists(phone)) {
                auth.currentUser?.delete()?.await()
                error("Broj telefona je već zauzet")
            }
        }

        val uid = auth.currentUser?.uid ?: error("Greška pri registraciji korisnika - nema UID")

        val user = User(
            id = uid,
            username = username,
            name = name,
            phone = phone,
            photoUrl = photoUrl ?: "",
            points = 0
        )

        try {
            db.child("users").child(uid).setValue(user).await()
        } catch (e: Exception) {
            auth.currentUser?.delete()?.await()
            error("Greška pri čuvanju korisnika u bazu: ${e.message}")
        }

        Unit
    }

    fun logout() = auth.signOut()
}

