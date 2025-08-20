package com.wooly.infinite_of_cute_animals.common.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer


@EnableKafka
@Configuration
class KafkaConfig {
    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.consumer.group-id}")
    private lateinit var groupId: String

    /**
     * Kafka Producer 설정
     */
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java

        // 성능 최적화 설정
        configProps[ProducerConfig.ACKS_CONFIG] = "1"
        configProps[ProducerConfig.RETRIES_CONFIG] = 3
        configProps[ProducerConfig.BATCH_SIZE_CONFIG] = 16384
        configProps[ProducerConfig.LINGER_MS_CONFIG] = 5
        configProps[ProducerConfig.BUFFER_MEMORY_CONFIG] = 33554432

        // JSON 직렬화 설정
        configProps[JsonSerializer.ADD_TYPE_INFO_HEADERS] = false

        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }

    /**
     * Kafka Consumer 설정
     */
    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val configProps = mutableMapOf<String, Any>()
        configProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configProps[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        configProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java

        // Consumer 안정성 설정
        configProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        configProps[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = true
        configProps[ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG] = 1000
        configProps[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = 30000
        configProps[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = 3000

        // JSON 역직렬화 설정
        configProps[JsonDeserializer.TRUSTED_PACKAGES] = "com.wooly.infinite_of_cute_animals"
        configProps[JsonDeserializer.USE_TYPE_INFO_HEADERS] = false
        configProps[JsonDeserializer.VALUE_DEFAULT_TYPE] = "com.wooly.infinite_of_cute_animals.management.model.AnimalImageEvent"

        return DefaultKafkaConsumerFactory(configProps)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()

        // 동시성 설정 (단일 인스턴스이므로 1로 설정)
        factory.setConcurrency(1)

        return factory
    }
}