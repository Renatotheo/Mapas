package com.example.mapas

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mapas.MainActivity
import com.example.mapas.R
import com.google.android.gms.location.*
import android.Manifest
import android.content.pm.PackageManager

class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val NOTIFICATION_CHANNEL_ID = "LocationForegroundServiceChannel"
    private val NOTIFICATION_ID = 123

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocationToActivity(location)
                    Log.d("LocationForeground", "Latitude: ${location.latitude}, Longitude: ${location.longitude}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        stopLocationUpdates()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun requestLocationUpdates() {
        startLocationUpdates()
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Localização em segundo plano")
            .setContentText("Estamos rastreando sua localização.")
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startLocationUpdates() {
        if (checkLocationPermission()) {

            val locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)  // Solicitar atualizações a cada 5 segundos
                .setFastestInterval(3000)

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            Log.d("LocationForegroundService", "Location updates requested")
        } else {
            Log.e("LocationForegroundService", "Permissão de localização não concedida")
        }
    }

    // Adicione este método para verificar a permissão de localização
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun sendLocationToActivity(location: Location) {
        val intent = Intent(MainActivity.LOCATION_UPDATE_ACTION)
        val bundle = Bundle()
        val latLng = Location(location)
        bundle.putParcelable("location", latLng)
        intent.putExtra(MainActivity.RESULT_RECEIVER, ResultReceiver(null).apply {
            send(0, bundle)
        })
        sendBroadcast(intent)
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService {
            return this@LocationForegroundService
        }
    }
}

