package com.wooly.infinite_of_cute_animals.exposal.service

import com.wooly.infinite_of_cute_animals.exposal.model.AnimalFeedResponse
import com.wooly.infinite_of_cute_animals.exposal.model.AnimalImage
import com.wooly.infinite_of_cute_animals.management.model.AnimalImageEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class FeedService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Value("\${app.cache.animal-feed-key}")
    private lateinit var feedKey: String

    @Value("\${app.streaming.batch-size}")
    private var defaultBatchSize: Int = 20


    /**
     * 무한 스크롤용 동물 피드 조회
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기 (기본값: 20)
     * @return 동물 이미지 리스트와 페이징 정보
     */
    fun getAnimalFeed(page: Int, size: Int = defaultBatchSize): AnimalFeedResponse {
        try {
            val startIndex = (page * size).toLong()
            val endIndex = startIndex + size - 1

            // Redis에서 페이지네이션으로 피드 데이터 조회
            val feedData = redisTemplate.opsForList()
                .range(feedKey, startIndex, endIndex)
                ?: emptyList()

            // AnimalImageEvent를 AnimalImage로 변환
            val animalImages = feedData.mapNotNull { data ->
                try {
                    if (data is AnimalImageEvent) {
                        AnimalImage.from(data)
                    } else {
                        log.warn("⚠️ 예상하지 못한 데이터 타입: ${data::class.java}")
                        null
                    }
                } catch (e: Exception) {
                    log.error("🚨 데이터 변환 실패", e)
                    null
                }
            }

            // 전체 피드 크기 조회
            val totalSize = redisTemplate.opsForList().size(feedKey) ?: 0L
            val hasNext = (startIndex + size) < totalSize

            log.debug("📱 피드 조회 완료 - Page: $page, Size: ${animalImages.size}, HasNext: $hasNext")

            return AnimalFeedResponse(
                images = animalImages,
                page = page,
                size = size,
                totalElements = totalSize,
                hasNext = hasNext,
                isEmpty = animalImages.isEmpty()
            )

        } catch (e: Exception) {
            log.error("🚨 피드 조회 중 오류 발생 - Page: $page, Size: $size", e)
            return AnimalFeedResponse.empty(page, size)
        }
    }

    /**
     * 최신 동물 이미지들 조회 (페이지네이션 없이)
     * @param limit 조회할 최대 개수
     */
    fun getLatestImages(limit: Int = 10): List<AnimalImage> {
        return try {
            val feedData = redisTemplate.opsForList()
                .range(feedKey, 0, (limit - 1).toLong())
                ?: emptyList()

            feedData.mapNotNull { data ->
                try {
                    if (data is AnimalImageEvent) {
                        AnimalImage.from(data)
                    } else null
                } catch (e: Exception) {
                    log.error("🚨 최신 이미지 변환 실패", e)
                    null
                }
            }
        } catch (e: Exception) {
            log.error("🚨 최신 이미지 조회 실패", e)
            emptyList()
        }
    }
}