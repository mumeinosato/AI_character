package com.mumeinosato.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SlashCommand extends ListenerAdapter {

    @Autowired
    private JDA jda;

    @Value("${discord.guild-id:}")
    private String guildId;

    @Value("${devlopment.mode}")
    private boolean developmentMode;

    @PostConstruct
    public void initializeCommands(){
        try {
            // JDAにイベントリスナーを登録
            jda.addEventListener(this);

            if (developmentMode) {
                // 開発環境：ギルドにスラッシュコマンドを登録（即座に反映される）
                registerGuildCommands();
            } else {
                // 本番環境：グローバルにスラッシュコマンドを登録（最大1時間で反映）
                registerGlobalCommands();
            }
        } catch (Exception e) {
            System.err.println("An error occurred while registering the command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerGuildCommands() {
        if (guildId == null || guildId.isEmpty()) {
            System.err.println("This is a development environment, but discord.guild-id is not set");
            return;
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            System.out.println("Development : Registering guild commands... (" + guild.getName() + ")");
            guild.updateCommands().addCommands(
                    getCommonCommands()
            ).queue(
                success -> System.out.println("Guild command registration completed: " + guild.getName()),
                error -> System.err.println("An error occurred while registering the guild command.: " + error.getMessage())
            );
        } else {
            System.err.println("The specified guild ID was not found: " + guildId);
        }
    }

    private void registerGlobalCommands() {
        System.out.println("Production: Registering global command...");
        jda.updateCommands().addCommands(
                getCommonCommands()
        ).queue(
            success -> System.out.println("Global command registration has been completed (up to 1 hour for changes to take effect)"),
            error -> System.err.println("An error occurred while registering the global command: " + error.getMessage())
        );
    }

    private SlashCommandData[] getCommonCommands() {
        return new SlashCommandData[]{
                Commands.slash("join", "VCに参加します"),
                Commands.slash("leave", "VCから退出します"),
        };
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event){
        try {
            if(event.getName().equals("join") || event.getName().equals("leave") || event.getName().equals("save")) {
                Guild guild = event.getGuild();
                Member member = event.getMember();

                if(guild == null || member == null){
                    event.reply("このコマンドはサーバー内でのみ使用できます").setEphemeral(true).queue();
                    return;
                }

                VoiceChannel voiceChannel = null;
                if (member.getVoiceState() != null && member.getVoiceState().getChannel() != null) {
                    voiceChannel = member.getVoiceState().getChannel().asVoiceChannel();
                }
                if (voiceChannel == null) {
                    event.reply("VCに参加してください").setEphemeral(true).queue();
                    return;
                }

                if(event.getName().equals("join")) {
                    guild.getAudioManager().openAudioConnection(voiceChannel);
                    event.reply("VCに参加しました: " + voiceChannel.getName()).queue();
                }else if(event.getName().equals("leave")) {
                    guild.getAudioManager().closeAudioConnection();
                    event.reply("VCから退出しました: " + voiceChannel.getName()).queue();
                }
            }
        } catch (Exception e) {
            event.reply("コマンドの実行中にエラーが発生しました: " + e.getMessage()).setEphemeral(true).queue();
            e.printStackTrace();
        }
    }
}
