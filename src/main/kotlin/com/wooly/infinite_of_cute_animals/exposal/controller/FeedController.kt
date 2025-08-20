package com.wooly.infinite_of_cute_animals.exposal.controller

import com.wooly.infinite_of_cute_animals.exposal.model.AnimalFeedResponse
import com.wooly.infinite_of_cute_animals.exposal.service.FeedService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@CrossOrigin(origins = ["*"]) // CORS 설정 (필요시 특정 도메인으로 제한)
class FeedController(
    private val feedService: FeedService
) {

    private val log = LoggerFactory.getLogger(FeedController::class.java)

    /**
     * 무한 스크롤 동물 피드 조회
     *
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기 (기본값: 20)
     * @return 동물 이미지 리스트와 페이지 정보
     */
    @GetMapping("/feed")
    fun getAnimalFeed(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<AnimalFeedResponse> {
        log.debug("🐾 동물 피드 요청 - 페이지: $page, 크기: $size")

        return try {
            // 입력값 검증
            if (page < 0 || size <= 0 || size > 100) {
                log.warn("⚠️ 잘못된 파라미터 - 페이지: $page, 크기: $size")
                return ResponseEntity.badRequest().build()
            }

            val feedResponse = feedService.getAnimalFeed(page, size)

            log.debug("✅ 동물 피드 응답 - 이미지 수: ${feedResponse.images.size}, 다음 페이지: ${feedResponse.hasNext}")

            ResponseEntity.ok(feedResponse)

        } catch (e: Exception) {
            log.error("🚨 동물 피드 조회 중 오류 발생", e)
            ResponseEntity.internalServerError().build()
        }
    }
}