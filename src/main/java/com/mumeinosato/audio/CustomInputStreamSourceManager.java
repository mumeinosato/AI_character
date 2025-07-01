package com.mumeinosato.audio;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.container.wav.WavContainerProbe;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.ProbingAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.logging.log4j.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class CustomInputStreamSourceManager extends ProbingAudioSourceManager {
    private static final Logger logger = org.apache.logging.log4j.LogManager.getLogger(CustomInputStreamSourceManager.class);

    public CustomInputStreamSourceManager() {
        super(MediaContainerRegistry.DEFAULT_REGISTRY);
    }

    @Override
    public String getSourceName() {
        return "CustomInputStream";
    }

    @Override
    public AudioItem loadItem(final AudioPlayerManager manager, final AudioReference reference){
        try {
            final var title = reference.getTitle();
            final var author = reference.getAuthor();
            final var length = 0L;
            final var identifier = reference.getIdentifier();
            final var isStream = false;
            final var uri = reference.getUri();

            final var trackInfo = new AudioTrackInfo(title, author, length, identifier, isStream, uri);
            final var containerDescriptor = new MediaContainerDescriptor(new WavContainerProbe(), null);

            return this.createTrack(trackInfo, containerDescriptor);
        } catch (final Exception e){
            logger.error("Error loading item: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isTrackEncodable(final AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(final AudioTrack track, final DataOutput output) throws IOException{
        this.encodeTrackFactory(((CustomInputStreamAudioTrack) track).getContainerTrackFactory(),output);
    }

    @Override
    public AudioTrack decodeTrack(final AudioTrackInfo trackInfo, final DataInput input) throws IOException{
        final var containerTrackFactory = this.decodeTrackFactory(input);

        if(containerTrackFactory != null)
            return this.createTrack(trackInfo, containerTrackFactory);

        return null;
    }

    @Override
    public void shutdown() {}

    @Override
    protected AudioTrack createTrack(final AudioTrackInfo trackInfo, final MediaContainerDescriptor containerTrackFactory){
        return new CustomInputStreamAudioTrack(trackInfo, containerTrackFactory, this);
    }
}
