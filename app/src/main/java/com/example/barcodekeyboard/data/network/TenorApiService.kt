package com.example.barcodekeyboard.data.network

import com.example.barcodekeyboard.data.model.tenor.TenorResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Tenor API v2.
 */
interface TenorApiService {

    @GET("v2/search")
    suspend fun searchGifs(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("client_key") clientKey: String = "one_keyboard_android",
        @Query("limit") limit: Int = 30,
        @Query("media_filter") mediaFilter: String = "nanogif,tinygif,gif",
        @Query("contentfilter") contentFilter: String = "medium",
        @Query("pos") pos: String? = null
    ): Response<TenorResponse>

    @GET("v2/featured")
    suspend fun getFeaturedGifs(
        @Query("key") apiKey: String,
        @Query("client_key") clientKey: String = "one_keyboard_android",
        @Query("limit") limit: Int = 30,
        @Query("media_filter") mediaFilter: String = "nanogif,tinygif,gif",
        @Query("contentfilter") contentFilter: String = "medium",
        @Query("pos") pos: String? = null
    ): Response<TenorResponse>
}
