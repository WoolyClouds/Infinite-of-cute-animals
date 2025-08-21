package com.wooly.infinite_of_cute_animals.management.schedule


import com.wooly.infinite_of_cute_animals.management.constant.AnimalImageUrls
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.util.*
import javax.imageio.ImageIO
import kotlin.random.Random

@Component
class ImageCacheScheduler(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val DAILY_CACHE_SIZE = 100
        private const val MAX_IMAGE_SIZE_BYTES = 2_097_152 // 2MB 제한
        private const val CACHE_TTL_HOURS = 25L // 25시간 (다음날 갱신 전까지 여유)
        private const val DAILY_IMAGES_KEY_PREFIX = "daily_images"
        private const val IMAGE_DATA_KEY_PREFIX = "image_data"
    }

    /**
     * 매일 자정에 실행 - 일일 이미지 캐시 갱신
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun refreshDailyImageCache() {
        val today = LocalDate.now().toString()
        logger.info("🗓️ [$today] 일일 이미지 캐시 갱신 시작...")

        try {
            // 1. 어제 캐시 정리
            clearPreviousDayCache()

            // 2. 오늘의 랜덤 100개 선택 (날짜 기반 시드로 동일한 결과 보장)
            val todaySelectedUrls = selectTodayImages(today)
            logger.info("📋 오늘 선택된 이미지: ${todaySelectedUrls.size}개")

            // 3. 선택된 이미지 URL 목록 Redis에 저장
            saveTodayImageList(today, todaySelectedUrls)

            // 4. 각 이미지를 다운로드하여 Redis에 캐싱
            cacheSelectedImages(today, todaySelectedUrls)

            logger.info("✅ [$today] 일일 이미지 캐시 갱신 완료!")

        } catch (e: Exception) {
            logger.error("❌ 일일 이미지 캐시 갱신 실패: ${e.message}", e)
        }
    }

    /**
     * 애플리케이션 시작 시 오늘의 캐시가 없으면 즉시 실행
     */
    @Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 5000) // 5초 후 한 번만 실행
    fun initializeTodayCache() {
        val today = LocalDate.now().toString()
        val todayImagesKey = "$DAILY_IMAGES_KEY_PREFIX:$today"

        val exists = redisTemplate.hasKey(todayImagesKey)
        if (exists != true) {
            logger.info("🚀 오늘의 이미지 캐시가 없음. 즉시 생성 중...")
            refreshDailyImageCache()
        } else {
            val cachedCount = redisTemplate.opsForList().size(todayImagesKey) ?: 0
            logger.info("🎯 오늘의 이미지 캐시 존재: ${cachedCount}개")
        }
    }

    /**
     * 날짜를 시드로 사용하여 매일 동일한 100개 이미지 선택
     */
    private fun selectTodayImages(date: String): List<String> {
        val seed = date.hashCode().toLong()
        val random = Random(seed)

        return AnimalImageUrls.IMAGE_URLS
            .shuffled(random)
            .take(DAILY_CACHE_SIZE)
    }

    /**
     * 오늘 선택된 이미지 URL 목록을 Redis에 저장
     */
    private fun saveTodayImageList(date: String, imageUrls: List<String>) {
        val todayImagesKey = "$DAILY_IMAGES_KEY_PREFIX:$date"

        try {
            // 기존 목록 삭제 후 새로 저장
            redisTemplate.delete(todayImagesKey)
            redisTemplate.opsForList().rightPushAll(todayImagesKey, imageUrls)
            redisTemplate.expire(todayImagesKey, Duration.ofHours(CACHE_TTL_HOURS))

            logger.info("📝 오늘의 이미지 목록 저장 완료: $todayImagesKey")
        } catch (e: Exception) {
            logger.error("❌ 이미지 목록 저장 실패: ${e.message}")
        }
    }

    /**
     * 선택된 이미지들을 다운로드하여 Redis에 캐싱
     */
    private fun cacheSelectedImages(date: String, imageUrls: List<String>) {
        var successCount = 0
        var skipCount = 0
        var failCount = 0

        imageUrls.forEachIndexed { index, imageUrl ->
            try {
                val imageId = extractImageId(imageUrl)
                val imageCacheKey = "$IMAGE_DATA_KEY_PREFIX:$date:$imageId"

                // 이미지 다운로드
                val imageBytes = downloadImage(imageUrl)

                if (imageBytes != null) {
                    // 크기 확인
                    if (imageBytes.size <= MAX_IMAGE_SIZE_BYTES) {
                        // Redis에 저장
                        redisTemplate.opsForValue().set(
                            imageCacheKey,
                            imageBytes,
                            Duration.ofHours(CACHE_TTL_HOURS)
                        )

                        successCount++
                        logger.debug("💾 이미지 캐시 성공: $imageId (${formatBytes(imageBytes.size)})")
                    } else {
                        skipCount++
                        logger.warn("⚠️ 이미지 크기 초과로 스킵: $imageId (${formatBytes(imageBytes.size)})")
                    }
                } else {
                    failCount++
                    logger.warn("⚠️ 이미지 다운로드 실패: $imageUrl")
                }

                // 진행률 로그 (10개마다)
                if ((index + 1) % 10 == 0) {
                    logger.info("📊 진행률: ${index + 1}/${imageUrls.size} (성공: $successCount, 스킵: $skipCount, 실패: $failCount)")
                }

                // 서버 부하 방지를 위한 딜레이 (100ms)
                Thread.sleep(100)

            } catch (e: Exception) {
                failCount++
                logger.error("❌ 이미지 캐싱 중 오류: $imageUrl - ${e.message}")
            }
        }

        logger.info("🎯 이미지 캐싱 완료 - 성공: $successCount, 스킵: $skipCount, 실패: $failCount")
    }

    /**
     * 어제 캐시 정리
     */
    private fun clearPreviousDayCache() {
        val yesterday = LocalDate.now().minusDays(1).toString()

        try {
            // 어제 이미지 목록 삭제
            val yesterdayImagesKey = "$DAILY_IMAGES_KEY_PREFIX:$yesterday"
            redisTemplate.delete(yesterdayImagesKey)

            // 어제 이미지 데이터들 삭제
            val pattern = "$IMAGE_DATA_KEY_PREFIX:$yesterday:*"
            val keys = redisTemplate.keys(pattern)

            if (keys.isNotEmpty()) {
                redisTemplate.delete(keys)
                logger.info("🗑️ 어제 캐시 삭제 완료: ${keys.size}개")
            }

        } catch (e: Exception) {
            logger.warn("⚠️ 어제 캐시 정리 중 오류: ${e.message}")
        }
    }

    /**
     * URL에서 이미지 다운로드
     */
    private fun downloadImage(imageUrl: String): ByteArray? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 10000 // 10초
            connection.readTimeout = 15000    // 15초
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; WoolyCute/1.0)")

            connection.getInputStream().use { inputStream ->
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.toByteArray()
            }
        } catch (e: Exception) {
            logger.debug("다운로드 실패: $imageUrl - ${e.message}")
            null
        }
    }

    /**
     * URL에서 이미지 ID 추출
     */
    private fun extractImageId(imageUrl: String): String {
        return imageUrl.substringAfterLast("/").substringBeforeLast(".")
    }

    /**
     * 바이트 크기를 읽기 쉬운 형태로 포맷
     */
    private fun formatBytes(bytes: Int): String {
        return when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}