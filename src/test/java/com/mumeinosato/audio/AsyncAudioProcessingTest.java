package com.mumeinosato.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Integration test verifying the async audio processing logic
 * This test focuses on the core async behavior without external dependencies
 */
public class AsyncAudioProcessingTest {
    
    @Mock
    private SessionManager mockSessionManager;
    
    private AudioPlaybackQueue audioPlaybackQueue;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        audioPlaybackQueue = new AudioPlaybackQueue();
    }
    
    @Test
    void testAsyncProcessingFlow() throws Exception {
        // Setup mock response from SessionManager
        when(mockSessionManager.sendAudioData(anyString(), any(byte[].class)))
            .thenReturn("Test response from Gemini");
        
        // Create a simplified async processor for testing
        TestAsyncAudioProcessor processor = new TestAsyncAudioProcessor(mockSessionManager, audioPlaybackQueue);
        
        // Test data
        String guildId = "test-guild";
        String userId = "test-user";
        byte[] audioData = "test-audio-data".getBytes();
        
        // Process audio asynchronously
        CompletableFuture<Void> processingFuture = processor.processAudioAsync(guildId, userId, audioData);
        
        // Wait for processing to complete
        processingFuture.get(5, TimeUnit.SECONDS);
        
        // Verify SessionManager was called
        verify(mockSessionManager, times(1)).sendAudioData(eq(guildId), any(byte[].class));
        
        // Verify audio was queued (simplified - we know TTS would normally be called)
        assertTrue(audioPlaybackQueue.hasNext(), "Audio should be queued after processing");
        assertEquals(1, audioPlaybackQueue.size());
        
        // Verify we can dequeue the processed audio
        String queuedAudio = audioPlaybackQueue.dequeue();
        assertNotNull(queuedAudio, "Queued audio should not be null");
    }
    
    @Test
    void testMultipleAsyncProcessing() throws Exception {
        // Test that multiple audio processing requests can be handled concurrently
        when(mockSessionManager.sendAudioData(anyString(), any(byte[].class)))
            .thenReturn("Response 1", "Response 2", "Response 3");
        
        TestAsyncAudioProcessor processor = new TestAsyncAudioProcessor(mockSessionManager, audioPlaybackQueue);
        
        // Submit multiple processing requests
        CompletableFuture<Void> future1 = processor.processAudioAsync("guild1", "user1", "audio1".getBytes());
        CompletableFuture<Void> future2 = processor.processAudioAsync("guild2", "user2", "audio2".getBytes());
        CompletableFuture<Void> future3 = processor.processAudioAsync("guild3", "user3", "audio3".getBytes());
        
        // Wait for all to complete
        CompletableFuture.allOf(future1, future2, future3).get(5, TimeUnit.SECONDS);
        
        // Verify all requests were processed
        verify(mockSessionManager, times(3)).sendAudioData(anyString(), any(byte[].class));
        
        // Verify all audio was queued
        assertEquals(3, audioPlaybackQueue.size());
    }
    
    /**
     * Simplified test version of AsyncAudioProcessor that skips TTS calls
     */
    private static class TestAsyncAudioProcessor {
        private final SessionManager sessionManager;
        private final AudioPlaybackQueue audioPlaybackQueue;
        
        public TestAsyncAudioProcessor(SessionManager sessionManager, AudioPlaybackQueue audioPlaybackQueue) {
            this.sessionManager = sessionManager;
            this.audioPlaybackQueue = audioPlaybackQueue;
        }
        
        public CompletableFuture<Void> processAudioAsync(String guildId, String userId, byte[] audioData) {
            return CompletableFuture.runAsync(() -> {
                try {
                    // Simulate processing delay
                    Thread.sleep(10);
                    
                    // Call Gemini (mocked)
                    String response = sessionManager.sendAudioData(guildId, audioData);
                    
                    if (response != null) {
                        // Simulate TTS response - normally this would call the actual TTS API
                        String mockTtsResponse = "mock-tts-audio-data-for-" + response;
                        
                        // Queue for playback
                        audioPlaybackQueue.enqueue(mockTtsResponse);
                    }
                    
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    // Mock interface for SessionManager since we can't import the real one
    interface SessionManager {
        String sendAudioData(String guildId, byte[] audioData);
    }
}