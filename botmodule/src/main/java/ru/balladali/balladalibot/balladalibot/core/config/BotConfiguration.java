package ru.balladali.balladalibot.balladalibot.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.generics.BotOptions;
import ru.balladali.balladalibot.balladalibot.telegram.BalladaliBot;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

@Configuration
public class BotConfiguration {

    @Value("${proxy.host}")
    private String proxyHost;

    @Value("${proxy.port}")
    private String proxyPort;

    @Value("${proxy.user}")
    private String proxyUser;

    @Value("${proxy.password}")
    private String proxyPassword;

    @Bean
    public DefaultBotOptions botOptions() {
        if (!StringUtils.isEmpty(proxyUser) && !StringUtils.isEmpty(proxyPassword)) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                }
            });
        }

        DefaultBotOptions botOptions = ApiContext.getInstance(DefaultBotOptions.class);

        if (!StringUtils.isEmpty(proxyHost) && !StringUtils.isEmpty(proxyPort)) {
            botOptions.setProxyHost(proxyHost);
            botOptions.setProxyPort(Integer.valueOf(proxyPort));
            botOptions.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
        }
        return botOptions;
    }

    @Bean
    public BalladaliBot bot() {
        return new BalladaliBot(botOptions());
    }
}
