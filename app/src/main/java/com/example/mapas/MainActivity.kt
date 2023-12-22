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
import android.graphics.Color
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.widget.GridLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.PolylineOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var locationServiceIntent: Intent
    private var isTracking = false
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val pathPoints = mutableListOf<LatLng>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var location: Location
    private lateinit var timerTextView: TextView
    private lateinit var timerHandler: Handler
    private var elapsedTimeInSeconds: Long = 0
    private var elapsedTimeSeconds = 0L
    private lateinit var calculadoraAtividade: CalculadoraAtividade
    private var pesoUsuario: Double = 70.0 // Peso constante para teste
    private lateinit var containerOpcoes: GridLayout
    private lateinit var containerPauseFinalizar: GridLayout

    companion object {
        const val LOCATION_UPDATE_ACTION = "com.example.mapas.LOCATION_UPDATE"
        const val RESULT_RECEIVER = "result_receiver"
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val resultReceiver = intent?.getParcelableExtra<ResultReceiver>(RESULT_RECEIVER)
            val bundle = Bundle()

            if (checkLocationPermission()) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val latLng = LatLng(location.latitude, location.longitude)
                        bundle.putParcelable("location", latLng)
                        resultReceiver?.send(0, bundle)

                        // Atualiza a rota
                        updateLocation(location)
                    }
                }
            } else {
                Log.e("LocationUpdate", "Permissão de localização não concedida")
            }
        }
    }

    private val locationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            val binder = service as LocationForegroundService.LocalBinder
            val boundService = binder.getService()
            boundService.requestLocationUpdates()
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            // Este método será chamado quando a conexão com o serviço for perdida
        }
    }

    //foco na localização atual do mapa ao iniciar
    private fun focusOnCurrentLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 19f))
                }
            }
        } else {
            Log.e("LocationUpdate", "Permissão de localização não concedida")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicialize a fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicialize o TextView do temporizador
        timerTextView = findViewById(R.id.timerTextView)

        // Inicialize o manipulador do temporizador
        timerHandler = Handler(Looper.getMainLooper())

        // Inicialize a calculadora com o peso do usuário
        calculadoraAtividade = CalculadoraAtividade(pesoUsuario)

        //Incialização oculta do container containerPauseFinalizar
        containerOpcoes = findViewById(R.id.containeropcoes)
        containerPauseFinalizar = findViewById(R.id.containerPauseFinalizar)
        containerPauseFinalizar.visibility = View.GONE // Inicialmente oculto


        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        locationServiceIntent = Intent(this, LocationForegroundService::class.java)

        val btnIniciarAtividade: Button = findViewById(R.id.bntIniciarAtividade)
        btnIniciarAtividade.setOnClickListener {
            startTracking()
        }


        val btnFinalizarAtividade: Button = findViewById(R.id.btnFinalizarAtividade)
        btnFinalizarAtividade.setOnClickListener {
            stopTracking()
        }

        // Registra o receiver para receber atualizações de localização do serviço
        val filter = IntentFilter(LOCATION_UPDATE_ACTION)
        registerReceiver(locationReceiver, filter)
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
            focusOnCurrentLocation() // Chama a função para centralizar no local atual
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    //Essa função serve para gerenciar o estado do botão quando utilizar um único botão;

    /*private fun toggleTracking() {
        if (isTracking) {
            stopTracking()
        } else {
            startTracking()
        }
    }*/

    private fun startTracking() {
        isTracking = true


        // Oculta o containerOpcoes original e exibe o containerPauseFinalizar
        containerOpcoes.visibility = View.GONE
        containerPauseFinalizar.visibility = View.VISIBLE

        // Oculta o botão startStopButton
        findViewById<Button>(R.id.bntIniciarAtividade)?.visibility = View.GONE

        // Limpa a polilinha anterior
        pathPoints.clear()
        map.clear()

        // Zera o contador
        elapsedTimeSeconds = 0L
        updateTimerText()

        // Inicia o serviço em primeiro plano
        startService(locationServiceIntent)
        // Vincula o serviço em primeiro plano
        bindService(locationServiceIntent, locationServiceConnection, Context.BIND_AUTO_CREATE)

        // Inicia o temporizador
        startTimer()

        // Atualiza as métricas periodicamente
        timerHandler.post(object : Runnable {
            override fun run() {
                updateActivityMetrics()
                timerHandler.postDelayed(this, 1000)
            }
        })

        Log.d("LocationUpdate", "Tracking Started")
    }

    private fun stopTracking() {
        isTracking = false

        // Oculta o containerPauseFinalizar e exibe o containerOpcoes original
        containerPauseFinalizar.visibility = View.GONE
        containerOpcoes.visibility = View.VISIBLE

        // Exibe o botão startStopButton
        findViewById<Button>(R.id.bntIniciarAtividade)?.visibility = View.VISIBLE

        // Desvincula o serviço em primeiro plano
        unbindService(locationServiceConnection)
        // Para o serviço em primeiro plano
        stopService(locationServiceIntent)

        // Para o temporizador
        stopTimer()
    }

    private fun startTimer() {
        elapsedTimeInSeconds = 0
        val startTimeMillis = SystemClock.elapsedRealtime()

        timerHandler.post(object : Runnable {
            override fun run() {
                // Calcula o tempo decorrido com base no tempo atual do sistema
                val elapsedMillis = SystemClock.elapsedRealtime() - startTimeMillis
                elapsedTimeInSeconds = (elapsedMillis / 1000L).toInt().toLong()

                // Atualiza o TextView do temporizador
                runOnUiThread {
                    timerTextView.text = formatElapsedTime(elapsedTimeInSeconds)
                }

                // Incrementa o tempo decorrido
                elapsedTimeInSeconds++

                // Agenda a execução novamente após 1 segundo
                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateActivityMetrics() {
        val velocidadeMedia = calcularVelocidadeMedia() // Implemente a lógica para calcular a velocidade média
        val tempoDecorrido = elapsedTimeInSeconds

        // Calcula e atualiza as métricas
        val calorias = calculadoraAtividade.calcularCalorias(velocidadeMedia, tempoDecorrido)
        val distancia = updateDistance()
        val ritmo = calculadoraAtividade.calcularRitmo(tempoDecorrido, distancia)

        // Formata os valores para duas casas decimais
        val formattedCalorias = String.format("%.2f", calorias)
        val formattedDistancia = String.format("%.2f", distancia)
        val formattedRitmo = String.format("%.2f", ritmo)

        // Atualiza os TextViews
        updateTextView(R.id.valorcalorias, formattedCalorias)
        updateTextView(R.id.valordistancia, formattedDistancia)
        updateTextView(R.id.valorritmo, formattedRitmo)
    }

    private fun updateTextView(textViewId: Int, value: String) {
        findViewById<TextView>(textViewId)?.text = value
    }

    private fun calcularVelocidadeMedia(): Double {
        // Implemente a lógica para calcular a velocidade média com base na rota
        // Retorna um valor de exemplo, substitua pela lógica real
        return 10.0
    }


    private fun stopTimer() {
        // Remove callbacks do Handler para parar o temporizador
        timerHandler.removeCallbacksAndMessages(null)
    }

    private fun formatElapsedTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun updateTimerText() {
        val minutes = elapsedTimeSeconds / 60
        val seconds = elapsedTimeSeconds % 60
        val timerText = String.format("%02d:%02d", minutes, seconds)
        timerTextView.text = timerText
    }

    private fun updateLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        pathPoints.add(latLng)

        // Polyline com as configurações
        map.addPolyline(PolylineOptions().addAll(pathPoints).color(Color.RED).width(15f))
        Log.d("LocationUpdate", "Latitude: ${location.latitude}, Longitude: ${location.longitude}")

        // Atualiza a distância em tempo real
        updateDistance()
    }

    private fun updateDistance(): Double {
        var distancia = 0.0
        if (pathPoints.size >= 2) {
            val lastTwoPoints = pathPoints.takeLast(2)
            distancia = calculateDistance(lastTwoPoints[0], lastTwoPoints[1])
            calculadoraAtividade.adicionarDistancia(distancia)
        }
        return distancia
    }

    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val result = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude, result
        )
        return result[0].toDouble()
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}



