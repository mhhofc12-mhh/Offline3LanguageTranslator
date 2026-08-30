# Offline 3 Language Translator

Languages:
- Bengali (bn)
- English (en)
- Arabic (ar)

Features:
- Translate from any of the three boxes to the other two.
- Copy.
- Text-to-speech.
- Voice input when the device's speech recognizer supports it.
- On-device ML Kit translation.

Important:
1. ML Kit translation models are downloaded on first setup. After the models are present, translation runs on-device.
2. This project requests the models over Wi-Fi at first launch.
3. Android speech recognition is device/service dependent and may not be fully offline on every phone.
4. Offline TTS requires the corresponding language voice data to be installed on the phone.

Build:
- Open this project in Android Studio.
- Let Gradle sync.
- Build > Build APK(s).

The ML Kit translation dependency is com.google.mlkit:translate:17.0.3.
