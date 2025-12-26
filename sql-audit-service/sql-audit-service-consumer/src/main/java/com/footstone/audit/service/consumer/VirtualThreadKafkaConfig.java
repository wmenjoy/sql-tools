package com.footstone.audit.service.consumer;

import com.footstone.audit.service.consumer.config.KafkaConsumerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka虚拟线程配置.
 *
 * <h2>虚拟线程优势</h2>
 * <ul>
 *   <li>轻量级: 每个虚拟线程只占用很小内存，可以启动数百万个虚拟线程</li>
 *   <li>高吞吐: 减少线程切换开销，提高消息处理吞吐量</li>
 *   <li>简化编程: 使用同步代码风格，避免异步回调的复杂性</li>
 * </ul>
 *
 * <h2>配置说明</h2>
 * <p>所有参数可通过 audit.kafka.consumer.virtual-thread 配置节点调整</p>
 *
 * <h2>系统要求</h2>
 * <p>需要 JDK 21 或更高版本，且需要启用虚拟线程特性</p>
 *
 * @see KafkaConsumerProperties.VirtualThread
 * @since 2.0.0
 */
@Configuration
@ConditionalOnProperty(
    prefix = "audit.kafka.consumer.virtual-thread",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
public class VirtualThreadKafkaConfig {

    /**
     * 创建使用虚拟线程的Kafka监听器容器工厂.
     *
     * @param consumerFactory Kafka消费者工厂
     * @param errorHandler 错误处理器
     * @param properties Kafka消费者配置属性
     * @return 配置好的容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
        virtualThreadKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            AuditEventErrorHandler errorHandler,
            KafkaConsumerProperties properties) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        // 设置手动确认模式
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 设置并发数
        factory.setConcurrency(properties.getVirtualThread().getConcurrency());

        // 配置虚拟线程执行器
        String threadNamePrefix = properties.getVirtualThread().getNamePrefix();
        factory.getContainerProperties().setListenerTaskExecutor(
            new VirtualThreadTaskExecutor(threadNamePrefix));

        log.info("VirtualThreadKafkaListenerContainerFactory initialized with concurrency={}, threadNamePrefix={}",
                properties.getVirtualThread().getConcurrency(), threadNamePrefix);

        return factory;
    }
}
