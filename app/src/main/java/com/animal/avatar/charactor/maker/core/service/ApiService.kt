package com.animal.avatar.charactor.maker.core.service
import com.animal.avatar.charactor.maker.data.model.PartAPI
import retrofit2.Response
import retrofit2.http.GET
interface ApiService {
    @GET("/api/ST169_AnimalOcMaker_v2")
    suspend fun getAllData(): Response<Map<String, List<PartAPI>>>
}