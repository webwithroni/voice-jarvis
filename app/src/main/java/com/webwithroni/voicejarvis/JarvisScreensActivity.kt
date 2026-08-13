package com.webwithroni.voicejarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvisScreensActivity : AppCompatActivity() {

    companion object {

        const val ROUTE = "route"

        const val HISTORY = "history"
        const val DETAIL = "detail"
        const val SETTINGS = "settings"
        const val ONBOARDING = "onboarding"
        const val MICROPHONE = "microphone"
        const val CALL = "call"
        const val SMS = "sms"
        const val NOTIFICATION = "notification"
        const val ACCESSIBILITY = "accessibility"

        const val CONVERSATION_ID = "conversation_id"

        private const val REQUEST_PERMISSION = 700
    }

    private val bg = Color.parseColor("#07090D")
    private val surface = Color.parseColor("#0D1118")
    private val elevated = Color.parseColor("#121821")
    private val border = Color.parseColor("#202733")

    private val white = Color.parseColor("#F4F7FB")
    private val secondary = Color.parseColor("#8C97A8")
    private val tertiary = Color.parseColor("#566171")

    private val cyan = Color.parseColor("#5CE7FF")
    private val blue = Color.parseColor("#4A8DFF")
    private val violet = Color.parseColor("#9B7CFF")
    private val red = Color.parseColor("#FF7181")
    private val green = Color.parseColor("#54E38E")
    private val orange = Color.parseColor("#FFB86B")

    private val dateFormat =
        SimpleDateFormat(
            "d MMM yyyy • h:mm a",
            Locale.getDefault()
        )

    private val compactTime =
        SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        )

    private data class ConversationRow(
        val id: String,
        val source: String,
        val startedAt: Date?,
        val preview: String
    )

    private val conversations =
        mutableListOf<ConversationRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (
            intent.getStringExtra(ROUTE)
        ) {

            HISTORY ->
                showHistory()

            DETAIL ->
                showConversationDetail(
                    intent.getStringExtra(
                        CONVERSATION_ID
                    )
                )

            SETTINGS ->
                showSettings()

            ONBOARDING ->
                showOnboarding()

            MICROPHONE ->
                showPermission(
                    title = "MICROPHONE ACCESS",
                    subtitle = "Voice is how Jarvis understands you.",
                    description =
                        "Microphone access lets Jarvis hear your speech in real time. Raw microphone audio is not stored by Firebase.",
                    icon = R.drawable.ic_mic,
                    color = red,
                    permission =
                        Manifest.permission.RECORD_AUDIO
                )

            CALL ->
                showPermission(
                    title = "CALL ACCESS",
                    subtitle = "Let Jarvis help with calls.",
                    description =
                        "Call permission allows Jarvis to place a phone call when you explicitly ask it to.",
                    icon = R.drawable.ic_phone,
                    color = green,
                    permission =
                        Manifest.permission.CALL_PHONE
                )

            SMS ->
                showPermission(
                    title = "MESSAGE ACCESS",
                    subtitle = "Let Jarvis help you communicate.",
                    description =
                        "SMS permission allows Jarvis to send messages only when the action is explicitly requested and permitted.",
                    icon = R.drawable.ic_message,
                    color = blue,
                    permission =
                        Manifest.permission.SEND_SMS
                )

            NOTIFICATION ->
                showNotificationPermission()

            ACCESSIBILITY ->
                showAccessibilityPermission()

            else ->
                showSettings()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density)
            .toInt()

    private fun lp(
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        top: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            width,
            height
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun surfaceBackground(
        color: Int = surface,
        radius: Float = 22f,
        stroke: Int = border
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
            setStroke(
                dp(1),
                stroke
            )
        }

    private fun root(): ScrollView {

        val content =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(18),
                    dp(20),
                    dp(28)
                )

                setBackgroundColor(
                    bg
                )
            }

        return ScrollView(this).apply {

            setBackgroundColor(bg)

            isFillViewport = true

            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            tag = content
        }
    }

    private fun content(root: ScrollView):
        LinearLayout =
        root.tag as LinearLayout

    private fun header(
        title: String,
        subtitle: String? = null,
        showBack: Boolean = true
    ): LinearLayout {

        val wrapper =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
            }

        if (showBack) {

            wrapper.addView(
                ImageButton(this).apply {

                    setImageResource(
                        R.drawable.ic_back
                    )

                    background =
                        surfaceBackground(
                            elevated,
                            24f,
                            border
                        )

                    contentDescription =
                        "Back"

                    setPadding(
                        dp(12),
                        dp(12),
                        dp(12),
                        dp(12)
                    )

                    setOnClickListener {
                        finish()
                    }
                },
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(48)
                )
            )
        }

        val heading =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        leftMargin =
                            if (showBack) dp(12) else 0
                    }

                addView(
                    TextView(this@JarvisScreensActivity).apply {
                        text = title
                        textSize = 25f
                        typeface =
                            Typeface.DEFAULT_BOLD
                        letterSpacing = 0.05f
                        setTextColor(white)
                    },
                    lp()
                )

                subtitle?.let {

                    addView(
                        TextView(this@JarvisScreensActivity).apply {
                            text = it
                            textSize = 14f
                            setTextColor(
                                secondary
                            )
                            setPadding(
                                0,
                                dp(5),
                                0,
                                0
                            )
                        },
                        lp()
                    )
                }
            }

        wrapper.addView(
            heading
        )

        val logo =
            ImageView(this).apply {
                setImageResource(
                    R.drawable.ic_jarvis_logo
                )
                contentDescription =
                    "Voice Jarvis"
            }

        wrapper.addView(
            logo,
            LinearLayout.LayoutParams(
                dp(42),
                dp(42)
            )
        )

        return wrapper
    }

    private fun sectionTitle(
        text: String,
        accent: Int = cyan
    ): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            typeface =
                Typeface.DEFAULT_BOLD
            letterSpacing = 0.10f
            setTextColor(accent)
            setPadding(
                dp(2),
                dp(8),
                0,
                dp(8)
            )
        }

    private fun miniOrb(): View =
        ImageView(this).apply {

            setImageResource(
                R.drawable.ic_jarvis_logo
            )

            background =
                surfaceBackground(
                    elevated,
                    24f,
                    border
                )

            setPadding(
                dp(10),
                dp(10),
                dp(10),
                dp(10)
            )

            contentDescription =
                "Jarvis"
        }

    private fun actionRow(
        icon: Int,
        title: String,
        subtitle: String,
        accent: Int = cyan,
        onClick: (() -> Unit)? = null
    ): LinearLayout {

        val row =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(14),
                    dp(13),
                    dp(12),
                    dp(13)
                )

                background =
                    surfaceBackground(
                        surface,
                        20f,
                        border
                    )

                minimumHeight =
                    dp(72)
            }

        val iconView =
            ImageView(this).apply {

                setImageResource(
                    icon
                )

                setPadding(
                    dp(10),
                    dp(10),
                    dp(10),
                    dp(10)
                )

                background =
                    surfaceBackground(
                        elevated,
                        18f,
                        border
                    )

                imageTintList =
                    android.content.res.ColorStateList.valueOf(
                        accent
                    )
            }

        row.addView(
            iconView,
            LinearLayout.LayoutParams(
                dp(46),
                dp(46)
            )
        )

        val copy =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        leftMargin = dp(12)
                    }

                addView(
                    TextView(this@JarvisScreensActivity).apply {
                        text = title
                        textSize = 15f
                        typeface =
                            Typeface.DEFAULT_BOLD
                        setTextColor(white)
                    },
                    lp()
                )

                addView(
                    TextView(this@JarvisScreensActivity).apply {
                        text = subtitle
                        textSize = 13f
                        setTextColor(
                            secondary
                        )
                        setPadding(
                            0,
                            dp(4),
                            0,
                            0
                        )
                    },
                    lp()
                )
            }

        row.addView(copy)

        if (onClick != null) {

            row.addView(
                ImageView(this).apply {
                    setImageResource(
                        R.drawable.ic_chevron
                    )
                    contentDescription =
                        "Open"
                },
                LinearLayout.LayoutParams(
                    dp(24),
                    dp(24)
                )
            )

            row.isClickable = true
            row.isFocusable = true

            row.setOnClickListener {
                onClick()
            }
        }

        return row
    }

    private fun primaryButton(
        text: String,
        onClick: () -> Unit,
        accent: Int = cyan
    ): Button =
        Button(this).apply {

            this.text = text

            textSize = 13f
            typeface =
                Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f

            setTextColor(
                bg
            )

            background =
                GradientDrawable().apply {
                    setColor(accent)
                    cornerRadius =
                        dp(16).toFloat()
                }

            minimumHeight =
                dp(52)

            setOnClickListener {
                onClick()
            }
        }

    private fun secondaryButton(
        text: String,
        onClick: () -> Unit
    ): Button =
        Button(this).apply {

            this.text = text

            textSize = 13f
            typeface =
                Typeface.DEFAULT_BOLD

            setTextColor(
                white
            )

            background =
                surfaceBackground(
                    elevated,
                    16f,
                    border
                )

            minimumHeight =
                dp(52)

            setOnClickListener {
                onClick()
            }
        }

    private fun statusPill(
        text: String,
        color: Int
    ): TextView =
        TextView(this).apply {

            this.text = text

            textSize = 11f
            typeface =
                Typeface.DEFAULT_BOLD

            letterSpacing = 0.08f

            setTextColor(
                color
            )

            background =
                GradientDrawable().apply {
                    setColor(
                        Color.argb(
                            28,
                            Color.red(color),
                            Color.green(color),
                            Color.blue(color)
                        )
                    )
                    cornerRadius =
                        dp(20).toFloat()
                    setStroke(
                        dp(1),
                        Color.argb(
                            65,
                            Color.red(color),
                            Color.green(color),
                            Color.blue(color)
                        )
                    )
                }

            setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
            )
        }

    private fun showHistory() {

        val page =
            root()

        val body =
            content(page)

        body.addView(
            header(
                "History",
                "Your recent Jarvis sessions."
            ),
            lp(
                top = 2,
                bottom = 14
            )
        )

        val searchContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                background =
                    surfaceBackground(
                        surface,
                        18f,
                        border
                    )

                setPadding(
                    dp(12),
                    0,
                    dp(12),
                    0
                )
            }

        searchContainer.addView(
            ImageView(this).apply {
                setImageResource(
                    R.drawable.ic_search
                )
                contentDescription =
                    "Search"
            },
            LinearLayout.LayoutParams(
                dp(26),
                dp(26)
            )
        )

        val search =
            EditText(this).apply {

                hint =
                    "Search conversations"

                textSize =
                    15f

                setTextColor(
                    white
                )

                setHintTextColor(
                    tertiary
                )

                setSingleLine(true)

                background =
                    null

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        dp(54),
                        1f
                    ).apply {
                        leftMargin = dp(8)
                    }
            }

        searchContainer.addView(
            search
        )

        body.addView(
            searchContainer,
            lp(
                bottom = 16
            )
        )

        val list =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        body.addView(
            list
        )

        search.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    renderHistory(
                        list,
                        s?.toString()
                            ?.trim()
                            ?.lowercase()
                            ?: ""
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )

        setContentView(
            page
        )

        loadConversations(
            list
        )
    }

    private fun loadConversations(
        list: LinearLayout
    ) {

        FirebaseManager.initialize(this) {

            val uid =
                FirebaseManager.getUserId()

            if (uid == null) {

                runOnUiThread {
                    list.removeAllViews()
                    list.addView(
                        emptyState(
                            R.drawable.ic_jarvis_logo,
                            "HISTORY UNAVAILABLE",
                            "Jarvis hasn't connected to Firebase yet.",
                            red
                        ),
                        lp(bottom = 10)
                    )
                }

                return@initialize
            }

            FirebaseFirestore
                .getInstance()
                .collection("users")
                .document(uid)
                .collection("conversations")
                .orderBy(
                    "startedAt",
                    Query.Direction.DESCENDING
                )
                .limit(50)
                .get()
                .addOnSuccessListener { snapshot ->

                    conversations.clear()

                    snapshot.documents
                        .forEach { doc ->

                            val timestamp =
                                doc.getTimestamp(
                                    "startedAt"
                                )?.toDate()

                            conversations.add(
                                ConversationRow(
                                    id = doc.id,
                                    source =
                                        doc.getString(
                                            "source"
                                        ) ?: "voice",
                                    startedAt =
                                        timestamp,
                                    preview =
                                        "Voice conversation with Jarvis"
                                )
                            )
                        }

                    runOnUiThread {
                        renderHistory(
                            list
                        )
                    }
                }
                .addOnFailureListener { error ->

                    runOnUiThread {

                        list.removeAllViews()

                        list.addView(
                            emptyState(
                                R.drawable.ic_history,
                                "HISTORY ERROR",
                                error.message
                                    ?: "Unable to load history.",
                                red
                            ),
                            lp(bottom = 10)
                        )
                    }
                }
        }
    }

    private fun renderHistory(
        list: LinearLayout,
        query: String = ""
    ) {

        list.removeAllViews()

        val filtered =
            if (query.isBlank()) {
                conversations
            } else {
                conversations.filter {
                    it.source
                        .lowercase()
                        .contains(query) ||
                    it.preview
                        .lowercase()
                        .contains(query)
                }
            }

        if (filtered.isEmpty()) {

            list.addView(
                emptyState(
                    R.drawable.ic_history,
                    if (query.isBlank())
                        "NO CONVERSATIONS YET"
                    else
                        "NO RESULTS",
                    if (query.isBlank())
                        "Your conversations with Jarvis will appear here."
                    else
                        "Try a different word or phrase.",
                    violet
                ),
                lp(bottom = 12)
            )

            list.addView(
                primaryButton(
                    "START A CONVERSATION",
                    {
                        finish()
                    }
                ),
                lp(
                    bottom = 10
                )
            )

            return
        }

        var lastDate =
            ""

        filtered.forEach { row ->

            val dateKey =
                row.startedAt?.let {
                    SimpleDateFormat(
                        "d MMM yyyy",
                        Locale.getDefault()
                    ).format(it)
                } ?: "Recent"

            if (dateKey != lastDate) {

                list.addView(
                    sectionTitle(
                        dateKey
                    ),
                    lp(
                        top = 4,
                        bottom = 2
                    )
                )

                lastDate = dateKey
            }

            val card =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    setPadding(
                        dp(12),
                        dp(12),
                        dp(12),
                        dp(12)
                    )

                    background =
                        surfaceBackground(
                            surface,
                            20f,
                            border
                        )

                    setOnClickListener {

                        startActivity(
                            Intent(
                                this@JarvisScreensActivity,
                                JarvisScreensActivity::class.java
                            ).apply {

                                putExtra(
                                    ROUTE,
                                    DETAIL
                                )

                                putExtra(
                                    CONVERSATION_ID,
                                    row.id
                                )
                            }
                        )
                    }
                }

            card.addView(
                miniOrb(),
                LinearLayout.LayoutParams(
                    dp(46),
                    dp(46)
                )
            )

            val copy =
                LinearLayout(this).apply {
                    orientation =
                        LinearLayout.VERTICAL

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            leftMargin =
                                dp(12)
                        }

                    addView(
                        TextView(this@JarvisScreensActivity).apply {
                            text =
                                row.source
                                    .uppercase(
                                        Locale.getDefault()
                                    )
                            textSize =
                                11f
                            typeface =
                                Typeface.DEFAULT_BOLD
                            letterSpacing =
                                0.08f
                            setTextColor(
                                cyan
                            )
                        },
                        lp()
                    )

                    addView(
                        TextView(this@JarvisScreensActivity).apply {
                            text =
                                row.preview
                            textSize =
                                14f
                            setTextColor(
                                white
                            )
                            setPadding(
                                0,
                                dp(4),
                                0,
                                0
                            )
                        },
                        lp()
                    )

                    addView(
                        TextView(this@JarvisScreensActivity).apply {
                            text =
                                row.startedAt?.let {
                                    compactTime.format(it)
                                } ?: "—"
                            textSize =
                                11f
                            setTextColor(
                                tertiary
                            )
                            setPadding(
                                0,
                                dp(4),
                                0,
                                0
                            )
                        },
                        lp()
                    )
                }

            card.addView(copy)

            card.addView(
                ImageView(this).apply {
                    setImageResource(
                        R.drawable.ic_chevron
                    )
                    contentDescription =
                        "Open conversation"
                },
                LinearLayout.LayoutParams(
                    dp(24),
                    dp(24)
                )
            )

            list.addView(
                card,
                lp(
                    bottom = 10
                )
            )
        }
    }

    private fun showConversationDetail(
        conversationId: String?
    ) {

        if (
            conversationId.isNullOrBlank()
        ) {

            finish()
            return
        }

        val page =
            root()

        val body =
            content(page)

        body.addView(
            header(
                "Conversation",
                "Memory playback"
            ),
            lp(
                bottom = 16
            )
        )

        val titleRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
            }

        titleRow.addView(
            statusPill(
                "VOICE SESSION",
                cyan
            ),
            lp(
                width = ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        titleRow.addView(
            TextView(this).apply {
                text =
                    "  Today"
                textSize =
                    12f
                setTextColor(
                    tertiary
                )
            }
        )

        body.addView(
            titleRow,
            lp(
                bottom = 16
            )
        )

        val transcript =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        body.addView(
            transcript
        )

        setContentView(
            page
        )

        FirebaseManager.initialize(this) {

            val uid =
                FirebaseManager.getUserId()
                    ?: return@initialize

            FirebaseFirestore
                .getInstance()
                .collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversationId)
                .collection("turns")
                .orderBy(
                    "createdAt",
                    Query.Direction.ASCENDING
                )
                .limit(100)
                .get()
                .addOnSuccessListener { snapshot ->

                    runOnUiThread {

                        transcript.removeAllViews()

                        if (snapshot.isEmpty) {

                            transcript.addView(
                                emptyState(
                                    R.drawable.ic_jarvis_logo,
                                    "NO TRANSCRIPT",
                                    "This session has no stored transcript.",
                                    tertiary
                                ),
                                lp()
                            )

                            return@runOnUiThread
                        }

                        snapshot.documents.forEach { doc ->

                            val user =
                                doc.getString(
                                    "userTranscript"
                                )

                            val jarvis =
                                doc.getString(
                                    "assistantTranscript"
                                )

                            user?.takeIf {
                                it.isNotBlank()
                            }?.let {

                                transcript.addView(
                                    messageCard(
                                        "YOU",
                                        it,
                                        blue
                                    ),
                                    lp(
                                        bottom = 10
                                    )
                                )
                            }

                            jarvis?.takeIf {
                                it.isNotBlank()
                            }?.let {

                                transcript.addView(
                                    assistantMessageCard(
                                        it
                                    ),
                                    lp(
                                        bottom = 10
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun messageCard(
        role: String,
        text: String,
        accent: Int
    ): LinearLayout {

        val box =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(14),
                    dp(16),
                    dp(14)
                )

                background =
                    surfaceBackground(
                        surface,
                        20f,
                        border
                    )
            }

        box.addView(
            TextView(this).apply {
                this.text =
                    role
                textSize =
                    11f
                typeface =
                    Typeface.DEFAULT_BOLD
                letterSpacing =
                    0.08f
                setTextColor(
                    accent
                )
            },
            lp()
        )

        box.addView(
            TextView(this).apply {
                this.text =
                    text
                textSize =
                    16f
                setTextColor(
                    white
                )
                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            },
            lp()
        )

        return box
    }

    private fun assistantMessageCard(
        text: String
    ): LinearLayout {

        val card =
            messageCard(
                "JARVIS",
                text,
                cyan
            )

        val replay =
            TextView(this).apply {

                this.text =
                    "  ▶  Replay response"

                textSize =
                    12f
                typeface =
                    Typeface.DEFAULT_BOLD
                setTextColor(
                    cyan
                )

                setPadding(
                    0,
                    dp(14),
                    0,
                    0
                )

                setOnClickListener {

                    Toast.makeText(
                        this@JarvisScreensActivity,
                        "Replay uses the current Jarvis voice pipeline.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        card.addView(
            replay,
            lp()
        )

        return card
    }

    private fun showSettings() {

        val page =
            root()

        val body =
            content(page)

        body.addView(
            header(
                "Settings",
                "Your Jarvis control room."
            ),
            lp(
                bottom = 14
            )
        )

        body.addView(
            sectionTitle(
                "ASSISTANT"
            ),
            lp()
        )

        body.addView(
            actionRow(
                R.drawable.ic_jarvis_logo,
                "Language",
                "English • বাংলা • हिंदी • Hinglish",
                cyan
            ),
            lp(
                bottom = 10
            )
        )

        body.addView(
            actionRow(
                R.drawable.ic_mic,
                "Voice",
                "Aoede • Gemini Live • Real-time",
                cyan
            ),
            lp(
                bottom = 10
            )
        )

        body.addView(
            actionRow(
                R.drawable.ic_mic,
                "Wake Word",
                "Hey Jarvis • Always listening",
                violet
            ),
            lp(
                bottom = 18
            )
        )

        body.addView(
            sectionTitle(
                "PERMISSIONS",
                violet
            ),
            lp()
        )

        body.addView(
            actionRow(
                R.drawable.ic_mic,
                "Microphone",
                "Voice input and conversation",
                red
            ) {
                openRoute(MICROPHONE)
            },
            lp(
                bottom = 10
            )
        )

        body.addView(
            actionRow(
                R.drawable.ic_phone,
                "Calls",
                "Call control and phone actions",
                green
            ) {
                openRoute(CALL)
            },
            lp(
                bottom = 10
            )
        )

        body.addView(
            actionRow(
                R.drawable.ic_message,
                "SMS / Messaging",
                "Messages and communication",
                blue
            ) {
                openRoute(SMS)
            },
            lp(
                bottom = 10
            )
        )

        body.addView(
            actionRow(
                R.drawable.ic_bell,
                "Notifications",
                "Read and manage notifications",
                orange
            ) {
                openRoute(NOTIFICATION)
            },
            lp(
                bottom = 10
            )
        )

        body.addView(
            actionRow(
                R.drawable.ic_accessibility,
                "Accessibility",
                "Screen automation and device control",
                violet
            ) {
                openRoute(ACCESSIBILITY)
            },
            lp(
                bottom = 18
            )
        )

        body.addView(
            sectionTitle(
                "PRIVACY",
                green
            ),
            lp()
        )

        val telemetry =
            Switch(this).apply {

                text =
                    "Improve Jarvis"

                textSize =
                    15f

                setTextColor(
                    white
                )

                isChecked =
                    FirebaseManager
                        .isTelemetryEnabled()

                setOnCheckedChangeListener { _, checked ->

                    FirebaseManager
                        .setTelemetryEnabled(
                            checked
                        )
                }
            }

        val telemetryCard =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(14),
                    dp(16),
                    dp(14)
                )

                background =
                    surfaceBackground(
                        surface,
                        20f,
                        border
                    )

                addView(
                    TextView(this@JarvisScreensActivity).apply {
                        text =
                            "LEARNING TELEMETRY"
                        textSize =
                            12f
                        typeface =
                            Typeface.DEFAULT_BOLD
                        setTextColor(
                            cyan
                        )
                    }
                )

                addView(
                    TextView(this@JarvisScreensActivity).apply {
                        text =
                            "Stores cleaned transcripts, latency and quality signals. Raw microphone audio is never uploaded here."
                        textSize =
                            13f
                        setTextColor(
                            secondary
                        )
                        setPadding(
                            0,
                            dp(6),
                            0,
                            dp(4)
                        )
                    }
                )

                addView(
                    telemetry
                )
            }

        body.addView(
            telemetryCard,
            lp(
                bottom = 18
            )
        )

        body.addView(
            sectionTitle(
                "ABOUT",
                tertiary
            ),
            lp()
        )

        body.addView(
            actionRow(
                R.drawable.ic_jarvis_logo,
                "Voice Jarvis",
                "V1 • Web With Roni • Android",
                cyan
            ),
            lp(
                bottom = 8
            )
        )

        body.addView(
            TextView(this).apply {

                text =
                    "Voice-first intelligence for your device."

                textSize =
                    12f

                setTextColor(
                    tertiary
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(16),
                    0,
                    0
                )
            },
            lp()
        )

        setContentView(
            page
        )
    }

    private fun showOnboarding() {

        val page =
            root()

        val body =
            content(page)

        body.gravity =
            Gravity.CENTER_HORIZONTAL

        val logo =
            ImageView(this).apply {

                setImageResource(
                    R.drawable.ic_jarvis_logo
                )

                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(20)
                )

                background =
                    surfaceBackground(
                        elevated,
                        42f,
                        border
                    )
            }

        body.addView(
            Space(this),
            LinearLayout.LayoutParams(
                1,
                dp(60)
            )
        )

        body.addView(
            logo,
            LinearLayout.LayoutParams(
                dp(118),
                dp(118)
            ).apply {
                gravity =
                    Gravity.CENTER_HORIZONTAL
            }
        )

        body.addView(
            TextView(this).apply {
                text =
                    "MEET JARVIS"
                textSize =
                    28f
                typeface =
                    Typeface.DEFAULT_BOLD
                letterSpacing =
                    0.04f
                setTextColor(
                    white
                )
                gravity =
                    Gravity.CENTER
            },
            lp(
                top = 28
            )
        )

        body.addView(
            TextView(this).apply {
                text =
                    "A living voice-first assistant for your Android device."
                textSize =
                    15f
                setTextColor(
                    secondary
                )
                gravity =
                    Gravity.CENTER
                setPadding(
                    dp(18),
                    dp(10),
                    dp(18),
                    0
                )
            },
            lp()
        )

        body.addView(
            sectionTitle(
                "TALK NATURALLY"
            ),
            lp(
                top = 34
            )
        )

        body.addView(
            actionRow(
                R.drawable.ic_mic,
                "Speak naturally",
                "English, Bengali, Hindi or mixed speech.",
                cyan
            ),
            lp(
                bottom = 10
            )
        )

        body.addView(
            actionRow(
                R.drawable.ic_accessibility,
                "You stay in control",
                "Jarvis explains sensitive permissions before asking.",
                violet
            ),
            lp(
                bottom = 10
            )
        )

        body.addView(
            primaryButton(
                "START JARVIS",
                {
                    openRoute(
                        MICROPHONE
                    )
                }
            ),
            lp(
                top = 20,
                bottom = 10
            )
        )

        body.addView(
            secondaryButton(
                "SKIP TO HOME"
            ) {
                finish()
            },
            lp()
        )

        setContentView(
            page
        )
    }

    private fun showPermission(
        title: String,
        subtitle: String,
        description: String,
        icon: Int,
        color: Int,
        permission: String
    ) {

        val page =
            root()

        val body =
            content(page)

        body.gravity =
            Gravity.CENTER_HORIZONTAL

        body.addView(
            header(
                title,
                subtitle
            ),
            lp(
                bottom = 26
            )
        )

        val iconView =
            ImageView(this).apply {

                setImageResource(
                    icon
                )

                imageTintList =
                    android.content.res.ColorStateList.valueOf(
                        color
                    )

                background =
                    surfaceBackground(
                        elevated,
                        48f,
                        border
                    )

                setPadding(
                    dp(24),
                    dp(24),
                    dp(24),
                    dp(24)
                )
            }

        body.addView(
            iconView,
            LinearLayout.LayoutParams(
                dp(118),
                dp(118)
            ).apply {
                gravity =
                    Gravity.CENTER_HORIZONTAL
            }
        )

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) ==
                PackageManager.PERMISSION_GRANTED

        body.addView(
            statusPill(
                if (granted)
                    "ENABLED"
                else
                    "PERMISSION NEEDED",
                if (granted)
                    green
                else
                    color
            ),
            lp(
                width =
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                top = 22,
                bottom = 10
            )
        )

        body.addView(
            TextView(this).apply {

                text =
                    description

                textSize =
                    15f

                setTextColor(
                    secondary
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(14),
                    0,
                    dp(14),
                    0
                )
            },
            lp(
                bottom = 14
            )
        )

        if (granted) {

            body.addView(
                primaryButton(
                    "ENABLED",
                    ::finish,
                    green
                ),
                lp(
                    bottom = 10
                )
            )

        } else {

            body.addView(
                primaryButton(
                    "ENABLE",
                    {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(
                                permission
                            ),
                            REQUEST_PERMISSION
                        )
                    },
                    color
                ),
                lp(
                    bottom = 10
                )
            )
        }

        body.addView(
            secondaryButton(
                "OPEN APP SETTINGS"
            ) {

                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse(
                            "package:$packageName"
                        )
                    )
                )
            },
            lp()
        )

        setContentView(
            page
        )
    }

    private fun showNotificationPermission() {

        val page =
            root()

        val body =
            content(page)

        body.gravity =
            Gravity.CENTER_HORIZONTAL

        body.addView(
            header(
                "NOTIFICATIONS",
                "Quiet awareness for Jarvis."
            ),
            lp(
                bottom = 26
            )
        )

        body.addView(
            ImageView(this).apply {

                setImageResource(
                    R.drawable.ic_bell
                )

                background =
                    surfaceBackground(
                        elevated,
                        48f,
                        border
                    )

                setPadding(
                    dp(24),
                    dp(24),
                    dp(24),
                    dp(24)
                )
            },
            LinearLayout.LayoutParams(
                dp(118),
                dp(118)
            )
        )

        val granted =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        body.addView(
            statusPill(
                if (granted)
                    "ENABLED"
                else
                    "PERMISSION NEEDED",
                if (granted)
                    green
                else
                    orange
            ),
            lp(
                width =
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                top = 22,
                bottom = 12
            )
        )

        body.addView(
            TextView(this).apply {

                text =
                    "Notifications let Jarvis surface reminders, task updates and important events without becoming noisy."

                textSize =
                    15f

                setTextColor(
                    secondary
                )

                gravity =
                    Gravity.CENTER
            },
            lp(
                bottom = 16
            )
        )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            !granted
        ) {

            body.addView(
                primaryButton(
                    "ENABLE NOTIFICATIONS",
                    {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(
                                Manifest.permission.POST_NOTIFICATIONS
                            ),
                            REQUEST_PERMISSION
                        )
                    },
                    orange
                ),
                lp(
                    bottom = 10
                )
            )
        }

        body.addView(
            secondaryButton(
                "OPEN NOTIFICATION SETTINGS"
            ) {

                startActivity(
                    Intent(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    ).apply {
                        putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            packageName
                        )
                    }
                )
            },
            lp()
        )

        setContentView(
            page
        )
    }

    private fun showAccessibilityPermission() {

        val page =
            root()

        val body =
            content(page)

        body.gravity =
            Gravity.CENTER_HORIZONTAL

        body.addView(
            header(
                "ACCESSIBILITY",
                "Jarvis's device-control bridge."
            ),
            lp(
                bottom = 26
            )
        )

        body.addView(
            ImageView(this).apply {

                setImageResource(
                    R.drawable.ic_accessibility
                )

                background =
                    surfaceBackground(
                        elevated,
                        48f,
                        border
                    )

                setPadding(
                    dp(24),
                    dp(24),
                    dp(24),
                    dp(24)
                )
            },
            LinearLayout.LayoutParams(
                dp(118),
                dp(118)
            )
        )

        val enabled =
            VoiceJarvisAccessibilityService
                .isEnabled(
                    this
                )

        body.addView(
            statusPill(
                if (enabled)
                    "ACCESS ENABLED"
                else
                    "SETUP REQUIRED",
                if (enabled)
                    green
                else
                    violet
            ),
            lp(
                width =
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                top = 22,
                bottom = 12
            )
        )

        body.addView(
            TextView(this).apply {

                text =
                    "Accessibility access allows Jarvis to inspect visible UI, tap elements, type text and perform gestures across supported Android apps."

                textSize =
                    15f

                setTextColor(
                    secondary
                )

                gravity =
                    Gravity.CENTER
            },
            lp(
                bottom = 16
            )
        )

        body.addView(
            primaryButton(
                if (enabled)
                    "OPEN ACCESSIBILITY SETTINGS"
                else
                    "OPEN ACCESSIBILITY SETTINGS",
                {
                    startActivity(
                        Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS
                        )
                    )
                },
                violet
            ),
            lp(
                bottom = 10
            )
        )

        body.addView(
            secondaryButton(
                "WHY DOES JARVIS NEED THIS?"
            ) {

                Toast.makeText(
                    this,
                    "It is required for screen automation and device-control workflows.",
                    Toast.LENGTH_LONG
                ).show()
            },
            lp()
        )

        setContentView(
            page
        )
    }

    private fun emptyState(
        icon: Int,
        title: String,
        subtitle: String,
        accent: Int
    ): LinearLayout {

        val box =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                setPadding(
                    dp(24),
                    dp(28),
                    dp(24),
                    dp(28)
                )

                background =
                    surfaceBackground(
                        surface,
                        22f,
                        border
                    )
            }

        box.addView(
            ImageView(this).apply {

                setImageResource(
                    icon
                )

                setPadding(
                    dp(18),
                    dp(18),
                    dp(18),
                    dp(18)
                )

                background =
                    surfaceBackground(
                        elevated,
                        32f,
                        border
                    )
            },
            LinearLayout.LayoutParams(
                dp(74),
                dp(74)
            )
        )

        box.addView(
            TextView(this).apply {

                text =
                    title

                textSize =
                    15f

                typeface =
                    Typeface.DEFAULT_BOLD

                letterSpacing =
                    0.06f

                setTextColor(
                    accent
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(16),
                    0,
                    0
                )
            },
            lp()
        )

        box.addView(
            TextView(this).apply {

                text =
                    subtitle

                textSize =
                    14f

                setTextColor(
                    secondary
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            },
            lp()
        )

        return box
    }

    private fun openRoute(
        route: String
    ) {

        startActivity(
            Intent(
                this,
                JarvisScreensActivity::class.java
            ).apply {
                putExtra(
                    ROUTE,
                    route
                )
            }
        )
    }

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

        if (
            requestCode != REQUEST_PERMISSION
        ) {
            return
        }

        recreate()
    }
}
