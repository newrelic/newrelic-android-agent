package com.newrelic.agent.android.perftest.xml.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.newrelic.agent.android.perftest.xml.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NetworkTestActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvResponse: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_test)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvStatus = findViewById(R.id.tvStatus)
        tvResponse = findViewById(R.id.tvResponse)

        findViewById<MaterialButton>(R.id.btnGetRequest).setOnClickListener {
            sendGetRequest()
        }
    }

    private fun sendGetRequest() {
        lifecycleScope.launch {
            try {
                tvStatus.text = "Status: Loading..."
                tvResponse.text = ""

                val result = withContext(Dispatchers.IO) {
                    val url = URL("https://jsonplaceholder.typicode.com/posts/1")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val responseCode = connection.responseCode
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()

                    "Response Code: $responseCode\n\n$response"
                }

                tvStatus.text = "Status: Success"
                tvResponse.text = result
            } catch (e: Exception) {
                tvStatus.text = "Status: Error"
                tvResponse.text = "Error: ${e.message}"
            }
        }
    }
}
