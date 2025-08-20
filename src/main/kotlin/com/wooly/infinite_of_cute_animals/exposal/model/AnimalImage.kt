package com.wooly.infinite_of_cute_animals.exposal.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

data class AnimalImage(
    val id: String, //이미지 고유 ID
    val imageUrl: String, //동물 이미지 URL
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    val createdAt: Instant //이벤트 생성 시간
) {
    companion object {
        /**
         * AnimalImageEvent를 AnimalImage로 변환
         */
        fun from(event: com.wooly.infinite_of_cute_animals.management.model.AnimalImageEvent): AnimalImage {
            return AnimalImage(
                id = event.id,
                imageUrl = event.imageUrl,
                createdAt = event.timestamp
            )
        }
    }
}