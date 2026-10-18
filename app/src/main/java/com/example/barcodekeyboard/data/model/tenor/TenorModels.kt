package com.example.barcodekeyboard.data.model.tenor

import com.google.gson.annotations.SerializedName

/**
 * Data models for Google Tenor GIF API v2 response.
 */
data class TenorResponse(
    @SerializedName("results")
    val results: List<TenorResult> = emptyList(),
    @SerializedName("next")
    val next: String? = null
)

data class TenorResult(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("content_description")
    val contentDescription: String? = null,
    @SerializedName("media_formats")
    val mediaFormats: MediaFormats? = null
) {
    /**
     * URL suitable for lightweight thumbnail rendering in RecyclerView.
     */
    val previewUrl: String
        get() = mediaFormats?.nanogif?.url
            ?: mediaFormats?.tinygif?.url
            ?: mediaFormats?.gif?.url
            ?: ""

    /**
     * URL suitable for committing rich content to chat applications.
     */
    val fullGifUrl: String
        get() = mediaFormats?.gif?.url
            ?: mediaFormats?.tinygif?.url
            ?: previewUrl
}

data class MediaFormats(
    @SerializedName("nanogif")
    val nanogif: MediaFormat? = null,
    @SerializedName("tinygif")
    val tinygif: MediaFormat? = null,
    @SerializedName("gif")
    val gif: MediaFormat? = null
)

data class MediaFormat(
    @SerializedName("url")
    val url: String = "",
    @SerializedName("dims")
    val dims: List<Int>? = null,
    @SerializedName("size")
    val size: Int? = null
)
