package com.newrelic.agent.android.perftest.xml.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.newrelic.agent.android.perftest.xml.R

class UIElementsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ui_elements)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Add click listeners for interaction
        findViewById<MaterialButton>(R.id.btnTest1).setOnClickListener {
            Toast.makeText(this, "Button 1 clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnTest2).setOnClickListener {
            Toast.makeText(this, "Button 2 clicked", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnTest3).setOnClickListener {
            Toast.makeText(this, "Bottom button clicked", Toast.LENGTH_SHORT).show()
        }
    }
}
