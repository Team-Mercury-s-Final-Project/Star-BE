package com.mercury.star_be.global.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**Spring Boot 애플리케이션에서 RabbitMQ와의 통합 설정*/
@Configuration
public class RabbitMQConfig {
    @Value("${spring.rabbitmq.host}")
    String rabbitmqHost;

    @Value("${spring.rabbitmq.password}")
    String rabbitmqPassword;

    @Value("${spring.rabbitmq.username}")
    String rabbitmqUsername;

    @Value("${spring.rabbitmq.port}")
    int rabbitmqPort;


    public static final String CHAT_QUEUE_NAME = "chat.queue";
    public static final String CHAT_RECENT_MESSAGE_QUEUE_NAME = "chat.recentMessage.queue";
    public static final String CHAT_CONNECT_QUEUE_NAME = "chat.connect.queue";
    public static final String CHAT_DISCONNECT_QUEUE_NAME = "chat.disconnect.queue";
    public static final String READ_CHECK_REQUEST_QUEUE_NAME = "readCheck.request.queue";
    public static final String READ_CHECK_BULK_RESPONSE_QUEUE_NAME = "readCheck.bulkResponse.queue";
    public static final String READ_CHECK_RESPONSE_QUEUE_NAME = "readCheck.response.queue";

    public static final String CHAT_EXCHANGE_NAME = "chat.exchange";
    public static final String READ_CHECK_EXCHANGE_NAME = "readCheck.exchange";

    public static final String ROUTING_KEY = "chat.*";
    public static final String CHAT_RECENT_MESSAGE_ROUTING_KEY = "chat.recentMessage.*";
    public static final String READ_CHECK_REQUEST_ROUTING_KEY = "readCheck.request.*";
    public static final String READ_CHECK_BULK_RESPONSE_ROUTING_KEY = "readCheck.bulkResponse.*";
    public static final String READ_CHECK_RESPONSE_ROUTING_KEY = "readCheck.response.*";
    public static final String CHAT_CONNECT_ROUTING_KEY = "chat.connect.*";
    public static final String CHAT_DISCONNECT_ROUTING_KEY = "chat.disconnect.*";

    // TIMER QUEUE
    private static final String TIMER_QUEUE_NAME = "groups.queue";
    private static final String TIMER_EXCHANGE_NAME = "groups.exchange";
    private static final String TIMER_ROUTING_KEY = "groups.#";

    // SSE
    public static final String SSE_EXCHANGE_NAME = "sse.exchange";
    private static final String SSE_QUEUE_NAME = "sse.queue.";


    //Queue 등록
    @Bean
    public Queue chatQueue() {
        //chat.queue라는 이름의 새로운 큐를 생성
        return new Queue("chat.queue");
    }


    //Queue 등록(채팅 / 메시지 읽음)
    //채팅 큐
    @Bean
    public Queue queue(){ return new Queue(CHAT_QUEUE_NAME, true); }
    //채팅목록 최신 메시지 큐
    @Bean
    public Queue recentMessageQueue() {
        return new Queue(CHAT_RECENT_MESSAGE_QUEUE_NAME, true);
    }
    //채팅방 접속 큐
    @Bean
    public Queue chatConnectQueue() {
        return new Queue(CHAT_CONNECT_QUEUE_NAME, true);
    }
    //채팅방 접속해제 큐
    @Bean
    public Queue chatDisconnectQueue() {
        return new Queue(CHAT_DISCONNECT_QUEUE_NAME, true);
    }

    //메시지 읽음 리퀘스트 큐
    @Bean
    public Queue readCheckRequestQueue(){ return new Queue(READ_CHECK_REQUEST_QUEUE_NAME, true); }
    //메시지 읽음 벌크 리스폰스 큐
    @Bean
    public Queue readCheckBulkResponseQueue(){ return new Queue(READ_CHECK_BULK_RESPONSE_QUEUE_NAME, true); }
    //메시지 읽음 리스폰스 큐
    @Bean
    public Queue readCheckResponseQueue(){ return new Queue(READ_CHECK_RESPONSE_QUEUE_NAME, true); }

    // Timer Queue 등록
    @Bean
    public Queue timerQueue() {
        return new Queue(TIMER_QUEUE_NAME,true);
    }

    // SSE Queue 등록
    @Bean
    public Queue sseQueue() {
        String instanceName = System.getenv("INSTANCE_NAME");
        if (instanceName == null) {
            instanceName = "local";
        }
        return new Queue(SSE_QUEUE_NAME + instanceName, true);
    }

    //Exchange 등록
    @Bean
    public TopicExchange exchange(){ return new TopicExchange(CHAT_EXCHANGE_NAME); }
    @Bean
    public TopicExchange readCheckExchange(){ return new TopicExchange(READ_CHECK_EXCHANGE_NAME); }

    // Timer Exchange 등록
    @Bean
    public TopicExchange timerExchange() {
        return new TopicExchange(TIMER_EXCHANGE_NAME);
    }

    // SSE Exchange 등록
    @Bean
    public FanoutExchange sseExchange() {
        return new FanoutExchange(SSE_EXCHANGE_NAME);
    }

    //Exchange와 Queue 바인딩
    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }
    @Bean
    public Binding chatRecentMessageBinding(Queue recentMessageQueue, TopicExchange exchange) {
        return BindingBuilder.bind(recentMessageQueue).to(exchange).with(CHAT_RECENT_MESSAGE_ROUTING_KEY);
    }
    @Bean
    public Binding readCheckRequestBinding(Queue readCheckRequestQueue, TopicExchange readCheckExchange) {
        return BindingBuilder.bind(readCheckRequestQueue).to(readCheckExchange).with(READ_CHECK_REQUEST_ROUTING_KEY);
    }
    @Bean
    public Binding readCheckBulkRequestBinding(Queue readCheckBulkResponseQueue, TopicExchange readCheckExchange) {
        return BindingBuilder.bind(readCheckBulkResponseQueue).to(readCheckExchange).with(READ_CHECK_BULK_RESPONSE_ROUTING_KEY);
    }
    @Bean
    public Binding readCheckResponseBinding(Queue readCheckResponseQueue, TopicExchange readCheckExchange) {
        return BindingBuilder.bind(readCheckResponseQueue).to(readCheckExchange).with(READ_CHECK_RESPONSE_ROUTING_KEY);
    }

    // Timer Exchange와 Queue 바인딩
    @Bean
    public Binding timerBinding(Queue timerQueue, TopicExchange timerExchange) {
        return BindingBuilder.bind(timerQueue).to(timerExchange).with(TIMER_ROUTING_KEY);
    }
    @Bean
    public Binding chatConnectBinding(Queue chatConnectQueue, TopicExchange exchange) {
        return BindingBuilder.bind(chatConnectQueue).to(exchange).with(CHAT_CONNECT_ROUTING_KEY);
    }
    @Bean
    public Binding chatDisconnectBinding(Queue chatDisconnectQueue, TopicExchange exchange) {
        return BindingBuilder.bind(chatDisconnectQueue).to(exchange).with(CHAT_DISCONNECT_ROUTING_KEY);
    }

    // SSE Exchange와 Queue 바인딩
    @Bean
    public Binding statusBinding(Queue sseQueue, FanoutExchange sseExchange) {
        return BindingBuilder.bind(sseQueue).to(sseExchange);
    }

    /* messageConverter를 커스터마이징 하기 위해 Bean 새로 등록 */
    @Bean
    public RabbitTemplate rabbitTemplate(){
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory());
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    //Spring에서 자동생성해주는 ConnectionFactory는 SimpleConnectionFactory인가? 그건데
    //여기서 사용하는 건 CachingConnectionFacotry라 새로 등록해줌
    @Bean
    public ConnectionFactory connectionFactory(){
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitmqHost);
        factory.setUsername(rabbitmqUsername);
        factory.setPassword(rabbitmqPassword);
        return factory;
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter(){
        //LocalDateTime serializable을 위해
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        objectMapper.registerModule(dateTimeModule());

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);

        return converter;
    }

    @Bean
    public Module dateTimeModule(){
        return new JavaTimeModule();
    }
}
