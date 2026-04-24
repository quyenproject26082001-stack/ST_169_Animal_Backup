package com.animal.avatar.charactor.maker.data.model.custom

import com.animal.avatar.charactor.maker.data.model.custom.ColorModel

data class LayerModel(
    val image: String,
    val isMoreColors: Boolean = false,
    var listColor: ArrayList<ColorModel> = arrayListOf(),
    val thumb: String = ""
)