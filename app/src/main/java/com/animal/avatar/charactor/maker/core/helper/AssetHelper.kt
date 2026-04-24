package com.animal.avatar.charactor.maker.core.helper

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.animal.avatar.charactor.maker.core.utils.key.AssetsKey
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.data.model.custom.ColorModel
import com.animal.avatar.charactor.maker.data.model.custom.CustomizeModel
import com.animal.avatar.charactor.maker.data.model.custom.LayerListModel
import com.animal.avatar.charactor.maker.data.model.custom.LayerModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream


object AssetHelper {
    // Read sub folder
    fun getSubfoldersAsset(context: Context, path: String): ArrayList<String> {
        val allData = context.assets.list(path)
        val sortedData = MediaHelper.sortAsset(allData)?.map { "${AssetsKey.ASSET_MANAGER}/$path/$it" }?.toCollection(ArrayList())
        return sortedData ?: arrayListOf()
    }

    // Read sub folder
    fun getSubfoldersNotDomainAsset(context: Context, path: String): ArrayList<String> {
        val allData = context.assets.list(path)
        val sortedData = MediaHelper.sortAsset(allData)?.map { "${AssetsKey.DATA}/$it" }?.toCollection(ArrayList())
        return sortedData ?: arrayListOf()
    }

    // Read file txt -> json -> T
    inline fun <reified T> readJsonAsset(context: Context, path: String): T? {
        return try {
            val json = context.assets.open(path).bufferedReader().use { it.readText() }
            Gson().fromJson(json, T::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Read file txt -> json -> list
    inline fun <reified T> readTextToJsonAssets(context: Context, path: String): ArrayList<T> {
        return try {
            val json = context.assets.open(path).bufferedReader().use { it.readText() }
            val type = object : TypeToken<ArrayList<T>>() {}.type
            Gson().fromJson(json, type) ?: arrayListOf()
        } catch (e: Exception) {
            e.printStackTrace()
            arrayListOf()
        }
    }

    // Read file -> bitmap
    fun getBitmapFromAsset(context: Context, fileName: String): Bitmap? {
        return try {
            context.assets.open(fileName).use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // file asset -> internal
    fun copyAssetToInternal(context: Context, fileName: String): File? {
        return try {
            val outFile = File(context.filesDir, fileName)
            outFile.parentFile?.mkdirs()

            if (!outFile.exists()) {
                context.assets.open(fileName).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            outFile
        } catch (e: Exception) {
            Log.e("nbhieu", "Copy asset failed: ${e.message}")
            null
        }
    }


    // ---------------------------------------------------------------------------------------------

    fun getDataFromAsset(context: Context) : ArrayList<CustomizeModel> {
        val start = System.currentTimeMillis()
        val customList = ArrayList<CustomizeModel>()
        val assetManager = context.assets

        // "data1, character_2,..."
        val characterList = assetManager.list(AssetsKey.DATA)
        val sortedCharacter = MediaHelper.sortAsset(characterList)
        Log.d("nbhieu", "----------------------------------------------------------------------------------")

        sortedCharacter!!.forEach {
            Log.d("nbhieu", "sortedCharacter: $it")
        }

        Log.d("nbhieu", "----------------------------------------------------------------------------------")

        sortedCharacter.forEachIndexed { indexCharacter, character ->
            val layerListModelList = ArrayList<LayerListModel>()
            Log.d("nbhieu", "indexCharacter: $indexCharacter")
            // "1.30, 2.4, 3.1, 4.22,..."
            val layer = assetManager.list("${AssetsKey.DATA}/${character}")
            val allItems = MediaHelper.sortAsset(layer)?.toCollection(ArrayList()) ?: arrayListOf()

            // Tìm avatar file
            val avatarFile = allItems.find {
                it.equals("avatar.png", ignoreCase = true) ||
                it.equals("avatar.jpg", ignoreCase = true) ||
                it.equals("avatar.webp", ignoreCase = true)
            }

            // Filter chỉ lấy các folder layer có format đúng (dạng 1-13 hoặc 1_3)
            val sortedLayer = allItems.filter { item ->
                val hasHyphen = item.contains("-")
                val hasUnderscore = item.contains("_")

                if (hasHyphen) {
                    val parts = item.split("-")
                    parts.size == 2 && parts[0].toIntOrNull() != null && parts[1].toIntOrNull() != null
                } else if (hasUnderscore) {
                    val parts = item.split("_")
                    parts.size == 2 && parts[0].toIntOrNull() != null && parts[1].toIntOrNull() != null
                } else {
                    false
                }
            }.toCollection(ArrayList())

            val avatar = "${AssetsKey.DATA_ASSET}${character}/${avatarFile ?: "avatar.png"}"
            Log.d("nbhieu", "avatar: $avatar")

            Log.d("nbhieu", "----------------------------------------------------------------------------------")

            for (i in 0 until sortedLayer.size) {
                // Tách 1 và 30 từ "1-30" hoặc "1_30"
                val layerName = sortedLayer[i]
                val position = if (layerName.contains("-")) {
                    layerName.split("-")
                } else {
                    layerName.split("_")
                }
                val positionCustom = position[0].toInt() - 1
                val positionNavigation = position[1].toInt() - 1

                // Lấy folder màu hoặc lấy ảnh nếu không có màu, lấy ảnh navigation
                // data/data1/1.30
                val folderOrImageList = assetManager.list("${AssetsKey.DATA}/${character}/${sortedLayer[i]}")
                val folderOrImageSortedList =
                    MediaHelper.sortAsset(folderOrImageList)?.toCollection(ArrayList()) ?: arrayListOf()
                //Lấy navigation
                val navigationImage =
                    "${AssetsKey.DATA_ASSET}${character}/${sortedLayer[i]}/${folderOrImageSortedList.last()}"
                folderOrImageSortedList.removeAt(folderOrImageSortedList.size - 1)

                // Tách thumb files ra khỏi list, lấy full path, sort theo index
                val thumbFiles = folderOrImageSortedList.filter { it.startsWith("thumb_") }
                    .sortedBy { it.removePrefix("thumb_").removeSuffix(".png").toIntOrNull() ?: 0 }
                    .map { "${AssetsKey.DATA_ASSET}$character/${sortedLayer[i]}/$it" }
                folderOrImageSortedList.removeAll { it.startsWith("thumb_") }

                // Nếu không có folder -> không có màu
                val layer = if (AssetsKey.FIRST_IMAGE.any { it in folderOrImageSortedList[0] }) {
                    getDataNoColor(character, folderOrImageSortedList, sortedLayer[i], thumbFiles)
                } else {
                    getDataColor(assetManager, character, folderOrImageSortedList, sortedLayer[i], thumbFiles)
                }
                val layerListModel = LayerListModel(positionCustom, positionNavigation, navigationImage, layer)
                layerListModelList.add(layerListModel)
            }
            layerListModelList.sortBy { it.positionNavigation }
            customList.add(CustomizeModel(character, avatar, layerListModelList, level = 100))
            Log.d("nbhieu", "----------------------------------------------------------------------------------")
        }
        MediaHelper.writeListToFile(context, ValueKey.DATA_FILE_INTERNAL, customList)
        customList.forEach {
            Log.d("nbhieu", "customList: ${it}")
        }
        Log.d("nbhieu", "count time: ${System.currentTimeMillis() - start}")
        return customList
    }

    private fun getDataNoColor(character: String, filesList: List<String>, folder: String, thumbFiles: List<String> = emptyList()): ArrayList<LayerModel> {
        val layerPath = ArrayList<LayerModel>()
        filesList.forEachIndexed { index, fileName ->
            layerPath.add(
                LayerModel(
                    image = "${AssetsKey.DATA_ASSET}$character/$folder/$fileName",
                    isMoreColors = false,
                    listColor = arrayListOf(),
                    thumb = thumbFiles.getOrElse(index) { "" }
                )
            )
        }
        return layerPath
    }

    private fun getDataColor(
        assetManager: AssetManager, character: String, folderList: List<String>, folder: String, thumbFiles: List<String> = emptyList()
    ): ArrayList<LayerModel> {
        val colorNames = folderList.map { "#$it" }
        val fileList = folderList.map { colorFolder ->
            assetManager.list("${AssetsKey.DATA}/$character/$folder/$colorFolder")?.let {
                MediaHelper.sortAsset(it)
            }?.map { "${AssetsKey.DATA_ASSET}$character/$folder/$colorFolder/$it" } ?: emptyList()
        }

        // Lấy số file TỐI THIỂU để tránh IndexOutOfBoundsException
        val minSize = fileList.minOfOrNull { it.size } ?: 0
        if (minSize == 0) return arrayListOf()

        // Khởi tạo danh sách màu và ghép danh sách file theo index
        val colorList = Array(minSize) { index ->
            Array(folderList.size) { folderIndex ->
                ColorModel(color = colorNames[folderIndex], path = fileList[folderIndex][index])
            }.toCollection(ArrayList())
        }.toCollection(ArrayList())

        return fileList.first().take(minSize).mapIndexed { index, file ->
            LayerModel(image = file, isMoreColors = true, listColor = colorList[index], thumb = thumbFiles.getOrElse(index) { "" })
        }.toCollection(ArrayList())
    }
}