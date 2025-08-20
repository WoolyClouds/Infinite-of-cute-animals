package com.wooly.infinite_of_cute_animals.common.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {

    /**
     * Redis Template 설정
     * - Key: String 직렬화
     * - Value: JSON 직렬화 (Kotlin 객체 지원)
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory

        // ObjectMapper 설정 (Kotlin + Java Time 지원)
        val objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            // ISO 8601 날짜 형식 사용
            findAndRegisterModules()
        }

        // JSON 직렬화 설정
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
        val stringSerializer = StringRedisSerializer()

        // Key는 String, Value는 JSON으로 직렬화
        template.keySerializer = stringSerializer
        template.valueSerializer = jsonSerializer
        template.hashKeySerializer = stringSerializer
        template.hashValueSerializer = jsonSerializer

        // 트랜잭션 지원 활성화
        template.setEnableTransactionSupport(true)

        return template
    }

//    /**
//     * String 전용 Redis Template
//     * - 간단한 카운터, 플래그 등에 사용
//     */
//    @Bean
//    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
//        val template = RedisTemplate<String, String>()
//        template.connectionFactory = connectionFactory
//
//        val stringSerializer = StringRedisSerializer()
//        template.keySerializer = stringSerializer
//        template.valueSerializer = stringSerializer
//        template.hashKeySerializer = stringSerializer
//        template.hashValueSerializer = stringSerializer
//
//        return template
//    }
}