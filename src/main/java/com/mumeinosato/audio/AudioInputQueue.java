package com.mumeinosato.audio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Queue for managing incoming audio data from voice chat
 */
@Component
public class AudioInputQueue {
    private static final Logger logger = LogManager.getLogger(AudioInputQueue.class);
    
    private final ConcurrentLinkedQueue<AudioData> inputQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * Audio data wrapper
     */
    public static class AudioData {
        private final String guildId;
        private final String userId;
        private final byte[] data;
        
        public AudioData(String guildId, String userId, byte[] data) {
            this.guildId = guildId;
            this.userId = userId;
            this.data = data;
        }
        
        public String getGuildId() { return guildId; }
        public String getUserId() { return userId; }
        public byte[] getData() { return data; }
    }
    
    /**
     * Add audio data to the input queue
     * @param guildId Discord guild ID
     * @param userId User ID
     * @param audioData Raw audio data
     */
    public void enqueue(String guildId, String userId, byte[] audioData) {
        inputQueue.offer(new AudioData(guildId, userId, audioData));
        logger.info("Audio data added to input queue for user: {}. Queue size: {}", userId, inputQueue.size());
    }
    
    /**
     * Get the next audio data from the queue for processing
     * @return AudioData, or null if queue is empty
     */
    public AudioData dequeue() {
        return inputQueue.poll();
    }
    
    /**
     * Check if there are items in the queue
     * @return true if queue has items
     */
    public boolean hasNext() {
        return !inputQueue.isEmpty();
    }
    
    /**
     * Get current queue size
     * @return number of items in queue
     */
    public int size() {
        return inputQueue.size();
    }
}