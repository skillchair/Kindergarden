package com.strucnjak.kindergarden.util

object FirebaseReflect {
    fun getCurrentUserInfo(): Pair<String?, String?> {
        return try {
            val authClass = Class.forName("com.google.firebase.auth.FirebaseAuth")
            val getInstance = authClass.getMethod("getInstance")
            val authInstance = getInstance.invoke(null)
            val getCurrentUser = authClass.getMethod("getCurrentUser")
            val user = getCurrentUser.invoke(authInstance)
            if (user == null) return Pair(null, null)
            val userClass = user.javaClass
            val getUid = userClass.getMethod("getUid")
            val getEmail = userClass.getMethod("getEmail")
            val uid = getUid.invoke(user) as? String
            val email = getEmail.invoke(user) as? String
            Pair(uid, email)
        } catch (t: Throwable) {
            Pair(null, null)
        }
    }

    fun signOut(): Boolean {
        return try {
            val authClass = Class.forName("com.google.firebase.auth.FirebaseAuth")
            val getInstance = authClass.getMethod("getInstance")
            val authInstance = getInstance.invoke(null)
            val signOut = authClass.getMethod("signOut")
            signOut.invoke(authInstance)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
