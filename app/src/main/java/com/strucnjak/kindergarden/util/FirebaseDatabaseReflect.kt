package com.strucnjak.kindergarden.util

import com.strucnjak.kindergarden.data.model.User

object FirebaseDatabaseReflect {
    fun addUsersListener(onUpdate: (List<User>) -> Unit): (() -> Unit)? {
        return try {
            val dbClass = Class.forName("com.google.firebase.database.FirebaseDatabase")
            val getInstance = dbClass.getMethod("getInstance")
            val dbInstance = getInstance.invoke(null)

            val dbRefClass = Class.forName("com.google.firebase.database.DatabaseReference")
            val getRefMethod = dbClass.getMethod("getReference")
            val rootRef = getRefMethod.invoke(dbInstance)
            val childMethod = dbRefClass.getMethod("child", String::class.java)
            val usersRef = childMethod.invoke(rootRef, "users")

            val listenerInterface = Class.forName("com.google.firebase.database.ValueEventListener")
            val snapshotClass = Class.forName("com.google.firebase.database.DataSnapshot")

            val handler = java.lang.reflect.InvocationHandler { _, method, args ->
                if (method.name == "onDataChange") {
                    val snapshot = args?.get(0)
                    if (snapshot != null) {
                        val childrenMethod = snapshotClass.getMethod("getChildren")
                        val iterator = (childrenMethod.invoke(snapshot) as Iterable<*>).iterator()
                        val list = mutableListOf<User>()
                        val getValueMethod = snapshotClass.getMethod("getValue", Class::class.java)
                        val getKeyMethod = snapshotClass.getMethod("getKey")
                        while (iterator.hasNext()) {
                            val child = iterator.next()
                            try {
                                val userObj = getValueMethod.invoke(child, User::class.java) as? User
                                val userId = getKeyMethod.invoke(child) as? String
                                if (userObj != null && userId != null) {
                                    list.add(userObj.copy(id = userId))
                                }
                            } catch (_: Exception) {
                            }
                        }
                        onUpdate(list.sortedByDescending { it.points })
                    }
                }
                null
            }

            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface),
                handler
            )

            val addMethod = dbRefClass.getMethod("addValueEventListener", listenerInterface)
            addMethod.invoke(usersRef, listener)

            val removeMethod = dbRefClass.getMethod("removeEventListener", listenerInterface)
            return { try { removeMethod.invoke(usersRef, listener) } catch (_: Exception) { } }
        } catch (_: Throwable) {
            null
        }
    }

    fun addLocationsListener(onUpdate: (Map<String, Pair<Double, Double>>) -> Unit): (() -> Unit)? {
        return try {
            val dbClass = Class.forName("com.google.firebase.database.FirebaseDatabase")
            val getInstance = dbClass.getMethod("getInstance")
            val dbInstance = getInstance.invoke(null)

            val dbRefClass = Class.forName("com.google.firebase.database.DatabaseReference")
            val getRefMethod = dbClass.getMethod("getReference")
            val rootRef = getRefMethod.invoke(dbInstance)
            val childMethod = dbRefClass.getMethod("child", String::class.java)
            val locationsRef = childMethod.invoke(rootRef, "locations")

            val listenerInterface = Class.forName("com.google.firebase.database.ValueEventListener")
            val snapshotClass = Class.forName("com.google.firebase.database.DataSnapshot")

            val handler = java.lang.reflect.InvocationHandler { _, method, args ->
                if (method.name == "onDataChange") {
                    val snapshot = args?.get(0)
                    if (snapshot != null) {
                        val childrenMethod = snapshotClass.getMethod("getChildren")
                        val iterator = (childrenMethod.invoke(snapshot) as Iterable<*>).iterator()
                        val map = mutableMapOf<String, Pair<Double, Double>>()
                        while (iterator.hasNext()) {
                            val childAny = iterator.next() ?: continue
                            try {
                                val childClass = childAny.javaClass
                                val keyMethod = childClass.getMethod("getKey")
                                val key = keyMethod.invoke(childAny) as? String ?: continue
                                val childMethodRef = childClass.getMethod("child", String::class.java)
                                val latNode = childMethodRef.invoke(childAny, "lat")
                                val lngNode = childMethodRef.invoke(childAny, "lng")
                                val valueMethod = latNode?.javaClass?.getMethod("getValue")
                                val latVal = valueMethod?.invoke(latNode) as? Number
                                val lngVal = valueMethod?.invoke(lngNode) as? Number
                                if (latVal != null && lngVal != null) {
                                    map[key] = Pair(latVal.toDouble(), lngVal.toDouble())
                                }
                            } catch (_: Exception) { }
                        }
                        onUpdate(map)
                    }
                }
                null
            }

            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface),
                handler
            )

            val addMethod = dbRefClass.getMethod("addValueEventListener", listenerInterface)
            addMethod.invoke(locationsRef, listener)

            val removeMethod = dbRefClass.getMethod("removeEventListener", listenerInterface)
            return { try { removeMethod.invoke(locationsRef, listener) } catch (_: Exception) { } }
        } catch (_: Throwable) {
            null
        }
    }
}
