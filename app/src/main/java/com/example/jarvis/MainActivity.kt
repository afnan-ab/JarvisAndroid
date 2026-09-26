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
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
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
            text = "Tap LISTEN and speak a command"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFFE8F7FF.toInt())
            setPadding(10, 30, 10, 30)
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

        bg.addView(orb, LinearLayout.LayoutParams(-1, 220))
        bg.addView(status, LinearLayout.LayoutParams(-1, 70))
        bg.addView(transcript, LinearLayout.LayoutParams(-1, 130))
        bg.addView(listen, LinearLayout.LayoutParams(-1, 60))
        bg.addView(access, LinearLayout.LayoutParams(-1, 60))
        setContentView(bg)
    }

    private fun startListening() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }
        listening = true
        status.text = "LISTENING..."
        orb.text = "◉"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
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
        override fun onEndOfSpeech() { listening = false }
        override fun onError(error: Int) {
            listening = false
            status.text = "READY"
        }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            transcript.text = "You: $text"
            handleCommand(text.lowercase(Locale.getDefault()))
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleCommand(command: String) {
        when {
            command.contains("flashlight") || command.contains("torch") -> toggleFlash()
            command.contains("volume up") || command.contains("increase volume") -> volume(true)
            command.contains("volume down") || command.contains("decrease volume") -> volume(false)
            command.contains("open youtube") -> openPackage("com.google.android.youtube", "YouTube")
            command.contains("open chrome") -> openPackage("com.android.chrome", "Chrome")
            command.contains("open settings") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS)); say("Opening settings.")
            }
            command.contains("go home") -> {
                val s = getSystemService(AccessibilityService::class.java)
                say("Use the enabled accessibility service for this command.")
            }
            command.contains("what can you do") -> say("I can open apps, control volume and flashlight, open settings, and use accessibility actions when enabled.")
            else -> say("I heard you. I don't have a safe built-in action for that command yet.")
        }
    }

    private fun openPackage(pkg: String, name: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            startActivity(intent)
            say("Opening $name.")
        } else say("$name is not installed.")
    }

    private fun volume(up: Boolean) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.adjustVolume(if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        say(if (up) "Volume increased." else "Volume decreased.")
    }

    private fun toggleFlash() {
        if (Build.VERSION.SDK_INT >= 23) {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id != null) {
                val prefs = getPreferences(MODE_PRIVATE)
                val on = !prefs.getBoolean("flash", false)
                cm.setTorchMode(id, on)
                prefs.edit().putBoolean("flash", on).apply()
                say(if (on) "Flashlight on." else "Flashlight off.")
            }
        }
    }

    private fun say(text: String) {
        transcript.text = "JARVIS: $text"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        status.text = "READY"
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts.language = Locale.US
    }

    override fun onDestroy() {
        recognizer.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
