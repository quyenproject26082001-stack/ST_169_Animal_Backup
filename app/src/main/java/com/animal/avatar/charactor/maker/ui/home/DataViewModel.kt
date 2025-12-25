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
import com.animal.avatar.charactor.maker.core.service.RetrofitClient
import com.animal.avatar.charactor.maker.core.service.RetrofitPreventive
import com.animal.avatar.charactor.maker.core.utils.DataLocal.isFailBaseURL
import com.animal.avatar.charactor.maker.core.utils.key.AssetsKey
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
                // Lần đầu vào app -> Load data Asset -> Lưu file internal
                if (!MediaHelper.checkFileInternal(context, ValueKey.DATA_FILE_INTERNAL)) {
                    Log.d("DATA_LOAD", "📦 First launch - loading data from assets...")
                    AssetHelper.getDataFromAsset(context)
                } else {
                    Log.d("DATA_LOAD", "📦 Loading local data from internal storage...")
                }

                val totalData = MediaHelper.readListFromFile<CustomizeModel>(context, ValueKey.DATA_FILE_INTERNAL)
                    .toCollection(ArrayList())
                Log.d("DATA_LOAD", "✅ Local data loaded: ${totalData.size} characters")

                var dataApi = MediaHelper.readListFromFile<CustomizeModel>(context, ValueKey.DATA_FILE_API_INTERNAL)
                    ?: arrayListOf()

                if (dataApi.isEmpty()) {
                    Log.d("DATA_LOAD", "⚠️ No cached API data found")
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
                                    Log.e("DATA_LOAD", "❌ API fetch failed - using local data only")
                                }
                            }
                        }
                    } else {
                        Log.e("DATA_LOAD", "🔌 No internet connection - using local data only")
                    }
                } else {
                    Log.d("DATA_LOAD", "✅ Using cached API data: ${dataApi.size} characters")
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

    fun getAllParts(context: Context): Flow<HandleState> = flow {
        Log.d("API_FETCH", "========================================")
        Log.d("API_FETCH", "🌐 STARTING API CALL...")
        Log.d("API_FETCH", "========================================")
        val startTime = System.currentTimeMillis()
        emit(HandleState.LOADING)

        // Try primary URL first
        Log.d("API_FETCH", "📡 Attempting BASE_URL: ${DomainKey.BASE_URL}")
        Log.d("API_FETCH", "⏰ Timeout: 5000ms")
        var primaryError: Exception? = null
        var preventiveError: Exception? = null

        val response = withTimeoutOrNull(5_000) {
            try {
                RetrofitClient.api.getAllData()
            } catch (e: Exception) {
                primaryError = e
                Log.e("API_FETCH", "❌ BASE_URL FAILED!")
                Log.e("API_FETCH", "   Error Type: ${e.javaClass.simpleName}")
                Log.e("API_FETCH", "   Error Message: ${e.message}")
                Log.e("API_FETCH", "   Stack Trace: ${e.stackTraceToString().take(500)}")
                null
            }
        } ?: run {
            if (primaryError == null) {
                Log.e("API_FETCH", "⏱️ BASE_URL TIMEOUT after 5000ms")
            }

            // Try backup URL
            Log.d("API_FETCH", "")
            Log.d("API_FETCH", "🔄 Switching to backup URL...")
            Log.d("API_FETCH", "📡 Attempting BASE_URL_PREVENTIVE: ${DomainKey.BASE_URL_PREVENTIVE}")
            Log.d("API_FETCH", "⏰ Timeout: 5000ms")

            withTimeoutOrNull(5_000) {
                try {
                    RetrofitPreventive.api.getAllData()
                } catch (e: Exception) {
                    preventiveError = e
                    Log.e("API_FETCH", "❌ BASE_URL_PREVENTIVE FAILED!")
                    Log.e("API_FETCH", "   Error Type: ${e.javaClass.simpleName}")
                    Log.e("API_FETCH", "   Error Message: ${e.message}")
                    Log.e("API_FETCH", "   Stack Trace: ${e.stackTraceToString().take(500)}")
                    null
                }
            } ?: run {
                if (preventiveError == null) {
                    Log.e("API_FETCH", "⏱️ BASE_URL_PREVENTIVE TIMEOUT after 5000ms")
                }
                null
            }
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        if (response != null && response.isSuccessful && response.body() != null) {
            val dataMap = ArrayList<DataAPI>()
            response.body()?.forEach { (key, dataBody) ->
                dataMap.add(DataAPI(key, dataBody))
            }

            Log.d("API_FETCH", "")
            Log.d("API_FETCH", "✅ API CALL SUCCESS!")
            Log.d("API_FETCH", "📊 Response Code: ${response.code()}")
            Log.d("API_FETCH", "📝 Response Message: ${response.message()}")
            Log.d("API_FETCH", "📦 Characters received: ${dataMap.size}")
            Log.d("API_FETCH", "⏱️ Duration: ${duration}ms")

            withContext(Dispatchers.IO) {
                getDataAPI(context, dataMap)
            }

            Log.d("API_FETCH", "💾 Data saved to internal storage")
            Log.d("API_FETCH", "========================================")
            emit(HandleState.SUCCESS)
        } else {
            val file = File(context.filesDir, ValueKey.DATA_FILE_API_INTERNAL)
            if (file.exists()) file.delete()

            Log.e("API_FETCH", "")
            Log.e("API_FETCH", "❌❌❌ API CALL FAILED! ❌❌❌")
            Log.e("API_FETCH", "")
            Log.e("API_FETCH", "🔍 FAILURE DETAILS:")

            if (response != null) {
                // Response received but not successful
                Log.e("API_FETCH", "  Response received but failed:")
                Log.e("API_FETCH", "  📊 HTTP Code: ${response.code()}")
                Log.e("API_FETCH", "  📝 HTTP Message: ${response.message()}")
                Log.e("API_FETCH", "  🔍 Success: ${response.isSuccessful}")
                Log.e("API_FETCH", "  🔍 Body is null: ${response.body() == null}")

                // Try to get error body
                try {
                    val errorBody = response.errorBody()?.string()
                    if (!errorBody.isNullOrEmpty()) {
                        Log.e("API_FETCH", "  📄 Error Body: ${errorBody.take(500)}")
                    }
                } catch (e: Exception) {
                    Log.e("API_FETCH", "  ⚠️ Could not read error body: ${e.message}")
                }

                // Common HTTP error explanations
                when (response.code()) {
                    400 -> Log.e("API_FETCH", "  ℹ️ 400 = Bad Request (API rejected the request)")
                    401 -> Log.e("API_FETCH", "  ℹ️ 401 = Unauthorized (Authentication required)")
                    403 -> Log.e("API_FETCH", "  ℹ️ 403 = Forbidden (No permission to access)")
                    404 -> Log.e("API_FETCH", "  ℹ️ 404 = Not Found (Endpoint doesn't exist)")
                    500 -> Log.e("API_FETCH", "  ℹ️ 500 = Server Error (API server crashed)")
                    502 -> Log.e("API_FETCH", "  ℹ️ 502 = Bad Gateway (Server offline or unreachable)")
                    503 -> Log.e("API_FETCH", "  ℹ️ 503 = Service Unavailable (Server overloaded)")
                    504 -> Log.e("API_FETCH", "  ℹ️ 504 = Gateway Timeout (Server took too long)")
                }
            } else {
                // No response at all
                Log.e("API_FETCH", "  No response received from server")
                Log.e("API_FETCH", "")

                // Detail primary URL failure
                if (primaryError != null) {
                    Log.e("API_FETCH", "  🔴 PRIMARY URL FAILURE:")
                    Log.e("API_FETCH", "     URL: ${DomainKey.BASE_URL}")
                    Log.e("API_FETCH", "     Error: ${primaryError!!.javaClass.simpleName}")
                    Log.e("API_FETCH", "     Reason: ${primaryError!!.message}")
                    explainError(primaryError!!)
                } else {
                    Log.e("API_FETCH", "  🔴 PRIMARY URL: Timeout (>5000ms)")
                    Log.e("API_FETCH", "     ℹ️ Server didn't respond in time")
                }

                Log.e("API_FETCH", "")

                // Detail preventive URL failure
                if (preventiveError != null) {
                    Log.e("API_FETCH", "  🔴 BACKUP URL FAILURE:")
                    Log.e("API_FETCH", "     URL: ${DomainKey.BASE_URL_PREVENTIVE}")
                    Log.e("API_FETCH", "     Error: ${preventiveError!!.javaClass.simpleName}")
                    Log.e("API_FETCH", "     Reason: ${preventiveError!!.message}")
                    explainError(preventiveError!!)
                } else {
                    Log.e("API_FETCH", "  🔴 BACKUP URL: Timeout (>5000ms)")
                    Log.e("API_FETCH", "     ℹ️ Server didn't respond in time")
                }
            }

            Log.e("API_FETCH", "")
            Log.e("API_FETCH", "⏱️ Total time spent: ${duration}ms")
            Log.e("API_FETCH", "🗑️ Deleted old API cache file")
            Log.e("API_FETCH", "")
            Log.e("API_FETCH", "💡 POSSIBLE SOLUTIONS:")
            Log.e("API_FETCH", "   1. Check internet connection")
            Log.e("API_FETCH", "   2. Verify API server is online")
            Log.e("API_FETCH", "   3. Check if URLs are correct in DomainKey")
            Log.e("API_FETCH", "   4. Check firewall/proxy settings")
            Log.e("API_FETCH", "   5. Try again later (server may be down)")
            Log.e("API_FETCH", "========================================")
            emit(HandleState.FAIL)
        }
    }

    private fun explainError(error: Exception) {
        when (error.javaClass.simpleName) {
            "UnknownHostException" -> {
                Log.e("API_FETCH", "     ℹ️ Cannot find server (DNS failed or no internet)")
            }
            "SocketTimeoutException" -> {
                Log.e("API_FETCH", "     ℹ️ Connection timeout (server too slow or unreachable)")
            }
            "ConnectException" -> {
                Log.e("API_FETCH", "     ℹ️ Cannot connect to server (server offline or blocked)")
            }
            "SSLException", "SSLHandshakeException" -> {
                Log.e("API_FETCH", "     ℹ️ SSL/HTTPS security error (certificate problem)")
            }
            "IOException" -> {
                Log.e("API_FETCH", "     ℹ️ Network I/O error (connection interrupted)")
            }
            "HttpException" -> {
                Log.e("API_FETCH", "     ℹ️ HTTP error (server returned error code)")
            }
        }
    }

    fun getDataAPI(context: Context, dataList: ArrayList<DataAPI>) {
        Log.d("API_PARSE", "========================================")
        Log.d("API_PARSE", "🔄 PARSING API DATA...")
        Log.d("API_PARSE", "Characters to parse: ${dataList.size}")

        val allDataAPI: ArrayList<CustomizeModel> = arrayListOf()
        var totalLayers = 0
        var totalItems = 0

        // Character 1, Character 2,...
        dataList.forEachIndexed { indexCharacter, data ->
            Log.d("API_PARSE", "")
            Log.d("API_PARSE", "--- Character ${indexCharacter + 1}: ${data.name} ---")
            Log.d("API_PARSE", "  Parts count: ${data.parts.size}")

            ///public/app/ChibiMaker/1/avatar.png
            val baseDomain = if (!isFailBaseURL) DomainKey.BASE_URL else DomainKey.BASE_URL_PREVENTIVE
            val avatarCharacter = "$baseDomain${DomainKey.SUB_DOMAIN}/${data.name}/${DomainKey.AVATAR_CHARACTER_API}"
            val layerList = ArrayList<LayerListModel>(data.parts.size)

            // Sort parts by level in ascending order
            val sortedParts = data.parts.sortedBy { it.level }

            sortedParts.forEachIndexed { indexLayer, dataLayer ->
                // Handle both "-" and "_" delimiters, similar to local asset loading
                val layerName = if (dataLayer.parts.contains("-")) {
                    dataLayer.parts.split("-")
                } else {
                    dataLayer.parts.split("_")
                }
                val positionCustom = layerName.first().toInt() - 1
                val positionNavigation = layerName.last().toInt() - 1
                val imageNavigation = "${baseDomain}${DomainKey.SUB_DOMAIN}/${data.name}/${dataLayer.parts}/${DomainKey.IMAGE_NAVIGATION}"
                val layer = getDataLayer(baseDomain, dataLayer, dataLayer.parts)

                Log.d("API_PARSE", "    Layer ${indexLayer + 1}: ${dataLayer.parts}")
                Log.d("API_PARSE", "      - Items: ${dataLayer.quantity}")
                Log.d("API_PARSE", "      - Has colors: ${dataLayer.colorArray.isNotEmpty()}")
                if (dataLayer.colorArray.isNotEmpty()) {
                    val colorCount = dataLayer.colorArray.split(",").size
                    Log.d("API_PARSE", "      - Color count: $colorCount")
                }

                totalItems += dataLayer.quantity

                val layerListModel = LayerListModel(
                    positionCustom = positionCustom,
                    positionNavigation = positionNavigation,
                    imageNavigation = imageNavigation,
                    layer = layer
                )
                layerList.add(layerListModel)
            }
            layerList.sortBy { it.positionNavigation }
            totalLayers += layerList.size

            // Use the minimum level from all parts as the character level
            val characterLevel = sortedParts.minOfOrNull { it.level } ?: 100

            val dataApi = CustomizeModel(
                dataName = data.name,
                avatar = avatarCharacter,
                layerList = layerList,
                level = characterLevel,
                isFromAPI = true
            )
            allDataAPI.add(dataApi)

            Log.d("API_PARSE", "  ✅ Character ${data.name} parsed - Level: $characterLevel")
        }

        MediaHelper.writeListToFile(context, ValueKey.DATA_FILE_API_INTERNAL, allDataAPI)

        Log.d("API_PARSE", "")
        Log.d("API_PARSE", "✅ PARSING COMPLETE!")
        Log.d("API_PARSE", "📊 Summary:")
        Log.d("API_PARSE", "  - Total characters: ${allDataAPI.size}")
        Log.d("API_PARSE", "  - Total layers: $totalLayers")
        Log.d("API_PARSE", "  - Total items: $totalItems")
        Log.d("API_PARSE", "========================================")
    }

    private fun getDataLayer(baseDomain: String, partData: PartAPI, layer: String): ArrayList<LayerModel> {
        return if (partData.colorArray != "" || partData.colorArray.isNotEmpty()) {
            getDataAPIColor(baseDomain, partData, layer)
        } else {
            getDataAPINoColor(baseDomain, partData, layer)
        }
    }

    private fun getDataAPINoColor(baseDomain: String, part: PartAPI, layer: String): ArrayList<LayerModel> {
        val layerPath = ArrayList<LayerModel>(part.quantity)
        val prefix = "$baseDomain${DomainKey.SUB_DOMAIN}/${part.position}/${layer}/"
        val suffix = DomainKey.LAYER_EXTENSION
        for (i in 1..part.quantity) {
            layerPath.add(
                LayerModel(
                    "$prefix${i}$suffix",
                    false,
                    arrayListOf()
                )
            )
        }
        return layerPath
    }

    private fun getDataAPIColor(baseDomain: String, part: PartAPI, layer: String): ArrayList<LayerModel> {
        val layerPath = ArrayList<LayerModel>(part.quantity)
        val getColorCode = part.colorArray.split(",")
        val prefix = "$baseDomain${DomainKey.SUB_DOMAIN}/${part.position}/${layer}/"
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
            layerPath.add(LayerModel(listColor.first().path, true, listColor))
        }
        return layerPath
    }
}