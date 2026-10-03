package org.example.connectcg_be.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // Exchange constants
    public static final String EXCHANGE_DIRECT = "connect.direct.exchange";
    public static final String EXCHANGE_DLX = "connect.dlx.exchange";

    // Queue constants
    public static final String QUEUE_EMAIL = "connect.email.queue";
    public static final String QUEUE_AI = "connect.ai.queue";
    public static final String QUEUE_MEDIA = "connect.media.queue";
    public static final String QUEUE_DEAD_LETTER = "connect.dead-letter.queue";

    // Routing key constants
    public static final String ROUTING_KEY_EMAIL = "email.auth";
    public static final String ROUTING_KEY_AI = "ai.moderate";
    public static final String ROUTING_KEY_MEDIA = "media.process";
    public static final String ROUTING_KEY_DLX_EMAIL = "dlx.email";
    public static final String ROUTING_KEY_DLX_AI = "dlx.ai";
    public static final String ROUTING_KEY_DLX_MEDIA = "dlx.media";
    public static final String ROUTING_KEY_DLX_ALL = "dlx.#";

    // 1. Exchanges
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(EXCHANGE_DIRECT, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }

    // 2. Queues
    @Bean
    public Queue emailQueue() {
        Map<String, Object> args = new HashMap<>();
        // Configure Dead Letter Exchange for failed messages
        args.put("x-dead-letter-exchange", EXCHANGE_DLX);
        args.put("x-dead-letter-routing-key", ROUTING_KEY_DLX_EMAIL);
        return new Queue(QUEUE_EMAIL, true, false, false, args);
    }

    @Bean
    public Queue aiQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EXCHANGE_DLX);
        args.put("x-dead-letter-routing-key", ROUTING_KEY_DLX_AI);
        return new Queue(QUEUE_AI, true, false, false, args);
    }

    @Bean
    public Queue mediaQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EXCHANGE_DLX);
        args.put("x-dead-letter-routing-key", ROUTING_KEY_DLX_MEDIA);
        return new Queue(QUEUE_MEDIA, true, false, false, args);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(QUEUE_DEAD_LETTER, true, false, false);
    }

    // 3. Bindings
    @Bean
    public Binding emailBinding(Queue emailQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(emailQueue).to(directExchange).with(ROUTING_KEY_EMAIL);
    }

    @Bean
    public Binding aiBinding(Queue aiQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(aiQueue).to(directExchange).with(ROUTING_KEY_AI);
    }

    @Bean
    public Binding mediaBinding(Queue mediaQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(mediaQueue).to(directExchange).with(ROUTING_KEY_MEDIA);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY_DLX_ALL);
    }

    // 4. Message Converter (JSON)
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // 5. RabbitTemplate
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
