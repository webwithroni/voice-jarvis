package com.webwithroni.voicejarvis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.webwithroni.voicejarvis.orb.OrbActivity
import com.webwithroni.voicejarvis.orb.OrbState
import com.webwithroni.voicejarvis.orb.ParticleOrbView

/**
 * First-run JARVIS onboarding.
 *
 * Flow:
 *
 * 01 Meet JARVIS
 * 02 Capabilities
 * 03 Voice
 * 04 Setup
 * 05 Personality
 * 06 Activation
 */
class OnboardingActivity : AppCompatActivity() {

    private val uiCyan: Int
        get() = getColor(R.color.vj_cyan)

    private val uiPrimaryText: Int
        get() = getColor(R.color.vj_text_primary)

    private val uiSecondaryText: Int
        get() = getColor(R.color.vj_text_secondary)

    private lateinit var orb: ParticleOrbView
    private lateinit var stepText: TextView
    private lateinit var eyebrow: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var primary: Button
    private lateinit var hint: TextView
    private lateinit var skip: TextView
    private lateinit var personalityScroll: View
    private lateinit var personalityList: LinearLayout
    private lateinit var selectionSummary: TextView
    private lateinit var voiceStudio: View
    private lateinit var voiceSelectedName: TextView
    private lateinit var voiceSearch: EditText
    private lateinit var voiceList: LinearLayout
    private lateinit var voicePreviewButton: Button

    private var selectedVoiceId =
        VoiceCatalog.DEFAULT_VOICE

    private var previewVoiceId =
        VoiceCatalog.DEFAULT_VOICE

    private var previewState =
        VoicePreviewState.IDLE

    private lateinit var voicePreviewController:
        VoicePreviewController

    private var step = 0

    private var selectedPersonalityId =
        AssistantPersonalityCatalog.DEFAULT_PERSONALITY

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        if (
            !AuthManager.isSignedIn() ||
            AuthManager.isAnonymous()
        ) {
            openAuth()
            return
        }

        if (
            AuthManager.hasCompletedOnboarding()
        ) {
            openHome()
            return
        }

        setContentView(
            R.layout.activity_onboarding
        )

        bindViews()
        configureOrb()

        voicePreviewController =
            VoicePreviewController(
                context = this,

                onStateChanged = { state ->

                    runOnUiThread {

                        previewState =
                            state

                        updateVoicePreviewButton()
                        renderVoiceLibrary()
                    }
                },

                onPlaybackAmplitude = { level ->

                    runOnUiThread {

                        if (
                            previewState ==
                                VoicePreviewState.PLAYING
                        ) {

                            orb.setPlaybackAmplitude(
                                level
                            )
                        }
                    }
                },

                onOrbState = { orbState ->

                    runOnUiThread {

                        orb.setState(
                            orbState
                        )
                    }
                }
            )

        selectedPersonalityId =
            AssistantPersonalityPreferences
                .getSelectedPersonality(
                    this
                )

        renderStep()

        primary.setOnClickListener {
            advance()
        }

        skip.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun bindViews() {

        orb =
            findViewById(
                R.id.onboardingOrb
            )

        stepText =
            findViewById(
                R.id.onboardingStep
            )

        eyebrow =
            findViewById(
                R.id.onboardingEyebrow
            )

        title =
            findViewById(
                R.id.onboardingTitle
            )

        body =
            findViewById(
                R.id.onboardingBody
            )

        primary =
            findViewById(
                R.id.onboardingPrimary
            )

        hint =
            findViewById(
                R.id.onboardingHint
            )

        skip =
            findViewById(
                R.id.onboardingSkip
            )

        personalityScroll =
            findViewById(
                R.id.personalityScroll
            )

        personalityList =
            findViewById(
                R.id.personalityList
            )

        selectionSummary =
            findViewById(
                R.id.personalitySelectionSummary
            )

        voiceStudio =
            findViewById(
                R.id.voiceStudio
            )

        voiceSelectedName =
            findViewById(
                R.id.voiceSelectedName
            )

        voiceSearch =
            findViewById(
                R.id.voiceSearch
            )

        voiceList =
            findViewById(
                R.id.voiceList
            )

        voicePreviewButton =
            findViewById(
                R.id.voicePreviewButton
            )
    }

    private fun configureOrb() {

        orb.setState(
            OrbState.LISTENING
        )

        orb.setActivity(
            OrbActivity.NONE
        )

        orb.contentDescription =
            "JARVIS onboarding particle orb"
    }

    private fun renderStep() {

        stepText.text =
            String.format(
                "%02d / 06",
                step + 1
            )

        personalityScroll.visibility =
            View.GONE

        selectionSummary.visibility =
            View.GONE

        voiceStudio.visibility =
            View.GONE

        /*
         * Voice preview belongs exclusively to Step 03.
         * Leaving this step immediately terminates playback.
         */
        if (
            ::voicePreviewController.isInitialized &&
            step != 2
        ) {

            voicePreviewController.stop()

            previewState =
                VoicePreviewState.IDLE
        }

        when (step) {

            0 -> renderIntro()

            1 -> renderCapabilities()

            2 -> renderVoice()

            3 -> renderSetup()

            4 -> renderPersonality()

            5 -> renderActivation()
        }
    }

    private fun renderIntro() {

        eyebrow.text =
            "MEET JARVIS"

        title.text =
            "A voice system built around you."

        body.text =
            "Talk naturally, get useful answers, and let JARVIS help with the things you actually do."

        primary.text =
            "Continue"

        hint.text =
            "Your setup takes about a minute."

        orb.setState(
            OrbState.LISTENING
        )
    }

    private fun renderCapabilities() {

        eyebrow.text =
            "CAPABILITIES"

        title.text =
            "More than a voice."

        body.text =
            "JARVIS can answer questions, use connected tools, work with supported phone actions, search when current information matters, and react to natural conversation."

        primary.text =
            "Continue"

        hint.text =
            "Sensitive actions can require your confirmation."

        orb.setState(
            OrbState.THINKING
        )
    }

    private fun renderVoice() {

        selectedVoiceId =
            VoicePreferences.getSelectedVoice(
                this
            )

        previewVoiceId =
            selectedVoiceId

        previewState =
            VoicePreviewState.IDLE

        eyebrow.text =
            "VOICE STUDIO"

        title.text =
            "Choose the voice behind JARVIS."

        body.text =
            "Select a Gemini Live voice and preview it before continuing."

        primary.text =
            "Continue"

        hint.text =
            "You can change your voice anytime from Settings."

        voiceStudio.visibility =
            View.VISIBLE

        voiceSelectedName.text =
            VoiceCatalog.find(
                selectedVoiceId
            ).name

        renderVoiceLibrary()

        updateVoicePreviewButton()

        voicePreviewButton.setOnClickListener {

            previewVoice(
                selectedVoiceId
            )
        }

        voiceSearch.setOnClickListener {
            voiceSearch.requestFocus()
        }

        voiceSearch.addTextChangedListener(
            object :
                android.text.TextWatcher {

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

                    renderVoiceLibrary(
                        s?.toString() ?: ""
                    )
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) = Unit
            }
        )

        orb.setState(
            OrbState.SPEAKING
        )
    }

    private fun renderVoiceLibrary(
        query: String = voiceSearch.text?.toString() ?: ""
    ) {

        voiceList.removeAllViews()

        val normalized =
            query.trim().lowercase()

        val voices =
            VoiceCatalog.all.filter { voice ->

                normalized.isBlank() ||
                    voice.name.lowercase().contains(
                        normalized
                    ) ||
                    voice.character.lowercase().contains(
                        normalized
                    ) ||
                    voice.id.lowercase().contains(
                        normalized
                    )
            }

        voices.forEach { voice ->

            val card =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        android.view.Gravity.CENTER_VERTICAL

                    setPadding(
                        dp(10),
                        dp(8),
                        dp(10),
                        dp(8)
                    )

                    background =
                        getDrawable(
                            R.drawable.vj_card_background
                        )

                    alpha =
                        if (
                            voice.id.equals(
                                selectedVoiceId,
                                ignoreCase = true
                            )
                        ) {
                            1f
                        } else {
                            0.94f
                        }

                    isClickable =
                        true

                    isFocusable =
                        true
                }

            val radio =
                RadioButton(
                    this@OnboardingActivity
                ).apply {

                    isChecked =
                        voice.id.equals(
                            selectedVoiceId,
                            ignoreCase = true
                        )

                    setOnClickListener {

                        selectVoice(
                            voice.id
                        )
                    }
                }

            card.addView(
                radio,
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(52)
                )
            )

            val copy =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )

                    addView(
                        TextView(
                            this@OnboardingActivity
                        ).apply {

                            text =
                                voice.name

                            textSize =
                                15f

                            typeface =
                                android.graphics.Typeface.DEFAULT_BOLD

                            setTextColor(
                                uiPrimaryText
                            )
                        }
                    )

                    addView(
                        TextView(
                            this@OnboardingActivity
                        ).apply {

                            text =
                                "${voice.character} • Gender not specified"

                            textSize =
                                12f

                            setTextColor(
                                if (
                                    voice.id.equals(
                                        selectedVoiceId,
                                        ignoreCase = true
                                    )
                                ) {
                                    uiCyan
                                } else {
                                    uiSecondaryText
                                }
                            )

                            setPadding(
                                0,
                                dp(3),
                                0,
                                0
                            )
                        }
                    )
                }

            card.addView(
                copy
            )

            val preview =
                TextView(
                    this@OnboardingActivity
                ).apply {

                    text =
                        if (
                            voice.id.equals(
                                previewVoiceId,
                                ignoreCase = true
                            ) &&
                            previewState ==
                                VoicePreviewState.PLAYING
                        ) {
                            "■"
                        } else {
                            "▶"
                        }

                    textSize =
                        18f

                    setTextColor(
                        uiCyan
                    )

                    gravity =
                        android.view.Gravity.CENTER

                    setPadding(
                        dp(10),
                        0,
                        dp(4),
                        0
                    )

                    setOnClickListener {

                        previewVoice(
                            voice.id
                        )
                    }
                }

            card.addView(
                preview,
                LinearLayout.LayoutParams(
                    dp(48),
                    dp(52)
                )
            )

            card.setOnClickListener {

                selectVoice(
                    voice.id
                )
            }

            voiceList.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(7)
                }
            )
        }
    }

    private fun selectVoice(
        voiceId: String
    ) {

        if (
            !VoiceCatalog.contains(
                voiceId
            )
        ) {
            return
        }

        selectedVoiceId =
            VoiceCatalog.find(
                voiceId
            ).id

        voiceSelectedName.text =
            VoiceCatalog.find(
                selectedVoiceId
            ).name

        renderVoiceLibrary()

        updateVoicePreviewButton()
    }

    private fun previewVoice(
        voiceId: String
    ) {

        val normalizedVoice =
            VoiceCatalog.find(
                voiceId
            ).id

        /*
         * Tapping the currently playing voice stops preview.
         */
        if (
            previewState ==
                VoicePreviewState.PLAYING &&
            previewVoiceId.equals(
                normalizedVoice,
                ignoreCase = true
            )
        ) {

            voicePreviewController.stop()

            return
        }

        previewVoiceId =
            normalizedVoice

        previewState =
            VoicePreviewState.LOADING

        updateVoicePreviewButton()
        renderVoiceLibrary()

        voicePreviewController.preview(
            normalizedVoice
        )
    }

    private fun updateVoicePreviewButton() {

        val voice =
            VoiceCatalog.find(
                selectedVoiceId
            )

        voicePreviewButton.text =
            when {

                previewState ==
                    VoicePreviewState.PLAYING &&
                    previewVoiceId.equals(
                        selectedVoiceId,
                        ignoreCase = true
                    ) ->
                    "■  STOP PREVIEW"

                previewState ==
                    VoicePreviewState.LOADING ->
                    "LOADING..."

                else ->
                    "▶  PREVIEW ${voice.name.uppercase()}"
            }
    }

    private fun renderSetup() {

        eyebrow.text =
            "SETUP"

        title.text =
            "JARVIS stays ready when you need it."

        body.text =
            "Permissions are requested only when a capability needs them. You can manage them later from Settings."

        primary.text =
            "Continue"

        hint.text =
            "No account creation, phone login or Google login is required."

        orb.setState(
            OrbState.LISTENING
        )
    }

    private fun renderPersonality() {

        eyebrow.text =
            "PERSONALITY"

        title.text =
            "Choose who JARVIS is for you."

        body.text =
            "Voice controls how JARVIS sounds. Personality controls how JARVIS thinks, speaks and behaves."

        primary.text =
            "Continue"

        hint.text =
            "You can change this anytime from Settings."

        personalityScroll.visibility =
            View.VISIBLE

        selectionSummary.visibility =
            View.VISIBLE

        renderPersonalityCards()

        orb.setState(
            OrbState.THINKING
        )
    }

    private fun renderActivation() {

        val personality =
            AssistantPersonalityCatalog.find(
                selectedPersonalityId
            )

        val voice =
            VoicePreferences.getSelectedVoiceInfo(
                this
            )

        eyebrow.text =
            "READY"

        title.text =
            "Your JARVIS is configured."

        body.text =
            "${voice.name} • ${voice.character}\n${personality.name}\n\nEverything is ready. Enter your workspace and start talking."

        primary.text =
            "Enter JARVIS"

        hint.text =
            "You can change voice, personality and preferences later."

        orb.setState(
            OrbState.SPEAKING
        )
    }

    private fun renderPersonalityCards() {

        personalityList.removeAllViews()

        AssistantPersonalityCatalog
            .all
            .forEach { personality ->

                val selected =
                    personality.id.equals(
                        selectedPersonalityId,
                        ignoreCase = true
                    )

                val card =
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
                            getDrawable(
                                if (selected) {
                                    R.drawable.vj_card_background
                                } else {
                                    R.drawable.vj_surface
                                }
                            )

                        isClickable =
                            true

                        isFocusable =
                            true

                        contentDescription =
                            "Select ${personality.name}"

                        setOnClickListener {

                            selectedPersonalityId =
                                personality.id

                            renderPersonalityCards()
                            updatePersonalitySummary()

                            orb.setActivity(
                                OrbActivity.NONE
                            )
                        }
                    }

                val header =
                    LinearLayout(this).apply {

                        orientation =
                            LinearLayout.HORIZONTAL
                    }

                header.addView(
                    TextView(this@OnboardingActivity).apply {

                        text =
                            personality.name

                        textSize =
                            16f

                        setTextColor(
                            getColor(
                                R.color.vj_text_primary
                            )
                        )

                        setTypeface(
                            typeface,
                            android.graphics.Typeface.BOLD
                        )

                        layoutParams =
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                    }
                )

                if (selected) {

                    header.addView(
                        TextView(
                            this@OnboardingActivity
                        ).apply {

                            text =
                                "SELECTED"

                            textSize =
                                10f

                            setTextColor(
                                getColor(
                                    R.color.vj_cyan
                                )
                            )

                            setTypeface(
                                typeface,
                                android.graphics.Typeface.BOLD
                            )
                        }
                    )
                }

                card.addView(
                    header
                )

                card.addView(
                    TextView(
                        this@OnboardingActivity
                    ).apply {

                        text =
                            personality.traits.joinToString(
                                " • "
                            )

                        textSize =
                            12f

                        setTextColor(
                            getColor(
                                R.color.vj_cyan
                            )
                        )

                        setPadding(
                            0,
                            dp(6),
                            0,
                            0
                        )
                    }
                )

                card.addView(
                    TextView(
                        this@OnboardingActivity
                    ).apply {

                        text =
                            personality.description

                        textSize =
                            13f

                        setTextColor(
                            getColor(
                                R.color.vj_text_secondary
                            )
                        )

                        setPadding(
                            0,
                            dp(7),
                            0,
                            0
                        )
                    }
                )

                card.addView(
                    TextView(
                        this@OnboardingActivity
                    ).apply {

                        text =
                            "“${personality.previewText}”"

                        textSize =
                            13f

                        setTextColor(
                            getColor(
                                R.color.vj_text_tertiary
                            )
                        )

                        setPadding(
                            0,
                            dp(8),
                            0,
                            0
                        )
                    }
                )

                personalityList.addView(
                    card,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin =
                            dp(8)
                    }
                )
            }

        updatePersonalitySummary()
    }

    private fun updatePersonalitySummary() {

        val personality =
            AssistantPersonalityCatalog.find(
                selectedPersonalityId
            )

        selectionSummary.text =
            "Selected • ${personality.name}"

        selectionSummary.setTextColor(
            getColor(
                R.color.vj_cyan
            )
        )
    }

    private fun advance() {

        if (
            step == 4
        ) {

            val saved =
                AssistantPersonalityPreferences
                    .setSelectedPersonality(
                        this,
                        selectedPersonalityId
                    )

            if (!saved) {

                Toast.makeText(
                    this,
                    "Unable to save personality.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }
        }

        if (
            step < 5
        ) {

            step += 1
            renderStep()

        } else {

            completeOnboarding()
        }
    }

    private fun completeOnboarding() {

        AssistantPersonalityPreferences
            .setSelectedPersonality(
                this,
                selectedPersonalityId
            )

        AuthManager.setOnboardingCompleted(
            true
        )

        openHome()
    }

    private fun openHome() {

        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )

        finish()
    }

    override fun onDestroy() {

        if (
            ::voicePreviewController.isInitialized
        ) {

            voicePreviewController.release()
        }

        super.onDestroy()
    }

    private fun openAuth() {

        startActivity(
            Intent(
                this,
                AuthActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )

        finish()
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
