package com.newrelic.agent.android.perftest.xml

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.newrelic.agent.android.perftest.xml.metrics.PerformanceMetricsCollector
import com.newrelic.agent.android.perftest.xml.ui.*

class MainActivity : AppCompatActivity() {

    private lateinit var metricsCollector: PerformanceMetricsCollector
    private lateinit var metricsTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val updateMetricsRunnable = object : Runnable {
        override fun run() {
            metricsTextView.text = metricsCollector.getMetricsJson()
            handler.postDelayed(this, 1000) // Update every second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        metricsTextView = findViewById(R.id.metricsTextView)

        // Initialize metrics collector
        metricsCollector = PerformanceMetricsCollector.getInstance(this)

        // Set up button click listeners
        findViewById<MaterialButton>(R.id.btnInfiniteScroll).setOnClickListener {
            startActivity(Intent(this, InfiniteScrollActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnImageGallery).setOnClickListener {
            startActivity(Intent(this, ImageGalleryActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnUIElements).setOnClickListener {
            startActivity(Intent(this, UIElementsActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnNetworkTest).setOnClickListener {
            startActivity(Intent(this, NetworkTestActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnNavigationFlow).setOnClickListener {
            startActivity(Intent(this, NavigationFlowActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        metricsCollector.startCollecting()
        handler.post(updateMetricsRunnable)
    }

    override fun onPause() {
        super.onPause()
        metricsCollector.stopCollecting()
        handler.removeCallbacks(updateMetricsRunnable)
    }
}
