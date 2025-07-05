package com.mumeinosato.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for AudioInputQueue functionality
 */
public class AudioInputQueueTest {
    
    private AudioInputQueue audioInputQueue;
    
    @BeforeEach
    void setUp() {
        audioInputQueue = new AudioInputQueue();
    }
    
    @Test
    void testEnqueueAndDequeue() {
        // Initially empty
        assertFalse(audioInputQueue.hasNext());
        assertEquals(0, audioInputQueue.size());
        assertNull(audioInputQueue.dequeue());
        
        // Add an item
        String guildId = "test-guild";
        String userId = "test-user";
        byte[] audioData = "test-audio".getBytes();
        
        audioInputQueue.enqueue(guildId, userId, audioData);
        
        assertTrue(audioInputQueue.hasNext());
        assertEquals(1, audioInputQueue.size());
        
        // Retrieve the item
        AudioInputQueue.AudioData retrieved = audioInputQueue.dequeue();
        
        assertNotNull(retrieved);
        assertEquals(guildId, retrieved.getGuildId());
        assertEquals(userId, retrieved.getUserId());
        assertArrayEquals(audioData, retrieved.getData());
        
        // Now empty again
        assertFalse(audioInputQueue.hasNext());
        assertEquals(0, audioInputQueue.size());
        assertNull(audioInputQueue.dequeue());
    }
    
    @Test
    void testMultipleItems() {
        // Add multiple items
        audioInputQueue.enqueue("guild1", "user1", "audio1".getBytes());
        audioInputQueue.enqueue("guild2", "user2", "audio2".getBytes());
        audioInputQueue.enqueue("guild3", "user3", "audio3".getBytes());
        
        assertEquals(3, audioInputQueue.size());
        assertTrue(audioInputQueue.hasNext());
        
        // Retrieve in FIFO order
        AudioInputQueue.AudioData first = audioInputQueue.dequeue();
        assertEquals("guild1", first.getGuildId());
        assertEquals("user1", first.getUserId());
        assertEquals(2, audioInputQueue.size());
        
        AudioInputQueue.AudioData second = audioInputQueue.dequeue();
        assertEquals("guild2", second.getGuildId());
        assertEquals("user2", second.getUserId());
        assertEquals(1, audioInputQueue.size());
        
        AudioInputQueue.AudioData third = audioInputQueue.dequeue();
        assertEquals("guild3", third.getGuildId());
        assertEquals("user3", third.getUserId());
        assertEquals(0, audioInputQueue.size());
        
        assertFalse(audioInputQueue.hasNext());
    }
    
    @Test
    void testThreadSafety() throws InterruptedException {
        final int numThreads = 10;
        final int itemsPerThread = 100;
        
        // Create multiple threads that enqueue items
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < itemsPerThread; j++) {
                    audioInputQueue.enqueue(
                        "guild-" + threadId,
                        "user-" + threadId + "-" + j,
                        ("audio-" + threadId + "-" + j).getBytes()
                    );
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all items were added
        assertEquals(numThreads * itemsPerThread, audioInputQueue.size());
        
        // Verify we can dequeue all items
        int count = 0;
        while (audioInputQueue.hasNext()) {
            AudioInputQueue.AudioData item = audioInputQueue.dequeue();
            assertNotNull(item);
            count++;
        }
        
        assertEquals(numThreads * itemsPerThread, count);
        assertEquals(0, audioInputQueue.size());
    }
}