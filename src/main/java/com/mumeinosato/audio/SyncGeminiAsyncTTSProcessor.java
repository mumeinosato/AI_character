package com.mumeinosato.audio;

import com.mumeinosato.gemini.SessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Audio processor with synchronous Gemini calls and asynchronous TTS processing
 */
@Component
public class SyncGeminiAsyncTTSProcessor {
    private static final Logger logger = LogManager.getLogger(SyncGeminiAsyncTTSProcessor.class);
    
    @Value("${tts_server.url}")
    private String ttsServerUrl;
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private AudioInputQueue audioInputQueue;
    
    @Autowired
    private AudioPlaybackQueue audioPlaybackQueue;
    
    // Dedicated executor for async TTS processing only
    private final Executor ttsExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "TTSProcessor-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    /**
     * Process audio from input queue: sync Gemini call + async TTS
     */
    public void processNextAudio() {
        AudioInputQueue.AudioData audioData = audioInputQueue.dequeue();
        if (audioData == null) {
            return; // No audio to process
        }
        
        logger.info("Processing audio for user: {} (data size: {} bytes)", audioData.getUserId(), audioData.getData().length);
        
        try {
            // Step 1: Prepare audio data
            byte[] processedAudioData = prepareAudioData(audioData.getData());
            if (processedAudioData == null) {
                logger.warn("Failed to prepare audio data for user: {}", audioData.getUserId());
                return;
            }
            
            // Step 2: Send to Gemini (SYNCHRONOUS)
            logger.info("Sending audio to Gemini synchronously for user: {}", audioData.getUserId());
            String geminiResponse = sessionManager.sendAudioData(audioData.getGuildId(), processedAudioData);
            if (geminiResponse == null) {
                logger.warn("Failed to get response from Gemini for user: {}", audioData.getUserId());
                return;
            }
            logger.info("Received Gemini response for user: {}", audioData.getUserId());
            
            // Step 3: Process TTS asynchronously
            processTTSAsync(audioData.getUserId(), geminiResponse);
            
        } catch (Exception e) {
            logger.error("Error processing audio for user {}: {}", audioData.getUserId(), e.getMessage(), e);
        }
    }
    
    /**
     * Process TTS asynchronously and queue result
     */
    private void processTTSAsync(String userId, String geminiResponse) {
        CompletableFuture.runAsync(() -> {
            logger.info("Starting async TTS processing for user: {}", userId);
            
            try {
                // Convert to speech via TTS
                byte[] ttsAudioData = callTTSApi(geminiResponse);
                if (ttsAudioData == null) {
                    logger.warn("Failed to get TTS audio for user: {}", userId);
                    return;
                }
                
                // Queue for playback
                String base64AudioData = Base64.getEncoder().encodeToString(ttsAudioData);
                audioPlaybackQueue.enqueue(base64AudioData);
                
                logger.info("Successfully processed TTS and queued audio response for user: {}", userId);
                
            } catch (Exception e) {
                logger.error("Error in async TTS processing for user {}: {}", userId, e.getMessage(), e);
            }
        }, ttsExecutor);
    }
    
    private byte[] prepareAudioData(byte[] audioData) {
        try {
            // Create audio directory if needed
            File audioDir = new File("audio");
            if (!audioDir.exists()) {
                boolean created = audioDir.mkdirs();
                if (!created) {
                    logger.warn("Failed to create audio directory");
                }
            }
            
            // Process audio data (same as original AudioProcessor)
            byte[] amplifiedPcmData = amplifyPcmData(audioData, 1.0);
            return convertAudioFormat(convertPcmToWav(amplifiedPcmData));
            
        } catch (Exception e) {
            logger.error("Error preparing audio data: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private byte[] callTTSApi(String text) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            String base64Text = Base64.getEncoder().encodeToString(text.getBytes("UTF-8"));
            String url = ttsServerUrl + "?text=" + java.net.URLEncoder.encode(base64Text, "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                logger.info("TTS API call successful, response size: {} bytes", response.body().length);
                return response.body();
            } else {
                logger.error("TTS API call failed with status code: {}", response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error calling TTS API: {}", e.getMessage(), e);
            return null;
        }
    }
    
    // Copy of original methods from AudioProcessor
    private byte[] convertAudioFormat(byte[] discordWavData) throws IOException, InterruptedException {
        Path inputFile = Files.createTempFile("discord", ".wav");
        Path outputFile = Files.createTempFile("gemini", ".pcm");

        try {
            Files.write(inputFile, discordWavData);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", inputFile.toString(),
                    "-f", "s16le",
                    "-acodec", "pcm_s16le",
                    "-ar", "16000",
                    "-ac", "1",
                    outputFile.toString()
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read the output to prevent blocking
            StringBuilder ffmpegOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ffmpegOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("FFmpeg process failed with exit code {}: {}", exitCode, ffmpegOutput);
                return null;
            }

            return Files.readAllBytes(outputFile);
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }
    
    private byte[] convertPcmToWav(byte[] pcmData) throws IOException {
        try (ByteArrayOutputStream wavOutputStream = new ByteArrayOutputStream()) {
            // PCM format: 16-bit, mono, 48000 Hz (Discord format)
            int sampleRate = 48000;
            short bitsPerSample = 16;
            short channels = 2; // Discord uses stereo
            
            // Create a simple AudioInputStream for PCM data
            AudioInputStream audioInputStream = new AudioInputStream(
                    new ByteArrayInputStream(pcmData),
                    new javax.sound.sampled.AudioFormat(sampleRate, bitsPerSample, channels, true, false),
                    pcmData.length / (bitsPerSample / 8 * channels)
            );

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavOutputStream);
            return wavOutputStream.toByteArray();
        }
    }
    
    private byte[] amplifyPcmData(byte[] pcmData, double factor) {
        byte[] amplifiedData = new byte[pcmData.length];
        for (int i = 0; i < pcmData.length - 1; i += 2) {
            // Convert bytes to 16-bit signed integer (little-endian)
            short sample = (short) ((pcmData[i + 1] << 8) | (pcmData[i] & 0xFF));
            
            // Apply amplification
            sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample * factor));
            
            // Convert back to bytes
            amplifiedData[i] = (byte) (sample & 0xFF);
            amplifiedData[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return amplifiedData;
    }
}