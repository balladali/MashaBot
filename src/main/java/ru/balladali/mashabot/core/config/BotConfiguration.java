package ru.balladali.mashabot.core.config;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.balladali.mashabot.telegram.Bot;

import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ProxyProperties.class)
public class BotConfiguration {

    @Bean
    TelegramBotsLongPollingApplication telegramApp() {
        return new TelegramBotsLongPollingApplication();
    }

    @Bean
    OkHttpClient okHttpClient(ProxyProperties pp) {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(pp.connectTimeoutMs()))
                .readTimeout(Duration.ofMillis(pp.readTimeoutMs()))
                .writeTimeout(Duration.ofMillis(pp.writeTimeoutMs()));

        if (pp.type() == ProxyProperties.Type.NONE) {
            return b.build();
        }

        if (pp.host() == null || pp.host().isBlank() || pp.port() <= 0) {
            // некорректно задан прокси — игнорируем
            return b.build();
        }

        if (pp.type() == ProxyProperties.Type.HTTP) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(pp.host(), pp.port()));
            b.proxy(proxy);

            // HTTP proxy auth (если нужно)
            if (pp.user() != null && !pp.user().isBlank()) {
                Authenticator proxyAuth = (route, response) -> {
                    String cred = Credentials.basic(pp.user(), pp.pass() == null ? "" : pp.pass());
                    return response.request().newBuilder().header("Proxy-Authorization", cred).build();
                };
                b.proxyAuthenticator(proxyAuth);
            }
            return b.build();
        }

        if (pp.type() == ProxyProperties.Type.SOCKS) {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(pp.host(), pp.port()));
            b.proxy(proxy);

            // SOCKS5 аутентификация делается через JVM Authenticator (OkHttp так работает с SOCKS)
            if (pp.user() != null && !pp.user().isBlank()) {
                java.net.Authenticator.setDefault(new java.net.Authenticator() {
                    @Override protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == RequestorType.PROXY) {
                            return new PasswordAuthentication(
                                    pp.user(),
                                    (pp.pass() == null ? "" : pp.pass()).toCharArray()
                            );
                        }
                        return null;
                    }
                });
            }
            return b.build();
        }

        return b.build();
    }

    @Bean
    TelegramClient telegramClient(@Value("${credential.telegram.token}") String token,
                                  OkHttpClient http) {
        return new OkHttpTelegramClient(http, token);
    }

    @Bean
    Object botRegistration(TelegramBotsLongPollingApplication app,
                           @Value("${credential.telegram.token}") String token,
                           Bot bot
    ) throws Exception {
        app.registerBot(token, bot);
        return new Object();
    }
}
