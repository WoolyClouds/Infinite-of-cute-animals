package com.wooly.infinite_of_cute_animals.exposal.model

data class AnimalFeedResponse(
    val images: List<AnimalImage>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
    val isEmpty: Boolean
) {
    companion object {
        fun empty(page: Int, size: Int): AnimalFeedResponse {
            return AnimalFeedResponse(
                images = emptyList(),
                page = page,
                size = size,
                totalElements = 0L,
                hasNext = false,
                isEmpty = true
            )
        }
    }
}
