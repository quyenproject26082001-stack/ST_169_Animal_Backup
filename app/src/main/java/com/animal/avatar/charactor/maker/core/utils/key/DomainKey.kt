package com.animal.avatar.charactor.maker.core.utils.key

object DomainKey {

    const val BASE_URL = "https://lvt-api-site.io.vn"
    const val BASE_URL_PREVENTIVE = "https://lvtglobal.site"
    const val SUB_DOMAIN = "/public/app/ST169_AnimalOcMaker_v2"
    const val HTTP = "https://"

    const val AVATAR_CHARACTER_API = "avatar.png"
    const val LAYER_EXTENSION = ".png"
    const val IMAGE_NAVIGATION = "nav.png"

    fun getAddCharacterAssetUrl(folder: String): String {
        return "$BASE_URL$SUB_DOMAIN/bg/$folder"
    }

}
