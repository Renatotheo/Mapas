package com.example.mapas

// MainActivity.kt

import android.content.*
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = layoutInflater.inflate(R.layout.fragment_atividade, null)
        setContentView(rootView)

        if (savedInstanceState == null) {
            // Aqui você pode adicionar código adicional se necessário
        }
    }
}
