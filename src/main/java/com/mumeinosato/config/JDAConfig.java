package com.mumeinosato.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JDAConfig {
    @Value("${discord.token}")
    private String discordToken;

    @Bean
    public JDA jda() throws Exception {
        try {
            System.out.println("Starting JDA initialization with token: " + discordToken.substring(0, 4) + "...");
            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_VOICE_STATES
                    )
                    .build();
            jda.awaitReady();
            System.out.println("JDA initialized successfully");
            return jda;
        } catch (Exception e) {
            System.err.println("Failed to initialize JDA: " + e.getMessage());
            throw e;
        }
    }
}
