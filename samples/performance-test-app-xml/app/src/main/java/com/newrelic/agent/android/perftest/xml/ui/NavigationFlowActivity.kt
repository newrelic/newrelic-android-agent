package com.newrelic.agent.android.perftest.xml.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.newrelic.agent.android.perftest.xml.R

class NavigationFlowActivity : AppCompatActivity() {

    private var currentStep = 1
    private val maxSteps = 5

    private lateinit var tvStep: TextView
    private lateinit var btnNext: MaterialButton
    private lateinit var btnFinish: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_flow)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvStep = findViewById(R.id.tvStep)
        btnNext = findViewById(R.id.btnNext)
        btnFinish = findViewById(R.id.btnFinish)

        btnNext.setOnClickListener {
            if (currentStep < maxSteps) {
                currentStep++
                updateUI()
            }
        }

        btnFinish.setOnClickListener {
            finish()
        }

        updateUI()
    }

    private fun updateUI() {
        tvStep.text = "Step $currentStep of $maxSteps"

        if (currentStep == maxSteps) {
            btnNext.visibility = View.GONE
            btnFinish.visibility = View.VISIBLE
        } else {
            btnNext.visibility = View.VISIBLE
            btnFinish.visibility = View.GONE
        }
    }
}
