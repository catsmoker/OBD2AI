package com.catsmoker.obd2ai.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiFrequency
import com.catsmoker.obd2ai.ai.AiPersonality
import com.catsmoker.obd2ai.ai.AiProvider
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.ai.AiSwitchRules
import com.catsmoker.obd2ai.prefs.AppLanguage
import com.catsmoker.obd2ai.prefs.PrefsKeys
import com.catsmoker.obd2ai.prefs.ThemeMode
import com.catsmoker.obd2ai.prefs.Units
import com.catsmoker.obd2ai.speedometers.SpeedometerHost
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import android.widget.RadioGroup

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

        val radioGroupTheme = view.findViewById<RadioGroup>(R.id.radioGroupTheme)
        when ((activity as MainActivity).currentThemeMode(prefs)) {
            ThemeMode.LIGHT -> radioGroupTheme.check(R.id.radioThemeLight)
            ThemeMode.DARK -> radioGroupTheme.check(R.id.radioThemeDark)
            ThemeMode.SYSTEM -> radioGroupTheme.check(R.id.radioThemeSystem)
        }
        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioThemeLight -> ThemeMode.LIGHT
                R.id.radioThemeDark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            prefs.edit {
                putString(PrefsKeys.THEME_MODE, mode.prefValue)
            }
            (activity as MainActivity).applyThemeMode(mode)
        }

        val apiKeyEditText = view.findViewById<TextInputEditText>(R.id.apiKeyEditText)
        val apiKeyLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.apiKeyLayout)
        val modelIdEditText = view.findViewById<TextInputEditText>(R.id.modelIdEditText)
        val baseUrlEditText = view.findViewById<TextInputEditText>(R.id.baseUrlEditText)
        val baseUrlLayout = view.findViewById<View>(R.id.baseUrlLayout)
        val providerSpinner = view.findViewById<Spinner>(R.id.providerSpinner)
        val saveButton = view.findViewById<Button>(R.id.saveButton)

        val providers = AiProvider.entries
        providerSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            providers.map { it.displayName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val savedProvider = AiProvider.fromId(prefs.getString(PrefsKeys.AI_PROVIDER, null))
        var lastProvider = savedProvider
        fun renderProvider(selected: AiProvider) {
            baseUrlLayout.visibility = if (selected.showBaseUrl) View.VISIBLE else View.GONE
            apiKeyLayout.hint = getString(
                if (selected.needsKey) R.string.settings_api_key_hint
                else R.string.settings_api_key_hint_custom
            )
        }
        providerSpinner.setSelection(providers.indexOf(savedProvider))
        renderProvider(savedProvider)
        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, itemView: View?, position: Int, id: Long) {
                val selected = providers[position]
                renderProvider(selected)
                // Suggest this provider's model: fill when blank, or swap out the
                // previous provider's default — a hand-typed custom model is kept.
                val current = modelIdEditText.text?.toString().orEmpty()
                if (current.isBlank() || current == lastProvider.defaultModel) {
                    modelIdEditText.setText(selected.defaultModel)
                }
                lastProvider = selected
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        apiKeyEditText.setText(prefs.getString(PrefsKeys.OPENAI_API_KEY, ""))
        val savedModel = prefs.getString(PrefsKeys.OPENAI_MODEL_ID, "").orEmpty()
        modelIdEditText.setText(savedModel.ifEmpty { savedProvider.defaultModel })
        baseUrlEditText.setText(prefs.getString(PrefsKeys.AI_BASE_URL, ""))

        val switchMuteSound = view.findViewById<SwitchMaterial>(R.id.switchMuteSound)

        switchMuteSound.isChecked = prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)

        switchMuteSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean(PrefsKeys.MUTE_SOUND, isChecked)
            }
        }

        val radioGroupUnits = view.findViewById<RadioGroup>(R.id.radioGroupUnits)
        if (Units.isImperial(prefs)) {
            radioGroupUnits.check(R.id.radioUnitsImperial)
        } else {
            radioGroupUnits.check(R.id.radioUnitsMetric)
        }
        radioGroupUnits.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit {
                putString(
                    PrefsKeys.UNITS,
                    if (checkedId == R.id.radioUnitsImperial) PrefsKeys.UNITS_IMPERIAL
                    else PrefsKeys.UNITS_METRIC
                )
            }
        }

        val switchKeepScreen = view.findViewById<SwitchMaterial>(R.id.switchKeepScreen)
        switchKeepScreen.isChecked = prefs.getBoolean(PrefsKeys.KEEP_SCREEN_ON, false)
        switchKeepScreen.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(PrefsKeys.KEEP_SCREEN_ON, isChecked) }
        }

        val switchEngineSound = view.findViewById<SwitchMaterial>(R.id.switchEngineSound)
        switchEngineSound.isChecked = prefs.getBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, false)
        switchEngineSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(PrefsKeys.ENGINE_SOUND_ENABLED, isChecked) }
        }

        // Speedometer gallery: one live preview at a time with arrows,
        // swipe and wrap-around. Selecting persists immediately through the
        // same key the dashboard reads; Save rewrites it like the rest.
        var selectedStyle = SpeedometerStyle.fromId(
            prefs.getString(PrefsKeys.SPEEDOMETER_STYLE, PrefsKeys.SPEEDOMETER_DEFAULT)
        )
        val styles = SpeedometerStyle.entries
        var styleIndex = styles.indexOf(selectedStyle).coerceAtLeast(0)
        val imperial = Units.isImperial(prefs)
        val stage = view.findViewById<FrameLayout>(R.id.speedometerStage)
        val styleName = view.findViewById<TextView>(R.id.speedometerName)
        val stylePosition = view.findViewById<TextView>(R.id.speedometerPosition)
        val styleDesc = view.findViewById<TextView>(R.id.speedometerDesc)
        // Assigned with the accordion setup below; defaults to no-op so
        // early callbacks are safe before it exists.
        var updateSummaries: () -> Unit = {}

        fun renderLabels() {
            val style = styles[styleIndex]
            styleName.text = getString(style.titleRes)
            styleDesc.text = getString(style.descRes)
            stylePosition.text = "${styleIndex + 1} / ${styles.size}"
        }

        fun showStyle(index: Int, direction: Int, animate: Boolean) {
            styleIndex = SpeedometerStyle.wrappedIndex(index)
            val style = styles[styleIndex]
            val old = if (stage.childCount > 0) stage.getChildAt(0) else null
            val preview = SpeedometerHost.createView(requireContext(), style)
            preview.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            preview.contentDescription =
                getString(R.string.speedometer_preview_desc, getString(style.titleRes))
            SpeedometerHost.bindPreview(preview, imperial)
            val width = stage.width.toFloat()
            if (animate && old != null && width > 0f) {
                stage.addView(preview)
                preview.translationX = direction * width
                preview.animate().translationX(0f).setDuration(220).start()
                old.animate().translationX(-direction * width).alpha(0f)
                    .setDuration(220)
                    .withEndAction { stage.removeView(old) }
                    .start()
            } else {
                stage.removeAllViews()
                stage.addView(preview)
            }
            renderLabels()
            if (selectedStyle != style) {
                selectedStyle = style
                prefs.edit { putString(PrefsKeys.SPEEDOMETER_STYLE, style.id) }
                updateSummaries()
            }
        }

        fun stepStyle(delta: Int) {
            showStyle(styleIndex + delta, delta, animate = true)
        }

        view.findViewById<ImageButton>(R.id.speedometerPrev).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            stepStyle(-1)
        }
        view.findViewById<ImageButton>(R.id.speedometerNext).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            stepStyle(1)
        }
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        val flingDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (kotlin.math.abs(dx) > touchSlop * 2 &&
                        kotlin.math.abs(dx) > 2 * kotlin.math.abs(dy)
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        stepStyle(if (dx < 0) 1 else -1)
                        return true
                    }
                    return false
                }
            }
        )
        stage.isClickable = true
        stage.setOnTouchListener { _, event ->
            flingDetector.onTouchEvent(event)
            true
        }
        // Labels now; the heavy live preview one frame after navigation so
        // opening Settings feels instant.
        renderLabels()
        stage.post {
            if (!isAdded) return@post
            showStyle(styleIndex, 0, animate = false)
        }

        val languages = AppLanguage.entries
        val languageSpinner = view.findViewById<Spinner>(R.id.languageSpinner)
        languageSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            languages.map { it.displayName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        languageSpinner.setSelection(
            languages.indexOf(AppLanguage.fromTag(prefs.getString(PrefsKeys.APP_LANGUAGE, null))).coerceAtLeast(0)
        )
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, itemView: View?, position: Int, id: Long) {
                val tag = languages[position].tag
                if (prefs.getString(PrefsKeys.APP_LANGUAGE, null) != tag) {
                    prefs.edit { putString(PrefsKeys.APP_LANGUAGE, tag) }
                    (activity as MainActivity).applyAppLanguage(tag)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val radioGroupSpeedSource = view.findViewById<RadioGroup>(R.id.radioGroupSpeedSource)
        val savedSpeedSource = prefs.getString(PrefsKeys.SPEED_SOURCE, PrefsKeys.SPEED_SOURCE_OBD)
        if (savedSpeedSource == PrefsKeys.SPEED_SOURCE_DEVICE) {
            radioGroupSpeedSource.check(R.id.radioButtonGps)
        } else {
            radioGroupSpeedSource.check(R.id.radioButtonObd2)
        }

        // -- Offline AI section: master switch, outputs, volume, event alerts ----
        val switchOfflineAi = view.findViewById<SwitchMaterial>(R.id.switchOfflineAi)
        val offlineAiContent = view.findViewById<View>(R.id.offlineAiContent)
        val switchOfflineVoice = view.findViewById<SwitchMaterial>(R.id.switchOfflineVoice)
        val switchOfflineText = view.findViewById<SwitchMaterial>(R.id.switchOfflineText)
        val offlineVolumeValue = view.findViewById<TextView>(R.id.offlineVolumeValue)
        val offlineVolumeSeek = view.findViewById<SeekBar>(R.id.offlineVolumeSeek)
        val switchShiftPoint = view.findViewById<SwitchMaterial>(R.id.switchShiftPoint)
        val shiftValue = view.findViewById<TextView>(R.id.shiftRpmValue)
        val shiftSeek = view.findViewById<SeekBar>(R.id.shiftRpmSeekBar)
        val switchHighRpm = view.findViewById<SwitchMaterial>(R.id.switchHighRpm)
        val highRpmValue = view.findViewById<TextView>(R.id.highRpmValue)
        val highRpmSeek = view.findViewById<SeekBar>(R.id.highRpmSeekBar)
        val switchHighSpeed = view.findViewById<SwitchMaterial>(R.id.switchHighSpeed)
        val highSpeedValue = view.findViewById<TextView>(R.id.highSpeedValue)
        val highSpeedSeek = view.findViewById<SeekBar>(R.id.highSpeedSeekBar)
        val switchCoolantAlert = view.findViewById<SwitchMaterial>(R.id.switchCoolantAlert)
        val coolantThresholdValue = view.findViewById<TextView>(R.id.coolantThresholdValue)
        val coolantThresholdSeek = view.findViewById<SeekBar>(R.id.coolantThresholdSeek)
        val switchNewFault = view.findViewById<SwitchMaterial>(R.id.switchNewFault)
        val switchConnLost = view.findViewById<SwitchMaterial>(R.id.switchConnLost)
        val switchConnRestored = view.findViewById<SwitchMaterial>(R.id.switchConnRestored)
        val switchEngineStart = view.findViewById<SwitchMaterial>(R.id.switchEngineStart)
        val switchEngineStop = view.findViewById<SwitchMaterial>(R.id.switchEngineStop)
        val switchEasterEggs = view.findViewById<SwitchMaterial>(R.id.switchEasterEggs)

        /** Guard against listener recursion on programmatic switch changes. */
        var updatingSwitches = false

        /** Binds a threshold SeekBar + label; slider writes instantly, getter feeds Save. */
        fun bindThreshold(
            seek: SeekBar,
            valueView: TextView,
            formatId: Int,
            min: Int,
            max: Int,
            step: Int,
            prefKey: String,
            def: Int
        ): () -> Int {
            val steps = (max - min) / step
            seek.max = steps
            var current = prefs.getInt(prefKey, def).coerceIn(min, max)
            fun render(v: Int) {
                valueView.text = getString(formatId, v)
            }
            seek.progress = ((current - min) / step).coerceIn(0, steps)
            render(current)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    current = min + progress * step
                    render(current)
                    prefs.edit { putInt(prefKey, current) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            return { current }
        }

        fun setThresholdEnabled(valueView: TextView, seek: SeekBar, enabled: Boolean) {
            valueView.isEnabled = enabled
            seek.isEnabled = enabled
            val alpha = if (enabled) 1f else 0.4f
            valueView.alpha = alpha
            seek.alpha = alpha
        }

        val getShiftRpm = bindThreshold(
            shiftSeek, shiftValue, R.string.settings_shift_rpm_value,
            PrefsKeys.SHIFT_RPM_MIN, PrefsKeys.SHIFT_RPM_MAX, PrefsKeys.SHIFT_RPM_STEP,
            PrefsKeys.SHIFT_RPM, PrefsKeys.DEFAULT_SHIFT_RPM
        )
        val getHighRpm = bindThreshold(
            highRpmSeek, highRpmValue, R.string.settings_shift_rpm_value,
            PrefsKeys.HIGH_RPM_MIN, PrefsKeys.HIGH_RPM_MAX, PrefsKeys.HIGH_RPM_STEP,
            PrefsKeys.HIGH_RPM_THRESHOLD, PrefsKeys.DEFAULT_HIGH_RPM
        )
        val getHighSpeed = bindThreshold(
            highSpeedSeek, highSpeedValue, R.string.settings_speed_value,
            PrefsKeys.HIGH_SPEED_MIN, PrefsKeys.HIGH_SPEED_MAX, PrefsKeys.HIGH_SPEED_STEP,
            PrefsKeys.HIGH_SPEED_THRESHOLD, PrefsKeys.DEFAULT_HIGH_SPEED
        )
        val getCoolantThreshold = bindThreshold(
            coolantThresholdSeek, coolantThresholdValue, R.string.settings_temp_value,
            PrefsKeys.OFFLINE_COOLANT_MIN, PrefsKeys.OFFLINE_COOLANT_MAX, PrefsKeys.OFFLINE_COOLANT_STEP,
            PrefsKeys.OFFLINE_COOLANT_THRESHOLD, PrefsKeys.DEFAULT_OFFLINE_COOLANT
        )
        val getOfflineVolume = bindThreshold(
            offlineVolumeSeek, offlineVolumeValue, R.string.settings_ai_volume_value,
            0, 100, 1, PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME
        )

        fun renderThresholdStates() {
            setThresholdEnabled(shiftValue, shiftSeek, switchShiftPoint.isChecked)
            setThresholdEnabled(highRpmValue, highRpmSeek, switchHighRpm.isChecked)
            setThresholdEnabled(highSpeedValue, highSpeedSeek, switchHighSpeed.isChecked)
            setThresholdEnabled(coolantThresholdValue, coolantThresholdSeek, switchCoolantAlert.isChecked)
        }

        fun renderOfflineVisibility() {
            offlineAiContent.visibility =
                if (switchOfflineAi.isChecked) View.VISIBLE else View.GONE
        }

        // Per-alert switches: instant save + threshold enable state.
        val alertSwitches = listOf(
            switchShiftPoint to PrefsKeys.SHIFT_POINT_ENABLED,
            switchHighRpm to PrefsKeys.HIGH_RPM_ENABLED,
            switchHighSpeed to PrefsKeys.HIGH_SPEED_ENABLED,
            switchCoolantAlert to PrefsKeys.OFFLINE_COOLANT_ENABLED,
            switchNewFault to PrefsKeys.NEW_FAULT_ENABLED,
            switchConnLost to PrefsKeys.CONN_LOST_ENABLED,
            switchConnRestored to PrefsKeys.CONN_RESTORED_ENABLED,
            switchEngineStart to PrefsKeys.ENGINE_START_ENABLED,
            switchEngineStop to PrefsKeys.ENGINE_STOP_ENABLED
        )
        val alertDefaults = mapOf(
            PrefsKeys.SHIFT_POINT_ENABLED to true,
            PrefsKeys.HIGH_RPM_ENABLED to true,
            PrefsKeys.HIGH_SPEED_ENABLED to true,
            PrefsKeys.OFFLINE_COOLANT_ENABLED to true,
            PrefsKeys.NEW_FAULT_ENABLED to true,
            PrefsKeys.CONN_LOST_ENABLED to true,
            PrefsKeys.CONN_RESTORED_ENABLED to true,
            PrefsKeys.ENGINE_START_ENABLED to true,
            PrefsKeys.ENGINE_STOP_ENABLED to false
        )
        for ((switch, key) in alertSwitches) {
            switch.isChecked = prefs.getBoolean(key, alertDefaults[key] == true)
            switch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean(key, isChecked) }
                renderThresholdStates()
            }
        }
        renderThresholdStates()

        fun applyOfflineOutputs(fromVoice: Boolean) {
            val (voiceOn, textOn) = AiSwitchRules.enforceOfflineOutput(
                fromVoice, switchOfflineVoice.isChecked, switchOfflineText.isChecked
            )
            prefs.edit {
                putBoolean(PrefsKeys.VOICE_INSIGHT, voiceOn)
                putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, textOn)
            }
            updatingSwitches = true
            switchOfflineVoice.isChecked = voiceOn
            switchOfflineText.isChecked = textOn
            updatingSwitches = false
        }
        switchOfflineVoice.isChecked = prefs.getBoolean(PrefsKeys.VOICE_INSIGHT, true)
        switchOfflineVoice.setOnCheckedChangeListener { _, _ ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            applyOfflineOutputs(fromVoice = true)
        }
        switchOfflineText.isChecked = prefs.getBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, true)
        switchOfflineText.setOnCheckedChangeListener { _, _ ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            applyOfflineOutputs(fromVoice = false)
        }
        // Heal legacy both-off states so the at-least-one rule always holds.
        val (healedVoice, healedText) = AiSwitchRules.enforceOfflineOutput(
            true, switchOfflineVoice.isChecked, switchOfflineText.isChecked
        )
        if (healedVoice != switchOfflineVoice.isChecked || healedText != switchOfflineText.isChecked) {
            prefs.edit {
                putBoolean(PrefsKeys.VOICE_INSIGHT, healedVoice)
                putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, healedText)
            }
            updatingSwitches = true
            switchOfflineVoice.isChecked = healedVoice
            switchOfflineText.isChecked = healedText
            updatingSwitches = false
        }
        switchEasterEggs.isChecked = prefs.getBoolean(PrefsKeys.EASTER_EGGS_ENABLED, true)
        switchEasterEggs.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(PrefsKeys.EASTER_EGGS_ENABLED, isChecked) }
        }

        // -- Online AI (online assistant; Offline AI stays offline and untouched) --------
        val switchOnlineAi = view.findViewById<SwitchMaterial>(R.id.switchOnlineAi)
        val aiApiStatus = view.findViewById<TextView>(R.id.aiApiStatus)
        val freqSpinner = view.findViewById<Spinner>(R.id.freqSpinner)
        val personalitySpinner = view.findViewById<Spinner>(R.id.personalitySpinner)
        val aiVolumeValue = view.findViewById<TextView>(R.id.aiVolumeValue)
        val aiVolumeSeek = view.findViewById<SeekBar>(R.id.aiVolumeSeek)

        val frequencies = AiFrequency.entries
        freqSpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            frequencies.map { it.name.lowercase().replaceFirstChar(Char::titlecase) }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        freqSpinner.setSelection(
            frequencies.indexOf(AiFrequency.fromPref(prefs.getString(PrefsKeys.AI_FREQUENCY, null)))
                .coerceAtLeast(0)
        )

        val personalities = AiPersonality.entries
        personalitySpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            personalities.map { it.name.lowercase().replaceFirstChar(Char::titlecase) }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        personalitySpinner.setSelection(
            personalities.indexOf(AiPersonality.fromPref(prefs.getString(PrefsKeys.AI_PERSONALITY, null)))
                .coerceAtLeast(0)
        )

        var aiVolume = prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100)
        fun renderVolume(v: Int) {
            aiVolumeValue.text = getString(R.string.settings_ai_volume_value, v)
        }
        aiVolumeSeek.progress = aiVolume
        renderVolume(aiVolume)
        aiVolumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                aiVolume = progress
                renderVolume(aiVolume)
                prefs.edit { putInt(PrefsKeys.AI_VOLUME, aiVolume) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val switchOnlineVoice = view.findViewById<SwitchMaterial>(R.id.switchOnlineVoice)
        val switchOnlineText = view.findViewById<SwitchMaterial>(R.id.switchOnlineText)
        val onlineAiContent = view.findViewById<View>(R.id.onlineAiContent)

        fun renderOnlineVisibility() {
            onlineAiContent.visibility =
                if (switchOnlineAi.isChecked) View.VISIBLE else View.GONE
        }

        // Normalize once: the two assistants are mutually exclusive, so a
        // legacy state with both on settles to Offline ON / Online OFF.
        val offlineOn = prefs.getBoolean(PrefsKeys.OFFLINE_AI_ENABLED, true)
        var onlineOn = prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)
        if (offlineOn && onlineOn) {
            onlineOn = false
            prefs.edit { putBoolean(PrefsKeys.ONLINE_AI_ENABLED, false) }
        }
        switchOfflineAi.isChecked = offlineOn
        switchOnlineAi.isChecked = onlineOn
        switchOnlineVoice.isChecked = prefs.getBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, true)
        switchOnlineText.isChecked = prefs.getBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, true)
        renderOfflineVisibility()
        renderOnlineVisibility()
        renderThresholdStates()

        switchOfflineAi.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            val (newOffline, newOnline) = AiSwitchRules.resolveAiSwitches(
                AiSwitchRules.AiSystem.OFFLINE, isChecked, isChecked, switchOnlineAi.isChecked
            )
            prefs.edit {
                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, newOffline)
                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, newOnline)
            }
            updatingSwitches = true
            switchOfflineAi.isChecked = newOffline
            switchOnlineAi.isChecked = newOnline
            updatingSwitches = false
            renderOfflineVisibility()
            renderOnlineVisibility()
        }
        switchOnlineAi.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            val (newOffline, newOnline) = AiSwitchRules.resolveAiSwitches(
                AiSwitchRules.AiSystem.ONLINE, isChecked, switchOfflineAi.isChecked, isChecked
            )
            prefs.edit {
                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, newOffline)
                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, newOnline)
            }
            updatingSwitches = true
            switchOfflineAi.isChecked = newOffline
            switchOnlineAi.isChecked = newOnline
            updatingSwitches = false
            renderOfflineVisibility()
            renderOnlineVisibility()
        }

        fun applyOnlineOutputs(fromVoice: Boolean) {
            val (voiceOn, textOn) = AiSwitchRules.enforceOnlineOutput(
                fromVoice, switchOnlineVoice.isChecked, switchOnlineText.isChecked
            )
            prefs.edit {
                putBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, voiceOn)
                putBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, textOn)
            }
            updatingSwitches = true
            switchOnlineVoice.isChecked = voiceOn
            switchOnlineText.isChecked = textOn
            updatingSwitches = false
        }
        switchOnlineVoice.setOnCheckedChangeListener { _, _ ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            applyOnlineOutputs(fromVoice = true)
        }
        switchOnlineText.setOnCheckedChangeListener { _, _ ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            applyOnlineOutputs(fromVoice = false)
        }

        fun renderApiStatus() {
            val service = AiService(requireContext())
            val config = service.getConfig()
            aiApiStatus.text = if (service.hasApiKey()) {
                getString(
                    R.string.settings_ai_status_ok,
                    config.provider.displayName,
                    config.model.ifEmpty { config.provider.defaultModel }
                )
            } else {
                getString(R.string.settings_ai_status_missing)
            }
        }
        renderApiStatus()

        saveButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val selectedSpeedSource = if (radioGroupSpeedSource.checkedRadioButtonId == R.id.radioButtonGps) PrefsKeys.SPEED_SOURCE_DEVICE else PrefsKeys.SPEED_SOURCE_OBD
            val selectedProvider = providers[providerSpinner.selectedItemPosition]
            val apiKey = apiKeyEditText.text.toString().trim()
            prefs.edit {
                putString(PrefsKeys.AI_PROVIDER, selectedProvider.id)
                putString(PrefsKeys.OPENAI_API_KEY, apiKey)
                putString(PrefsKeys.OPENAI_MODEL_ID, modelIdEditText.text.toString().trim())
                putString(PrefsKeys.AI_BASE_URL, baseUrlEditText.text.toString().trim())
                putString(PrefsKeys.SPEED_SOURCE, selectedSpeedSource)
                putString(PrefsKeys.SPEEDOMETER_STYLE, selectedStyle.id)
                putBoolean(PrefsKeys.OFFLINE_AI_ENABLED, switchOfflineAi.isChecked)
                putBoolean(PrefsKeys.OFFLINE_TEXT_ALERTS, switchOfflineText.isChecked)
                putInt(PrefsKeys.OFFLINE_VOLUME, getOfflineVolume())
                putBoolean(PrefsKeys.SHIFT_POINT_ENABLED, switchShiftPoint.isChecked)
                putInt(PrefsKeys.SHIFT_RPM, getShiftRpm())
                putBoolean(PrefsKeys.HIGH_RPM_ENABLED, switchHighRpm.isChecked)
                putInt(PrefsKeys.HIGH_RPM_THRESHOLD, getHighRpm())
                putBoolean(PrefsKeys.HIGH_SPEED_ENABLED, switchHighSpeed.isChecked)
                putInt(PrefsKeys.HIGH_SPEED_THRESHOLD, getHighSpeed())
                putBoolean(PrefsKeys.OFFLINE_COOLANT_ENABLED, switchCoolantAlert.isChecked)
                putInt(PrefsKeys.OFFLINE_COOLANT_THRESHOLD, getCoolantThreshold())
                putBoolean(PrefsKeys.NEW_FAULT_ENABLED, switchNewFault.isChecked)
                putBoolean(PrefsKeys.CONN_LOST_ENABLED, switchConnLost.isChecked)
                putBoolean(PrefsKeys.CONN_RESTORED_ENABLED, switchConnRestored.isChecked)
                putBoolean(PrefsKeys.ENGINE_START_ENABLED, switchEngineStart.isChecked)
                putBoolean(PrefsKeys.ENGINE_STOP_ENABLED, switchEngineStop.isChecked)
                putBoolean(PrefsKeys.EASTER_EGGS_ENABLED, switchEasterEggs.isChecked)
                putBoolean(PrefsKeys.ONLINE_AI_ENABLED, switchOnlineAi.isChecked)
                putBoolean(PrefsKeys.ONLINE_VOICE_ALERTS, switchOnlineVoice.isChecked)
                putBoolean(PrefsKeys.ONLINE_TEXT_ALERTS, switchOnlineText.isChecked)
                putString(PrefsKeys.AI_FREQUENCY, frequencies[freqSpinner.selectedItemPosition].prefValue)
                putString(PrefsKeys.AI_PERSONALITY, personalities[personalitySpinner.selectedItemPosition].prefValue)
                putInt(PrefsKeys.AI_VOLUME, aiVolumeSeek.progress)
            }
            renderApiStatus()
            if (selectedProvider.needsKey && apiKey.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.settings_api_key_missing, selectedProvider.displayName),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.buttonTestVoice).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            testOnlineAiVoice()
        }

        view.findViewById<Button>(R.id.buttonTtsSettings).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openSystemTtsSettings()
        }

        view.findViewById<Button>(R.id.buttonTestOfflineVoice).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            testOfflineAiVoice()
        }

        view.findViewById<Button>(R.id.buttonOfflineTtsSettings).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            openSystemTtsSettings()
        }

        // -- Accordion: one expanded group at a time, animated ------------------
        // Headers are button-like (icon + title + live summary + chevron);
        // contents reuse the exact controls wired above.
        val groups = listOf(
            Triple(R.id.headerGeneral, R.id.contentGeneral, R.id.chevronGeneral),
            Triple(R.id.headerDriving, R.id.contentDriving, R.id.chevronDriving),
            Triple(R.id.headerSpeedo, R.id.contentSpeedo, R.id.chevronSpeedo),
            Triple(R.id.headerOfflineAi, R.id.contentOfflineAi, R.id.chevronOfflineAi),
            Triple(R.id.headerOnlineAi, R.id.contentOnlineAi, R.id.chevronOnlineAi)
        )
        var expandedGroup = -1

        fun stateText(on: Boolean) = getString(
            if (on) R.string.settings_state_on else R.string.settings_state_off
        )

        updateSummaries = {
            val themeName = when ((activity as MainActivity).currentThemeMode(prefs)) {
                ThemeMode.LIGHT -> getString(R.string.settings_theme_light)
                ThemeMode.DARK -> getString(R.string.settings_theme_dark)
                else -> getString(R.string.settings_theme_system)
            }
            val langName =
                AppLanguage.fromTag(prefs.getString(PrefsKeys.APP_LANGUAGE, null)).displayName
            view.findViewById<TextView>(R.id.summaryGeneral).text = "$themeName · $langName"
            view.findViewById<TextView>(R.id.summaryDriving).text =
                getString(
                    if (Units.isImperial(prefs)) R.string.settings_units_imperial
                    else R.string.settings_units_metric
                )
            view.findViewById<TextView>(R.id.summarySpeedo).text =
                getString(selectedStyle.titleRes)
            view.findViewById<TextView>(R.id.summaryOfflineAi).text =
                stateText(switchOfflineAi.isChecked)
            view.findViewById<TextView>(R.id.summaryOnlineAi).text =
                stateText(switchOnlineAi.isChecked)
        }

        fun setGroupExpanded(index: Int, expand: Boolean, animate: Boolean) {
            val (_, contentId, chevronId) = groups[index]
            if (animate) {
                TransitionManager.beginDelayedTransition(
                    view.findViewById(R.id.settingsGroups),
                    AutoTransition().apply { duration = 220 }
                )
            }
            view.findViewById<View>(contentId).visibility =
                if (expand) View.VISIBLE else View.GONE
            view.findViewById<ImageView>(chevronId).animate()
                .rotation(if (expand) 90f else 0f).setDuration(220).start()
        }

        for ((index, triple) in groups.withIndex()) {
            view.findViewById<View>(triple.first).setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                updateSummaries()
                if (expandedGroup == index) {
                    setGroupExpanded(index, false, animate = true)
                    expandedGroup = -1
                } else {
                    val prev = expandedGroup
                    expandedGroup = index
                    if (prev >= 0) setGroupExpanded(prev, false, animate = true)
                    setGroupExpanded(index, true, animate = true)
                }
            }
        }
        updateSummaries()
        setGroupExpanded(0, expand = true, animate = false)
        expandedGroup = 0
    }

    /** Opens the Android system text-to-speech screen (engine, voices, rate). */
    private fun openSystemTtsSettings() {
        runCatching {
            // System text-to-speech screen (engine, language, voices).
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        }.onFailure { e ->
            Log.e("SettingsFragment", "Cannot open system TTS settings", e)
            Toast.makeText(
                context,
                getString(R.string.ai_test_failed, e.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var testTts: android.speech.tts.TextToSpeech? = null
    private var testOfflineTts: android.speech.tts.TextToSpeech? = null

    /** Speaks one sample line with the device voice (the only Online AI voice). */
    private fun testOnlineAiVoice() {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
            Toast.makeText(context, R.string.ai_test_off, Toast.LENGTH_SHORT).show()
            return
        }
        val line = getString(R.string.ai_test_line)
        stopTestVoice()
        val volume = prefs.getInt(PrefsKeys.AI_VOLUME, PrefsKeys.DEFAULT_AI_VOLUME).coerceIn(0, 100) / 100f
        val params = Bundle().apply {
            putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        testTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                runCatching { testTts?.language = java.util.Locale.getDefault() }
                testTts?.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test")
            } else if (isAdded) {
                Toast.makeText(
                    context,
                    getString(R.string.ai_test_failed, "TTS engine"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        Toast.makeText(context, R.string.ai_test_ok_device, Toast.LENGTH_SHORT).show()
    }

    private fun stopTestVoice() {
        runCatching { testTts?.stop() }
        runCatching { testOfflineTts?.stop() }
    }

    /** Speaks one sample line with the Offline AI device voice + volume. */
    private fun testOfflineAiVoice() {
        val prefs = requireActivity().getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsKeys.MUTE_SOUND, false)) {
            Toast.makeText(context, R.string.ai_test_off, Toast.LENGTH_SHORT).show()
            return
        }
        val line = getString(R.string.offline_ai_test_line)
        stopTestVoice()
        val volume = prefs.getInt(PrefsKeys.OFFLINE_VOLUME, PrefsKeys.DEFAULT_OFFLINE_VOLUME)
            .coerceIn(0, 100) / 100f
        val params = Bundle().apply {
            putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        testOfflineTts = android.speech.tts.TextToSpeech(requireContext()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                runCatching { testOfflineTts?.language = java.util.Locale.getDefault() }
                testOfflineTts?.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test-offline")
            } else if (isAdded) {
                Toast.makeText(
                    context,
                    getString(R.string.ai_test_failed, "TTS engine"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        Toast.makeText(context, R.string.ai_test_ok_device, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTestVoice()
        runCatching { testTts?.shutdown() }
        testTts = null
        runCatching { testOfflineTts?.shutdown() }
        testOfflineTts = null
    }
}
