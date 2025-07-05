package com.mumeinosato.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test for AudioPlaybackQueue to verify queue operations
 */
public class AudioPlaybackQueueTest {
    
    private AudioPlaybackQueue queue;
    
    @BeforeEach
    void setUp() {
        queue = new AudioPlaybackQueue();
    }
    
    @Test
    void testEnqueueAndDequeue() {
        // Test basic enqueue/dequeue operations
        assertFalse(queue.hasNext());
        assertEquals(0, queue.size());
        
        String testAudio1 = "base64audio1";
        String testAudio2 = "base64audio2";
        
        queue.enqueue(testAudio1);
        assertTrue(queue.hasNext());
        assertEquals(1, queue.size());
        
        queue.enqueue(testAudio2);
        assertEquals(2, queue.size());
        
        // Test FIFO order
        assertEquals(testAudio1, queue.dequeue());
        assertEquals(1, queue.size());
        
        assertEquals(testAudio2, queue.dequeue());
        assertEquals(0, queue.size());
        assertFalse(queue.hasNext());
        
        // Test empty queue
        assertNull(queue.dequeue());
    }
    
    @Test
    void testPlayingState() {
        assertFalse(queue.isPlaying());
        
        queue.setPlaying(true);
        assertTrue(queue.isPlaying());
        
        queue.setPlaying(false);
        assertFalse(queue.isPlaying());
    }
    
    @Test
    void testConcurrentOperations() throws Exception {
        // Test concurrent enqueue operations
        CompletableFuture<Void> producer1 = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 10; i++) {
                queue.enqueue("audio1_" + i);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        
        CompletableFuture<Void> producer2 = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 10; i++) {
                queue.enqueue("audio2_" + i);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        
        // Wait for producers to finish
        CompletableFuture.allOf(producer1, producer2).get(5, TimeUnit.SECONDS);
        
        // Should have 20 items total
        assertEquals(20, queue.size());
        
        // Consume all items
        int count = 0;
        while (queue.hasNext()) {
            assertNotNull(queue.dequeue());
            count++;
        }
        
        assertEquals(20, count);
        assertEquals(0, queue.size());
    }
}