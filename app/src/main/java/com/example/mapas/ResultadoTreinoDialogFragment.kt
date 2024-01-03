package com.example.mapas


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class ResultadoTreinoDialogFragment : DialogFragment() {



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla o novo layout com as informações do treino
        val summaryLayout = inflater.inflate(R.layout.resultado_treino, container, false)

        // Recupera os resultados dos argumentos
        val tempo = arguments?.getString("tempo")
        val calorias = arguments?.getString("calorias")
        val distancia = arguments?.getString("distancia")
        val ritmo = arguments?.getString("ritmo")

        // Atualiza os TextViews com as informações do treino
        summaryLayout.findViewById<TextView>(R.id.summaryTempo).text = "Tempo: $tempo"
        summaryLayout.findViewById<TextView>(R.id.summaryCalorias).text = "Calorias: $calorias"
        summaryLayout.findViewById<TextView>(R.id.summaryDistancia).text = "Distância: $distancia"
        summaryLayout.findViewById<TextView>(R.id.summaryRitmo).text = "Ritmo: $ritmo"

        // Adiciona botão e lógica para fechar
        val btnFechar = summaryLayout.findViewById<Button>(R.id.btnFechar)
        btnFechar.setOnClickListener {
            dismiss() // Fecha o DialogFragment
        }

        // Impede o fechamento ao tocar fora do dialog
        dialog?.setCanceledOnTouchOutside(false)

        return summaryLayout
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
