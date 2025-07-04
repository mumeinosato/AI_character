package com.mumeinosato.gemini;

import com.google.genai.AsyncSession;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.mumeinosato.audio.AudioHandler;
import com.mumeinosato.audio.AudioProcessor;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class SessionManager {
    private static final Logger logger = LogManager.getLogger(SessionManager.class);

    private static final Map<String, GeminiSession> sessions = new ConcurrentHashMap<>();

    private static final Map<String, StringBuilder> responseBuffers = new ConcurrentHashMap<>();

    private final Map<String, CompletableFuture<String>> responseFutures = new ConcurrentHashMap<>();

    @Value("${gemini.key}")
    private String apiKey;

    private String prompt = "女子高校生,敬語ではなく砕けたかんじで,1~2文ぐらいで短く,会話がつながるようにして,絵文字は使わないで,アルファベットは読み上げられないから、カタカナにして";

    Content systemInstruction = Content.fromParts(Part.fromText(prompt));

    private static class GeminiSession {
        @Getter
        private final AsyncSession session;
        @Getter
        private final String guildId;
        @Getter
        @Setter
        private volatile boolean active;

        public GeminiSession(AsyncSession session, String guildId) {
            this.session = session;
            this.guildId = guildId;
            this.active = true;
        }

        public void shutdown() {
            try {
                this.active = false;
                if (session != null)
                    session.close();
            } catch (Exception e) {
                Logger logger = LogManager.getLogger(SessionManager.class);
                logger.error("Error shutting down session for guild: {}", guildId, e);
            }
        }
    }

    public boolean createSession(String guildId) {
        if (sessions.containsKey(guildId)) {
            logger.warn("Session for guild {} already exists", guildId);
            return true;
        }

        try {
            Client client = Client.builder()
                    .apiKey(apiKey)
                    .build();

            String modelId = "gemini-2.0-flash-live-001";

            LiveConnectConfig config = LiveConnectConfig.builder()
                    .responseModalities(Modality.Known.TEXT)
                    .systemInstruction(systemInstruction)
                    .build();

            AsyncSession session = client.async.live.connect(modelId, config).get();
            logger.info("Successfully created session for guild {}", guildId);

            GeminiSession geminiSession = new GeminiSession(session, guildId);
            sessions.put(guildId, geminiSession);
            responseBuffers.put(guildId, new StringBuilder());
            startReceivingResponses(geminiSession);

            return true;
        } catch (Exception e) {
            logger.error("Failed to create session for guild {}: {}", guildId, e.getMessage(), e);
            return false;
        }
    }

    public void removeSession(String guildId) {
        GeminiSession session = sessions.remove(guildId);
        responseBuffers.remove(guildId);

        if (session != null) {
            logger.info("Session for guild {} removed", guildId);
            session.shutdown();
        } else {
            logger.warn("No session found for guild {}", guildId);
        }
    }

    public String sendAudioData(String guildId, byte[] audioData) {
        GeminiSession session = sessions.get(guildId);
        if (session == null) {
            logger.warn("No active session found for guild {}", guildId);
            return null;
        }

        if (audioData == null || audioData.length == 0) {
            logger.warn("Received null or empty audio data for guild {}", guildId);
            return null;
        }

        try {
            CompletableFuture<String> future = new CompletableFuture<>();
            responseFutures.put(guildId, future);

            LiveSendRealtimeInputParameters audioContent = LiveSendRealtimeInputParameters.builder()
                    .media(Blob.builder()
                            .mimeType("audio/pcm")
                            .data(audioData))
                    .build();

            session.getSession().sendRealtimeInput(audioContent)
                    .exceptionally(e -> {
                        logger.error("Failed to send realtime input for guild {}", guildId, e);
                        return null;
                    });

            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Error sending audio data for guild {}: {}", guildId, e.getMessage(), e);
            return null;
        } finally {
            responseFutures.remove(guildId);
        }
    }

    private void startReceivingResponses(GeminiSession geminiSession) {
        CompletableFuture<Void> receiveFuture = geminiSession.getSession().receive(message -> handleResponse(geminiSession.getGuildId(), message));

        receiveFuture.exceptionally(e -> {
            logger.error("Failed to receive session for guild {}: {}", geminiSession.getGuildId(), e.getMessage(), e);
            geminiSession.setActive(false);
            return null;
        });
    }

    private void handleResponse(String guildId, LiveServerMessage message) {
        message.serverContent().ifPresent(content -> {
            if (content.turnComplete().orElse(false)) {
                StringBuilder buffer = responseBuffers.get(guildId);
                if (buffer != null && !buffer.isEmpty()) {
                    String completeResponse = buffer.toString().trim();
                    logger.info("Gemini complete response for guild {}: {}", guildId, completeResponse);

                    CompletableFuture<String> future = responseFutures.get(guildId);
                    if(future != null && !future.isDone())
                        future.complete(completeResponse);

                    buffer.setLength(0);
                }

                logger.info("Turn complete for guild: {}", guildId);
                System.out.println("\n--- Turn complete for guild: " + guildId + " ---");
            }
            else {
                content.modelTurn().stream()
                        .flatMap(modelTurn -> modelTurn.parts().stream())
                        .flatMap(Collection::stream)
                        .forEach(part -> {
                            part.text().ifPresent(text -> {
                                StringBuilder buffer = responseBuffers.computeIfAbsent(guildId, k -> new StringBuilder());
                                buffer.append(text);

                            });
                        });
            }
        });
    }

    public void shutdownAllSessions() {
        logger.info("Shutting down all sessions");
        sessions.values().forEach(GeminiSession::shutdown);
        sessions.clear();
        logger.info("All sessions have been shut down");
    }

    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(GeminiSession::isActive).count();
    }

    public boolean hasActiveSession(String guildId) {
        GeminiSession session = sessions.get(guildId);
        return session != null && session.isActive();
    }
}
