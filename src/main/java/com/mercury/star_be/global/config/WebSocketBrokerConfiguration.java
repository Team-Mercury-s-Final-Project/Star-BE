package com.mercury.star_be.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketBrokerConfiguration implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.rabbitmq.host}")
    String rabbitmqHost;
    @Value("${spring.rabbitmq.password}")
    String rabbitmqPassword;
    @Value("${spring.rabbitmq.username}")
    String rabbitmqUsername;
    @Value("${spring.rabbitmq.port}")
    int rabbitmqPort;

//    @Value("${host}")
//    String host;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 웹소켓 연결 요청 엔드포인트
        // ws://localhost:8080/chat/
        registry.addEndpoint("/chat")
                .setAllowedOrigins("http://localhost:5173", "https://mercurystudy.store");

        // 웹소켓 연결 요청 엔드포인트
        // ws://localhost:8080/timer/
        registry.addEndpoint("/timer")
                .setAllowedOrigins("http://localhost:5173", "https://mercurystudy.store");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 구독 요청을 처리할 prefix 설정
        //enableStompBrokerRelay : 외부 메시지 브로커와 통합하기 위해 사용되는 옵션
        registry.enableStompBrokerRelay("/topic") // sub
                .setRelayHost(rabbitmqHost)
                .setRelayPort(rabbitmqPort)
                .setClientLogin(rabbitmqUsername)
                .setClientPasscode(rabbitmqPassword);

        registry.setApplicationDestinationPrefixes("/pub");
    }
}
