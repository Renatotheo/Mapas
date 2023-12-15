package com.example.mapas

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Button
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private val locationPermissionCode = 1
    private var tracking = false
    private val route = ArrayList<LatLng>()

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationRequest: LocationRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configuração do LocationRequest
        locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(3000) // Intervalo inicial em milissegundos
            .setFastestInterval(1000) // Intervalo mais rápido

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    addLocationToRoute(it)
                }
            }
        }

        val startButton = findViewById<Button>(R.id.startButton)
        startButton.setOnClickListener {
            if (tracking) {
                // Finalizar o rastreamento
                tracking = false
                stopLocationUpdates()
                startButton.text = "Iniciar Atividade"
            } else {
                // Iniciar o rastreamento
                tracking = true
                route.clear()
                startButton.text = "Finalizar Atividade"
                startLocationUpdates()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        if (hasLocationPermission()) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            map.isMyLocationEnabled = true
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, permissions, locationPermissionCode)
    }

    private fun startLocationUpdates() {
        if (hasLocationPermission()) {
            locationRequest?.let { request ->
                locationCallback?.let { callback ->
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        // Inicia as atualizações de localização
                        fusedLocationClient?.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    }
                }
            } ?: run {
                requestLocationPermission()
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun stopLocationUpdates() {
        // Para as atualizações de localização
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
    }

    private fun addLocationToRoute(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        route.add(latLng)

        val polylineOptions = PolylineOptions().addAll(route)
        map.addPolyline(polylineOptions)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                map.isMyLocationEnabled = true
            }
        }
    }
}

