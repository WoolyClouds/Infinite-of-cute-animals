package com.wooly.infinite_of_cute_animals.management.controller

import com.wooly.infinite_of_cute_animals.management.schedule.ImageCacheScheduler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController(
    private val imageCacheScheduler: ImageCacheScheduler
) {

    @PostMapping("/refresh-daily-cache")
    fun manualRefresh() {
        imageCacheScheduler.refreshDailyImageCache()
    }

}