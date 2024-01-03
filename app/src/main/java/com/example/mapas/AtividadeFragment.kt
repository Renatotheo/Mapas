package com.example.mapas

import android.content.*
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.IBinder
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.PolylineOptions


class AtividadeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var locationServiceIntent: Intent
    private var isTracking = false
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val pathPoints = mutableListOf<LatLng>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var location: Location
    private lateinit var timerTextView: TextView
    private var valorCalorias: String = ""
    private var valorDistancia: String = ""
    private var valorRitmo: String = ""
    private lateinit var timerHandler: Handler
    private var elapsedTimeInSeconds: Long = 0
    private var elapsedTimeSeconds = 0L
    private lateinit var calculadoraAtividade: CalculadoraAtividade
    private var pesoUsuario: Double = 70.0 // Peso constante para teste
    private lateinit var containerOpcoes: GridLayout
    private lateinit var containerPauseFinalizar: GridLayout
    private var tempoDecorridoMillis: Long = 0
    private var startTimeMillis: Long = 0
    private lateinit var btnPauseContinuarAtividade: Button
    private val MIN_DISTANCE_CHANGE_FOR_UPDATE = 10f // Ajuste conforme necessário
    private val MIN_ANGLE_CHANGE_FOR_UPDATE = 10f // Ajuste conforme necessário
    private var previousLocation: Location? = null


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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("AtividadeFragment", "onCreateView chamado")
        // Infla o layout do fragmento
        val rootView = inflater.inflate(R.layout.fragment_atividade, container, false)

        // Inicialize a fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())


        // Inicialize o TextView do temporizador
        timerTextView = rootView.findViewById(R.id.timerTextView)

        // Inicialize o manipulador do temporizador
        timerHandler = Handler(Looper.getMainLooper())

        // Inicialize a calculadora com o peso do usuário
        calculadoraAtividade = CalculadoraAtividade(pesoUsuario)

        //Incialização oculta do container containerPauseFinalizar
        containerOpcoes = rootView.findViewById(R.id.containeropcoes)
        containerPauseFinalizar = rootView.findViewById(R.id.containerPauseFinalizar)
        containerPauseFinalizar.visibility = View.GONE // Inicialmente oculto

        // Inicializa o botão de pausa/continuar
        btnPauseContinuarAtividade = rootView.findViewById(R.id.btnPauseContinuarAtividade)


        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        locationServiceIntent = Intent(requireContext(), LocationForegroundService::class.java)


        val btnIniciarAtividade: Button = rootView.findViewById(R.id.bntIniciarAtividade)
        btnIniciarAtividade.setOnClickListener {
            Log.d("AtividadeFragment", "Botão Iniciar Pressionado")
            startTracking()
            // Configura o estado inicial do btnPauseContinuarAtividade
            setPauseContinueButtonInitialState()

        }

        val btnPauseContinuarAtividade: Button = rootView.findViewById(R.id.btnPauseContinuarAtividade)
        btnPauseContinuarAtividade.setOnClickListener {
            toggleTracking()

            // Atualizar o texto do botão com base no estado atual
            btnPauseContinuarAtividade.text = if (isTracking) "Pausar Atividade" else "Continuar Atividade"
        }


        val btnFinalizarAtividade: Button = rootView.findViewById(R.id.btnFinalizarAtividade)
        btnFinalizarAtividade.setOnClickListener {
            stopTracking()
        }

        // Registra o receiver para receber atualizações de localização do serviço
        val filter = IntentFilter(LOCATION_UPDATE_ACTION)
        requireActivity().registerReceiver(locationReceiver, filter)

        return rootView
    }

    override fun onDestroy() {
        super.onDestroy()
        // Desregistra o receiver quando a atividade é destruída
        requireActivity().unregisterReceiver(locationReceiver)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = true

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            focusOnCurrentLocation() // Chama a função para centralizar no local atual
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    //Essa função serve para gerenciar o estado do botão quando utilizar um único botão;
    private fun toggleTracking() {
        if (isTracking) {
            pauseTracking()
        } else {
            continueTracking()
        }
    }

    private fun pauseTracking() {
        // Lógica para pausar a atividade

        isTracking = false
        stopTimer()
        tempoDecorridoMillis = SystemClock.elapsedRealtime() - startTimeMillis
    }

    private fun continueTracking() {
        // Lógica para continuar a atividade

        isTracking = true
        startTimer()
        startTimeMillis = SystemClock.elapsedRealtime() - tempoDecorridoMillis
        resumeElapsedTime()
    }

    private fun resumeElapsedTime() {
        // Reinicia o temporizador
        timerHandler.post(object : Runnable {
            override fun run() {
                // Calcula o tempo decorrido com base no tempo armazenado
                tempoDecorridoMillis = SystemClock.elapsedRealtime() - startTimeMillis

                // Atualiza o TextView do temporizador
                requireActivity().runOnUiThread {
                    timerTextView.text = formatElapsedTime(tempoDecorridoMillis / 1000)
                }

                // Agenda a execução novamente após 1 segundo
                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun setPauseContinueButtonInitialState() {
        btnPauseContinuarAtividade.text = "Pausar Atividade"
    }

    private fun startTracking() {
        isTracking = true

        // Oculta o containerOpcoes original e exibe o containerPauseFinalizar
        containerOpcoes.visibility = View.GONE
        containerPauseFinalizar.visibility = View.VISIBLE

        // Oculta o botão startStopButton
        view?.findViewById<Button>(R.id.bntIniciarAtividade)?.visibility = View.GONE

        // Limpa a polilinha anterior
        pathPoints.clear()
        map.clear()

        // Zera o contador
        elapsedTimeSeconds = 0L
        updateTimerText()

        // Reinicia as variáveis associadas ao rastreamento
        tempoDecorridoMillis = 0L
        startTimeMillis = SystemClock.elapsedRealtime()

        // Inicia o serviço em primeiro plano
        ContextCompat.startForegroundService(requireContext(), locationServiceIntent)

        // Vincula o serviço em primeiro plano
        requireContext().bindService(locationServiceIntent, locationServiceConnection, Context.BIND_AUTO_CREATE)

        if (tempoDecorridoMillis > 0) {
            // Se a corrida está sendo retomada, chama a função para continuar o temporizador
            resumeElapsedTime()
        } else {
            // Se é uma nova corrida, inicia o temporizador do zero
            startTimeMillis = SystemClock.elapsedRealtime()
            startTimer()
        }

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
        view?.findViewById<Button>(R.id.bntIniciarAtividade)?.visibility = View.VISIBLE

        // Desvincula o serviço em primeiro plano
        requireContext().unbindService(locationServiceConnection)

        // Para o serviço em primeiro plano
        requireContext().stopService(locationServiceIntent)

        // Para o temporizador
        stopTimer()

        // Constrói e exibe o AlertDialog
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setTitle("Confirmação")
        alertDialogBuilder.setMessage("Deseja finalizar a atividade?")
        alertDialogBuilder.setPositiveButton("Finalizar") { _, _ ->
            // Código para finalizar a atividade
            Log.d("LocationUpdate", "Chamando showSummaryLayout")

            showSummaryLayout()
        }
        alertDialogBuilder.setNegativeButton("Cancelar") { dialog, _ ->
            // Código para cancelar a ação
            dialog.dismiss()
        }
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
        Log.d("LocationUpdate", "Após mostrar o AlertDialog")
    }

    private fun showSummaryLayout() {
        // Cria um novo ResultadoTreinoDialogFragment
        val dialogFragment = ResultadoTreinoDialogFragment()

        // Passe os resultados como argumentos
        val arguments = Bundle().apply {
            putString("tempo", timerTextView.text.toString())
            putString("calorias", valorCalorias)
            putString("distancia", valorDistancia)
            putString("ritmo", valorRitmo)
        }
        dialogFragment.arguments = arguments

        // Exibe o ResultadoTreinoDialogFragment
        dialogFragment.show(requireFragmentManager(), "resultado_treino_dialog")

        // Captura o Snapshot do mapa
        captureMapSnapshot(dialogFragment)
    }

    private fun captureMapSnapshot(dialogFragment: ResultadoTreinoDialogFragment) {
        val callback = GoogleMap.SnapshotReadyCallback { snapshot ->
            // Carrega o snapshot no ImageView
            val imageMapa = dialogFragment.dialog?.findViewById<ImageView>(R.id.imageMapa)
            imageMapa?.setImageBitmap(snapshot)
        }

        // Substitua YOUR_GOOGLE_MAPS_API_KEY pela sua chave de API do Google Maps
        map.snapshot(callback)
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
                requireActivity().runOnUiThread {
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
        val velocidadeMedia = calcularVelocidadeMedia() // Implementar a lógica para calcular a velocidade média
        val tempoDecorrido = elapsedTimeInSeconds

        // Calcula e atualiza as métricas
        val calorias = calculadoraAtividade.calcularCalorias(velocidadeMedia, tempoDecorrido)
        val distancia = updateDistance()
        val ritmo = calculadoraAtividade.calcularRitmo(tempoDecorrido, distancia)

        // Formata os valores para duas casas decimais
        val formattedCalorias = String.format("%.2f", calorias)
        val formattedDistancia = String.format("%.2f", distancia)
        val formattedRitmo = String.format("%.2f", ritmo)

        // Atualiza as variáveis globais
        valorCalorias = formattedCalorias
        valorDistancia = formattedDistancia
        valorRitmo = formattedRitmo

        // Atualiza os TextViews
        updateTextView(R.id.valorcalorias, formattedCalorias)
        updateTextView(R.id.valordistancia, formattedDistancia)
        updateTextView(R.id.valorritmo, formattedRitmo)
    }

    private fun updateTextView(textViewId: Int, value: String) {
        view?.findViewById<TextView>(textViewId)?.text = value
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
        // Verifica se a distância ou o ângulo mudaram o suficiente para justificar a atualização
        if (shouldUpdateLocation(location)) {
            val latLng = LatLng(location.latitude, location.longitude)
            map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
            pathPoints.add(latLng)

            // Polyline com as configurações
            map.addPolyline(PolylineOptions().addAll(pathPoints).color(Color.RED).width(15f))
            Log.d("LocationUpdate", "Latitude: ${location.latitude}, Longitude: ${location.longitude}")

            // Atualiza a distância em tempo real
            updateDistance()

            // Atualiza a localização anterior
            previousLocation = location
        }
    }

    private fun shouldUpdateLocation(location: Location): Boolean {
        if (previousLocation == null) {
            // Primeira atualização, sempre atualiza
            return true
        }

        // Calcula a distância entre as localizações
        val distance = previousLocation?.let { location.distanceTo(it) } ?: 0f

        // Calcula a diferença de ângulo entre as direções das localizações
        val angleChange = previousLocation?.let { calculateAngleChange(location, it) } ?: 0f

        // Atualiza se a distância ou o ângulo mudaram o suficiente
        return distance >= MIN_DISTANCE_CHANGE_FOR_UPDATE || angleChange >= MIN_ANGLE_CHANGE_FOR_UPDATE
    }



    private fun calculateAngleChange(newLocation: Location, oldLocation: Location): Float {
        val bearing1 = newLocation.bearingTo(oldLocation)
        val bearing2 = oldLocation.bearingTo(newLocation)
        return Math.abs(bearing1 - bearing2)
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
            requireActivity(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

}