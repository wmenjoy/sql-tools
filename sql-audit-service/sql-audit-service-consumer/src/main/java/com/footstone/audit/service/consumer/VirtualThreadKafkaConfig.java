package com.footstone.audit.service.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class VirtualThreadKafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
        virtualThreadKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            AuditEventErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setListenerTaskExecutor(
            new VirtualThreadTaskExecutor("kafka-virtual-"));
        return factory;
    }
}
