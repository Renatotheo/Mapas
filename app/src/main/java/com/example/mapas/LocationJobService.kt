package com.example.mapas

// LocationJobService.kt

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest

@SuppressLint("SpecifyJobSchedulerIdRange")
class LocationJobService : JobService() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("LocationJobService", "Executando tarefa agendada")
        if (checkLocationPermission()) {
            startLocationUpdates()
        } else {
            Log.e("LocationJobService", "Permissão de localização não concedida1")
            jobFinished(params, true)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d("LocationJobService", "Ssserviço encerrado")
        // Retorna true se a tarefa deve ser reagendada
        return true
    }

    // Iniciar atualizações contínuas de localização
    private fun startLocationUpdates() {
        Log.d("LocationJobService", "Iniciando atualizações de localização em segundo plano...")
        if (checkLocationPermission()) {
            val locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)  // Solicitar atualizações a cada segundo
                .setFastestInterval(5000)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } else {
            // Lida com o caso em que a permissão de localização não foi concedida
            Log.e("LocationJobService", "Permissão de localização não concedida2")
        }
    }

    // Callback para receber as atualizações de localização
    private val locationCallback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
            locationResult.lastLocation?.let { location ->
                // Enviar a localização para a MainActivity
                sendLocationToActivity(location)

                Log.d("LocationJobService", "Latitude: ${location.latitude}, Longitude: ${location.longitude}")
            }
        }
    }

    // Verifica se a permissão de localização foi concedida
    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendLocationToActivity(location: Location) {
        val intent = Intent(MainActivity.LOCATION_UPDATE_ACTION)
        val bundle = Bundle()
        val latLng = LatLng(location.latitude, location.longitude)
        bundle.putParcelable("location", latLng)
        intent.putExtra(MainActivity.RESULT_RECEIVER, ResultReceiver(null).apply {
            send(0, bundle)
        })
        sendBroadcast(intent)
    }
}