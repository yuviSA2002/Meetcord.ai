Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.SetOutputToWaveFile(".\app\src\main\res\raw\sample.wav", 
    (New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)))
$synth.Speak("Hello! This is a test of the Meetcord AI transcription system. I am a virtual assistant feeding audio directly into the application because the emulator microphone is silent. Let's see if the AI can transcribe and summarize this perfectly.")
$synth.Dispose()
