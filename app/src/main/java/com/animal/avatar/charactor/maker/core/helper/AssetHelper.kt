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
        return try {
            val allData = context.assets.list(path)
            val sortedData = MediaHelper.sortAsset(allData)?.map { "${AssetsKey.ASSET_MANAGER}/$path/$it" }?.toCollection(ArrayList())
            sortedData ?: arrayListOf()
        } catch (e: Exception) {
            // Khi asset local đã bị xóa khỏi APK thì chỉ trả list rỗng, không crash.
            Log.e("AssetHelper", "Failed to read asset folder: $path", e)
            arrayListOf()
        }
    }

    // Read sub folder
    fun getSubfoldersNotDomainAsset(context: Context, path: String): ArrayList<String> {
        return try {
            val allData = context.assets.list(path)
            val sortedData = MediaHelper.sortAsset(allData)?.map { "${AssetsKey.DATA}/$it" }?.toCollection(ArrayList())
            sortedData ?: arrayListOf()
        } catch (e: Exception) {
            // Giữ behavior an toàn tương tự khi local asset không còn tồn tại.
            Log.e("AssetHelper", "Failed to read asset folder: $path", e)
            arrayListOf()
        }
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

    fun getDataFromAsset(context: Context): ArrayList<CustomizeModel> {
        val start = System.currentTimeMillis()
        val customList = ArrayList<CustomizeModel>()
        val categories = context.assets.list(AssetsKey.DATA) ?: return customList

        categories.forEach { category ->
            val categoryList = getDataFromFolder(
                context,
                folderPath = "${AssetsKey.DATA}/$category",
                assetPrefix = "${AssetsKey.DATA_ASSET}$category/",
                dataType = category
            )
            customList.addAll(categoryList)
            Log.d("nbhieu", "Loaded ${categoryList.size} characters from category: $category")
        }

        MediaHelper.writeListToFile(context, ValueKey.DATA_FILE_INTERNAL, customList)
        Log.d("nbhieu", "count time: ${System.currentTimeMillis() - start}")
        return customList
    }

    fun getDataFromFolder(context: Context, folderPath: String, assetPrefix: String, dataType: String = ""): ArrayList<CustomizeModel> {
        val customList = ArrayList<CustomizeModel>()
        val assetManager = context.assets

        val characterList = assetManager.list(folderPath) ?: return customList
        val sortedCharacter = MediaHelper.sortAsset(characterList) ?: return customList

        sortedCharacter.forEach { character ->
            val layerListModelList = ArrayList<LayerListModel>()
            val layer = assetManager.list("$folderPath/$character")
            val allItems = MediaHelper.sortAsset(layer)?.toCollection(ArrayList()) ?: arrayListOf()

            val avatarFile = allItems.find {
                it.equals("avatar.png", ignoreCase = true) ||
                it.equals("avatar.jpg", ignoreCase = true) ||
                it.equals("avatar.webp", ignoreCase = true)
            }

            val sortedLayer = allItems.filter { item ->
                val hasHyphen = item.contains("-")
                val hasUnderscore = item.contains("_")
                if (hasHyphen) {
                    val parts = item.split("-")
                    parts.size == 2 && parts[0].toIntOrNull() != null && parts[1].toIntOrNull() != null
                } else if (hasUnderscore) {
                    val parts = item.split("_")
                    parts.size == 2 && parts[0].toIntOrNull() != null && parts[1].toIntOrNull() != null
                } else false
            }.toCollection(ArrayList())

            val avatar = "$assetPrefix$character/${avatarFile ?: "avatar.png"}"

            for (i in sortedLayer.indices) {
                val layerName = sortedLayer[i]
                val position = if (layerName.contains("-")) layerName.split("-") else layerName.split("_")
                val positionCustom = position[0].toInt() - 1
                val positionNavigation = position[1].toInt() - 1

                val folderOrImageList = assetManager.list("$folderPath/$character/${sortedLayer[i]}")
                val folderOrImageSortedList = MediaHelper.sortAsset(folderOrImageList)?.toCollection(ArrayList()) ?: arrayListOf()
                if (folderOrImageSortedList.isEmpty()) continue

                val navigationImage = "$assetPrefix$character/${sortedLayer[i]}/${folderOrImageSortedList.last()}"
                folderOrImageSortedList.removeAt(folderOrImageSortedList.size - 1)

                val thumbFiles = folderOrImageSortedList.filter { it.startsWith("thumb_") }
                    .sortedBy { it.removePrefix("thumb_").removeSuffix(".png").toIntOrNull() ?: 0 }
                    .map { "$assetPrefix$character/${sortedLayer[i]}/$it" }
                folderOrImageSortedList.removeAll { it.startsWith("thumb_") }

                if (folderOrImageSortedList.isEmpty()) continue

                val layerData = if (AssetsKey.FIRST_IMAGE.any { it in folderOrImageSortedList[0] }) {
                    getDataNoColor(assetPrefix, character, folderOrImageSortedList, sortedLayer[i], thumbFiles)
                } else {
                    getDataColor(assetManager, folderPath, assetPrefix, character, folderOrImageSortedList, sortedLayer[i], thumbFiles)
                }
                layerListModelList.add(LayerListModel(positionCustom, positionNavigation, navigationImage, layerData))
            }
            layerListModelList.sortBy { it.positionNavigation }
            customList.add(CustomizeModel(character, avatar, layerListModelList, level = 100, dataType = dataType))
        }
        return customList
    }

    private fun getDataNoColor(assetPrefix: String, character: String, filesList: List<String>, folder: String, thumbFiles: List<String> = emptyList()): ArrayList<LayerModel> {
        val layerPath = ArrayList<LayerModel>()
        filesList.forEachIndexed { index, fileName ->
            layerPath.add(
                LayerModel(
                    image = "$assetPrefix$character/$folder/$fileName",
                    isMoreColors = false,
                    listColor = arrayListOf(),
                    thumb = thumbFiles.getOrElse(index) { "" }
                )
            )
        }
        return layerPath
    }

    private fun getDataColor(
        assetManager: AssetManager, folderPath: String, assetPrefix: String, character: String, folderList: List<String>, folder: String, thumbFiles: List<String> = emptyList()
    ): ArrayList<LayerModel> {
        val colorNames = folderList.map { "#$it" }
        val fileList = folderList.map { colorFolder ->
            assetManager.list("$folderPath/$character/$folder/$colorFolder")?.let {
                MediaHelper.sortAsset(it)
            }?.map { "$assetPrefix$character/$folder/$colorFolder/$it" } ?: emptyList()
        }

        val minSize = fileList.minOfOrNull { it.size } ?: 0
        if (minSize == 0) return arrayListOf()

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
