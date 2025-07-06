package com.mumeinosato;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DiscordBotApplication {
    private static final Logger logger = LogManager.getLogger(DiscordBotApplication.class);

    public static void main(String[] args){
        logger.info("Discord Bot Application starting...");
        SpringApplication.run(DiscordBotApplication.class, args);
        logger.info("Discord Bot Application started successfully");
    }
}
