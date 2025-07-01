package com.mumeinosato.audio;

import com.sedmelluq.discord.lavaplayer.tools.io.ExtendedBufferedInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.info.AudioTrackInfoProvider;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CustomSeekableInputStream extends SeekableInputStream {
    private static final Logger logger = org.apache.logging.log4j.LogManager.getLogger(CustomSeekableInputStream.class);

    private final ByteArrayInputStream inputStream;
    private final ExtendedBufferedInputStream bufferedStream;
    private long position;

    public CustomSeekableInputStream(final byte[] bytes){
        super(bytes.length,0);
        this.inputStream = new ByteArrayInputStream(bytes);
        this.bufferedStream = new ExtendedBufferedInputStream(this.inputStream);
    }

    @Override
    public int read() throws IOException {
        final var result = this.bufferedStream.read();

        if(result >= 0)
            this.position++;

        return result;
    }

    @Override
    public int read(final  byte[] b, final  int off, final int len) throws IOException{
        final var read = this.bufferedStream.read(b, off, len);
        this.position += read;
        return read;
    }

    @Override
    public long skip(final long n) throws IOException {
        final var skipped = this.bufferedStream.skip(n);
        this.position += skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException{
        return this.bufferedStream.available();
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
    public boolean markSupported(){
        return false;
    }

    @Override
    public void close() throws IOException{
        try{
            this.inputStream.close();
        }catch (final IOException e) {
            logger.error("Error closing stream: {}", e.getMessage(), e);
        }
    }

    @Override
    public long getPosition(){
        return this.position;
    }

    @Override
    public boolean canSeekHard(){
        return false;
    }

    @Override
    protected void seekHard(final long position) {}

    @Override
    public List <AudioTrackInfoProvider> getTrackInfoProviders() {
        return Collections.emptyList();
    }
}
