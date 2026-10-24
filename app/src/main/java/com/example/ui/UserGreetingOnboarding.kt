package com.example.ui

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldPrimary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

/**
 * 100% Free Native Android TextToSpeech Greeting Engine for NurAnjam.
 *
 * Forces Android's built-in Google Speech Services (`com.google.android.tts`) to use its
 * high-definition Indian English (`en-IN`) Female Network Voice (`voice.isNetworkConnectionRequired == true`),
 * avoiding robotic offline synthesis without requiring any paid API keys.
 */
class UserGreetingManager(context: Context) {

    companion object {
        private const val TAG = "UserGreetingManager"
        private const val PREFS_NAME = "nur_anjam_user_prefs"
        private const val KEY_USER_NAME = "user_display_name"
        private const val GOOGLE_TTS_ENGINE_PACKAGE = "com.google.android.tts"

        // Process-level flag ensuring the TTS greeting only fires once per cold boot (process creation)
        @Volatile
        private var hasGreetedThisColdBoot: Boolean = false

        fun getSavedUserName(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_USER_NAME, null)?.takeIf { it.isNotBlank() }
        }

        fun saveUserName(context: Context, name: String) {
            val trimmed = name.trim()
            if (trimmed.isNotBlank()) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_USER_NAME, trimmed).apply()
            }
        }

        /**
         * Dynamic Time-of-Day Calculation:
         * - Morning:   5 AM – 11 AM (hour in 5..11)
         * - Afternoon: 12 PM – 4 PM (hour in 12..16)
         * - Evening:   5 PM – 4 AM  (hour >= 17 || hour in 0..4)
         */
        fun getTimeOfDay(hour: Int = LocalTime.now().hour): String {
            return when (hour) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                else -> "Evening"
            }
        }

        fun getTimeBasedGreeting(hour: Int = LocalTime.now().hour): String {
            return "Good ${getTimeOfDay(hour)}"
        }

        /**
         * Exact Script:
         * "Good [Morning / Afternoon / Evening], ${userName} Sir, Enjoy NurAnjam."
         */
        fun buildGreetingScript(userName: String, hour: Int = LocalTime.now().hour): String {
            val timeOfDay = getTimeOfDay(hour)
            return "Good $timeOfDay, ${userName.trim()} Sir, Enjoy NurAnjam."
        }
    }

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isTtsReady: Boolean = false
    private var pendingScript: String? = null

    /**
     * Step 4: Cold-Boot Playback Logic
     * Triggered strictly from MainActivity.onCreate() when savedInstanceState == null.
     * Checks SharedPreferences for the user's name and speaks the greeting once per cold boot.
     */
    fun triggerColdBootGreetingIfEligible(isFreshActivityLaunch: Boolean) {
        if (!isFreshActivityLaunch || hasGreetedThisColdBoot) {
            return
        }
        val savedName = getSavedUserName(appContext) ?: return
        hasGreetedThisColdBoot = true
        speakGreetingForUser(savedName)
    }

    /**
     * Speaks the dynamic greeting for the given user using the high-quality Indian English
     * female network voice.
     */
    fun speakGreetingForUser(userName: String) {
        hasGreetedThisColdBoot = true
        val script = buildGreetingScript(userName)
        if (isTtsReady && tts != null) {
            speakNow(script)
        } else {
            pendingScript = script
            initializeNativeTts()
        }
    }

    /**
     * Step 2: Initialize Native TTS
     * Initializes TextToSpeech (preferring Google's TTS engine package so network voices are available)
     * and sets the base locale to Indian English: Locale("en", "IN").
     */
    private fun initializeNativeTts() {
        if (tts != null) return
        try {
            val onInitListener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@OnInitListener

                    // Set base locale to Indian English: en-IN
                    val indianLocale = Locale("en", "IN")
                    val langResult = engine.setLanguage(indianLocale)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.US)
                    }

                    // Step 3: Force the High-Quality Indian English Female Network Voice
                    forceHighQualityIndianFemaleNetworkVoice(engine)

                    // Tune cadence for natural, warm human delivery
                    engine.setSpeechRate(0.95f)
                    engine.setPitch(1.05f)
                    isTtsReady = true

                    pendingScript?.let { scriptToSpeak ->
                        pendingScript = null
                        speakNow(scriptToSpeak)
                    }
                } else {
                    Log.w(TAG, "Native TextToSpeech initialization failed with status: $status")
                }
            }

            // Bind directly to Google Speech Services ("com.google.android.tts") if installed
            tts = TextToSpeech(appContext, onInitListener, GOOGLE_TTS_ENGINE_PACKAGE)
        } catch (e: Exception) {
            Log.w(TAG, "Fallback to default TTS engine: ${e.message}")
            try {
                tts = TextToSpeech(appContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val engine = tts ?: return@TextToSpeech
                        engine.setLanguage(Locale("en", "IN"))
                        forceHighQualityIndianFemaleNetworkVoice(engine)
                        engine.setSpeechRate(0.95f)
                        engine.setPitch(1.05f)
                        isTtsReady = true
                        pendingScript?.let { scriptToSpeak ->
                            pendingScript = null
                            speakNow(scriptToSpeak)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Step 3: Force the High-Quality Network Voice (The Secret)
     *
     * Scans `tts.voices` to find a voice where:
     * 1. `voice.locale.language == "en"` and `voice.locale.country == "IN"`
     * 2. `voice.isNetworkConnectionRequired == true` (forces Google Play Services' cloud voice)
     * 3. `voice.name.contains("female", ignoreCase = true)` or Google's Indian English female
     *    voice identifiers (`en-in-x-end`, `en-in-x-ena`, `en-in-x-enc`, or `"female"` in `voice.features`).
     *
     * Once found, applies it via `tts.setVoice(premiumVoice)`.
     */
    private fun forceHighQualityIndianFemaleNetworkVoice(ttsEngine: TextToSpeech) {
        try {
            val availableVoices: Set<Voice> = ttsEngine.voices ?: return
            if (availableVoices.isEmpty()) return

            val isFemaleVoice = { voice: Voice ->
                val name = voice.name
                val features = voice.features ?: emptySet()
                name.contains("female", ignoreCase = true) ||
                    name.contains("en-in-x-end", ignoreCase = true) ||
                    name.contains("en-in-x-ena", ignoreCase = true) ||
                    name.contains("en-in-x-enc", ignoreCase = true) ||
                    features.any { it.contains("female", ignoreCase = true) }
            }

            // 1. Primary Target: en-IN + Network Required + Female
            val premiumNetworkVoice: Voice? = availableVoices
                .filter { voice ->
                    voice.locale.language.equals("en", ignoreCase = true) &&
                        voice.locale.country.equals("IN", ignoreCase = true) &&
                        voice.isNetworkConnectionRequired &&
                        isFemaleVoice(voice)
                }
                .maxByOrNull { it.quality }
                // 2. Fallback A: Any en-IN Network voice with highest quality
                ?: availableVoices
                    .filter { voice ->
                        voice.locale.language.equals("en", ignoreCase = true) &&
                            voice.locale.country.equals("IN", ignoreCase = true) &&
                            voice.isNetworkConnectionRequired
                    }
                    .maxByOrNull { it.quality }
                // 3. Fallback B (Offline): en-IN Female voice with highest quality
                ?: availableVoices
                    .filter { voice ->
                        voice.locale.language.equals("en", ignoreCase = true) &&
                            voice.locale.country.equals("IN", ignoreCase = true) &&
                            isFemaleVoice(voice)
                    }
                    .maxByOrNull { it.quality }

            if (premiumNetworkVoice != null) {
                ttsEngine.setVoice(premiumNetworkVoice)
                Log.i(
                    TAG,
                    "Applied premium Indian English TTS voice: ${premiumNetworkVoice.name} " +
                        "(network=${premiumNetworkVoice.isNetworkConnectionRequired}, quality=${premiumNetworkVoice.quality})"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to filter TTS voices: ${e.message}")
        }
    }

    private fun speakNow(script: String) {
        val engine = tts ?: return
        try {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            engine.speak(
                script,
                TextToSpeech.QUEUE_FLUSH,
                params,
                "nuranjam_cold_boot_greeting"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error speaking greeting: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
            isTtsReady = false
        }
    }
}

/**
 * Full-screen Aquamorphic / Glassmorphic First-Launch Onboarding Screen.
 * - Cinematic blur-reveal entrance for "What should we call you?"
 * - Translucent pill TextField
 * - Fluid spring-physics scale-down & zoom-out dismissal
 */
@Composable
fun GlassmorphicNameOnboardingOverlay(
    onNameSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var nameInput by remember { mutableStateOf("") }
    var isDismissing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Cinematic entrance blur-reveal & alpha animations
    val titleAlpha = remember { Animatable(0f) }
    val titleBlurDp = remember { Animatable(22f) }
    val contentAlpha = remember { Animatable(0f) }

    // Fluid spring-physics exit scale & alpha
    val containerScale = remember { Animatable(1f) }
    val containerAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                titleAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
                )
            }
            launch {
                titleBlurDp.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing)
                )
            }
            launch {
                contentAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 900, delayMillis = 250, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    fun submitName() {
        val trimmed = nameInput.trim()
        if (trimmed.isBlank() || isDismissing) return
        isDismissing = true
        focusManager.clearFocus()
        scope.launch {
            coroutineScope {
                launch {
                    containerScale.animateTo(
                        targetValue = 0.82f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                launch {
                    containerAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
            onNameSubmitted(trimmed)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = containerScale.value
                scaleY = containerScale.value
                alpha = containerAlpha.value
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF2040D0A),
                        Color(0xF5071813),
                        Color(0xFA030907)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
            .imePadding()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.05f),
                            GoldPrimary.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            GoldPrimary.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.12f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 26.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                GoldPrimary.copy(alpha = 0.28f),
                                Color(0xFF0A221B)
                            )
                        )
                    )
                    .border(1.dp, GoldPrimary.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Welcome to NurAnjam",
                    tint = GoldPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "What should we call you?",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                modifier = Modifier
                    .graphicsLayer { alpha = titleAlpha.value }
                    .blur(titleBlurDp.value.dp)
                    .testTag("onboarding_prompt_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Personalize your sacred audio sanctuary",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha.value }
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = {
                    Text(
                        text = "Enter your name (e.g., Noor)",
                        color = Color.White.copy(alpha = 0.45f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submitName() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = GoldPrimary,
                    focusedContainerColor = Color.White.copy(alpha = 0.10f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.28f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentAlpha.value }
                    .testTag("onboarding_name_input")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { submitName() },
                enabled = nameInput.trim().isNotBlank() && !isDismissing,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary,
                    contentColor = Color(0xFF061510),
                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                    disabledContentColor = Color.White.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer { alpha = contentAlpha.value }
                    .testTag("onboarding_continue_button")
            ) {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
