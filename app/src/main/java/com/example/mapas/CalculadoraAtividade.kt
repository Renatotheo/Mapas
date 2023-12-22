package com.example.mapas



class CalculadoraAtividade(private val peso: Double) {

    private var distanciaTotal: Double = 0.0

    fun calcularCalorias(velocidade: Double, tempo: Long): Double {
        return (peso * velocidade * tempo) / 200
    }

    fun calcularDistancia(tempo: Long, velocidadeMedia: Double): Double {
        // Fórmula genérica para estimativa de distância
        return velocidadeMedia * tempo / 3600  // Tempo em segundos, convertendo para horas
    }

    fun calcularRitmo(tempo: Long, distancia: Double): Double {
        // Fórmula genérica para estimativa de ritmo
        return if (distancia > 0) tempo / 60 / distancia else 0.0
    }

    fun adicionarDistancia(distancia: Double) {
        distanciaTotal += distancia
    }
}
