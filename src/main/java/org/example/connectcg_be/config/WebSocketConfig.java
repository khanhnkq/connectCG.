package org.example.connectcg_be.config;

import org.example.connectcg_be.service.WebSocketAuthorizationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.scheduling.TaskScheduler;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthorizationService authorizationService;
    private final TaskScheduler heartbeatScheduler;
    private final String[] allowedOrigins;
    private final org.example.connectcg_be.security.AuthCookieService authCookieService;
    private final org.example.connectcg_be.security.AccessTokenRevocationService revocationService;
    private final org.example.connectcg_be.security.JwtTokenProvider tokenProvider;
    private final org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService;

    public WebSocketConfig(
            WebSocketAuthorizationService authorizationService,
            @Qualifier("taskScheduler") TaskScheduler heartbeatScheduler,
            @Value("${app.websocket.allowed-origins:${frontend.url:http://localhost:5173}}") String allowedOrigins,
            org.example.connectcg_be.security.AuthCookieService authCookieService,
            org.example.connectcg_be.security.AccessTokenRevocationService revocationService,
            org.example.connectcg_be.security.JwtTokenProvider tokenProvider,
            org.example.connectcg_be.security.CustomUserDetailsService customUserDetailsService) {
        this.authorizationService = authorizationService;
        this.heartbeatScheduler = heartbeatScheduler;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        this.authCookieService = authCookieService;
        this.revocationService = revocationService;
        this.tokenProvider = tokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .addInterceptors(new HttpPrincipalHandshakeInterceptor(authCookieService))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketAuthInterceptor(authorizationService, revocationService, tokenProvider, customUserDetailsService));
        registration.taskExecutor()
                .corePoolSize(2)
                .maxPoolSize(8)
                .queueCapacity(500);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(2)
                .maxPoolSize(8)
                .queueCapacity(500);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(64 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(10_000);
    }
}
