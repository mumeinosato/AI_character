package com.mumeinosato.audio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Queue for managing ordered audio playback
 */
@Component
public class AudioPlaybackQueue {
    private static final Logger logger = LogManager.getLogger(AudioPlaybackQueue.class);
    
    private final ConcurrentLinkedQueue<String> audioQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    
    /**
     * Add audio data to the playback queue
     * @param base64AudioData Base64 encoded audio data
     */
    public void enqueue(String base64AudioData) {
        audioQueue.offer(base64AudioData);
        logger.info("Audio added to playback queue. Queue size: {}", audioQueue.size());
    }
    
    /**
     * Get the next audio data from the queue for playback
     * @return Base64 encoded audio data, or null if queue is empty
     */
    public String dequeue() {
        return audioQueue.poll();
    }
    
    /**
     * Check if there are items in the queue
     * @return true if queue has items
     */
    public boolean hasNext() {
        return !audioQueue.isEmpty();
    }
    
    /**
     * Get current queue size
     * @return number of items in queue
     */
    public int size() {
        return audioQueue.size();
    }
    
    /**
     * Check if currently playing audio
     * @return true if playing
     */
    public boolean isPlaying() {
        return isPlaying.get();
    }
    
    /**
     * Set playing state
     * @param playing true if currently playing
     */
    public void setPlaying(boolean playing) {
        isPlaying.set(playing);
        if (playing) {
            logger.debug("Audio playback started");
        } else {
            logger.debug("Audio playback finished");
        }
    }
}