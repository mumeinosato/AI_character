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
    private final SharedAudioData sharedAudioData;
    private final AudioPlayerManager playerManager;
    private final AudioPlayer audioPlayer;
    private AudioFrame lastFrame;
    private boolean isProcessingAudio = false;


    public AudioHandler(final AudioProcessor audioProcessor, SharedAudioData sharedAudioData, final AudioPlayerManager playerManager) {
        this.audioProcessor = audioProcessor;
        this.sharedAudioData = sharedAudioData;
        this.playerManager = playerManager;
        this.audioPlayer = playerManager.createPlayer();
    }

    @Override
    public boolean canReceiveCombined() {
        return false;
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
        // 音声処理中でない場合のみ新しい音声データを処理
        if (!isProcessingAudio) {
            try {
                // 定期的にデータの移動をチェック（話し終わり判定）
                this.sharedAudioData.checkAndMoveData();

                final var audioData = this.sharedAudioData.takeAudioData();
                if (audioData != null) {
                    isProcessingAudio = true;
                    logger.info("Processing complete audio data for user: {} (speech finished)", audioData.getId());
                    final var replyData = this.audioProcessor.processAudio(audioData.getId(), audioData.getData());
                    if (replyData != null && replyData.length > 0) {
                        final var base64String = Base64.getEncoder().encodeToString(replyData);
                        this.loadAndPlayTrack(base64String);
                    } else {
                        logger.debug("Audio processing returned empty data for user: {}", audioData.getId());
                        isProcessingAudio = false;
                    }
                }
            } catch (final Exception e) {
                logger.error("Error in canProvide: {}", e.getMessage(), e);
                isProcessingAudio = false;
            }
        }

        this.lastFrame = this.audioPlayer.provide();

        // 音声フレームがない場合は処理完了とみなす
        if (this.lastFrame == null && isProcessingAudio) {
            isProcessingAudio = false;
            logger.debug("Audio playback finished, ready for next audio");
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
                isProcessingAudio = false; // 処理失敗時にフラグをリセット
            }


            @Override
            public void loadFailed(final FriendlyException e) {
                logger.error("Failed to load track: {}", e.getMessage(), e);
                isProcessingAudio = false; // 処理失敗時にフラグをリセット
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
