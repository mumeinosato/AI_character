package com.mumeinosato.commands;

import com.mumeinosato.audio.*;
import com.mumeinosato.gemini.SessionManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SlashCommand extends ListenerAdapter {
    private static final Logger logger = LogManager.getLogger(SlashCommand.class);

    @Autowired
    private JDA jda;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private AudioProcessor audioProcessor;

    @Autowired
    private AudioQueueManager audioQueueManager;

    @Value("${discord.guild-id:}")
    private String guildId;

    @Value("${development.mode}")
    private boolean developmentMode;


    @PostConstruct
    public void initializeCommands() {
        try {
            logger.info("Initializing slash commands...");
            jda.addEventListener(this);

            if (developmentMode)
                registerGuildCommands();
            else
                registerGlobalCommands();

        } catch (Exception e) {
            logger.error("An error occurred while registering the command: {}", e.getMessage(), e);
        }
    }

    private void registerGuildCommands() {
        if (guildId == null || guildId.isEmpty()) return;

        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            guild.updateCommands().addCommands(
                    getCommonCommands()
            ).queue(
                    success -> logger.info("Guild command registration completed: {}", guild.getName()),
                    error -> logger.error("An error occurred while registering the guild command: {}", error.getMessage())
            );
        } else {
            logger.error("The specified guild ID was not found: {}", guildId);
        }
    }

    private void registerGlobalCommands() {
        jda.updateCommands().addCommands(
                getCommonCommands()
        ).queue(
                success -> logger.info("Global command registration has been completed (up to 1 hour for changes to take effect)"),
                error -> logger.error("An error occurred while registering the global command: {}", error.getMessage())
        );
    }

    private SlashCommandData[] getCommonCommands() {
        return new SlashCommandData[]{
                Commands.slash("join", "VCに参加します"),
                Commands.slash("leave", "VCから退出します"),
        };
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        try {
            logger.debug("Received slash command: {} from user: {}", event.getName(), event.getUser().getAsTag());

            if (event.getName().equals("join") || event.getName().equals("leave") || event.getName().equals("save")) {
                Guild guild = event.getGuild();
                Member member = event.getMember();

                if (guild == null || member == null) {
                    logger.warn("Command {} attempted outside of guild context", event.getName());
                    event.reply("このコマンドはサーバー内でのみ使用できます").setEphemeral(true).queue();
                    return;
                }

                VoiceChannel voiceChannel = null;
                if (member.getVoiceState() != null && member.getVoiceState().getChannel() != null)
                    voiceChannel = member.getVoiceState().getChannel().asVoiceChannel();

                if (voiceChannel == null) {
                    event.reply("VCに参加してください").setEphemeral(true).queue();
                    return;
                }

                if (event.getName().equals("join")) {
                    boolean alreadyConnected = jda.getGuilds().stream().anyMatch(g -> g.getAudioManager().isConnected());

                    if (alreadyConnected) {
                        event.reply("このBotはすべてのサーバーのうち1つのVC二しか参加できません").setEphemeral(true).queue();
                        return;
                    }



                    boolean geminiCreated = sessionManager.createSession();
                    if (!geminiCreated) {
                        logger.error("Failed to create Gemini session for guild: {}", guild.getId());
                        return;
                    }
                    logger.info("Gemini session created successfully for guild: {}", guild.getId());

                    final var audioManager = guild.getAudioManager();
                    final var sharedAudioData = new SharedAudioData();
                    final var scheduler = new DataCheckScheduler(sharedAudioData);
                    scheduler.start();
                    final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
                    playerManager.registerSourceManager(new CustomInputStreamSourceManager());
                    AudioSourceManagers.registerLocalSource(playerManager);

                    final var Handler = new AudioHandler(audioProcessor, sharedAudioData, playerManager, guild.getId(), audioQueueManager);

                    audioManager.setReceivingHandler(Handler);
                    audioManager.setSendingHandler(Handler);

                    logger.info("Bot joining voice channel: {} in guild: {}", voiceChannel.getName(), guild.getName());
                    guild.getAudioManager().openAudioConnection(voiceChannel);
                    event.reply("VCに参加しました").setEphemeral(true).queue();
                } else if (event.getName().equals("leave")) {
                    sessionManager.removeSession();

                    logger.info("Bot leaving voice channel: {} in guild: {}", voiceChannel.getName(), guild.getName());
                    guild.getAudioManager().closeAudioConnection();
                    event.reply("VCから退出しました").setEphemeral(true).queue();
                }
            }
        } catch (Exception e) {
            logger.error("Error executing slash command {}: {}", event.getName(), e.getMessage(), e);
            event.reply("コマンドの実行中にエラーが発生しました: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    @PreDestroy
    public void disconnectFromAllVC() {
        logger.info("Bot shutting down: Disconnecting from all voice channels...");
        try {
            sessionManager.shutdownAllSessions();

            int disconnectedCount = 0;
            for (Guild guild : jda.getGuilds()) {
                if (guild.getAudioManager().isConnected()) {
                    logger.info("Disconnecting from voice channel in guild: {}", guild.getName());
                    guild.getAudioManager().closeAudioConnection();
                    disconnectedCount++;
                }
            }

            if (disconnectedCount > 0) {
                logger.info("Successfully disconnected from {} voice channels", disconnectedCount);
                // 少し待機してから完了
                Thread.sleep(1000);
            } else {
                logger.info("No active voice connections found");
            }

            logger.info("Voice channel disconnection completed");

            // JDAを適切にシャットダウン
            logger.info("Shutting down JDA...");
            jda.shutdown();

            // シャットダウン完了を待機（最大5秒）
            if (!jda.awaitShutdown(5, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.warn("JDA shutdown timeout, forcing shutdown...");
                jda.shutdownNow();
            } else {
                logger.info("JDA shutdown completed successfully");
            }

        } catch (Exception e) {
            logger.error("Error occurred while disconnecting from voice channels: {}", e.getMessage(), e);

            // エラーが発生した場合も強制的にJDAをシャットダウン
            try {
                jda.shutdownNow();
            } catch (Exception shutdownError) {
                logger.error("Error during forced JDA shutdown: {}", shutdownError.getMessage(), shutdownError);
            }
        }
    }
}
