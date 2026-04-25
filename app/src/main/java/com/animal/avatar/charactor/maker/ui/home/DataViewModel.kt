package com.animal.avatar.charactor.maker.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animal.avatar.charactor.maker.core.helper.AssetHelper
import com.animal.avatar.charactor.maker.core.helper.InternetHelper
import com.animal.avatar.charactor.maker.core.helper.MediaHelper
import com.animal.avatar.charactor.maker.core.helper.SharePreferenceHelper
import com.animal.avatar.charactor.maker.core.utils.key.AssetsKey
import com.animal.avatar.charactor.maker.core.service.RetrofitClient
import com.animal.avatar.charactor.maker.core.service.RetrofitPreventive
import com.animal.avatar.charactor.maker.core.utils.DataLocal.isFailBaseURL
import com.animal.avatar.charactor.maker.core.utils.key.DomainKey
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.core.utils.state.HandleState
import com.animal.avatar.charactor.maker.data.model.DataAPI
import com.animal.avatar.charactor.maker.data.model.PartAPI
import com.animal.avatar.charactor.maker.data.model.custom.ColorModel
import com.animal.avatar.charactor.maker.data.model.custom.CustomizeModel
import com.animal.avatar.charactor.maker.data.model.custom.LayerListModel
import com.animal.avatar.charactor.maker.data.model.custom.LayerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.collections.forEachIndexed

class DataViewModel() : ViewModel() {
    private val _allData = MutableStateFlow<ArrayList<CustomizeModel>>(arrayListOf())
    val allData: StateFlow<ArrayList<CustomizeModel>> = _allData.asStateFlow()
    private val _getDataAPI = MutableLiveData<List<PartAPI>>()
    val getDataAPI: LiveData<List<PartAPI>> get() = _getDataAPI

    fun saveAndReadData(context: Context) {
        viewModelScope.launch {
            Log.d("DATA_LOAD", "========================================")
            Log.d("DATA_LOAD", "📂 LOADING ALL DATA...")
            Log.d("DATA_LOAD", "========================================")
            val timeStart = System.currentTimeMillis()

            val list = withContext(Dispatchers.IO) {
                val pref = SharePreferenceHelper(context)
                val needsReload = !MediaHelper.checkFileInternal(context, ValueKey.DATA_FILE_INTERNAL)
                        || pref.getDataSchemaVersion() < ValueKey.DATA_SCHEMA_VERSION
                if (needsReload) {
                    Log.d("DATA_LOAD", "📦 Loading data from assets (schema v${ValueKey.DATA_SCHEMA_VERSION})...")
                    AssetHelper.getDataFromAsset(context)
                    pref.setDataSchemaVersion(ValueKey.DATA_SCHEMA_VERSION)
                } else {
                    Log.d("DATA_LOAD", "📦 Loading local data from internal storage...")
                }

                val totalData = MediaHelper.readListFromFile<CustomizeModel>(context, ValueKey.DATA_FILE_INTERNAL)
                    .toCollection(ArrayList())
                Log.d("DATA_LOAD", "✅ Local data loaded: ${totalData.size} characters")

                var dataApi = MediaHelper.readListFromFile<CustomizeModel>(context, ValueKey.DATA_FILE_API_INTERNAL)
                    ?: arrayListOf()

                if (InternetHelper.checkInternet(context)) {
                    Log.d("DATA_LOAD", "🌐 Internet available - fetching from API...")
                    getAllParts(context).collect { state ->
                        when (state) {
                            HandleState.LOADING -> {
                                Log.d("DATA_LOAD", "⏳ API loading...")
                            }
                            HandleState.SUCCESS -> {
                                dataApi = MediaHelper.readListFromFile<CustomizeModel>(context, ValueKey.DATA_FILE_API_INTERNAL)
                                Log.d("DATA_LOAD", "✅ API data loaded: ${dataApi.size} characters")
                            }
                            else -> {
                                Log.e("DATA_LOAD", "❌ API fetch failed - using cached data")
                            }
                        }
                    }
                } else {
                    Log.e("DATA_LOAD", "🔌 No internet connection - using cached data only")
                }

                totalData.addAll(dataApi)
                // Sort all data by level (ascending order)
                totalData.sortBy { it.level }

                Log.d("DATA_LOAD", "")
                Log.d("DATA_LOAD", "📊 FINAL DATA SUMMARY:")
                Log.d("DATA_LOAD", "  - Local characters: ${totalData.count { !it.isFromAPI }}")
                Log.d("DATA_LOAD", "  - API characters: ${totalData.count { it.isFromAPI }}")
                Log.d("DATA_LOAD", "  - Total characters: ${totalData.size}")

                totalData
            }
            _allData.value = list
            val timeEnd = System.currentTimeMillis()
            Log.d("DATA_LOAD", "⏱️ Total load time: ${timeEnd - timeStart}ms")
            Log.d("DATA_LOAD", "========================================")
        }
    }

    fun ensureData(context: Context) {
        if (_allData.value.isEmpty()) {
            saveAndReadData(context)
        }
    }

    fun loadDataByType(context: Context, dataType: Int) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val category = dataTypeToCategory(dataType)
                val localData = if (category.isNotEmpty()) {
                    AssetHelper.getDataFromFolder(
                        context,
                        folderPath = "${AssetsKey.DATA}/$category",
                        assetPrefix = "${AssetsKey.DATA_ASSET}$category/",
                        dataType = category
                    )
                } else arrayListOf()

                val apiData = MediaHelper.readListFromFile<CustomizeModel>(context, ValueKey.DATA_FILE_API_INTERNAL)
                    ?.filter { it.dataType == category || it.dataType.isEmpty() }
                    ?.toCollection(ArrayList()) ?: arrayListOf()

                val combined = ArrayList<CustomizeModel>(localData.size + apiData.size)
                combined.addAll(localData)
                combined.addAll(apiData)
                combined.sortBy { it.level }
                combined
            }
            _allData.value = list
        }
    }

    private fun dataTypeToCategory(dataType: Int): String = when (dataType) {
        1 -> "Cat"
        2 -> "Dragon"
        3 -> "Dog"
        4 -> "Pony"
        5 -> "Animal"
        else -> ""
    }

    fun getAllParts(context: Context): Flow<HandleState> = flow {
        emit(HandleState.LOADING)

        Log.d("PATTERN_P", "========================================")
        Log.d("PATTERN_P", "🚀 Firing both URLs concurrently...")
        Log.d("PATTERN_P", "   PRIMARY   : ${DomainKey.BASE_URL}")
        Log.d("PATTERN_P", "   PREVENTIVE: ${DomainKey.BASE_URL_PREVENTIVE}")

        data class ApiResult(val response: retrofit2.Response<Map<String, List<PartAPI>>>?, val isPreventive: Boolean)

        val result = coroutineScope {
            val primaryDeferred = async {
                withTimeoutOrNull(5_000) {
                    try { RetrofitClient.api.getAllData() } catch (e: Exception) {
                        Log.e("PATTERN_P", "❌ PRIMARY failed: ${e.javaClass.simpleName} - ${e.message}")
                        null
                    }
                }
            }
            val preventiveDeferred = async {
                withTimeoutOrNull(5_000) {
                    try { RetrofitPreventive.api.getAllData() } catch (e: Exception) {
                        Log.e("PATTERN_P", "❌ PREVENTIVE failed: ${e.javaClass.simpleName} - ${e.message}")
                        null
                    }
                }
            }

            val primary = primaryDeferred.await()
            if (primary != null && primary.isSuccessful) {
                preventiveDeferred.cancel()
                Log.d("PATTERN_P", "✅ PRIMARY won — PREVENTIVE cancelled")
                ApiResult(primary, false)
            } else {
                if (primary == null) {
                    Log.w("PATTERN_P", "⚠️ PRIMARY returned null (timeout or exception) — waiting for PREVENTIVE...")
                } else {
                    Log.w("PATTERN_P", "⚠️ PRIMARY responded but failed (HTTP ${primary.code()}) — waiting for PREVENTIVE...")
                }
                val preventive = preventiveDeferred.await()
                if (preventive != null && preventive.isSuccessful) {
                    Log.d("PATTERN_P", "✅ PREVENTIVE won — PRIMARY was dead")
                } else {
                    Log.e("PATTERN_P", "❌ BOTH URLs failed — no data available")
                }
                ApiResult(preventive, true)
            }
        }

        isFailBaseURL = result.isPreventive
        val response = result.response

        if (response != null && response.isSuccessful && response.body() != null) {
            val activeUrl = if (result.isPreventive) DomainKey.BASE_URL_PREVENTIVE else DomainKey.BASE_URL
            Log.d("PATTERN_P", "🌐 Active domain: $activeUrl")
            Log.d("PATTERN_P", "📦 Characters received: ${response.body()!!.size}")
            Log.d("PATTERN_P", "========================================")
            val dataMap = ArrayList<DataAPI>()
            response.body()?.forEach { (key, dataBody) ->
                dataMap.add(DataAPI(key, dataBody))
            }
            withContext(Dispatchers.IO) {
                getDataAPI(context, dataMap)
            }
            emit(HandleState.SUCCESS)
        } else {
            Log.e("PATTERN_P", "💀 Both PRIMARY and PREVENTIVE are dead — emitting FAIL")
            Log.e("PATTERN_P", "========================================")
            val file = File(context.filesDir, ValueKey.DATA_FILE_API_INTERNAL)
            if (file.exists()) file.delete()
            emit(HandleState.FAIL)
        }
    }

    fun getDataAPI(context: Context, dataList: ArrayList<DataAPI>) {
        Log.d("API_PARSE", "========================================")
        Log.d("API_PARSE", "🔄 PARSING API DATA...")

        val allDataAPI: ArrayList<CustomizeModel> = arrayListOf()
        val baseDomain = if (!isFailBaseURL) DomainKey.BASE_URL else DomainKey.BASE_URL_PREVENTIVE

        dataList.forEach { data ->
            // Group parts by category (data field), fallback to empty string if not set
            val partsByCategory = data.parts.groupBy { it.data.orEmpty() }

            partsByCategory.forEach { (category, parts) ->
                val avatarCharacter = "$baseDomain${DomainKey.SUB_DOMAIN}/$category/${data.name}/${DomainKey.AVATAR_CHARACTER_API}"
                val layerList = ArrayList<LayerListModel>(parts.size)
                val sortedParts = parts.sortedBy { it.level }

                sortedParts.forEach { dataLayer ->
                    val layerName = if (dataLayer.parts.contains("-")) dataLayer.parts.split("-") else dataLayer.parts.split("_")
                    val positionCustom = layerName.first().toInt() - 1
                    val positionNavigation = layerName.last().toInt() - 1
                    val imageNavigation = "$baseDomain${DomainKey.SUB_DOMAIN}/$category/${data.name}/${dataLayer.parts}/${DomainKey.IMAGE_NAVIGATION}"
                    val layer = getDataLayer(baseDomain, category, dataLayer, dataLayer.parts)
                    layerList.add(LayerListModel(positionCustom, positionNavigation, imageNavigation, layer))
                }
                layerList.sortBy { it.positionNavigation }

                val characterLevel = sortedParts.minOfOrNull { it.level } ?: 100
                allDataAPI.add(
                    CustomizeModel(
                        dataName = data.name,
                        avatar = avatarCharacter,
                        layerList = layerList,
                        level = characterLevel,
                        isFromAPI = true,
                        dataType = category
                    )
                )
                Log.d("API_PARSE", "✅ ${data.name} [${category.ifEmpty { "no-category" }}] - ${parts.size} layers")
            }
        }

        MediaHelper.writeListToFile(context, ValueKey.DATA_FILE_API_INTERNAL, allDataAPI)
        Log.d("API_PARSE", "Total API characters: ${allDataAPI.size}")
        Log.d("API_PARSE", "========================================")
    }

    private fun getDataLayer(baseDomain: String, category: String, partData: PartAPI, layer: String): ArrayList<LayerModel> {
        return if (partData.colorArray.isNotEmpty()) {
            getDataAPIColor(baseDomain, category, partData, layer)
        } else {
            getDataAPINoColor(baseDomain, category, partData, layer)
        }
    }

    private fun getDataAPINoColor(baseDomain: String, category: String, part: PartAPI, layer: String): ArrayList<LayerModel> {
        val layerPath = ArrayList<LayerModel>(part.quantity)
        val prefix = "$baseDomain${DomainKey.SUB_DOMAIN}/$category/${part.position}/${layer}/"
        val suffix = DomainKey.LAYER_EXTENSION
        for (i in 1..part.quantity) {
            layerPath.add(
                LayerModel(
                    "$prefix${i}$suffix",
                    false,
                    arrayListOf(),
                    "${prefix}thumb_${i}$suffix"
                )
            )
        }
        return layerPath
    }

    private fun getDataAPIColor(baseDomain: String, category: String, part: PartAPI, layer: String): ArrayList<LayerModel> {
        val layerPath = ArrayList<LayerModel>(part.quantity)
        val getColorCode = part.colorArray.split(",")
        val prefix = "$baseDomain${DomainKey.SUB_DOMAIN}/$category/${part.position}/${layer}/"
        val suffix = DomainKey.LAYER_EXTENSION

        for (i in 1..part.quantity) {
            val listColor = ArrayList<ColorModel>(getColorCode.size)
            for (j in 0 until getColorCode.size) {
                listColor.add(
                    ColorModel(
                        "#${getColorCode[j]}",
                        "$prefix${getColorCode[j]}/${i}$suffix"
                    )
                )
            }
            layerPath.add(LayerModel(listColor.first().path, true, listColor, "${prefix}thumb_${i}$suffix"))
        }
        return layerPath
    }
}