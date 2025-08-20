package com.wooly.infinite_of_cute_animals.management.service

import com.wooly.infinite_of_cute_animals.management.model.AnimalImageEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate

@Service
class FeedConsumer(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Value("\${app.cache.animal-feed-key}")
    private lateinit var feedKey: String

    @Value("\${app.cache.feed-max-size}")
    private var maxFeedSize: Long = 1000

    @Value("\${app.cache.stats-key-prefix}")
    private lateinit var statsKeyPrefix: String

    /**
     * Kafka에서 동물 이미지 이벤트를 수신하여 Redis 피드에 저장
     */
    @KafkaListener(topics = ["\${app.kafka.topics.animal-stream}"])
    fun handleAnimalImageEvent(animalImageEvent: AnimalImageEvent) {
        try {
            log.debug("📥 동물 이미지 이벤트 수신: ${animalImageEvent.id}")

            // 이벤트 유효성 검증
            if (!animalImageEvent.isValid()) {
                log.warn("⚠️ 유효하지 않은 이벤트: ${animalImageEvent.id}")
                return
            }

            // Redis 무한 스크롤 피드에 추가 (최신 이미지가 앞에 오도록)
            redisTemplate.opsForList().apply {
                leftPush(feedKey, animalImageEvent)
                trim(feedKey, 0, maxFeedSize - 1) // 최대 크기 유지
            }

            // 개별 이미지 캐시 (24시간 유지)
            redisTemplate.opsForValue().set(
                "animal:${animalImageEvent.id}",
                animalImageEvent,
                Duration.ofDays(1)
            )

            // 일일 통계 업데이트
            updateDailyStats()

            log.debug("✅ 피드에 동물 이미지 추가 완료: ${animalImageEvent.id}")

        } catch (e: Exception) {
            log.error("🚨 동물 이미지 이벤트 처리 실패: ${animalImageEvent.id}", e)
        }
    }

    /**
     * 일일 스트리밍 통계 업데이트
     */
    private fun updateDailyStats() {
        try {
            val today = LocalDate.now().toString()
            val todayKey = "$statsKeyPrefix:streamed:$today"
            val totalKey = "$statsKeyPrefix:total-streamed"

            // 오늘 스트리밍 수 증가
            redisTemplate.opsForValue().increment(todayKey)
            redisTemplate.expire(todayKey, Duration.ofDays(7)) // 7일 보관

            // 전체 스트리밍 수 증가
            redisTemplate.opsForValue().increment(totalKey)

        } catch (e: Exception) {
            log.error("🚨 통계 업데이트 실패", e)
        }
    }

    /**
     * 현재 피드 크기 반환
     */
    fun getCurrentFeedSize(): Long {
        return try {
            redisTemplate.opsForList().size(feedKey) ?: 0L
        } catch (e: Exception) {
            log.error("🚨 피드 크기 조회 실패", e)
            0L
        }
    }

    /**
     * 오늘 스트리밍된 이미지 수 반환
     */
    fun getTodayStreamCount(): Long {
        return try {
            val today = LocalDate.now().toString()
            val todayKey = "$statsKeyPrefix:streamed:$today"
            redisTemplate.opsForValue().get(todayKey)?.toString()?.toLong() ?: 0L
        } catch (e: Exception) {
            log.error("🚨 오늘 통계 조회 실패", e)
            0L
        }
    }

    /**
     * 전체 스트리밍된 이미지 수 반환
     */
    fun getTotalStreamCount(): Long {
        return try {
            val totalKey = "$statsKeyPrefix:total-streamed"
            redisTemplate.opsForValue().get(totalKey)?.toString()?.toLong() ?: 0L
        } catch (e: Exception) {
            log.error("🚨 전체 통계 조회 실패", e)
            0L
        }
    }
}