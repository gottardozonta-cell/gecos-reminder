package com.example.gecosreminder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

object Geo {

    @SuppressLint("MissingPermission")
    suspend fun posizione(c: Context): Location? = try {
        withTimeout(9000) {
            val client = LocationServices.getFusedLocationProviderClient(c)
            client.lastLocation.await() ?: client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        }
    } catch (e: Exception) { null }

    private fun Task<Location>.await(): Location? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener { if (cont.isActive) cont.resume(null) }
            addOnCanceledListener { if (cont.isActive) cont.resume(null) }
        }

    fun entroRaggio(loc: Location, s: Sede): Boolean {
        val res = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, s.lat, s.lng, res)
        return res[0] <= s.raggio
    }
}
