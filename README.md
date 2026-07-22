<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="Meetcord Logo" width="120" />
  <h1>Meetcord.ai</h1>
  <p><b>AI Meeting Transcriber & Summarizer for Android.</b></p>
</div>

Meetcord is an Android application designed to record, transcribe, and summarize your meetings. It leverages C++ bindings for [Whisper.cpp](https://github.com/ggerganov/whisper.cpp) to run the transcription engine entirely on your device. For summarization, it connects to Cloud LLMs using your own API keys (OpenAI/Gemini).

---

## 📸 Screenshots

| Home Screen | Recording | Meeting Summary |
| :---: | :---: | :---: |
| <img src="screenshot1.png" width="250" alt="Home Screen"/> | <img src="screenshot2.png" width="250" alt="Recording Interface"/> | <img src="screenshot3.png" width="250" alt="Meeting Summary"/> |

---

## ✨ Features

- **On-Device Transcription:** Flawless transcription powered by Whisper.cpp natively running via JNI directly on your phone.
- **Cloud Summarization:** Extracts key points and action items using your own API keys (OpenAI, Gemini).
- **Product Flavors (Lite vs. Pro):** Easily compile the blazing-fast `Base` model or the highly accurate `Quantized Small` model.
- **Unlimited Recording:** Processes audio in 30-second sliding windows, meaning it can transcribe 1-hour+ meetings without crashing or overflowing RAM.
- **Modern UI:** Built fully in Jetpack Compose with Material 3 styling and dynamic animations.

---

## 🛠️ How to Build & Run Locally

Due to GitHub's file size limits, the large AI models are ignored via `.gitignore` and are not included in this repository. You must download them manually before compiling the app.

### 1. Clone the Repository
```bash
git clone https://github.com/yuviSA2002/Meetcord.ai.git
cd Meetcord.ai
```

### 2. Download the AI Models
You need to place the Whisper model files into the `app/src/main/assets/` directory.

- **Lite Version (147MB):** Download `ggml-base.en.bin`
- **Pro Version (190MB):** Download `ggml-small.en-q5_1.bin`

You can download them directly from huggingface:
```bash
# Inside the Meetcord.ai project root:
curl -L -o app/src/main/assets/ggml-base.en.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin
curl -L -o app/src/main/assets/ggml-small.en-q5_1.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en-q5_1.bin
```

### 3. Open in Android Studio
1. Open Android Studio and select **Open**.
2. Navigate to the cloned `Meetcord.ai` folder.
3. Wait for Gradle to finish syncing.

### 4. Choose Your Flavor
Meetcord uses Android **Product Flavors** to juggle the different models. 
- Open the **Build Variants** panel in the bottom-left corner of Android Studio.
- To build the fast version, select `liteDebug`.
- To build the highly accurate version, select `proDebug`.
- Hit **Run**!

---

## 🏗️ Tech Stack

- **UI:** Jetpack Compose, Material 3
- **Language:** Kotlin & C++
- **Audio Engine:** `whisper.cpp` (via JNI)
- **Local Database:** Room Database

---

## 📜 License

This project is open-source. Feel free to fork, modify, and build upon it!
