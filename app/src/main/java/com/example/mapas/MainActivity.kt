package com.example.mapas

// MainActivity.kt

import android.content.*
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.pm.PackageManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.PolylineOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var isTracking = false
    private val JOB_ID = 123

    private val pathPoints = mutableListOf<LatLng>()

    companion object {
        const val LOCATION_UPDATE_ACTION = "com.example.mapas.LOCATION_UPDATE"
        const val RESULT_RECEIVER = "result_receiver"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val startStopButton: Button = findViewById(R.id.startButton)
        startStopButton.setOnClickListener {
            toggleTracking()

            // Atualizar o texto do botão com base no estado atual
            startStopButton.text = if (isTracking) "Finalizar Atividade" else "Iniciar Atividade"
        }

        locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(5000)
            .setFastestInterval(2000)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { updateLocation(it) }
            }
        }

        // Registra o receiver para receber atualizações de localização do serviço
        val filter = IntentFilter(LOCATION_UPDATE_ACTION)
        registerReceiver(locationReceiver, filter)
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val resultReceiver = intent?.getParcelableExtra<ResultReceiver>(RESULT_RECEIVER)
            val bundle = Bundle()

            if (checkLocationPermission()) {
                // Obter a localização atual do provedor de localização
                fusedLocationClient.lastLocation?.addOnSuccessListener { location ->
                    location?.let {
                        val latLng = LatLng(location.latitude, location.longitude)
                        bundle.putParcelable("location", latLng)
                        resultReceiver?.send(0, bundle)

                        // Atualiza a rota
                        updateLocation(location)
                    }
                }
            } else {
                // Lida com o caso em que a permissão de localização não foi concedida
                // Você pode exibir uma solicitação de permissão ou lidar com isso de acordo com sua lógica
                Log.e("LocationUpdate", "Permissão de localização não concedida")
            }
        }
    }

    // Verifica se a permissão de localização foi concedida
    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun toggleTracking() {
        if (isTracking) {
            stopTracking()
            stopJob()
        } else {
            startTracking()
            scheduleJob()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Desregistra o receiver quando a atividade é destruída
        unregisterReceiver(locationReceiver)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = true

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startTracking() {
        isTracking = true
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
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        Log.d("LocationUpdate", "Tracking Started")
    }

    private fun stopTracking() {
        isTracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        pathPoints.add(latLng)
        map.addPolyline(PolylineOptions().addAll(pathPoints))
        Log.d("LocationUpdate", "Latitude: ${location.latitude}, Longitude: ${location.longitude}")
    }

    private fun scheduleJob() {
        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

        // Cancelar o job existente com o mesmo ID
        jobScheduler.cancel(JOB_ID)

        // Criar um novo job
        val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(this, LocationJobService::class.java))
            .setMinimumLatency(5 * 1000)  // Agendar o mais rápido possível (5 segundos)
            .setPersisted(true)
            .build()

        jobScheduler.schedule(jobInfo)
    }


    private fun stopJob() {
        //val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        //jobScheduler.cancel(JOB_ID)
        Log.d("LocationUpdate", "Job Canceled")
    }
}


