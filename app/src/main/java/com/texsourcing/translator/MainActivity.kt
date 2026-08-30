package com.texsourcing.translator

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.texsourcing.translator.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityMainBinding
    private var activeBox: EditText? = null
    private var tts: TextToSpeech? = null
    private var busy = false

    private val bnToEn by lazy { makeTranslator("bn", "en") }
    private val bnToAr by lazy { makeTranslator("bn", "ar") }
    private val enToBn by lazy { makeTranslator("en", "bn") }
    private val enToAr by lazy { makeTranslator("en", "ar") }
    private val arToBn by lazy { makeTranslator("ar", "bn") }
    private val arToEn by lazy { makeTranslator("ar", "en") }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    activeBox?.setText(text)
                    activeBox?.setSelection(text.length)
                    translateFrom(activeBox)
                }
            }
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startSpeech() else toast("Microphone permission is required.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tts = TextToSpeech(this, this)
        setupListeners()
        prepareModels()
    }

    private fun setupListeners() {
        binding.banglaInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) activeBox = binding.banglaInput }
        binding.englishInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) activeBox = binding.englishInput }
        binding.arabicInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) activeBox = binding.arabicInput }

        binding.banglaInput.setOnEditorActionListener { _, _, _ -> translateFrom(binding.banglaInput); false }
        binding.englishInput.setOnEditorActionListener { _, _, _ -> translateFrom(binding.englishInput); false }
        binding.arabicInput.setOnEditorActionListener { _, _, _ -> translateFrom(binding.arabicInput); false }

        binding.banglaInput.setOnFocusChangeListener { _, has -> if (has) activeBox = binding.banglaInput }
        binding.englishInput.setOnFocusChangeListener { _, has -> if (has) activeBox = binding.englishInput }
        binding.arabicInput.setOnFocusChangeListener { _, has -> if (has) activeBox = binding.arabicInput }

        binding.banglaInput.setOnKeyListener { _, _, _ -> false }
        binding.englishInput.setOnKeyListener { _, _, _ -> false }
        binding.arabicInput.setOnKeyListener { _, _, _ -> false }

        binding.bnVoice.setOnClickListener { voiceFor(binding.banglaInput) }
        binding.enVoice.setOnClickListener { voiceFor(binding.englishInput) }
        binding.arVoice.setOnClickListener { voiceFor(binding.arabicInput) }

        binding.bnSpeak.setOnClickListener { speak(binding.banglaInput.text.toString(), "bn") }
        binding.enSpeak.setOnClickListener { speak(binding.englishInput.text.toString(), "en") }
        binding.arSpeak.setOnClickListener { speak(binding.arabicInput.text.toString(), "ar") }

        binding.bnCopy.setOnClickListener { copy(binding.banglaInput.text.toString()) }
        binding.enCopy.setOnClickListener { copy(binding.englishInput.text.toString()) }
        binding.arCopy.setOnClickListener { copy(binding.arabicInput.text.toString()) }

        binding.swapButton.setOnClickListener {
            val a = binding.banglaInput.text.toString()
            val b = binding.englishInput.text.toString()
            binding.banglaInput.setText(b)
            binding.englishInput.setText(a)
            if (b.isNotBlank()) translateFrom(binding.banglaInput)
        }

        binding.clearButton.setOnClickListener {
            binding.banglaInput.text.clear()
            binding.englishInput.text.clear()
            binding.arabicInput.text.clear()
        }

        // Translate after a short pause while typing.
        val watcher = object : android.text.TextWatcher {
            private var last: EditText? = null
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) {
                val v = when {
                    binding.banglaInput.hasFocus() -> binding.banglaInput
                    binding.englishInput.hasFocus() -> binding.englishInput
                    binding.arabicInput.hasFocus() -> binding.arabicInput
                    else -> null
                }
                if (v != null && v !== last) last = v
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                val v = when {
                    binding.banglaInput.hasFocus() -> binding.banglaInput
                    binding.englishInput.hasFocus() -> binding.englishInput
                    binding.arabicInput.hasFocus() -> binding.arabicInput
                    else -> null
                } ?: return
                v.postDelayed({ if (v.text.toString().isNotBlank()) translateFrom(v) }, 450)
            }
        }
        binding.banglaInput.addTextChangedListener(watcher)
        binding.englishInput.addTextChangedListener(watcher)
        binding.arabicInput.addTextChangedListener(watcher)
    }

    private fun makeTranslator(source: String, target: String): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.fromLanguageTag(source)!!)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(target)!!)
            .build()
        return Translation.getClient(options)
    }

    private fun prepareModels() {
        binding.statusText.text = "Preparing offline translation models…"
        val translators = listOf(bnToEn, bnToAr, enToBn, enToAr, arToBn, arToEn)
        val conditions = DownloadConditions.Builder().requireWifi().build()
        var remaining = translators.size
        translators.forEach { t ->
            t.downloadModelIfNeeded(conditions)
                .addOnCompleteListener {
                    remaining--
                    if (remaining == 0) {
                        binding.statusText.text = "Offline translation ready"
                    }
                }
        }
    }

    private fun translateFrom(box: EditText?) {
        if (busy || box == null) return
        val text = box.text.toString().trim()
        if (text.isEmpty()) return
        busy = true

        when (box.id) {
            binding.banglaInput.id -> {
                translate(bnToEn, text) { binding.englishInput.setText(it) }
                translate(bnToAr, text) { binding.arabicInput.setText(it); busy = false }
            }
            binding.englishInput.id -> {
                translate(enToBn, text) { binding.banglaInput.setText(it) }
                translate(enToAr, text) { binding.arabicInput.setText(it); busy = false }
            }
            binding.arabicInput.id -> {
                translate(arToBn, text) { binding.banglaInput.setText(it) }
                translate(arToEn, text) { binding.englishInput.setText(it); busy = false }
            }
        }
    }

    private fun translate(t: Translator, text: String, done: (String) -> Unit) {
        t.translate(text)
            .addOnSuccessListener { done(it) }
            .addOnFailureListener {
                binding.statusText.text = "Model not ready. Connect to Wi‑Fi once to download language models."
                busy = false
            }
    }

    private fun voiceFor(box: EditText) {
        activeBox = box
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else startSpeech()
    }

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, when (activeBox?.id) {
                binding.banglaInput.id -> "bn-BD"
                binding.englishInput.id -> "en-US"
                else -> "ar-SA"
            })
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        try { speechLauncher.launch(intent) } catch (_: Exception) {
            toast("Speech recognition is not available on this device.")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
        }
    }

    private fun speak(text: String, lang: String) {
        if (text.isBlank()) return
        val locale = when (lang) {
            "bn" -> Locale("bn", "BD")
            "ar" -> Locale("ar", "SA")
            else -> Locale.US
        }
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast("This language's offline voice is not installed on your phone.")
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translator")
    }

    private fun copy(text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Translation", text))
        toast("Copied")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        bnToEn.close(); bnToAr.close(); enToBn.close(); enToAr.close(); arToBn.close(); arToEn.close()
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }
}
