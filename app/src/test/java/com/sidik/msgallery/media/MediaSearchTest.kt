package com.sidik.msgallery.media

import android.net.Uri
import org.mockito.Mockito.mock
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSearchTest {
    private val items = listOf(
        MediaItem(1, mock(Uri::class.java), "Holiday.jpg", "image/jpeg", 1, 100, MediaType.IMAGE, 100, 100, 0),
        MediaItem(2, mock(Uri::class.java), "Movie.mp4", "video/mp4", 2, 200, MediaType.VIDEO, 100, 100, 1000)
    )

    @Test fun emptyQueryReturnsAll() {
        assertEquals(2, MediaSearch().filter(items, "").size)
    }

    @Test fun queryMatchesFilenameCaseInsensitively() {
        assertEquals(1, MediaSearch().filter(items, "HOLIDAY").single().id)
    }

    @Test fun videoFilterReturnsOnlyVideos() {
        assertEquals(1, MediaSearch().filter(items, "", videosOnly = true).single().id)
    }
}
