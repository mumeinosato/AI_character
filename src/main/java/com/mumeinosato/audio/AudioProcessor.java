package com.mumeinosato.audio;

import com.mumeinosato.gemini.SessionManager;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
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

@Component
public class AudioProcessor {
    private static final Logger logger = LogManager.getLogger(AudioProcessor.class);

    @Value("${tts_server.url}")
    private String ttsServerUrl;

    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private AudioQueueManager audioQueueManager;

    public void processAudio(final String guildId, String userId, final byte[] audioData) {
        logger.info("Processing complete speech from user: {} (data size: {} bytes)", userId, audioData.length);

        try {
            final var audioDir = new File("audio");
            if (!audioDir.exists()) {
                boolean created = audioDir.mkdirs();
                if (!created) {
                    logger.warn("Failed to create audio directory");
                }
            }

            final var convertedPcmData = convertAudioFormat(convertPcmToWav(audioData));
            audioQueueManager.enqueueGemini(convertedPcmData);

            //String sent = sessionManager.sendAudioData(guildId, convertedPcmData);
            //if (sent != null) {
            //    logger.info("Audio data sent to Gemini for guild: {}", guildId);
            //} else {
            //    logger.warn("Failed to send audio data to Gemini for guild: {}", guildId);
            //    return null;
            //}

            // ここでWAVデータを使って音声認識や合成を行う
            //return callTTSApi(sent);

            return;

        } catch (final IOException e) {
            logger.error("Error processing audio for user {}: {}", userId, e.getMessage(), e);
            return;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] convertAudioFormat(byte[] discordWavData) throws IOException, InterruptedException {
        Path inputFile = Files.createTempFile("discord", ".wav");
        Path outputFile = Files.createTempFile("gemini", ".pcm");

        try {
            Files.write(inputFile, discordWavData);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", inputFile.toString(),
                    "-f", "s16le",
                    "-ar", "16000",
                    "-ac", "1",
                    outputFile.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder ffmpegOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) ffmpegOutput.append(line).append("\n");
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

    public byte[] callTTSApi(String text) {
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

    private byte[] convertPcmToWav(final byte[] pcmData) throws IOException {

        try (var wavOutputStream = new ByteArrayOutputStream();
             var audioInputStream = new AudioInputStream(
                     new ByteArrayInputStream(pcmData),
                     AudioSendHandler.INPUT_FORMAT,
                     pcmData.length)) {

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavOutputStream);
            return wavOutputStream.toByteArray();
        }
    }
}
