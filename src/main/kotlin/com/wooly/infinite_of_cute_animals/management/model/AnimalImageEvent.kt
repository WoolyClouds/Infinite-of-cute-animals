package com.wooly.infinite_of_cute_animals.management.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant
import java.util.UUID

data class AnimalImageEvent(
    val id: String = UUID.randomUUID().toString(), //이미지 고유 ID
    val imageUrl: String, //동물 이미지 URL
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    val timestamp: Instant = Instant.now() //이벤트 생성 시간
) {
    /**
    * 이벤트가 유효한지 검증
    */
    fun isValid(): Boolean {
        return id.isNotBlank() && imageUrl.isNotBlank()
    }
}