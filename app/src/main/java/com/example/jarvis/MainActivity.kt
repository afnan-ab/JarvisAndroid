package com.example.jarvis

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.min

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var orbView: JarvisOrbView
    private lateinit var tts: TextToSpeech
    private lateinit var recognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()

        tts = TextToSpeech(this, this)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(listener)

        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(
        fill: Int,
        stroke: Int = Color.TRANSPARENT,
        strokeWidth: Int = 0,
        radius: Float = 22f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius.toInt()).toFloat()
            setColor(fill)
            if (stroke != Color.TRANSPARENT && strokeWidth > 0) {
                setStroke(dp(strokeWidth), stroke)
            }
        }
    }

    private fun makeButton(
        title: String,
        icon: String,
        onClick: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(
                Color.rgb(12, 25, 45),
                Color.rgb(38, 92, 145),
                1,
                20f
            )
            elevation = dp(7).toFloat()
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val iconView = TextView(this).apply {
            text = icon
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0, 229, 255))
        }

        val label = TextView(this).apply {
            text = title
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(225, 242, 255))
            setPadding(0, dp(5), 0, 0)
        }

        card.addView(iconView, LinearLayout.LayoutParams(-1, dp(38)))
        card.addView(label, LinearLayout.LayoutParams(-1, dp(28)))
        return card
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(3, 8, 18),
                    Color.rgb(5, 17, 33),
                    Color.rgb(2, 6, 14)
                )
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(this).apply {
            text = "J A R V I S"
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.rgb(190, 240, 255))
            letterSpacing = 0.12f
        }

        val subtitle = TextView(this).apply {
            text = "  AI ASSISTANT"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(0, 190, 255))
        }

        header.addView(brand, LinearLayout.LayoutParams(0, dp(45), 1f))
        header.addView(subtitle, LinearLayout.LayoutParams(-2, dp(45)))

        status = TextView(this).apply {
            text = "●  JARVIS ONLINE"
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(0, 229, 255))
        }

        orbView = JarvisOrbView(this)

        transcript = TextView(this).apply {
            text = "READY — SAY OR TYPE A COMMAND"
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.rgb(210, 235, 250))
            background = roundedBackground(
                Color.rgb(8, 19, 35),
                Color.rgb(25, 77, 120),
                1,
                20f
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val input = EditText(this).apply {
            hint = "Ask JARVIS..."
            textSize = 15f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(110, 145, 170))
            setPadding(dp(16), 0, dp(12), 0)
            background = roundedBackground(
                Color.rgb(9, 22, 40),
                Color.rgb(35, 100, 150),
                1,
                24f
            )
        }

        val send = TextView(this).apply {
            text = "➤"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(
                Color.rgb(0, 105, 180),
                Color.rgb(0, 229, 255),
                1,
                24f
            )
            elevation = dp(6).toFloat()
            setOnClickListener {
                val command = input.text.toString().trim()
                if (command.isNotEmpty()) {
                    transcript.text = "YOU  ›  $command"
                    handleCommand(command)
                    input.text.clear()
                }
            }
        }

        input.setOnEditorActionListener { _, _, _ ->
            send.performClick()
            true
        }

        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                marginEnd = dp(8)
            }
        )
        inputRow.addView(send, LinearLayout.LayoutParams(dp(58), dp(54)))

        val grid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 2
            useDefaultMargins = false
        }

        val buttons = listOf(
            makeButton("FLASH", "⚡") { toggleFlash() },
            makeButton("VOL +", "🔊") { volume(true) },
            makeButton("VOL −", "🔉") { volume(false) },
            makeButton("YOUTUBE", "▶") {
                openPackage("com.google.android.youtube", "YouTube")
            },
            makeButton("CHROME", "🌐") {
                openPackage("com.android.chrome", "Chrome")
            },
            makeButton("SETTINGS", "⚙") {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                say("Opening settings.")
            }
        )

        buttons.forEach { view ->
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(82)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(view, lp)
        }

        val listen = TextView(this).apply {
            text = "◉   LISTEN"
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBackground(
                Color.rgb(0, 80, 135),
                Color.rgb(0, 229, 255),
                1,
                26f
            )
            elevation = dp(9).toFloat()
            setOnClickListener { startListening() }
        }

        val access = TextView(this).apply {
            text = "PHONE CONTROL  •  ACCESSIBILITY"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(130, 175, 205))
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                say("Please enable JARVIS in Accessibility settings.")
            }
        }

        root.addView(header)
        root.addView(status, LinearLayout.LayoutParams(-1, dp(24)))
        root.addView(
            orbView,
            LinearLayout.LayoutParams(-1, dp(210))
        )
        root.addView(
            transcript,
            LinearLayout.LayoutParams(-1, dp(68)).apply {
                topMargin = dp(3)
                bottomMargin = dp(8)
            }
        )
        root.addView(
            inputRow,
            LinearLayout.LayoutParams(-1, dp(54)).apply {
                bottomMargin = dp(7)
            }
        )
        root.addView(
            grid,
            LinearLayout.LayoutParams(-1, dp(176)).apply {
                bottomMargin = dp(7)
            }
        )
        root.addView(
            listen,
            LinearLayout.LayoutParams(-1, dp(52)).apply {
                bottomMargin = dp(5)
            }
        )
        root.addView(access, LinearLayout.LayoutParams(-1, dp(28)))

        setContentView(root)
    }

    private fun startListening() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }

        status.text = "●  LISTENING..."
        orbView.listening = true

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
        override fun onBeginningOfSpeech() {
            status.text = "●  HEARING YOU..."
        }
        override fun onRmsChanged(rmsdB: Float) {
            orbView.level = rmsdB
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            orbView.listening = false
        }
        override fun onError(error: Int) {
            orbView.listening = false
            status.text = "●  READY"
        }
        override fun onResults(results: Bundle?) {
            orbView.listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?: return

            transcript.text = "YOU  ›  $text"
            handleCommand(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private var pendingAction: (() -> Unit)? = null

    private fun handleCommand(command: String) {
        val c = command.lowercase(Locale.getDefault()).trim()

        when {
            pendingAction != null &&
            (c == "yes" || c == "yeah" || c == "yep" ||
             c == "haan" || c == "ha" || c == "confirm" ||
             c == "do it" || c == "okay" || c == "ok") -> {
                val action = pendingAction
                pendingAction = null
                say("Confirmed.")
                action?.invoke()
            }

            pendingAction != null &&
            (c == "no" || c == "nope" || c == "nahi" ||
             c == "cancel" || c == "stop") -> {
                pendingAction = null
                say("Cancelled.")
            }

            c.contains("whatsapp") &&
            (c.contains("message") ||
             c.contains("send") ||
             c.contains("msg")) -> {
                prepareWhatsAppMessage(c)
            }

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
                say(
                    "I can control supported phone functions, open apps, " +
                    "control volume and flashlight, and answer questions using AI."
                )
            }

            c == "back" ||
            c == "go back" ||
            c == "peeche jao" ||
            c == "wapas jao" -> {
                val service = JarvisAccessibilityService.instance
                if (service?.goBack() == true) {
                    say("Going back.")
                } else {
                    say("Accessibility service is not enabled.")
                }
            }

            c == "home" ||
            c == "go home" ||
            c == "home jao" -> {
                val service = JarvisAccessibilityService.instance
                if (service?.goHome() == true) {
                    say("Going home.")
                } else {
                    say("Accessibility service is not enabled.")
                }
            }

            c.contains("scroll down") ||
            c.contains("neeche scroll") ||
            c.contains("scroll neeche") -> {
                val service = JarvisAccessibilityService.instance
                if (service?.scrollForward() == true) {
                    say("Scrolling down.")
                } else {
                    say("I could not scroll this screen.")
                }
            }

            c.contains("scroll up") ||
            c.contains("upar scroll") ||
            c.contains("scroll upar") -> {
                val service = JarvisAccessibilityService.instance
                if (service?.scrollBackward() == true) {
                    say("Scrolling up.")
                } else {
                    say("I could not scroll this screen.")
                }
            }

            c.startsWith("click ") ||
            c.startsWith("tap ") ||
            c.startsWith("press ") -> {
                val target = c
                    .removePrefix("click ")
                    .removePrefix("tap ")
                    .removePrefix("press ")
                    .trim()

                if (target.isBlank()) {
                    say("Tell me what to click.")
                } else {
                    val service = JarvisAccessibilityService.instance

                    if (service?.clickText(target) == true) {
                        say("Done.")
                    } else {
                        say("I could not find that button or text.")
                    }
                }
            }

            c.startsWith("type ") ||
            c.startsWith("enter ") ||
            c.startsWith("write ") ||
            c.startsWith("search ") -> {

                val text = c
                    .removePrefix("type ")
                    .removePrefix("enter ")
                    .removePrefix("write ")
                    .removePrefix("search ")
                    .trim()

                if (text.isBlank()) {
                    say("Tell me what to type.")
                } else {
                    val service = JarvisAccessibilityService.instance

                    if (service?.setText(text) == true) {
                        say("Text entered.")
                    } else {
                        say("I could not find an editable text field.")
                    }
                }
            }

            openInstalledApp(c) -> Unit

            else -> askGroq(command)
        }
    }

    private fun openInstalledApp(command: String): Boolean {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(launchIntent, 0)

        val cleaned = command
            .lowercase(Locale.getDefault())
            .replace(Regex("\\b(open|launch|start|khol|kholo|kholna|karo|do)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) return false

        val matches = apps.mapNotNull { info ->
            val label = info.loadLabel(packageManager).toString()
            val normalized = label.lowercase(Locale.getDefault()).trim()

            if (info.activityInfo.packageName == packageName) {
                null
            } else {
                Triple(label, normalized, info.activityInfo.packageName)
            }
        }

        val exact = matches.firstOrNull { it.second == cleaned }
        val partial = matches.firstOrNull {
            it.second.startsWith(cleaned) || cleaned.contains(it.second)
        }

        val match = exact ?: partial ?: return false

        val intent = packageManager.getLaunchIntentForPackage(match.third)

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            say("Opening ${match.first}.")
            return true
        }

        return false
    }

    private fun prepareWhatsAppMessage(command: String) {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(android.Manifest.permission.READ_CONTACTS),
                1001
            )

            say("Please allow Contacts permission, then repeat the WhatsApp command.")
            return
        }

        val contact = findContactFromCommand(command)

        if (contact == null) {
            say("I could not find that contact in your phone.")
            return
        }

        val message = extractMessageAfterContact(command, contact.first)

        if (message.isBlank()) {
            say("What message should I prepare for ${contact.first}?")
            return
        }

        val name = contact.first
        val number = contact.second

        pendingAction = {
            openWhatsAppChat(number, message)
        }

        say(
            "I found $name. I will open the WhatsApp chat with \"$message\". " +
            "Confirm?"
        )
    }

    private fun findContactFromCommand(command: String): Pair<String, String>? {
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        ) ?: return null

        var best: Pair<String, String>? = null

        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue

                if (command.contains(name.lowercase(Locale.getDefault()))) {
                    if (best == null || name.length > best!!.first.length) {
                        best = Pair(name, number)
                    }
                }
            }
        }

        return best
    }

    private fun extractMessageAfterContact(
        command: String,
        contactName: String
    ): String {
        var text = command

        listOf(
            "open whatsapp",
            "whatsapp",
            "message",
            "msg",
            "send"
        ).forEach {
            text = text.replace(it, " ", ignoreCase = true)
        }

        text = text.replace(
            contactName,
            " ",
            ignoreCase = true
        )

        return text
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun openWhatsAppChat(number: String, message: String) {
        try {
            val digits = number.filter { it.isDigit() }

            if (digits.isBlank()) {
                say("I could not read that contact's phone number.")
                return
            }

            val encodedMessage =
                java.net.URLEncoder.encode(message, "UTF-8")

            val uri = android.net.Uri.parse(
                "https://wa.me/$digits?text=$encodedMessage"
            )

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                say("WhatsApp chat opened. Please press Send.")
            } else {
                say("WhatsApp is not installed.")
            }

        } catch (e: Exception) {
            say("I could not open that WhatsApp chat.")
        }
    }

    private fun askGroq(question: String) {
        status.text = "●  THINKING..."
        orbView.thinking = true

        Thread {
            var connection: HttpURLConnection? = null

            try {
                val apiKey = BuildConfig.GROQ_API_KEY

                if (apiKey.isBlank()) {
                    runOnUiThread {
                        orbView.thinking = false
                        say("Groq API key is not configured.")
                    }
                    return@Thread
                }

                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.doOutput = true

                val body = JSONObject().apply {
                    put("model", "openai/gpt-oss-20b")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                "You are JARVIS, a helpful Android voice assistant. " +
                                "Answer clearly and briefly. Do not claim to perform phone " +
                                "actions unless the Android app actually performed them."
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

                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (responseCode !in 200..299) {
                    throw Exception("Groq HTTP $responseCode: $response")
                }

                val json = JSONObject(response)
                val answer = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                runOnUiThread {
                    orbView.thinking = false
                    say(answer)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    orbView.thinking = false
                    transcript.text = "JARVIS  ›  Groq connection failed"
                    status.text = "●  ERROR"
                    tts.speak(
                        "I could not connect to Groq right now.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "jarvis"
                    )
                }
            } finally {
                connection?.disconnect()
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
        if (Build.VERSION.SDK_INT < 23) {
            say("Flashlight control is not supported on this Android version.")
            return
        }

        try {
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
        } catch (_: Exception) {
            say("I could not control the flashlight.")
        }
    }

    private fun say(text: String) {
        orbView.thinking = false
        transcript.text = "JARVIS  ›  $text"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        status.text = "●  READY"
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(0.95f)
        }
    }

    override fun onDestroy() {
        if (::recognizer.isInitialized) recognizer.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }

    class JarvisOrbView(context: android.content.Context) : View(context) {

        var listening = false
            set(value) {
                field = value
                invalidate()
            }

        var thinking = false
            set(value) {
                field = value
                invalidate()
            }

        var level = 0f
            set(value) {
                field = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var rotation = 0f
        private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 8000L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                rotation = it.animatedValue as Float
                invalidate()
            }
        }

        init {
            animator.start()
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.28f

            // Outer neon halo.
            paint.shader = RadialGradient(
                cx, cy, radius * 1.55f,
                intArrayOf(
                    Color.argb(120, 0, 229, 255),
                    Color.argb(35, 0, 130, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius * 1.55f, paint)

            // Rotating rings.
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.argb(180, 0, 220, 255)

            canvas.save()
            canvas.rotate(rotation, cx, cy)
            canvas.drawOval(
                cx - radius * 1.18f,
                cy - radius * 0.40f,
                cx + radius * 1.18f,
                cy + radius * 0.40f,
                paint
            )
            canvas.drawOval(
                cx - radius * 0.40f,
                cy - radius * 1.18f,
                cx + radius * 0.40f,
                cy + radius * 1.18f,
                paint
            )
            canvas.restore()

            // Main glass/metal orb.
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                cx - radius * 0.30f,
                cy - radius * 0.35f,
                radius * 1.25f,
                intArrayOf(
                    Color.rgb(90, 225, 255),
                    Color.rgb(15, 86, 145),
                    Color.rgb(4, 18, 38),
                    Color.rgb(1, 7, 17)
                ),
                floatArrayOf(0f, 0.32f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, paint)

            // Highlight.
            paint.shader = null
            paint.color = Color.argb(130, 220, 250, 255)
            canvas.drawOval(
                cx - radius * 0.48f,
                cy - radius * 0.62f,
                cx - radius * 0.05f,
                cy - radius * 0.34f,
                paint
            )

            // JARVIS eye.
            paint.color = if (listening) Color.WHITE else Color.rgb(0, 229, 255)
            paint.setShadowLayer(
                dp(if (thinking) 18 else 12).toFloat(),
                0f,
                0f,
                Color.rgb(0, 220, 255)
            )

            val eyeW = radius * 0.22f
            val eyeH = radius * 0.10f

            canvas.save()
            canvas.rotate(-10f, cx - radius * 0.32f, cy)
            canvas.drawOval(
                cx - radius * 0.57f,
                cy - eyeH,
                cx - radius * 0.12f,
                cy + eyeH,
                paint
            )
            canvas.restore()

            canvas.save()
            canvas.rotate(10f, cx + radius * 0.32f, cy)
            canvas.drawOval(
                cx + radius * 0.12f,
                cy - eyeH,
                cx + radius * 0.57f,
                cy + eyeH,
                paint
            )
            canvas.restore()

            paint.clearShadowLayer()

            // Voice level particles.
            paint.color = Color.argb(190, 0, 229, 255)
            val bars = 9
            for (i in 0 until bars) {
                val x = cx - dp(36) + i * dp(9)
                val h = dp(4) + dp(28) *
                    (if (listening) (0.25f + ((level + i * 2f) % 12f) / 12f) else 0.25f)
                canvas.drawRoundRect(
                    x,
                    cy + radius * 0.72f - h / 2f,
                    x + dp(5),
                    cy + radius * 0.72f + h / 2f,
                    dp(3).toFloat(),
                    dp(3).toFloat(),
                    paint
                )
            }

            paint.style = Paint.Style.FILL
        }

        private fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()
    }
}
