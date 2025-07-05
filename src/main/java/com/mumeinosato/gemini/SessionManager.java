package com.mumeinosato.gemini;

import com.google.genai.AsyncSession;
import com.google.genai.Client;
import com.google.genai.types.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    private static final Logger logger = LogManager.getLogger(SessionManager.class);

    private static final Map<String, GeminiSession> sessions = new ConcurrentHashMap<>();

    private static final Map<String, StringBuilder> responseBuffers = new ConcurrentHashMap<>();

    @Value("${gemini.key}")
    private String apiKey;

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

    public boolean sendAudioData(String guildId, byte[] audioData) {
        GeminiSession session = sessions.get(guildId);
        if (session == null) {
            logger.warn("No active session found for guild {}", guildId);
            return false;
        }

        if (audioData == null || audioData.length == 0) {
            logger.warn("Received null or empty audio data for guild {}", guildId);
            return false;
        }

        try {
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

            logger.info("Audio data sent to Gemini for guild {} (size: {} bytes)", guildId, audioData.length);
            return true;
        } catch (Exception e) {
            logger.error("Error sending audio data for guild {}: {}", guildId, e.getMessage(), e);
            return false;
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
        logger.debug("Received LiveServerMessage: {}", message);

        message.serverContent().ifPresent(content -> {
            if (content.turnComplete().orElse(false)) {
                StringBuilder buffer = responseBuffers.get(guildId);
                if (buffer != null && !buffer.isEmpty()) {
                    String completeResponse = buffer.toString().trim();
                    logger.info("Gemini complete response for guild {}: {}", guildId, completeResponse);
                    System.out.println("\n[Gemini Response]: " + completeResponse);

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
