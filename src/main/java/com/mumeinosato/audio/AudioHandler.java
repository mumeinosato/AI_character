package com.mumeinosato.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.UserAudio;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Base64;

public class AudioHandler implements AudioReceiveHandler, AudioSendHandler {
    private static final Logger logger = LogManager.getLogger(AudioHandler.class);

    private final AudioProcessor audioProcessor;
    private final SyncGeminiAsyncTTSProcessor syncGeminiAsyncTTSProcessor;
    private final AudioInputQueue audioInputQueue;
    private final AudioPlaybackQueue audioPlaybackQueue;
    private final SharedAudioData sharedAudioData;
    private final AudioPlayerManager playerManager;
    private final AudioPlayer audioPlayer;
    private final String guildId;
    private AudioFrame lastFrame;
    private boolean isPlayingFromQueue = false;


    public AudioHandler(final AudioProcessor audioProcessor, final SyncGeminiAsyncTTSProcessor syncGeminiAsyncTTSProcessor,
                        final AudioInputQueue audioInputQueue, final AudioPlaybackQueue audioPlaybackQueue, 
                        SharedAudioData sharedAudioData, final AudioPlayerManager playerManager, final String guildId) {
        this.audioProcessor = audioProcessor;
        this.syncGeminiAsyncTTSProcessor = syncGeminiAsyncTTSProcessor;
        this.audioInputQueue = audioInputQueue;
        this.audioPlaybackQueue = audioPlaybackQueue;
        this.sharedAudioData = sharedAudioData;
        this.playerManager = playerManager;
        this.audioPlayer = playerManager.createPlayer();
        this.guildId = guildId;
    }

    @Override
    public boolean canReceiveCombined() {
        return AudioReceiveHandler.super.canReceiveCombined();
    }

    @Override
    public boolean canReceiveUser() {
        return true;
    }

    @Override
    public void handleUserAudio(final UserAudio userAudio) {
        final var receiveData = userAudio.getAudioData(1.0);
        this.sharedAudioData.addAudioData(userAudio.getUser().getId(), receiveData);

        // handleUserAudioでは音声データの追加のみ行い、移動チェックはcanProvideで行う
        // this.sharedAudioData.checkAndMoveData(); // この行を削除
    }

    @Override
    public boolean canProvide() {
        // Check for new audio data and add to input queue
        try {
            this.sharedAudioData.checkAndMoveData();
            
            final var audioData = this.sharedAudioData.takeAudioData();
            if (audioData != null) {
                logger.info("Adding audio to input queue for user: {} (speech finished)", audioData.getId());
                // Add to input queue for processing
                audioInputQueue.enqueue(guildId, audioData.getId(), audioData.getData());
            }
            
            // Process audio from input queue (sync Gemini + async TTS)
            if (audioInputQueue.hasNext()) {
                syncGeminiAsyncTTSProcessor.processNextAudio();
            }
            
            // Handle playback queue - check if we should start playing next item
            if (!isPlayingFromQueue && audioPlaybackQueue.hasNext()) {
                String nextAudioData = audioPlaybackQueue.dequeue();
                if (nextAudioData != null) {
                    logger.info("Starting playback from queue. Remaining queue size: {}", audioPlaybackQueue.size());
                    this.loadAndPlayTrack(nextAudioData);
                    isPlayingFromQueue = true;
                }
            }
            
        } catch (final Exception e) {
            logger.error("Error in canProvide: {}", e.getMessage(), e);
        }

        // Get next audio frame for playback
        this.lastFrame = this.audioPlayer.provide();

        // Check if current track finished playing
        if (this.lastFrame == null && isPlayingFromQueue) {
            isPlayingFromQueue = false;
            logger.info("Current track finished, ready for next from queue");
        }

        return this.lastFrame != null;
    }

    private void loadAndPlayTrack(final String trackString) {
        // 現在再生中のトラックを停止
        if (this.audioPlayer.getPlayingTrack() != null) {
            this.audioPlayer.stopTrack();
            logger.debug("Stopped current track before loading new one");
        }

        this.playerManager.loadItem(trackString, new AudioLoadResultHandler() {

            @Override
            public void trackLoaded(final AudioTrack track) {
                logger.info("Track loaded successfully, starting playback");
                AudioHandler.this.audioPlayer.playTrack(track);
            }
            
            @Override
            public void playlistLoaded(final AudioPlaylist playlist) {
                logger.debug("Playlist loaded (not expected in this context)");
            }

            @Override
            public void noMatches() {
                logger.warn("No matches found for track string");
                isPlayingFromQueue = false; // Reset flag on failure
            }

            @Override
            public void loadFailed(final FriendlyException e) {
                logger.error("Failed to load track: {}", e.getMessage(), e);
                isPlayingFromQueue = false; // Reset flag on failure
            }
        });
    }

    @Override
    public ByteBuffer provide20MsAudio() {

        return ByteBuffer.wrap(this.lastFrame.getData());
    }

    @Override
    public boolean isOpus() {

        return true;
    }
}
