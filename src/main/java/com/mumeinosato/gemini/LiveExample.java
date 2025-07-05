package com.mumeinosato.gemini;

import com.google.genai.AsyncSession;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.*;

@Component
public final class LiveExample {

    private static final Logger logger = LogManager.getLogger(LiveExample.class);

    private static final AudioFormat MIC_AUDIO_FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private static final AudioFormat SPEAKER_AUDIO_FORMAT = new AudioFormat(24000.0f, 16, 1, true, false);
    private static final int CHUNK_SIZE = 4096; // 4KB

    private static volatile boolean running = true;
    private static volatile boolean speakerPlaying = false;
    private static TargetDataLine microphoneLine;
    private static AsyncSession session;
    private static ExecutorService micExecutor = Executors.newSingleThreadExecutor();

    @Value("${gemini.key}")
    private String apiKey;

    public static LiveSendRealtimeInputParameters createAudioContent(byte[] audioData) {
        if (audioData == null) {
            System.err.println("Audio data is null");
            return null;
        }

        return LiveSendRealtimeInputParameters.builder().media(Blob.builder().mimeType("audio/pcm").data(audioData)).build();
    }

    private static void sendMicrophoneAudio() {
        byte[] buffer = new byte[CHUNK_SIZE];
        int bytesRead;

        while (running && microphoneLine != null && microphoneLine.isOpen()) {
            bytesRead = microphoneLine.read(buffer, 0, buffer.length);

            if (bytesRead > 0 && !speakerPlaying) {
                byte[] audioChunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);

                if (session != null) session.sendRealtimeInput(createAudioContent(audioChunk)).exceptionally(e -> {
                    System.err.println(e.getMessage());
                    return null;
                });
            } else if (bytesRead == -1) {
                System.err.println("Microphone audio ended unexpectedly");
                running = false;
            }
        }
        System.out.println("Microphone audio sending stopped");
    }

    // mainメソッドの処理をインスタンスメソッドに移動
    public void run(String[] args) throws LineUnavailableException {
        System.setProperty("GOOGLE_API_KEY", apiKey);
        System.setProperty("GOOGLE_GENAI_USE_VERTEXAI", "false");
        Client client = new Client();

        String modelId = "gemini-2.0-flash-live-001";
        if (args != null && args.length != 0) modelId = args[0];

        microphoneLine = getMicrophoneLine();

        LiveConnectConfig config = LiveConnectConfig.builder().responseModalities(Modality.Known.TEXT).build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            running = false; // Signal mic thread to stop
            micExecutor.shutdown();
            try {
                if (!micExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Mic executor did not terminate gracefully.");
                    micExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                micExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Close session first
            if (session != null) {
                try {
                    System.out.println("Closing API session...");
                    session.close().get(5, TimeUnit.SECONDS); // Wait with timeout
                    System.out.println("API session closed.");
                } catch (Exception e) {
                    System.err.println("Error closing API session: " + e.getMessage());
                }
            }
            // Close audio lines
            closeAudioLine(microphoneLine);
            System.out.println("Audio lines closed.");
        }));

        try {
            // --- Connect to Gemini Live API ---
            System.out.println("Connecting to Gemini Live API...");

            session = client.async.live.connect(modelId, config).get();
            System.out.println("Connected.");

            // --- Start Audio Lines ---
            microphoneLine.start();
            System.out.println("Microphone and speakers started. Speak now (Press Ctrl+C to exit)...");

            // --- Start Receiving Audio Responses ---
            CompletableFuture<Void> receiveFuture =
                    session.receive(LiveExample::handleTextResponse);
            System.err.println("Receive stream started."); // Add this line

            // --- Start Sending Microphone Audio ---
            CompletableFuture<Void> sendFuture =
                    CompletableFuture.runAsync(LiveExample::sendMicrophoneAudio, micExecutor);

            // Keep the main thread alive. Wait for sending or receiving to finish (or
            // error).
            // In this continuous streaming case, we rely on the shutdown hook triggered by
            // Ctrl+C.
            // We can wait on the futures, but they might not complete normally in this
            // design.
            CompletableFuture.anyOf(receiveFuture, sendFuture)
                    .handle(
                            (res, err) -> {
                                if (err != null) {
                                    System.err.println("An error occurred in sending/receiving: " + err.getMessage());
                                    // Trigger shutdown if needed
                                    System.exit(1);
                                }
                                return null;
                            })
                    .get(); // Wait indefinitely or until an error occurs in send/receive

        } catch (InterruptedException | ExecutionException e) {
            System.err.println("An error occurred during setup or connection: " + e.getMessage());
            logger.error("An error occurred during setup or connection");
            System.exit(1);
        }
    }

    private static TargetDataLine getMicrophoneLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, MIC_AUDIO_FORMAT);
        if (!AudioSystem.isLineSupported(info)) throw new LineUnavailableException("Microphone line not supported");

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(MIC_AUDIO_FORMAT);
        System.out.println("Microphone line opened");
        return line;
    }

    private static SourceDataLine getSpeakerLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, SPEAKER_AUDIO_FORMAT);

        if (!AudioSystem.isLineSupported(info)) throw new LineUnavailableException("Speaker line not supported");

        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(SPEAKER_AUDIO_FORMAT);
        System.out.println("Speaker line opened");
        return line;
    }

    private static void closeAudioLine(Line line) {
        if (line != null && line.isOpen()) line.close();
    }

    public static void handleTextResponse(LiveServerMessage message) {
        message.serverContent().ifPresent(content -> {
            if (content.turnComplete().orElse(false)) {
                System.out.println("\n--- Turn Complete ---");
            } else {
                // ←テキスト出力
                // ここでTTS処理を追加することも可能
                content.modelTurn().stream()
                        .flatMap(modelTurn -> modelTurn.parts().stream())
                        .flatMap(Collection::stream)
                        .map(Part::text)  // ←テキスト取得
                        .flatMap(Optional::stream)
                        .forEach(System.out::print);
            }
        });
    }

    private LiveExample() {
    }
}
