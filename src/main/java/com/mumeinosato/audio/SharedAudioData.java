package com.mumeinosato.audio;

import com.mumeinosato.config.DiscordSymbol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Setter
public class SharedAudioData {
    private final Queue<AudioData> audioQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, List<byte[]>> accumulatedDataMap = new ConcurrentHashMap<>();
    private final Map<String, Long> dataStartTimeMap = new ConcurrentHashMap<>();
    private long lastAddTime = System.currentTimeMillis();

    public void checkAndMoveData() {
        final var currentTime = System.currentTimeMillis();

        for(final Map.Entry<String, List<byte[]>> entry:this.accumulatedDataMap.entrySet()){
            if(this.shouldMoveData(entry.getKey(), currentTime))
                this.moveDataToQueue(entry);
        }
    }

    private boolean shouldMoveData(final String id,final long currentTime){
        final long startTime = this.dataStartTimeMap.getOrDefault(id, this.lastAddTime);
        final var data = this.accumulatedDataMap.get(id);

        return !data.isEmpty() && ((currentTime - this.lastAddTime) > DiscordSymbol.DURING_CONVERSATION_MILLISECONDS || (currentTime - startTime) > DiscordSymbol.TALK_MILLISECONDS);
    }

    private void moveDataToQueue(final Map.Entry<String, List<byte[]>> entry){
        final var combinedData = this.combineData(entry.getValue());
        final var audioData = new AudioData(entry.getKey(), combinedData);
        this.audioQueue.add(audioData);

        // 処理済みデータをマップから削除して重複処理を防ぐ
        this.accumulatedDataMap.remove(entry.getKey());
        this.dataStartTimeMap.remove(entry.getKey());
        this.lastAddTime = System.currentTimeMillis();
    }

    public void addAudioData(final String id, final  byte[] data){
        final var accumulatedData = this.accumulatedDataMap.computeIfAbsent(id, k -> {
            this.dataStartTimeMap.put(id, System.currentTimeMillis());
            return new CopyOnWriteArrayList<>();
        });

        accumulatedData.add(data);
        this.lastAddTime = System.currentTimeMillis();
    }

    private byte[] combineData(final List<byte[]> accumulatedData){
        final var size = accumulatedData.stream().mapToInt(array -> array.length).sum();
        final var decodedData = new byte[size];
        var index = 0;

        for(final byte[] bytes:accumulatedData){
            System.arraycopy(bytes,0,decodedData,index,bytes.length);
            index += bytes.length;
        }

        return decodedData;
    }

    public AudioData takeAudioData(){
        return this.audioQueue.poll();
    }

    @Getter
    @Setter
    public static class AudioData {
        private final String id;
        private final byte[] data;

        public AudioData(final String id, final byte[] data){
            this.id = id;
            this.data = data;
        }
    }
}
