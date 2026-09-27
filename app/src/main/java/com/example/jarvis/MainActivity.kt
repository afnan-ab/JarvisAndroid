package com.example.jarvis

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var orb: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var recognizer: SpeechRecognizer
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        tts = TextToSpeech(this, this)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(listener)

        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun buildUi() {
        val bg = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 50, 28, 28)
            setBackgroundColor(0xFF050814.toInt())
        }

        orb = TextView(this).apply {
            text = "◉"
            textSize = 96f
            gravity = Gravity.CENTER
            setTextColor(0xFF00E5FF.toInt())
        }

        status = TextView(this).apply {
            text = "JARVIS ONLINE"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFF00E5FF.toInt())
        }

        transcript = TextView(this).apply {
            text = "Tap LISTEN or type a command"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFFE8F7FF.toInt())
            setPadding(10, 30, 10, 30)
        }

        val input = EditText(this).apply {
            hint = "Ask JARVIS..."
            textSize = 17f
            setSingleLine(true)
            setTextColor(0xFFE8F7FF.toInt())
            setHintTextColor(0xFF78909C.toInt())
            setPadding(20, 10, 20, 10)
        }

        val send = Button(this).apply {
            text = "SEND"
            setOnClickListener {
                val command = input.text.toString().trim()
                if (command.isNotEmpty()) {
                    transcript.text = "You: $command"
                    handleCommand(command)
                    input.text.clear()
                }
            }
        }

        input.setOnEditorActionListener { _, _, _ ->
            send.performClick()
            true
        }

        val listen = Button(this).apply {
            text = "LISTEN"
            setOnClickListener { startListening() }
        }

        val access = Button(this).apply {
            text = "ENABLE PHONE CONTROL"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                say("Please enable JARVIS in Accessibility settings.")
            }
        }

        bg.addView(orb, LinearLayout.LayoutParams(-1, 180))
        bg.addView(status, LinearLayout.LayoutParams(-1, 60))
        bg.addView(transcript, LinearLayout.LayoutParams(-1, 110))
        bg.addView(input, LinearLayout.LayoutParams(-1, 65))
        bg.addView(send, LinearLayout.LayoutParams(-1, 55))
        bg.addView(listen, LinearLayout.LayoutParams(-1, 55))
        bg.addView(access, LinearLayout.LayoutParams(-1, 55))

        setContentView(bg)
    }

    private fun startListening() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }

        listening = true
        status.text = "LISTENING..."
        orb.text = "◉"

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        recognizer.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            listening = false
        }

        override fun onError(error: Int) {
            listening = false
            status.text = "READY"
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?: return

            transcript.text = "You: $text"
            handleCommand(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleCommand(command: String) {
        val c = command.lowercase(Locale.getDefault()).trim()

        when {
            c.contains("flashlight") ||
            c.contains("torch") ||
            c.contains("flash on") ||
            c.contains("flash off") ||
            c.contains("फ्लैशलाइट") -> toggleFlash()

            c.contains("volume up") ||
            c.contains("increase volume") ||
            c.contains("volume badhao") ||
            c.contains("awaz badhao") -> volume(true)

            c.contains("volume down") ||
            c.contains("decrease volume") ||
            c.contains("volume kam") ||
            c.contains("awaz kam") -> volume(false)

            c.contains("open youtube") ||
            c.contains("youtube kholo") ||
            c.contains("youtube open") -> {
                openPackage("com.google.android.youtube", "YouTube")
            }

            c.contains("open chrome") ||
            c.contains("chrome kholo") ||
            c.contains("chrome open") -> {
                openPackage("com.android.chrome", "Chrome")
            }

            c.contains("open settings") ||
            c.contains("settings kholo") ||
            c.contains("settings open") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                say("Opening settings.")
            }

            c.contains("what can you do") ||
            c.contains("tum kya kar sakte ho") ||
            c.contains("what can you") -> {
                say("I can control supported phone functions, open apps, control volume and flashlight, and answer questions using AI.")
            }

            else -> {
                askGroq(command)
            }
        }
    }

    private fun askGroq(question: String) {
        status.text = "THINKING..."
        orb.text = "◉"

        Thread {
            try {
                val apiKey = BuildConfig.GROQ_API_KEY

                if (apiKey.isBlank()) {
                    runOnUiThread {
                        say("Groq API key is not configured.")
                    }
                    return@Thread
                }

                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.doOutput = true

                val body = JSONObject().apply {
                    put("model", "llama-3.3-70b-versatile")
                    put("messages", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                "You are JARVIS, a helpful Android voice assistant. " +
                                "Answer clearly and briefly. Do not claim to perform phone actions " +
                                "unless the Android app actually performed them."
                            )
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", question)
                        })
                    })
                    put("temperature", 0.7)
                }

                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode

                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream.bufferedReader().use { it.readText() }

                if (responseCode !in 200..299) {
                    throw Exception("Groq HTTP $responseCode")
                }

                val json = JSONObject(response)
                val answer = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                runOnUiThread {
                    say(answer)
                }

                connection.disconnect()

            } catch (e: Exception) {
                runOnUiThread {
                    say("I could not connect to Groq right now.")
                }
            }
        }.start()
    }

    private fun openPackage(pkg: String, name: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)

        if (intent != null) {
            startActivity(intent)
            say("Opening $name.")
        } else {
            say("$name is not installed.")
        }
    }

    private fun volume(up: Boolean) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager

        am.adjustVolume(
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )

        say(if (up) "Volume increased." else "Volume decreased.")
    }

    private fun toggleFlash() {
        if (Build.VERSION.SDK_INT >= 23) {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager

            val id = cm.cameraIdList.firstOrNull { cameraId ->
                cm.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (id != null) {
                val prefs = getPreferences(MODE_PRIVATE)
                val on = !prefs.getBoolean("flash", false)

                cm.setTorchMode(id, on)
                prefs.edit().putBoolean("flash", on).apply()

                say(if (on) "Flashlight on." else "Flashlight off.")
            } else {
                say("No flashlight found.")
            }
        }
    }

    private fun say(text: String) {
        transcript.text = "JARVIS: $text"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        status.text = "READY"
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    override fun onDestroy() {
        recognizer.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
