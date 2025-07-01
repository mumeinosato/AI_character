package com.mumeinosato.config;

public class DiscordSymbol {
    // DataCheckScheduler がデータをチェックする間隔（ミリ秒）
    public static final long LOOP_MILLISECONDS = 100;

    // 会話の継続とみなす時間（ミリ秒）- 話し終わりの判定を遅らせる
    public static final long DURING_CONVERSATION_MILLISECONDS = 2000; // 0.5秒 → 2秒に延長

    // 1回の会話の最大時間（ミリ秒）
    public static final long TALK_MILLISECONDS = 10000; // 3秒 → 10秒に延長
}
