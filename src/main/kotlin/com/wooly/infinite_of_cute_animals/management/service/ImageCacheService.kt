package com.wooly.infinite_of_cute_animals.management.service

import com.wooly.infinite_of_cute_animals.management.constant.AnimalImageUrls
import com.wooly.infinite_of_cute_animals.management.model.AnimalImageEvent
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ImageCacheService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${app.cache.animal-pool-key}")
    private lateinit var animalPoolKey: String

    @Value("\${app.cache.animal-feed-key}")
    private lateinit var animalFeedKey: String

    @Value("\${app.cache.feed-max-size}")
    private var feedMaxSize: Long = 1000

    /**
     * 애플리케이션 시작 시 이미지 풀을 Redis에 초기화
     */
    @PostConstruct
    fun initializeImagePool() {
        try {
            val poolSize = redisTemplate.opsForList().size(animalPoolKey) ?: 0

            if (poolSize == 0L) {
                logger.info("🐾 동물 이미지 풀 초기화 중...")
                refreshImagePool()
            } else {
                logger.info("🐾 기존 동물 이미지 풀 발견: ${poolSize}개")
            }
        } catch (e: Exception) {
            logger.warn("⚠️ Redis 연결 실패 - 로컬 개발 모드로 실행: ${e.message}")
        }
    }

    /**
     * 이미지 풀을 새로고침 (application.yml의 URL 목록 사용)
     */
    fun refreshImagePool() {
        if (AnimalImageUrls.IMAGE_URLS.isEmpty()) {
            logger.warn("⚠️ 설정된 동물 이미지 URL이 없습니다")
            return
        }

        try {
            // 기존 풀 삭제하고 새로 생성
            redisTemplate.delete(animalPoolKey)
            redisTemplate.opsForList().rightPushAll(animalPoolKey, AnimalImageUrls.IMAGE_URLS)

            // 24시간 TTL 설정
            redisTemplate.expire(animalPoolKey, Duration.ofHours(24))

            logger.info("✅ 동물 이미지 풀 갱신 완료: ${AnimalImageUrls.IMAGE_URLS.size}개")
        } catch (e: Exception) {
            logger.error("❌ 이미지 풀 갱신 실패: ${e.message}")
        }
    }

    /**
     * 랜덤 동물 이미지 URL 반환
     */
    fun getRandomImageUrl(): String? {
        return try {
            val poolSize = redisTemplate.opsForList().size(animalPoolKey) ?: 0

            if (poolSize == 0L) {
                logger.warn("⚠️ 이미지 풀이 비어있음. 풀 새로고침 중...")
                refreshImagePool()
                // 재시도
                val newPoolSize = redisTemplate.opsForList().size(animalPoolKey) ?: 0
                if (newPoolSize == 0L) return null

                val randomIndex = (0 until newPoolSize).random()
                redisTemplate.opsForList().index(animalPoolKey, randomIndex) as? String
            } else {
                val randomIndex = (0 until poolSize).random()
                redisTemplate.opsForList().index(animalPoolKey, randomIndex) as? String
            }
        } catch (e: Exception) {
            logger.error("❌ 랜덤 이미지 URL 조회 실패: ${e.message}")
            // Redis 실패 시 로컬 리스트에서 랜덤 반환
            AnimalImageUrls.IMAGE_URLS.randomOrNull()
        }
    }

    /**
     * 새로운 동물 이미지를 피드에 추가 (Kafka Consumer에서 호출)
     */
    fun addToFeed(animalImage: AnimalImageEvent) {
        try {
            // 피드 맨 앞에 추가 (최신순)
            redisTemplate.opsForList().leftPush(animalFeedKey, animalImage)

            // 최대 크기 제한
            redisTemplate.opsForList().trim(animalFeedKey, 0, feedMaxSize - 1)

            logger.debug("📸 피드에 이미지 추가: ${animalImage.id}")
        } catch (e: Exception) {
            logger.error("❌ 피드 추가 실패: ${e.message}")
        }
    }

    /**
     * 무한 스크롤을 위한 피드 조회 (페이지네이션)
     */
    fun getFeed(page: Int, size: Int): List<AnimalImageEvent> {
        return try {
            val startIndex = (page * size).toLong()
            val endIndex = startIndex + size - 1

            val feedItems = redisTemplate.opsForList()
                .range(animalFeedKey, startIndex, endIndex)
                ?.filterIsInstance<AnimalImageEvent>()
                ?: emptyList()

            logger.debug("📋 피드 조회: page=$page, size=$size, 결과=${feedItems.size}개")
            feedItems
        } catch (e: Exception) {
            logger.error("❌ 피드 조회 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * 전체 피드 크기 반환
     */
    fun getFeedSize(): Long {
        return try {
            redisTemplate.opsForList().size(animalFeedKey) ?: 0L
        } catch (e: Exception) {
            logger.error("❌ 피드 크기 조회 실패: ${e.message}")
            0L
        }
    }

    /**
     * 다음 페이지 존재 여부 확인
     */
    fun hasNextPage(page: Int, size: Int): Boolean {
        val totalSize = getFeedSize()
        val currentOffset = (page + 1) * size
        return currentOffset < totalSize
    }

    /**
     * 캐시 상태 정보 반환
     */
    fun getCacheStats(): Map<String, Any> {
        return try {
            mapOf(
                "imagePoolSize" to (redisTemplate.opsForList().size(animalPoolKey) ?: 0L),
                "feedSize" to getFeedSize(),
                "configuredImages" to AnimalImageUrls.IMAGE_URLS.size,
                "isRedisConnected" to true
            )
        } catch (e: Exception) {
            mapOf(
                "imagePoolSize" to 0L,
                "feedSize" to 0L,
                "configuredImages" to AnimalImageUrls.IMAGE_URLS.size,
                "isRedisConnected" to false,
                "error" to e
            )
        }
    }

    /**
     * 캐시 초기화 (개발/테스트용)
     */
    fun clearCache() {
        try {
            redisTemplate.delete(animalPoolKey)
            redisTemplate.delete(animalFeedKey)
            logger.info("🗑️ 모든 캐시 삭제 완료")
        } catch (e: Exception) {
            logger.error("❌ 캐시 삭제 실패: ${e.message}")
        }
    }
}