package com.webwithroni.voicejarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.Query

class JarvisScreensActivity : AppCompatActivity() {

    companion object {
        const val ROUTE = "route"

        const val HISTORY = "history"
        const val SETTINGS = "settings"
        const val ONBOARDING = "onboarding"
        const val MICROPHONE = "microphone"
        const val CALL = "call"
        const val SMS = "sms"
        const val NOTIFICATION = "notification"
        const val ACCESSIBILITY = "accessibility"

        private const val REQUEST_PERMISSION = 700
    }

    private val bg = Color.parseColor("#07090D")
    private val card = Color.parseColor("#11151C")
    private val border = Color.parseColor("#202733")
    private val cyan = Color.parseColor("#5CE7FF")
    private val violet = Color.parseColor("#9B7CFF")
    private val red = Color.parseColor("#FF7181")
    private val white = Color.parseColor("#F4F7FB")
    private val secondary = Color.parseColor("#98A4B3")
    private val green = Color.parseColor("#54E38E")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val route = intent.getStringExtra(ROUTE) ?: SETTINGS

        when (route) {
            HISTORY -> showHistory()
            SETTINGS -> showSettings()
            ONBOARDING -> showOnboarding()
            MICROPHONE -> showPermission(
                title = "MICROPHONE",
                description = "Allows Jarvis to hear your voice in real time.",
                permission = Manifest.permission.RECORD_AUDIO
            )
            CALL -> showPermission(
                title = "CALL ACCESS",
                description = "Allows Jarvis to place calls when you explicitly ask.",
                permission = Manifest.permission.CALL_PHONE
            )
            SMS -> showPermission(
                title = "SMS ACCESS",
                description = "Allows Jarvis to prepare and send SMS when permitted.",
                permission = Manifest.permission.SEND_SMS
            )
            NOTIFICATION -> showNotificationPermission()
            ACCESSIBILITY -> showAccessibilityPermission()
            else -> showSettings()
        }
    }

    private fun root(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(28, 36, 28, 28)
        }
    }

    private fun title(text: String, subtitle: String? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(
                TextView(this@JarvisScreensActivity).apply {
                    this.text = text
                    textSize = 25f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(white)
                    letterSpacing = 0.04f
                },
                lpWrap()
            )

            if (subtitle != null) {
                addView(
                    TextView(this@JarvisScreensActivity).apply {
                        this.text = subtitle
                        textSize = 14f
                        setTextColor(secondary)
                        setPadding(0, 8, 0, 0)
                    },
                    lpWrap()
                )
            }
        }
    }

    private fun backButton(container: LinearLayout) {
        container.addView(
            TextView(this).apply {
                text = "‹  BACK"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(cyan)
                setPadding(0, 0, 0, 24)
                setOnClickListener { finish() }
            },
            lpWrap()
        )
    }

    private fun card(
        title: String,
        subtitle: String? = null,
        accent: Int = cyan,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            background = rounded(card, border)

            val titleView = TextView(this@JarvisScreensActivity).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            }

            addView(titleView, lpWrap())

            subtitle?.let {
                addView(
                    TextView(this@JarvisScreensActivity).apply {
                        text = it
                        textSize = 13f
                        setTextColor(secondary)
                        setPadding(0, 7, 0, 0)
                    },
                    lpWrap()
                )
            }

            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
                setBackgroundColor(card)
            }

            if (accent != cyan) {
                titleView.setTextColor(accent)
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        }
    }

    private fun primaryButton(
        text: String,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bg)
            background = rounded(cyan, cyan)
            isAllCaps = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 14
            }
        }
    }

    private fun secondaryButton(
        text: String,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            background = rounded(card, border)
            isAllCaps = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
            }
        }
    }

    private fun setRoot(view: View) {
        setContentView(view)
    }

    private fun showHistory() {
        val root = root()
        backButton(root)
        root.addView(title("CONVERSATION HISTORY", "Your recent Jarvis sessions."), lpWrap())

        val search = EditText(this).apply {
            hint = "Search conversations"
            setHintTextColor(secondary)
            setTextColor(white)
            background = rounded(card, border)
            setSingleLine(true)
            setPadding(18, 0, 18, 0)
        }

        root.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                54.dp()
            ).apply {
                topMargin = 20
                bottomMargin = 16
            }
        )

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            ScrollView(this).apply {
                addView(list)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f
            }
        )

        setRoot(root)

        FirebaseManager.initialize(this) {
            val uid = FirebaseManager.getUserId()

            if (uid == null) {
                runOnUiThread {
                    list.addView(
                        card(
                            "FIREBASE UNAVAILABLE",
                            "History will appear when Firebase authentication is ready.",
                            red
                        )
                    )
                }
                return@initialize
            }

            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("conversations")
                .orderBy("startedAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener { snapshot ->
                    runOnUiThread {
                        list.removeAllViews()

                        if (snapshot.isEmpty) {
                            list.addView(
                                card(
                                    "NO CONVERSATIONS YET",
                                    "Start talking to Jarvis and your sessions will appear here.",
                                    violet
                                )
                            )
                            return@runOnUiThread
                        }

                        snapshot.documents.forEach { doc ->
                            val id = doc.id
                            val source = doc.getString("source") ?: "voice"

                            list.addView(
                                card(
                                    "VOICE SESSION",
                                    "$source  •  $id",
                                    cyan
                                )
                            )
                        }
                    }
                }
                .addOnFailureListener { error ->
                    runOnUiThread {
                        list.addView(
                            card(
                                "HISTORY ERROR",
                                error.message ?: "Unable to load history.",
                                red
                            )
                        )
                    }
                }
        }
    }

    private fun showSettings() {
        val root = root()
        backButton(root)
        root.addView(title("SETTINGS", "Control Jarvis behaviour, privacy and permissions."), lpWrap())

        val telemetry = Switch(this).apply {
            text = "Improve Jarvis"
            textSize = 16f
            setTextColor(white)
            isChecked = FirebaseManager.isTelemetryEnabled()
            setOnCheckedChangeListener { _, checked ->
                FirebaseManager.setTelemetryEnabled(checked)
            }
        }

        root.addView(
            card(
                "LEARNING TELEMETRY",
                "Stores cleaned transcripts, latency and quality signals. Raw microphone audio is not uploaded.",
                cyan
            ).apply {
                addView(telemetry, lpWrap().apply {
                    topMargin = 12
                })
            },
            lpWrap()
        )

        root.addView(
            card(
                "VOICE",
                "Gemini Live • Aoede • Real-time voice"
            ),
            lpWrap()
        )

        root.addView(
            card(
                "MICROPHONE",
                "Manage microphone permission",
                cyan
            ) {
                openRoute(MICROPHONE)
            },
            lpWrap()
        )

        root.addView(
            card(
                "CALL & SMS",
                "Manage communication permissions",
                violet
            ) {
                openRoute(CALL)
            },
            lpWrap()
        )

        root.addView(
            card(
                "NOTIFICATIONS",
                "Allow Jarvis notifications",
                green
            ) {
                openRoute(NOTIFICATION)
            },
            lpWrap()
        )

        root.addView(
            card(
                "ACCESSIBILITY",
                "Enable screen automation and UI control",
                violet
            ) {
                openRoute(ACCESSIBILITY)
            },
            lpWrap()
        )

        root.addView(
            card(
                "ABOUT",
                "VOICE JARVIS V1 • Web With Roni"
            ),
            lpWrap()
        )

        setRoot(
            ScrollView(this).apply {
                addView(root)
            }
        )
    }

    private fun showOnboarding() {
        val root = root()
        root.gravity = Gravity.CENTER_HORIZONTAL

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        content.addView(
            TextView(this).apply {
                text = "VOICE JARVIS"
                textSize = 30f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(white)
            },
            lpWrap()
        )

        content.addView(
            TextView(this).apply {
                text = "YOUR REAL-TIME ANDROID ASSISTANT"
                textSize = 12f
                setTextColor(cyan)
                letterSpacing = 0.12f
                setPadding(0, 10, 0, 30)
            },
            lpWrap()
        )

        content.addView(
            card(
                "1  MICROPHONE",
                "Speak naturally with Jarvis.",
                cyan
            ) {
                openRoute(MICROPHONE)
            },
            lpWrap()
        )

        content.addView(
            card(
                "2  NOTIFICATIONS",
                "Let Jarvis surface important events.",
                green
            ) {
                openRoute(NOTIFICATION)
            },
            lpWrap()
        )

        content.addView(
            card(
                "3  ACCESSIBILITY",
                "Enable advanced screen control.",
                violet
            ) {
                openRoute(ACCESSIBILITY)
            },
            lpWrap()
        )

        content.addView(
            primaryButton("ENTER JARVIS", ::finish),
            lpWrap()
        )

        root.addView(
            Space(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        root.addView(content, lpWrap())

        root.addView(
            Space(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        setRoot(root)
    }

    private fun showPermission(
        title: String,
        description: String,
        permission: String
    ) {
        val root = root()
        backButton(root)
        root.addView(title(title, "Permission center"), lpWrap())

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED

        root.addView(
            card(
                if (granted) "ENABLED" else "REQUIRED",
                description,
                if (granted) green else red
            ),
            lpWrap()
        )

        if (granted) {
            root.addView(
                primaryButton("ENABLED", ::finish),
                lpWrap()
            )
        } else {
            root.addView(
                primaryButton("ENABLE", ::requestPermission),
                lpWrap()
            )
        }

        root.addView(
            secondaryButton(
                "OPEN APP SETTINGS"
            ) {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            },
            lpWrap()
        )

        setRoot(root)
    }

    private fun requestPermission() {
        val route = intent.getStringExtra(ROUTE)

        val permission = when (route) {
            MICROPHONE -> Manifest.permission.RECORD_AUDIO
            CALL -> Manifest.permission.CALL_PHONE
            SMS -> Manifest.permission.SEND_SMS
            else -> null
        } ?: return

        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            REQUEST_PERMISSION
        )
    }

    private fun showNotificationPermission() {
        val root = root()
        backButton(root)
        root.addView(title("NOTIFICATIONS", "Jarvis notifications"), lpWrap())

        val granted =
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        root.addView(
            card(
                if (granted) "ENABLED" else "REQUIRED",
                "Notifications are used for reminders, missions and important Jarvis events.",
                if (granted) green else red
            ),
            lpWrap()
        )

        if (Build.VERSION.SDK_INT >= 33 && !granted) {
            root.addView(
                primaryButton("ENABLE NOTIFICATIONS") {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_PERMISSION
                    )
                },
                lpWrap()
            )
        }

        root.addView(
            secondaryButton("OPEN NOTIFICATION SETTINGS") {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            packageName
                        )
                    }
                )
            },
            lpWrap()
        )

        setRoot(root)
    }

    private fun showAccessibilityPermission() {
        val root = root()
        backButton(root)
        root.addView(title("ACCESSIBILITY", "Screen automation"), lpWrap())

        root.addView(
            card(
                "ACCESSIBILITY SERVICE",
                "Required for reading screens, tapping elements, typing and navigating apps.",
                violet
            ),
            lpWrap()
        )

        root.addView(
            primaryButton("OPEN ACCESSIBILITY SETTINGS") {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            },
            lpWrap()
        )

        root.addView(
            secondaryButton("DONE", ::finish),
            lpWrap()
        )

        setRoot(root)
    }

    private fun openRoute(route: String) {
        startActivity(
            Intent(this, JarvisScreensActivity::class.java).apply {
                putExtra(ROUTE, route)
            }
        )
    }

    private fun rounded(fill: Int, stroke: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            setStroke(1, stroke)
            cornerRadius = 22f
        }
    }

    private fun lpWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == REQUEST_PERMISSION) {
            recreate()
        }
    }
}
