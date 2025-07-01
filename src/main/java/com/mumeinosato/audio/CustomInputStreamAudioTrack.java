package com.mumeinosato.audio;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.util.Base64;

public class CustomInputStreamAudioTrack extends DelegatedAudioTrack {
    private final byte[] bytes;
    private final MediaContainerDescriptor containerTrackFactory;
    private final CustomInputStreamSourceManager sourceManager;

    public CustomInputStreamAudioTrack(final AudioTrackInfo trackInfo, final MediaContainerDescriptor containerTrackFactory, final CustomInputStreamSourceManager sourceManager){
        super(trackInfo);
        this.bytes = Base64.getDecoder().decode(trackInfo.identifier);
        this.containerTrackFactory = containerTrackFactory;
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(final LocalAudioTrackExecutor localExecutor) throws Exception {
        try(var inputStream = new CustomSeekableInputStream(this.bytes)) {
            final var internalTrack = (InternalAudioTrack)this.containerTrackFactory.createTrack(this.trackInfo, inputStream);
            this.processDelegate(internalTrack, localExecutor);
        } catch (final FriendlyException e){
            return;
        }
    }

    public MediaContainerDescriptor getContainerTrackFactory(){
        return this.containerTrackFactory;
    }

    @Override
    protected AudioTrack makeShallowClone(){
        return new CustomInputStreamAudioTrack(this.trackInfo,this.containerTrackFactory, this.sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager(){
        return this.sourceManager;
    }
}
