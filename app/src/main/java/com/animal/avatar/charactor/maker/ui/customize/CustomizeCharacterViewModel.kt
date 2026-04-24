package com.animal.avatar.charactor.maker.ui.customize

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.ViewModel
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.core.extensions.hideNavigation

import com.animal.avatar.charactor.maker.core.helper.BitmapHelper
import com.animal.avatar.charactor.maker.core.helper.InternetHelper
import com.animal.avatar.charactor.maker.core.helper.MediaHelper
import com.animal.avatar.charactor.maker.core.utils.DataLocal.isFailBaseURL
import com.animal.avatar.charactor.maker.core.utils.key.AssetsKey
import com.animal.avatar.charactor.maker.core.utils.key.DomainKey
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.core.utils.state.HandleState
import com.animal.avatar.charactor.maker.core.utils.state.SaveState
import com.animal.avatar.charactor.maker.data.model.custom.CustomizeModel
import com.animal.avatar.charactor.maker.data.model.custom.ItemColorImageModel
import com.animal.avatar.charactor.maker.data.model.custom.ItemColorModel
import com.animal.avatar.charactor.maker.data.model.custom.ItemNavCustomModel
import com.animal.avatar.charactor.maker.data.model.custom.LayerListModel
import com.animal.avatar.charactor.maker.data.model.custom.NavigationModel
import com.animal.avatar.charactor.maker.data.model.custom.SuggestionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class CustomizeCharacterViewModel : ViewModel() {
    // Đếm số lần random, chỉ số được chọn
    var countRandom = 0
    var positionSelected = 0

    // Data từ API hay không
    private val _isDataAPI = MutableStateFlow(false)

    // Trạng thái flip
    private val _isFlip = MutableStateFlow(false)
    val isFlip = _isFlip.asStateFlow()

    private val _isHideView = MutableStateFlow(false)
    val isHideView = _isHideView.asStateFlow()

    private val _isCreated = MutableStateFlow(false)
    val isCreated = _isCreated.asStateFlow()

    var statusFrom = ValueKey.CREATE

    var avatarPath = ""

    //----------------------------------------------------------------------------------------------------------------------
    var positionNavSelected = -1

    var positionCustom = -1

    // Data gốc
    private val _dataCustomize = MutableStateFlow<CustomizeModel?>(null)
    val dataCustomize = _dataCustomize.asStateFlow()

    // Danh sách Navigation bottom
    private val _bottomNavigationList = MutableStateFlow(arrayListOf<NavigationModel>())
    val bottomNavigationList = _bottomNavigationList.asStateFlow()

    val itemNavList = ArrayList<ArrayList<ItemNavCustomModel>>()

    // Danh sách màu
    var colorItemNavList = ArrayList<ArrayList<ItemColorModel>>()

    // Trạng thái chọn item/màu
    var positionColorItemList = ArrayList<Int>()

    val isSelectedItemList = ArrayList<Boolean>()

    val isShowColorList = ArrayList<Boolean>()

    // Key + Path đã chọn
    var keySelectedItemList = ArrayList<String>()

    var pathSelectedList = ArrayList<String>()

    // Danh sách ImageView trên layout
    val imageViewList = ArrayList<ImageView>()

    val colorListMost = ArrayList<String>()

    var suggestionModel = SuggestionModel()

    //----------------------------------------------------------------------------------------------------------------------
    // Base setter
    suspend fun setPositionNavSelected(position: Int) {
        positionNavSelected = position
    }

    suspend fun setPositionCustom(position: Int) {
        positionCustom = position
    }

    fun setDataCustomize(data: CustomizeModel) {
        _dataCustomize.value = data
    }

    fun setIsDataAPI(isAPI: Boolean) {
        _isDataAPI.value = isAPI
    }

    fun setIsFlip() {
        _isFlip.value = !_isFlip.value
    }

    fun setIsHideView() {
        _isHideView.value = !_isHideView.value
    }

    fun setIsCreated(status: Boolean) {
        _isCreated.value = status
    }

    fun updatePositionColorItemList(positionList: ArrayList<Int>) {
        positionColorItemList.clear()
        positionColorItemList.addAll(positionList)
    }

    fun updateIsSelectedItemList(selectedList: ArrayList<Boolean>) {
        isSelectedItemList.clear()
        isSelectedItemList.addAll(selectedList)
    }

    fun updateIsShowColorList(position: Int, status: Boolean) {
        isShowColorList[position] = status
    }

    fun updateIsShowColorList(showList: ArrayList<Boolean>) {
        isShowColorList.clear()
        isShowColorList.addAll(showList)
    }

    fun updateKeySelectedItemList(keyList: ArrayList<String>) {
        keySelectedItemList.clear()
        keySelectedItemList = keyList
    }

    fun updatePathSelectedList(pathList: ArrayList<String>) {
        pathSelectedList.clear()
        pathSelectedList.addAll(pathList)
    }

    fun setColorListMost(colorList: ArrayList<String>) {
        colorListMost.clear()
        colorListMost.addAll(colorList)
    }

    fun updateSuggestionModel(model: SuggestionModel) {
        suggestionModel = model
    }

    fun updateAvatarPath(path: String) {
        avatarPath = path
    }

    //----------------------------------------------------------------------------------------------------------------------
    // Setter suspend
    suspend fun setPositionColorItem(position: Int, newPosition: Int) {
        positionColorItemList =
            positionColorItemList.mapIndexed { index, oldPosition -> if (index == position) newPosition else oldPosition }
                .toCollection(ArrayList())
    }

    suspend fun setIsSelectedItem(position: Int) {
        isSelectedItemList[position] = true
    }

    suspend fun setKeySelected(position: Int, newKey: String) {
        keySelectedItemList[position] = newKey
    }

    suspend fun setPathSelected(position: Int, newPath: String) {
        pathSelectedList[position] = newPath
    }

    //----------------------------------------------------------------------------------------------------------------------
    // Bottom Navigation
    suspend fun setBottomNavigationList(bottomNavList: ArrayList<NavigationModel>) {
        _bottomNavigationList.value = bottomNavList
    }

    suspend fun setBottomNavigationListDefault() {
        val outputBottomNavigationList = arrayListOf<NavigationModel>()
        _dataCustomize.value!!.layerList.forEach { layerList ->
            outputBottomNavigationList.add(NavigationModel(imageNavigation = layerList.imageNavigation))
        }
        outputBottomNavigationList.first().isSelected = true
        _bottomNavigationList.value = outputBottomNavigationList
    }


    suspend fun setClickBottomNavigation(position: Int) {
        _bottomNavigationList.value =
            _bottomNavigationList.value.mapIndexed { index, model -> model.copy(isSelected = index == position) }
                .toCollection(ArrayList())
    }

    //----------------------------------------------------------------------------------------------------------------------
    //  Item Nav / Layer
    suspend fun addValueToItemNavList() {
        itemNavList.clear()
        _dataCustomize.value!!.layerList.forEachIndexed { index, layer ->
            if (index == 0) {
                itemNavList.add(createListItem(layer, true))
            } else {
                itemNavList.add(createListItem(layer))
            }
        }
    }

    suspend fun setFocusItemNavDefault() {
        for (itemParent in itemNavList) {
            itemParent.forEachIndexed { index, item ->
                item.isSelected = index == 0
            }
        }
        itemNavList.first()[0].isSelected = false
        itemNavList.first()[1].isSelected = true
    }

    fun updateItemNavList(list: ArrayList<ArrayList<ItemNavCustomModel>>) {
        itemNavList.clear()
        itemNavList.addAll(list)
    }

    suspend fun setItemNavList(positionNavigation: Int, position: Int) {
        itemNavList[positionNavigation] =
            itemNavList[positionNavigation].mapIndexed { index, models -> models.copy(isSelected = index == position) }
                .toCollection(ArrayList())
    }

    suspend fun setClickFillLayer(item: ItemNavCustomModel, position: Int): String {
        // Validate positionNavSelected bounds
        if (positionNavSelected < 0 || positionNavSelected >= positionColorItemList.size) {
            Log.e("CustomizeViewModel", "setClickFillLayer - Index out of bounds! positionNavSelected: $positionNavSelected, positionColorItemList.size: ${positionColorItemList.size}")
            // Reset to safe value
            positionNavSelected = 0.coerceAtMost(positionColorItemList.size - 1).coerceAtLeast(0)
            Log.e("CustomizeViewModel", "Reset positionNavSelected to: $positionNavSelected")
        }

        val path = item.path
        setKeySelected(positionNavSelected, path)
        val pathSelected = if (item.listImageColor.isEmpty()) {
            path
        } else {
            // Double-check bounds before accessing
            val colorIndex = positionColorItemList.getOrElse(positionNavSelected) { 0 }
            if (colorIndex >= 0 && colorIndex < item.listImageColor.size) {
                item.listImageColor[colorIndex].path
            } else {
                Log.e("CustomizeViewModel", "Color index out of bounds! colorIndex: $colorIndex, listImageColor.size: ${item.listImageColor.size}")
                item.listImageColor.firstOrNull()?.path ?: path
            }
        }
        setIsSelectedItem(positionNavSelected)
        setPathSelected(positionCustom, path)
        setItemNavList(positionNavSelected, position)
        return pathSelected
    }

    suspend fun setClickRandomLayer(): Pair<String, Boolean> {
        // Validate positionNavSelected bounds
        if (positionNavSelected < 0 || positionNavSelected >= itemNavList.size) {
            Log.e("CustomizeViewModel", "setClickRandomLayer - positionNavSelected out of bounds! positionNavSelected: $positionNavSelected, itemNavList.size: ${itemNavList.size}")
            positionNavSelected = 0.coerceAtMost(itemNavList.size - 1).coerceAtLeast(0)
        }

        val positionStartLayer = if (positionNavSelected == 0) 1 else 2
        val randomLayer = if (positionNavSelected == 0) {
            if (itemNavList[positionNavSelected].size == 1) {
                1
            } else {
                (positionStartLayer..<itemNavList[positionNavSelected].size).random()
            }
        } else {
            (positionStartLayer..<itemNavList[positionNavSelected].size).random()
        }

        var randomColor: Int? = null

        var isMoreColors = false

        if (itemNavList[positionNavSelected][positionStartLayer].listImageColor.isNotEmpty()) {
            isMoreColors = true
            randomColor = (0..<(itemNavList[positionNavSelected][positionStartLayer].listImageColor.size)).random()
        }
        var pathRandom = itemNavList[positionNavSelected][randomLayer].path
        setKeySelected(positionNavSelected, pathRandom)

        if (!isMoreColors) {
            setPositionColorItem(positionCustom, 0)
        } else {
            pathRandom = itemNavList[positionNavSelected][randomLayer].listImageColor[randomColor!!].path
            setPositionColorItem(positionCustom, randomColor)
        }
        setPathSelected(positionCustom, pathRandom)
        setItemNavList(positionNavSelected, randomLayer)
        if (isMoreColors) {
            setColorItemNav(positionNavSelected, randomColor!!)
        }
        return pathRandom to isMoreColors
    }

    suspend fun setClickRandomFullLayer(): Boolean {
        countRandom++
        val isOutTurn = countRandom == 3

        val colorCode = if (colorListMost.isNotEmpty()) colorListMost[(0..<colorListMost.size).random()] else "#123456"
        for (i in 0 until _bottomNavigationList.value.size) {
            val minSize = if (i == 0) 1 else 2
            if (itemNavList[i].size <= minSize) {
                continue
            }
            val randomLayer = (minSize..<itemNavList[i].size).random()

            var randomColor: Int = 0

            val isMoreColors = if (itemNavList[i][minSize].listImageColor.isNotEmpty()) {
                randomColor = itemNavList[i][randomLayer].listImageColor.indexOfFirst { it.color == colorCode }
                if (randomColor == -1) {
                    randomColor = (0..<itemNavList[i][minSize].listImageColor.size).random()
                }
                true
            } else {
                false
            }
            keySelectedItemList[i] = itemNavList[i][randomLayer].path

            val pathItem = if (!isMoreColors) {
                positionColorItemList[i] = 0
                itemNavList[i][randomLayer].path
            } else {
                positionColorItemList[i] = randomColor
                itemNavList[i][randomLayer].listImageColor[randomColor].path
            }
            pathSelectedList[_dataCustomize.value!!.layerList[i].positionCustom] = pathItem
            setItemNavList(i, randomLayer)
            if (isMoreColors) {
                setColorItemNav(i, randomColor)
            }
        }
        return isOutTurn
    }

    suspend fun setClickReset(): String {
        resetDataList()
        _bottomNavigationList.value.forEachIndexed { index, model ->
            val positionSelected = if (index == 0) 1 else 0
            setItemNavList(index, positionSelected)
            setColorItemNav(index, 0)
        }
        val pathDefault = _dataCustomize.value!!.layerList.first().layer.first().image
        pathSelectedList[_dataCustomize.value!!.layerList.first().positionCustom] = pathDefault
        keySelectedItemList[_dataCustomize.value!!.layerList.first().positionNavigation] = pathDefault
        isSelectedItemList[_dataCustomize.value!!.layerList.first().positionNavigation] = true
        return pathDefault
    }


    //----------------------------------------------------------------------------------------------------------------------
// Color
    suspend fun setItemColorDefault() {
        colorItemNavList.clear()
        for (i in 0 until _dataCustomize.value!!.layerList.size) {
            // Lấy đối tượng LayerModel đầu tiên trong danh sách con
            val currentLayer = _dataCustomize.value!!.layerList[i].layer.first()
            var firstIndex = true
            // Kiểm tra isMoreColors để thêm màu hoặc danh sách rỗng
            if (currentLayer.isMoreColors) {
                val colorList = ArrayList<ItemColorModel>()
                for (j in 0 until currentLayer.listColor.size) {
                    val color = currentLayer.listColor[j].color
                    if (firstIndex) {
                        colorList.add(ItemColorModel(color, true))
                    } else {
                        colorList.add(ItemColorModel(color))
                    }
                    firstIndex = false
                }
                colorItemNavList.add(colorList)
            } else {
                colorItemNavList.add(arrayListOf())
            }
        }
        val getAllColor = ArrayList<String>()
        itemNavList.forEachIndexed { index, nav ->
            val position = if (index != 0) 2 else 1
            val itemNav = nav[position]
            itemNav.listImageColor.forEach { colorList ->
                getAllColor.add(colorList.color)
            }
        }
        setColorListMost(
            getAllColor.groupingBy { it }.eachCount()
                .filter { it.value > 3 }.keys.toCollection(ArrayList())
        )
    }

    fun updateColorNavList(list: ArrayList<ArrayList<ItemColorModel>>) {
        colorItemNavList.clear()
        colorItemNavList.addAll(list)
    }

    suspend fun setColorItemNav(positionNavSelected: Int, position: Int) {
        colorItemNavList[positionNavSelected] =
            colorItemNavList[positionNavSelected].mapIndexed { index, models -> models.copy(isSelected = index == position) }
                .toCollection(ArrayList())
    }

    suspend fun setClickChangeColor(position: Int): String {
        // Validate positionNavSelected bounds
        if (positionNavSelected < 0 || positionNavSelected >= positionColorItemList.size) {
            Log.e("CustomizeViewModel", "setClickChangeColor - positionNavSelected out of bounds! positionNavSelected: $positionNavSelected, positionColorItemList.size: ${positionColorItemList.size}")
            return ""
        }

        var pathColor = ""
        positionColorItemList[positionNavSelected] = position

        // Đã chọn hình ảnh chưa
        if (keySelectedItemList[positionNavSelected] != "") {
            // Lấy filename từ keySelected (vd: "1.png" từ "403347/1.png" hoặc "746C78/1.png")
            val keySelected = keySelectedItemList[positionNavSelected]
            val keyFileName = keySelected.substringAfterLast('/')

            // Duyệt qua từng item trong bộ phận
            for (item in _dataCustomize.value!!.layerList[positionNavSelected].layer) {
                // So sánh theo filename thay vì full path
                val itemFileName = item.image.substringAfterLast('/')

                if (itemFileName == keyFileName) {
                    // Tìm thấy item matching
                    pathColor = item.listColor[position].path
                    pathSelectedList[positionCustom] = pathColor

                    // ⭐ Update keySelected để lần sau vẫn tìm được
                    keySelectedItemList[positionNavSelected] = item.listColor[position].path
                    break
                }
            }
        }
        setColorItemNav(positionNavSelected, position)
        return pathColor
    }

    /**
     * Update màu cho TẤT CẢ items trong layer hiện tại khi user chọn màu khác
     * @param colorPosition: Vị trí màu được chọn trong rcvColor (0, 1, 2...)
     *
     * Logic:
     * - Duyệt qua tất cả items trong itemNavList[positionNavSelected]
     * - Với mỗi item có listImageColor (layer có màu):
     *   + Lấy path mới từ listImageColor[colorPosition]
     *   + Update item.path sang màu mới
     * - Skip NONE và RANDOM buttons
     * - Skip items không có màu (listImageColor rỗng)
     */
    suspend fun updateAllItemsColor(colorPosition: Int) {
        val currentNavIndex = positionNavSelected
        val currentList = itemNavList[currentNavIndex]

        // Validate: Check list không rỗng
        if (currentList.isEmpty()) return

        // Check xem layer có items màu không
        val hasColorItems = currentList.any {
            it.listImageColor.isNotEmpty() &&
                    it.path != AssetsKey.NONE_LAYER &&
                    it.path != AssetsKey.RANDOM_LAYER
        }
        if (!hasColorItems) return

        // Update từng item
        val updatedList = ArrayList<ItemNavCustomModel>()
        for (item in currentList) {
            // Skip NONE và RANDOM buttons
            if (item.path == AssetsKey.NONE_LAYER ||
                item.path == AssetsKey.RANDOM_LAYER) {
                updatedList.add(item)
                continue
            }

            // Nếu item có màu
            if (item.listImageColor.isNotEmpty()) {
                // Validate bounds để tránh IndexOutOfBoundsException
                if (colorPosition < item.listImageColor.size) {
                    // Lấy path mới từ màu được chọn
                    val newPath = item.listImageColor[colorPosition].path
                    // Tạo item mới với path mới
                    updatedList.add(item.copy(path = newPath))
                } else {
                    // ColorPosition invalid, giữ nguyên
                    updatedList.add(item)
                }
            } else {
                // Item không có màu, giữ nguyên
                updatedList.add(item)
            }
        }

        // Update lại list
        itemNavList[currentNavIndex] = updatedList
    }

//----------------------------------------------------------------------------------------------------------------------
// Extension other

    suspend fun setImageViewList(frameLayout: FrameLayout) {
        imageViewList.clear()
        imageViewList.addAll(addImageViewToLayout(_dataCustomize.value!!.layerList.size, frameLayout))
    }

    fun addImageViewToLayout(quantityLayer: Int, frameLayout: FrameLayout): ArrayList<ImageView> {
        val imageViewList = ArrayList<ImageView>()
        for (i in 0 until quantityLayer) {
            val imageView = ImageView(frameLayout.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            frameLayout.addView(imageView)
            imageViewList.add(imageView)
        }
        return imageViewList
    }

    fun createListItem(layers: LayerListModel, isBody: Boolean = false): ArrayList<ItemNavCustomModel> {
        val listItem = arrayListOf<ItemNavCustomModel>()
        val positionCustom = layers.positionCustom
        val positionNavigation = layers.positionNavigation
        if (isBody) {
            listItem.add(
                ItemNavCustomModel(
                    path = AssetsKey.RANDOM_LAYER,
                    positionCustom = positionCustom,
                    positionNavigation = positionNavigation
                )
            )
        } else {
            listItem.add(
                ItemNavCustomModel(
                    path = AssetsKey.NONE_LAYER,
                    positionCustom = positionCustom,
                    positionNavigation = positionNavigation,
                    isSelected = true
                )
            )
            listItem.add(
                ItemNavCustomModel(
                    path = AssetsKey.RANDOM_LAYER,
                    positionCustom = positionCustom,
                    positionNavigation = positionNavigation,
                )
            )
        }
        for (layer in layers.layer) {
            if (!layer.isMoreColors) {
                listItem.add(
                    ItemNavCustomModel(
                        path = layer.image, positionCustom = positionCustom, positionNavigation = positionNavigation,
                        thumb = layer.thumb
                    )
                )
            } else {
                val listItemColor = ArrayList<ItemColorImageModel>()

                for (colorModel in layer.listColor) {
                    listItemColor.add(
                        ItemColorImageModel(
                            color = colorModel.color, path = colorModel.path
                        )
                    )
                }
                listItem.add(
                    ItemNavCustomModel(
                        path = layer.image,
                        positionCustom = positionCustom,
                        positionNavigation = positionNavigation,
                        isSelected = false,
                        listImageColor = listItemColor,
                        thumb = layer.thumb
                    )
                )
            }
        }
        return listItem
    }

    fun saveImageFromView(context: Context, view: View): Flow<SaveState> = flow {
        emit(SaveState.Loading)
        val bitmap = BitmapHelper.createBimapFromView(view)
        MediaHelper.saveBitmapToInternalStorageZip(context, ValueKey.DOWNLOAD_ALBUM_BACKGROUND, bitmap).collect { state ->
            emit(state)
        }
    }.flowOn(Dispatchers.IO)

    fun checkDataInternet(context: CustomizeCharacterActivity, action: (() -> Unit)) {
        if (!_isDataAPI.value) {
            action.invoke()
            return
        }
        InternetHelper.checkInternet(context) { result ->
            if (result == HandleState.SUCCESS) {
                action.invoke()
            } else {
                // Show No Internet dialog
                val dialog = com.animal.avatar.charactor.maker.dialog.YesNoDialog(
                    context,
                    R.string.error,
                    R.string.please_check_your_internet,
                    isError = true
                )
                dialog.show()
                dialog.onYesClick = {
                    dialog.dismiss()
                }

            }
        }
    }



    suspend fun resetDataList() {
        val quantityLayer = _dataCustomize.value!!.layerList.size
        Log.d("CustomizeViewModel", "resetDataList - quantityLayer: $quantityLayer")

        val positionColorItemList = ArrayList<Int>(quantityLayer)
        val isSelectedItemList = ArrayList<Boolean>(quantityLayer)
        val keySelectedItemList = ArrayList<String>(quantityLayer)
        val isShowColorList = ArrayList<Boolean>(quantityLayer)
        val pathSelectedList = ArrayList<String>(quantityLayer)

        repeat(quantityLayer) {
            positionColorItemList.add(0)
            isSelectedItemList.add(false)
            keySelectedItemList.add("")
            isShowColorList.add(true)
            pathSelectedList.add("")
        }

        updatePositionColorItemList(positionColorItemList)
        updateIsSelectedItemList(isSelectedItemList)
        updateKeySelectedItemList(keySelectedItemList)
        updateIsShowColorList(isShowColorList)
        updatePathSelectedList(pathSelectedList)

        Log.d("CustomizeViewModel", "resetDataList completed - All lists initialized with size: $quantityLayer")
    }

    fun getSuggestionList(): SuggestionModel {
        return SuggestionModel(
            avatarPath = avatarPath,
            positionColorItemList = ArrayList(positionColorItemList),
            itemNavList = ArrayList(itemNavList),
            colorItemNavList = ArrayList(colorItemNavList),
            isSelectedItemList = ArrayList(isSelectedItemList),
            keySelectedItemList = ArrayList(keySelectedItemList),
            isShowColorList = ArrayList(isShowColorList),
            pathSelectedList = ArrayList(pathSelectedList)
        )
    }

    fun fillSuggestionToCustomize() {
        // Validate and fix list sizes to match current data structure
        val expectedSize = _dataCustomize.value?.layerList?.size ?: 0

        Log.d("CustomizeViewModel", "fillSuggestionToCustomize - Expected size: $expectedSize")
        Log.d("CustomizeViewModel", "Loaded positionColorItemList size: ${suggestionModel.positionColorItemList.size}")

        // Fix positionColorItemList size
        val fixedPositionColorItemList = validateListSize(
            suggestionModel.positionColorItemList,
            expectedSize,
            "positionColorItemList"
        ) { 0 }

        // Fix isSelectedItemList size
        val fixedIsSelectedItemList = validateListSize(
            suggestionModel.isSelectedItemList,
            expectedSize,
            "isSelectedItemList"
        ) { false }

        // Fix keySelectedItemList size
        val fixedKeySelectedItemList = validateListSize(
            suggestionModel.keySelectedItemList,
            expectedSize,
            "keySelectedItemList"
        ) { "" }

        // Fix isShowColorList size
        val fixedIsShowColorList = validateListSize(
            suggestionModel.isShowColorList,
            expectedSize,
            "isShowColorList"
        ) { true }

        // Fix pathSelectedList size
        val fixedPathSelectedList = validateListSize(
            suggestionModel.pathSelectedList,
            expectedSize,
            "pathSelectedList"
        ) { "" }

        updatePositionColorItemList(fixedPositionColorItemList)
        updateItemNavList(suggestionModel.itemNavList)
        updateColorNavList(suggestionModel.colorItemNavList)
        updateIsSelectedItemList(fixedIsSelectedItemList)
        updateKeySelectedItemList(fixedKeySelectedItemList)
        updateIsShowColorList(fixedIsShowColorList)
        updatePathSelectedList(fixedPathSelectedList)
    }

    fun rewriteDomainsToActive() {
        val activeDomain = if (!isFailBaseURL) DomainKey.BASE_URL else DomainKey.BASE_URL_PREVENTIVE
        val deadDomain = if (!isFailBaseURL) DomainKey.BASE_URL_PREVENTIVE else DomainKey.BASE_URL

        if (!pathSelectedList.any { it.contains(deadDomain) } &&
            !keySelectedItemList.any { it.contains(deadDomain) }) return

        Log.d("PATTERN_P", "🔄 Rewriting dead domain in saved creation...")
        Log.d("PATTERN_P", "   dead  : $deadDomain")
        Log.d("PATTERN_P", "   active: $activeDomain")

        for (i in pathSelectedList.indices) {
            pathSelectedList[i] = pathSelectedList[i].replace(deadDomain, activeDomain)
        }

        for (i in keySelectedItemList.indices) {
            keySelectedItemList[i] = keySelectedItemList[i].replace(deadDomain, activeDomain)
        }

        for (i in itemNavList.indices) {
            val navList = itemNavList[i]
            for (j in navList.indices) {
                val item = navList[j]
                val newColors = ArrayList<ItemColorImageModel>(item.listImageColor.size)
                for (colorItem in item.listImageColor) {
                    newColors.add(colorItem.copy(path = colorItem.path.replace(deadDomain, activeDomain)))
                }
                navList[j] = item.copy(
                    path = item.path.replace(deadDomain, activeDomain),
                    listImageColor = newColors
                )
            }
        }

        Log.d("PATTERN_P", "✅ Domain rewrite complete")
    }

    private fun <T> validateListSize(
        list: ArrayList<T>,
        expectedSize: Int,
        listName: String,
        defaultValue: () -> T
    ): ArrayList<T> {
        val result = ArrayList(list)
        val actualSize = result.size

        if (actualSize != expectedSize) {
            Log.w("CustomizeViewModel", "$listName size mismatch! Expected: $expectedSize, Actual: $actualSize")

            when {
                actualSize < expectedSize -> {
                    // Add missing elements
                    val itemsToAdd = expectedSize - actualSize
                    Log.w("CustomizeViewModel", "Adding $itemsToAdd default items to $listName")
                    repeat(itemsToAdd) {
                        result.add(defaultValue())
                    }
                }
                actualSize > expectedSize -> {
                    // Remove extra elements
                    val itemsToRemove = actualSize - expectedSize
                    Log.w("CustomizeViewModel", "Removing $itemsToRemove extra items from $listName")
                    repeat(itemsToRemove) {
                        if (result.size > expectedSize) {
                            result.removeAt(result.size - 1)
                        }
                    }
                }
            }
            Log.d("CustomizeViewModel", "$listName fixed. New size: ${result.size}")
        }

        return result
    }

    suspend fun updateEditCharacter(context: Context, pathInternal: String) {
        val editList = loadEditList(context)
        val indexEdit = editList.indexOfFirst { it.pathInternalEdit == suggestionModel.pathInternalEdit }
        if (indexEdit != -1) {
            editList[indexEdit].apply {
                avatarPath = this@CustomizeCharacterViewModel.avatarPath
                positionColorItemList = ArrayList(this@CustomizeCharacterViewModel.positionColorItemList)
                itemNavList = ArrayList(this@CustomizeCharacterViewModel.itemNavList.map { ArrayList(it) })
                colorItemNavList = ArrayList(this@CustomizeCharacterViewModel.colorItemNavList.map { ArrayList(it) })
                isSelectedItemList = ArrayList(this@CustomizeCharacterViewModel.isSelectedItemList)
                keySelectedItemList = ArrayList(this@CustomizeCharacterViewModel.keySelectedItemList)
                isShowColorList = ArrayList(this@CustomizeCharacterViewModel.isShowColorList)
                pathSelectedList = ArrayList(this@CustomizeCharacterViewModel.pathSelectedList)
                pathInternalEdit = pathInternal
            }
            MediaHelper.writeListToFile(context, ValueKey.EDIT_FILE_INTERNAL, editList)

            // ⭐ CRITICAL FIX: Update suggestionModel.pathInternalEdit so next save can find the entry
            suggestionModel.pathInternalEdit = pathInternal
        }
    }

    fun addCharacterToEditList(context: Context, pathInternal: String) {
        val editList = loadEditList(context)
        val newEditModel = SuggestionModel(
            avatarPath = avatarPath,
            positionColorItemList = this@CustomizeCharacterViewModel.positionColorItemList,
            itemNavList = this@CustomizeCharacterViewModel.itemNavList,
            colorItemNavList = this@CustomizeCharacterViewModel.colorItemNavList,
            isSelectedItemList = this@CustomizeCharacterViewModel.isSelectedItemList,
            keySelectedItemList = this@CustomizeCharacterViewModel.keySelectedItemList,
            isShowColorList = this@CustomizeCharacterViewModel.isShowColorList,
            pathSelectedList = this@CustomizeCharacterViewModel.pathSelectedList,
            pathInternalEdit = pathInternal,
        )
        editList.add(0, newEditModel)
        MediaHelper.writeListToFile(context, ValueKey.EDIT_FILE_INTERNAL, editList)
    }

    private fun loadEditList(context: Context): ArrayList<SuggestionModel> {
        return try {
            MediaHelper.readListFromFile<SuggestionModel>(context, ValueKey.EDIT_FILE_INTERNAL)
                .toCollection(ArrayList())
        } catch (e: Exception) {
            Log.e("nbhieu", "updateEditCharacter: $e")
            arrayListOf()
        }
    }
//----------------------------------------------------------------------------------------------------------------------

}