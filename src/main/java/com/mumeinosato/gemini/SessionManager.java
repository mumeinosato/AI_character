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

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SessionManager {
    private static final Logger logger = LogManager.getLogger(SessionManager.class);

    private GeminiSession session;
    private StringBuilder responseBuffer = new StringBuilder();
    private CompletableFuture<String> responseFuture;

    @Value("${gemini.key}")
    private String apiKey;

    private String prompt = "女子高校生,敬語ではなく砕けたかんじで,1~2文ぐらいで短く,会話がつながるようにして,絵文字は使わないで,アルファベットは読み上げられないから、カタカナにして";
    //private String prompt = "女子高校生,敬語ではなく砕けたかんじで,1文で短く,会話がつながるようにして,絵文字は使わないで,アルファベットは読み上げられないから、カタカナにして";

    Content systemInstruction = Content.fromParts(Part.fromText(prompt));

    private static class GeminiSession {
        @Getter
        private final AsyncSession session;
        @Getter
        @Setter
        private volatile boolean active;

        public GeminiSession(AsyncSession session) {
            this.session = session;
            this.active = true;
        }

        @PreDestroy
        public void shutdown() {
            try {
                this.active = false;
                if (session != null)
                    session.close();
            } catch (Exception e) {
                Logger logger = LogManager.getLogger(SessionManager.class);
                logger.error("Error shutting down session", e);
            }
        }
    }

    public boolean createSession() {
        if (session != null && session.isActive()) {
            logger.warn("Session already exists and is active");
            return true;
        }

        try {
            Client client = Client.builder().apiKey(apiKey).build();
            String modelId = "gemini-2.0-flash-live-001";

            LiveConnectConfig config = LiveConnectConfig.builder()
                    .responseModalities(Modality.Known.TEXT)
                    .systemInstruction(systemInstruction)
                    .build();

            AsyncSession asyncSession = client.async.live.connect(modelId, config).get();
            logger.info("Successfully created session");

            session = new GeminiSession(asyncSession);
            responseBuffer.setLength(0);
            startReceivingResponses();

            return true;
        } catch (Exception e) {
            logger.error("Failed to create session: {}", e.getMessage(), e);
            return false;
        }
    }

    public void removeSession() {
        if (session != null) {
            logger.info("Session removed");
            session.shutdown();
            session = null;
        } else {
            logger.warn("No session found");
        }
    }

    public CompletableFuture<String> sendAudioData(byte[] audioData) {
        if (session == null || !session.isActive()) {
            logger.warn("No active session found");
            return null;
        }

        if (audioData == null || audioData.length == 0) {
            logger.warn("Received null or empty audio data");
            return null;
        }

        responseFuture = new CompletableFuture<>();

        LiveSendRealtimeInputParameters audioContent = LiveSendRealtimeInputParameters.builder()
                .media(Blob.builder().mimeType("audio/pcm").data(audioData))
                .build();

        session.getSession().sendRealtimeInput(audioContent)
                .exceptionally(e -> {
                    logger.error("Failed to send realtime input", e);
                    return null;
                });

        return responseFuture.orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((r,e) -> responseFuture = null);
    }

    private void startReceivingResponses() {
        CompletableFuture<Void> receiveFuture = session.getSession().receive(this::handleResponse);

        receiveFuture.exceptionally(e -> {
            logger.error("Failed to receive session: {}", e.getMessage(), e);
            session.setActive(false);
            return null;
        });
    }

    private void handleResponse(LiveServerMessage message) {
        message.serverContent().ifPresent(content -> {
            if (content.turnComplete().orElse(false)) {
                if (!responseBuffer.isEmpty()) {
                    String completeResponse = responseBuffer.toString().trim();
                    logger.info("Gemini complete response: {}", completeResponse);
                    if (responseFuture != null && !responseFuture.isDone())
                        responseFuture.complete(completeResponse);

                    responseBuffer.setLength(0);
                }
                System.out.println("Turn complete");
            } else {
                content.modelTurn().stream()
                        .flatMap(modelTurn -> modelTurn.parts().stream())
                        .flatMap(Collection::stream)
                        .forEach(part -> part.text().ifPresent(responseBuffer::append));
            }
        });
    }

    public void shutdownAllSessions() {
        logger.info("Shutting down session");
        if (session != null) {
            session.shutdown();
            session = null;
        }
        responseBuffer.setLength(0);
        logger.info("Sessions have been shut down");
    }

    public boolean hasActiveSession() {
        return session != null && session.isActive();
    }
}
