package com.mumeinosato.audio;

import com.mumeinosato.config.DiscordSymbol;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataCheckScheduler {
    private final SharedAudioData sharedAudioData;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public DataCheckScheduler(final SharedAudioData sharedAudioData){
        this.sharedAudioData = sharedAudioData;
    }

    public void start(){
        this.scheduler.scheduleAtFixedRate(() -> this.sharedAudioData.checkAndMoveData(),
                0,
                DiscordSymbol.LOOP_MILLISECONDS,
                TimeUnit.MICROSECONDS);
    }
}
