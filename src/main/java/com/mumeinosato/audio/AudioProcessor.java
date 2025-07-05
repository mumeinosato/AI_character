package com.mumeinosato.audio;

import com.mumeinosato.gemini.SessionManager;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.apache.http.impl.client.DefaultHttpClient;
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

@Component
public class AudioProcessor {
    private static final Logger logger = LogManager.getLogger(AudioProcessor.class);

    @Value("${tts_server.url}")
    private String ttsServerUrl;

    @Autowired
    private SessionManager sessionManager;

    public byte[] processAudio(final String guildId, String userId, final byte[] audioData) {
        logger.info("Processing complete speech from user: {} (data size: {} bytes)", userId, audioData.length);

        try {
            final var audioDir = new File("audio");
            if (!audioDir.exists()) {
                boolean created = audioDir.mkdirs();
                if (!created) {
                    logger.warn("Failed to create audio directory");
                }
            }

            // PCMデータを増幅
            final var amplifiedPcmData = amplifyPcmData(audioData, 1.0); // n倍に増幅
            final var convertedPcmData = convertAudioFormat(convertPcmToWav(amplifiedPcmData));


            String sent = sessionManager.sendAudioData(guildId, convertedPcmData);
            if (sent != null) {
                logger.info("Audio data sent to Gemini for guild: {}", guildId);
            } else {
                logger.warn("Failed to send audio data to Gemini for guild: {}", guildId);
                return null;
            }


            // ここでWAVデータを使って音声認識や合成を行う
            return callTTSApi(sent);

        } catch (final IOException e) {
            logger.error("Error processing audio for user {}: {}", userId, e.getMessage(), e);
            return null;
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

    private byte[] convertPcmToWav(final byte[] pcmData) throws IOException {

        try (
                var wavOutputStream = new ByteArrayOutputStream();
                var audioInputStream = new AudioInputStream(
                        new ByteArrayInputStream(pcmData),
                        AudioSendHandler.INPUT_FORMAT,
                        pcmData.length)) {

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavOutputStream);
            return wavOutputStream.toByteArray();
        }
    }

    private byte[] amplifyPcmData(final byte[] pcmData, final double factor) {
        if (pcmData == null || pcmData.length == 0) {
            logger.warn("PCM data is null or empty");
            return pcmData;
        }

        logger.debug("Amplifying PCM data: size={} bytes, factor={}", pcmData.length, factor);

        byte[] amplifiedData = new byte[pcmData.length];
        int bytesPerSample = 2;

        for (int i = 0; i < pcmData.length; i += bytesPerSample) {
            if (i + 1 < pcmData.length) {
                int sample = (pcmData[i] & 0xFF) | ((pcmData[i + 1] & 0xFF) << 8);

                if (sample > 32767)
                    sample -= 65536;

                sample = (int) (sample * factor);
                sample = Math.max(-32768, Math.min(32767, sample));

                if (sample < 0)
                    sample += 65536;

                amplifiedData[i] = (byte) (sample & 0xFF);
                amplifiedData[i + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }

        logger.debug("PCM amplification completed");
        return amplifiedData;
    }

}
