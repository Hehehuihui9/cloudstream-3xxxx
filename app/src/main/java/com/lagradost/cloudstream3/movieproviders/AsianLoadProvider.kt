package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.TvType

/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */
class AsianLoadProvider : VidstreamProviderTemplate() {
    override var name = "AsianLoad"
    override var mainUrl = "https://asianembed.io"
    override val homePageUrlList = listOf(
        mainUrl,
        "$mainUrl/recently-added-raw",
        "$mainUrl/movies",
        "$mainUrl/kshow",
        "$mainUrl/popular",
        "$mainUrl/ongoing-series"
    )

    override val iv = "9262859232435825".toByteArray()
    override val secretKey = "93422192433952489752342908585752".toByteArray()

    override val supportedTypes = setOf(TvType.AsianDrama)
}
