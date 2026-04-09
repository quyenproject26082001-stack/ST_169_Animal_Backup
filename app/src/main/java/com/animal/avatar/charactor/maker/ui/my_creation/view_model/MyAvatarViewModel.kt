package com.animal.avatar.charactor.maker.ui.my_creation.view_model

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.core.base.BaseActivity
import com.animal.avatar.charactor.maker.core.helper.InternetHelper
import com.animal.avatar.charactor.maker.core.helper.MediaHelper
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.core.utils.state.HandleState
import com.animal.avatar.charactor.maker.data.model.MyAlbumModel
import com.animal.avatar.charactor.maker.data.model.custom.CustomizeModel
import com.animal.avatar.charactor.maker.data.model.custom.SuggestionModel
import com.animal.avatar.charactor.maker.ui.my_creation.MyCreationActivity
import com.animal.avatar.charactor.maker.ui.random_character.RandomCharacterActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private fun String.urlPath(): String =
    if (startsWith("http")) substringAfter("://").substringAfter("/")
    else this

class MyAvatarViewModel : ViewModel() {
    private val _myAvatarList = MutableStateFlow<ArrayList<MyAlbumModel>>(arrayListOf())
    val myAvatarList = _myAvatarList.asStateFlow()
    private val _isLastItem = MutableStateFlow<Boolean>(false)
    val isLastItem: StateFlow<Boolean> = _isLastItem


    var isApi: Boolean = false
    var positionCharacter = -1
    var editModel = SuggestionModel()

    fun loadMyAvatar(context: Context) {
        android.util.Log.d("MyAvatarViewModel", "📂 loadMyAvatar() START")
        android.util.Log.d("MyAvatarViewModel", "Thread: ${Thread.currentThread().name}")
        android.util.Log.d("MyAvatarViewModel", "Context: ${context.javaClass.simpleName}")

        try {
            val editList = MediaHelper.readListFromFile<SuggestionModel>(context, ValueKey.EDIT_FILE_INTERNAL)
            android.util.Log.d("MyAvatarViewModel", "✅ Loaded ${editList.size} items from EDIT_FILE_INTERNAL")

            editList.forEachIndexed { index, suggestion ->
                android.util.Log.d("MyAvatarViewModel", "  [$index] path: ${suggestion.pathInternalEdit}")
                android.util.Log.d("MyAvatarViewModel", "  [$index] avatarPath: ${suggestion.avatarPath}")
                // Check if file exists
                val file = java.io.File(suggestion.pathInternalEdit)
                val exists = file.exists()
                val size = if (exists) file.length() else 0
                android.util.Log.d("MyAvatarViewModel", "  [$index] File exists: $exists, Size: $size bytes")
            }

            val albumList = editList.map { MyAlbumModel(it.pathInternalEdit) }.toCollection(ArrayList())
            _myAvatarList.value = albumList

            android.util.Log.d("MyAvatarViewModel", "✅ Updated myAvatarList with ${albumList.size} items")
            android.util.Log.d("MyAvatarViewModel", "Current myAvatarList size: ${_myAvatarList.value.size}")
        } catch (e: Exception) {
            android.util.Log.e("MyAvatarViewModel", "❌ ERROR loading avatars: ${e.message}", e)
            _myAvatarList.value = arrayListOf()
        }

        checkLastItem()
        android.util.Log.d("MyAvatarViewModel", "📂 loadMyAvatar() END")
    }

    private fun checkLastItem() {
        _isLastItem.value = _myAvatarList.value.any { !it.isSelected }
    }

    suspend fun deleteItem(context: Context, pathList: ArrayList<String>) {

        val originList = MediaHelper
            .readListFromFile<SuggestionModel>(context, ValueKey.EDIT_FILE_INTERNAL)
            .toCollection(ArrayList())

        val editDeleteList = originList.filter { it.pathInternalEdit in pathList }
        val myAvatarDeleteList = _myAvatarList.value.filter { it.path in pathList }

        // Update origin file
        val newOriginList = ArrayList(originList).apply {
            removeAll(editDeleteList)
        }
        MediaHelper.writeListToFile(context, ValueKey.EDIT_FILE_INTERNAL, newOriginList)

        // Update StateFlow properly (important!)
        val newAvatarList = ArrayList(_myAvatarList.value).apply {
            removeAll(myAvatarDeleteList)
        }

        _myAvatarList.value = newAvatarList
    }

    suspend fun editItem(context: Context, pathInternal: String, allData: ArrayList<CustomizeModel>){
        val originList = MediaHelper
            .readListFromFile<SuggestionModel>(context, ValueKey.EDIT_FILE_INTERNAL)
            .toCollection(ArrayList())

        editModel = originList.first { it.pathInternalEdit == pathInternal }
        val savedAvatarPath = editModel.avatarPath.urlPath()
        positionCharacter = allData.indexOfFirst { character ->
            character.avatar.urlPath() == savedAvatarPath
        }
        // ✅ FIX: Use isFromAPI flag from character data instead of position
        isApi = if (positionCharacter >= 0) allData[positionCharacter].isFromAPI else false
        MediaHelper.writeModelToFile(context, ValueKey.SUGGESTION_FILE_INTERNAL, editModel)
    }

    fun checkDataInternet(context: BaseActivity<*>, action: (() -> Unit)) {
        if (!isApi) {
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
                    com.animal.avatar.charactor.maker.R.string.error,
                    com.animal.avatar.charactor.maker.R.string.please_check_your_internet,
                    isError = true
                )
                dialog.show()
                dialog.onYesClick = {
                    dialog.dismiss()
                }
            }
        }
    }

    fun showLongClick(positionSelect: Int) {
        _myAvatarList.value = _myAvatarList.value.mapIndexed { position, item ->
            item.copy(isSelected = position == positionSelect, isShowSelection = true)
        }.toCollection(ArrayList())
        checkLastItem()
    }

    fun selectAll(shouldSelect: Boolean) {
        _myAvatarList.value = _myAvatarList.value.map {
            it.copy(isSelected = shouldSelect, isShowSelection = true)
        }.toCollection(ArrayList())
        checkLastItem()
    }

    fun toggleSelect(position: Int) {
        val list = _myAvatarList.value.toMutableList()
        list[position] = list[position].copy(isSelected = !list[position].isSelected, isShowSelection = true)
        _myAvatarList.value = list.toCollection(ArrayList())
        checkLastItem()
    }

    fun getPathSelected() : ArrayList<String>{
        return _myAvatarList.value
            .filter { it.isSelected }
            .map { it.path }
            .toCollection(ArrayList())
    }

    fun clearSelection() {
        _myAvatarList.value = _myAvatarList.value.map {
            it.copy(isSelected = false, isShowSelection = false)
        }.toCollection(ArrayList())
        checkLastItem()
    }
}