package com.mumeinosato.audio;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;


public class AudioProcessor {
    private static final Logger logger = LogManager.getLogger(AudioProcessor.class);

    public byte[] processAudio(final String userId, final byte[] audioData) {
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

            // 増幅されたPCMファイルの保存
            final var pcmFile = new File(audioDir, userId + "_" + System.currentTimeMillis() + ".pcm");
            try (var fos = new FileOutputStream(pcmFile)) {
                fos.write(amplifiedPcmData);
                logger.debug("Saved amplified PCM file: {}", pcmFile.getName());
            } catch (IOException e) {
                logger.error("Failed to write PCM data for user {}: {}", userId, e.getMessage(), e);
            }

            // 増幅されたPCMをWAVに変換
            final var wavData = this.convertPcmToWav(amplifiedPcmData);
            if (wavData == null || wavData.length == 0) {
                logger.error("Failed to convert PCM to WAV for user: {}", userId);
                return generateResponseAudio(userId);
            }

            // WAVファイルの保存
            final var wavFile = new File(audioDir, userId + "_" + System.currentTimeMillis() + ".wav");
            try (var fos = new FileOutputStream(wavFile)) {
                fos.write(wavData);
                logger.info("Saved WAV file: {} (size: {} bytes)", wavFile.getName(), wavData.length);
            } catch (IOException e) {
                logger.error("Failed to write WAV data for user {}: {}", userId, e.getMessage(), e);
            }

            // ここでWAVデータを使って音声認識や合成を行う
            return generateResponseAudio(userId);

        } catch (final IOException e) {
            logger.error("Error processing audio for user {}: {}", userId, e.getMessage(), e);
            return generateResponseAudio(userId); // エラー時も応答を返す
        }
    }

    private byte[] generateResponseAudio(String userId) {
        // TODO: 実際の音声合成処理をここに実装
        logger.info("Generating response audio for user: {}", userId);

        try {
            File testAudioFile = new File("audio/114514.wav");
            if (testAudioFile.exists()) {
                logger.info("Playing test audio file: {}", testAudioFile.getAbsolutePath());
                try (FileInputStream fis = new FileInputStream(testAudioFile);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }

                    return baos.toByteArray();
                }
            } else {
                logger.warn("Test audio file not found: {}, generating silent audio instead", testAudioFile.getAbsolutePath());
                // ファイルが見つからない場合は無音データを返す
                return null;
            }
        } catch (IOException e) {
            logger.error("Failed to read test audio file: {}", e.getMessage(), e);
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
