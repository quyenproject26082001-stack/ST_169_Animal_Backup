package com.animal.avatar.charactor.maker.data.model

data class PartAPI(
    val position: String,
    val parts: String,
    val colorArray: String,
    val quantity: Int,
    val level: Int,
    val data: String? = null  // category: "Cat", "Dog", "Dragon", "Pony", "Animal"
)

data class DataAPI(val name: String, val parts: List<PartAPI>)
