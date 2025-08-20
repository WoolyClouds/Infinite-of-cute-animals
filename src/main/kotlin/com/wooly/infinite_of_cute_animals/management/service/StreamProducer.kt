package com.wooly.infinite_of_cute_animals.management.service

import com.wooly.infinite_of_cute_animals.management.model.AnimalImageEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.*

@Service
@EnableScheduling
class StreamProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val animalImageCacheService: ImageCacheService

) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Value("\${app.kafka.topics.animal-stream}")
    private lateinit var topicName: String

    @Value("\${app.streaming.interval}")
    private var streamingInterval: Long = 3000


    /**
     * 주기적으로 랜덤 동물 이미지를 Kafka로 스트리밍
     * application.yml의 app.streaming.interval 설정값에 따라 실행
     */
    @Scheduled(fixedDelayString = "\${app.streaming.interval}")
    fun streamRandomAnimalImage() {
        try {
            // 캐시에서 랜덤 이미지 URL 가져오기
            val randomImageUrl = animalImageCacheService.getRandomImageUrl()

            if (randomImageUrl != null) {
                // AnimalImageEvent 생성
                val animalImageEvent = AnimalImageEvent(
                    id = UUID.randomUUID().toString(),
                    imageUrl = randomImageUrl
                )

                // Kafka로 이벤트 전송
                kafkaTemplate.send(topicName, animalImageEvent.id, animalImageEvent)
                    .whenComplete { result, exception ->
                        if (exception != null) {
                            log.error("🚨 동물 이미지 스트리밍 실패: ${animalImageEvent.id}", exception)
                        } else {
                            log.debug("🐾 동물 이미지 스트리밍 성공: ${animalImageEvent.id}")
                        }
                    }

            } else {
                log.warn("⚠️ 캐시에서 동물 이미지를 찾을 수 없습니다. 캐시 새로고침이 필요할 수 있습니다.")
            }

        } catch (e: Exception) {
            log.error("🚨 동물 이미지 스트리밍 중 오류 발생", e)
        }
    }

    /**
     * 수동으로 특정 이미지 URL 스트리밍
     */
    fun streamImage(imageUrl: String): String {
        return try {
            val animalImageEvent = AnimalImageEvent(
                id = UUID.randomUUID().toString(),
                imageUrl = imageUrl
            )

            kafkaTemplate.send(topicName, animalImageEvent.id, animalImageEvent)
            log.info("🎯 수동 스트리밍 완료: ${animalImageEvent.id}")

            animalImageEvent.id
        } catch (e: Exception) {
            log.error("🚨 수동 스트리밍 실패: $imageUrl", e)
            throw e
        }
    }

    /**
     * 여러 이미지를 배치로 스트리밍
     */
    fun streamImageBatch(imageUrls: List<String>): List<String> {
        val streamedIds = mutableListOf<String>()

        imageUrls.forEach { imageUrl ->
            try {
                val id = streamImage(imageUrl)
                streamedIds.add(id)
            } catch (e: Exception) {
                log.error("🚨 배치 스트리밍 중 실패한 이미지: $imageUrl", e)
            }
        }

        log.info("📦 배치 스트리밍 완료: ${streamedIds.size}/${imageUrls.size}")
        return streamedIds
    }
}