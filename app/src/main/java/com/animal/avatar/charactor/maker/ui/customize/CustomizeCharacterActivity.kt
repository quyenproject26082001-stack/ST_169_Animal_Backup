package com.animal.avatar.charactor.maker.ui.customize

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.core.base.BaseActivity
import com.animal.avatar.charactor.maker.core.extensions.dLog
import com.animal.avatar.charactor.maker.core.extensions.eLog
import com.animal.avatar.charactor.maker.core.extensions.hideNavigation
import com.animal.avatar.charactor.maker.core.extensions.invisible
import com.animal.avatar.charactor.maker.core.extensions.loadNativeCollabAds
import com.animal.avatar.charactor.maker.core.extensions.logEvent
import com.animal.avatar.charactor.maker.core.extensions.setImageActionBar
import com.animal.avatar.charactor.maker.core.extensions.showInterAll
import com.animal.avatar.charactor.maker.core.extensions.startIntentLeftToRight
import com.animal.avatar.charactor.maker.core.extensions.startIntentRightToLeft
import com.animal.avatar.charactor.maker.core.extensions.visible
import com.animal.avatar.charactor.maker.core.helper.LanguageHelper
import com.animal.avatar.charactor.maker.core.utils.key.IntentKey
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.core.utils.state.SaveState
import com.animal.avatar.charactor.maker.data.model.custom.ItemNavCustomModel
import com.animal.avatar.charactor.maker.databinding.ActivityCustomizeBinding
import com.animal.avatar.charactor.maker.dialog.YesNoDialog
import com.animal.avatar.charactor.maker.ui.home.DataViewModel
import com.animal.avatar.charactor.maker.core.extensions.tap
import com.animal.avatar.charactor.maker.core.helper.MediaHelper
import com.animal.avatar.charactor.maker.data.model.custom.SuggestionModel
import com.animal.avatar.charactor.maker.ui.add_character.AddCharacterActivity
import com.animal.avatar.charactor.maker.ui.my_creation.MyCreationActivity
import com.lvt.ads.util.Admob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.jvm.java

class CustomizeCharacterActivity : BaseActivity<ActivityCustomizeBinding>() {
    private val viewModel: CustomizeCharacterViewModel by viewModels()
    private var lastClickedLayerPosition: Int =
        -1 // Track last clicked layer position for scrolling
    private val dataViewModel: DataViewModel by viewModels()
    val colorLayerCustomizeAdapter by lazy { ColorLayerCustomizeAdapter(this) }
    val layerCustomizeAdapter by lazy { LayerCustomizeAdapter(this) }
    val bottomNavigationCustomizeAdapter by lazy { BottomNavigationCustomizeAdapter(this) }
    val hideList: ArrayList<View> by lazy {
        arrayListOf(
            binding.btnRandom,
            binding.color,
            binding.rcvLayer,
            binding.flBottomNav
        )
    }

    override fun setViewBinding(): ActivityCustomizeBinding {
        return ActivityCustomizeBinding.inflate(LayoutInflater.from(this))
    }

    override fun initView() {
        initRcv()
        lifecycleScope.launch { showLoading() }
        val dataType = intent.getIntExtra(IntentKey.DATA_TYPE_KEY, IntentKey.DATA_TYPE_DEFAULT)
        if (dataType != IntentKey.DATA_TYPE_DEFAULT) {
            dataViewModel.loadDataByType(this, dataType)
        } else {
            dataViewModel.ensureData(this)
        }
    }

    override fun dataObservable() {
        val dataType = intent.getIntExtra(IntentKey.DATA_TYPE_KEY, IntentKey.DATA_TYPE_DEFAULT)
        lifecycleScope.launch {
            launch {
                val flow = if (dataType != IntentKey.DATA_TYPE_DEFAULT)
                    dataViewModel.filteredData
                else
                    dataViewModel.allData
                flow.collect { list ->
                    if (list.isNotEmpty()) {
                        viewModel.positionSelected = intent.getIntExtra(IntentKey.INTENT_KEY, 0)
                        viewModel.statusFrom =
                            intent.getIntExtra(IntentKey.STATUS_FROM_KEY, ValueKey.CREATE)

                        Log.d("CustomizeCharacter", "🔍 FIX APPLIED! Position: ${viewModel.positionSelected}, List size: ${list.size}")

                        // ✅ Check bounds before accessing list (both negative and out of bounds)
                        if (viewModel.positionSelected < 0 || viewModel.positionSelected >= list.size) {
                            Log.e("CustomizeCharacter", "❌ Invalid position: ${viewModel.positionSelected}, list size: ${list.size}. Resetting to 0.")
                            viewModel.positionSelected = 0
                        }

                        viewModel.setDataCustomize(list[viewModel.positionSelected])
                        viewModel.setIsDataAPI(list[viewModel.positionSelected].isFromAPI)

                        // 🔍 LOG API DATA
                        if (list[viewModel.positionSelected].isFromAPI) {
                            Log.d("API_ITEMS", "========== DATA FROM API ==========")
                            Log.d("API_ITEMS", "Total categories: ${list[viewModel.positionSelected].layerList.size}")
                            list[viewModel.positionSelected].layerList.forEachIndexed { categoryIndex, layerList ->
                                Log.d("API_ITEMS", "")
                                Log.d("API_ITEMS", "--- Category $categoryIndex ---")
                                Log.d("API_ITEMS", "Navigation Image: ${layerList.imageNavigation}")
                                Log.d("API_ITEMS", "Position Custom: ${layerList.positionCustom}")
                                Log.d("API_ITEMS", "Position Navigation: ${layerList.positionNavigation}")
                                Log.d("API_ITEMS", "Total items in category: ${layerList.layer.size}")

                                layerList.layer.forEachIndexed { itemIndex, layer ->
                                    Log.d("API_ITEMS", "  Item $itemIndex:")
                                    Log.d("API_ITEMS", "    - Image Path: ${layer.image}")
                                    Log.d("API_ITEMS", "    - Has Colors: ${layer.isMoreColors}")
                                    if (layer.isMoreColors && layer.listColor.isNotEmpty()) {
                                        Log.d("API_ITEMS", "    - Color Count: ${layer.listColor.size}")
                                        layer.listColor.forEachIndexed { colorIndex, colorModel ->
                                            Log.d("API_ITEMS", "      Color $colorIndex: ${colorModel.color} -> ${colorModel.path}")
                                        }
                                    }
                                }
                            }
                            Log.d("API_ITEMS", "===================================")
                        }

                        initData()
                    }
                }
            }
            launch {
                viewModel.isFlip.collect { status ->
                    val rotation = if (status) -180f else 0f
                    viewModel.imageViewList.forEachIndexed { index, view ->
                        view.rotationY = rotation
                    }
                }
            }
            launch {
                viewModel.isHideView.collect { status ->
                    if (viewModel.isCreated.value) {
                        val res = if (status) {
                            hideList.forEach { it.invisible() }
                            R.drawable.ic_hide
                        } else {
                            hideList.forEach { it.visible() }
                            checkStatusColor()
                            R.drawable.ic_show
                        }
                        binding.btnHide.setImageResource(res)
                    }
                }
            }
            launch {
                viewModel.bottomNavigationList.collect { bottomNavigationList ->
                    if (bottomNavigationList.isNotEmpty()) {
                        bottomNavigationCustomizeAdapter.submitList(bottomNavigationList)
                        layerCustomizeAdapter.submitList(viewModel.itemNavList[viewModel.positionNavSelected])
                        colorLayerCustomizeAdapter.submitList(viewModel.colorItemNavList[viewModel.positionNavSelected])
                        if (viewModel.colorItemNavList[viewModel.positionNavSelected].isNotEmpty()) {
                            binding.rcvColor.smoothScrollToPosition(viewModel.colorItemNavList[viewModel.positionNavSelected].indexOfFirst { it.isSelected })
                        }
                    }
                }
            }
        }
    }

    override fun viewListener() {
        binding.apply {
            actionBar.apply {
                btnActionBarLeft.tap { confirmExit() }
                btnActionBarCenter.tap { handleReset() }
                btnFlip.tap { viewModel.setIsFlip() }
                binding.actionBar.btnActionBarRightText.tap {
                    handleSave()
                }
            }
            btnRandom.tap { viewModel.checkDataInternet(this@CustomizeCharacterActivity) { handleRandomAllLayer() } }
            btnColor.tap { viewModel.checkDataInternet(this@CustomizeCharacterActivity) { handleStatusColor() } }
            btnHide.tap { viewModel.checkDataInternet(this@CustomizeCharacterActivity) { viewModel.setIsHideView() } }
        }
        handleRcv()
    }

    override fun initActionBar() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.ic_back)
            setImageActionBar(btnActionBarCenter, R.drawable.ic_reset)
            btnActionBarRightText.visible()
            btnActionBarRight.invisible()
            tvRightText.isSelected = true
        }

    }

    private fun initRcv() {
        binding.apply {
            rcvLayer.apply {
                adapter = layerCustomizeAdapter
                itemAnimator = null
            }

            rcvColor.apply {
                adapter = colorLayerCustomizeAdapter
                itemAnimator = null
            }

            rcvNavigation.apply {
                adapter = bottomNavigationCustomizeAdapter
                itemAnimator = null
            }
        }
    }

    private fun handleRcv() {
        layerCustomizeAdapter.onItemClick =
            { item, position ->
                viewModel.checkDataInternet(this) {
                    handleFillLayer(
                        item,
                        position
                    )
                }
            }

        layerCustomizeAdapter.onNoneClick =
            { position -> viewModel.checkDataInternet(this) { handleNoneLayer(position) } }

        layerCustomizeAdapter.onRandomClick =
            { viewModel.checkDataInternet(this) { handleRandomLayer() } }

        colorLayerCustomizeAdapter.onItemClick =
            { position -> viewModel.checkDataInternet(this) { handleChangeColorLayer(position) } }

        bottomNavigationCustomizeAdapter.onItemClick =
            { positionBottomNavigation ->
                viewModel.checkDataInternet(this) {
                    handleClickBottomNavigation(
                        positionBottomNavigation
                    )
                }
            }
    }

    private fun initData() {
        val handleExceptionCoroutine = CoroutineExceptionHandler { _, throwable ->
            eLog("initData: ${throwable.message}")
            CoroutineScope(Dispatchers.Main).launch {
                dismissLoading()
                hideNavigation(false)

                val dialogExit =
                    YesNoDialog(
                        this@CustomizeCharacterActivity,
                        R.string.error,
                        R.string.an_error_occurred
                    )
                dialogExit.show()
                dialogExit.onNoClick = {
                    dialogExit.dismiss()
                    finish()
                }
                dialogExit.onYesClick = {
                    dialogExit.dismiss()
                    hideNavigation(false)

                    startIntentRightToLeft(
                        CustomizeCharacterActivity::class.java,
                        viewModel.positionSelected
                    )
                    finish()
                }
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO + handleExceptionCoroutine).launch {
            var pathImageDefault = ""
            // Get data from list
            val deferred1 = async {
                viewModel.updateAvatarPath(viewModel.dataCustomize.value!!.avatar)
                when (viewModel.statusFrom) {
                    ValueKey.CREATE -> {
                        viewModel.resetDataList()
                        viewModel.addValueToItemNavList()
                        viewModel.setItemColorDefault()
                        viewModel.setFocusItemNavDefault()
                    }

                    // Edit
                    else -> {
                        viewModel.updateSuggestionModel(
                            MediaHelper.readModelFromFile<SuggestionModel>(
                                this@CustomizeCharacterActivity,
                                ValueKey.SUGGESTION_FILE_INTERNAL
                            )!!
                        )
                        viewModel.fillSuggestionToCustomize()
                        viewModel.rewriteDomainsToActive()
                    }
                }

                viewModel.setPositionCustom(viewModel.dataCustomize.value!!.layerList.first().positionCustom)
                viewModel.setPositionNavSelected(viewModel.dataCustomize.value!!.layerList.first().positionNavigation)
                viewModel.setBottomNavigationListDefault()
                dLog("deferred1")
                return@async true
            }
            // Add custom view in FrameLayout
            val deferred2 = async(Dispatchers.Main) {
                if (deferred1.await()) {
                    viewModel.setImageViewList(binding.layoutCustomLayer)
                    dLog("deferred2")
                }
                return@async true
            }

            // Fill data default
            val deferred3 = async {
                if (deferred1.await() && deferred2.await()) {
                    if (viewModel.statusFrom == ValueKey.CREATE) {
                        pathImageDefault =
                            viewModel.dataCustomize.value!!.layerList.first().layer.first().image
                        viewModel.setIsSelectedItem(viewModel.positionCustom)
                        viewModel.setPathSelected(viewModel.positionCustom, pathImageDefault)
                        viewModel.setKeySelected(viewModel.positionNavSelected, pathImageDefault)
                    }
                    dLog("deferred3")
                }
                return@async true
            }

            withContext(Dispatchers.Main) {
                if (deferred1.await() && deferred2.await() && deferred3.await()) {
                    when (viewModel.statusFrom) {
                        ValueKey.CREATE -> {
                            Glide.with(this@CustomizeCharacterActivity).load(pathImageDefault)
                                .into(viewModel.imageViewList[viewModel.positionCustom])
                        }

                        // Edit
                        else -> {
                            viewModel.pathSelectedList.forEachIndexed { index, path ->
                                if (path != "") {
                                    Glide.with(this@CustomizeCharacterActivity).load(path)
                                        .into(viewModel.imageViewList[index])
                                }
                            }
                        }
                    }

                    layerCustomizeAdapter.submitList(viewModel.itemNavList[viewModel.positionNavSelected])
                    colorLayerCustomizeAdapter.submitList(viewModel.colorItemNavList[viewModel.positionNavSelected])
                    checkStatusColor()
                    viewModel.setIsCreated(true)
                    dismissLoading()
                    delay(300)
                    dismissLoading()
                    hideNavigation(false)
                    dLog("main")
                }
            }
        }
    }

    private fun checkStatusColor() {
        if (viewModel.colorItemNavList[viewModel.positionNavSelected].isNotEmpty()) {
            binding.color.visible()
             binding.btnColor.visible()
            val (res, status) = if (viewModel.isShowColorList[viewModel.positionNavSelected]) {
                R.drawable.ic_color to true
            } else {
                R.drawable.ic_color_hide to false
            }
            binding.btnColor.setImageResource(res)
            binding.flColor.isVisible = status
        } else {
            binding.color.invisible()
            binding.btnColor.invisible()
            binding.flColor.invisible()
        }
    }

    private fun handleStatusColor(isClose: Boolean = false) {
        if (isClose) {
            binding.flColor.invisible()
            viewModel.updateIsShowColorList(viewModel.positionNavSelected, false)
        } else {
            if (viewModel.isShowColorList[viewModel.positionNavSelected]) {
                binding.flColor.invisible()
            } else {
                binding.flColor.visible()
            }
            viewModel.updateIsShowColorList(
                viewModel.positionNavSelected,
                !viewModel.isShowColorList[viewModel.positionNavSelected]
            )
        }
        checkStatusColor()
    }

    private fun handleFillLayer(item: ItemNavCustomModel, position: Int) {
        lastClickedLayerPosition = position // Save clicked position for scrolling
        android.util.Log.d("CustomizeScroll", "Layer clicked at position: $position")

        lifecycleScope.launch(Dispatchers.IO) {
            val pathSelected = viewModel.setClickFillLayer(item, position)
            withContext(Dispatchers.Main) {
                Glide.with(this@CustomizeCharacterActivity).load(pathSelected)
                    .into(viewModel.imageViewList[viewModel.positionCustom])
                layerCustomizeAdapter.submitList(viewModel.itemNavList[viewModel.positionNavSelected])
            }
        }
    }

    private fun handleNoneLayer(position: Int) {
        lastClickedLayerPosition = position // Save clicked position for scrolling
        android.util.Log.d("CustomizeScroll", "None layer clicked at position: $position")

        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.setIsSelectedItem(viewModel.positionCustom)
            viewModel.setPathSelected(viewModel.positionCustom, "")
            viewModel.setKeySelected(viewModel.positionNavSelected, "")
            viewModel.setItemNavList(viewModel.positionNavSelected, position)
            withContext(Dispatchers.Main) {
                Glide.with(this@CustomizeCharacterActivity)
                    .clear(viewModel.imageViewList[viewModel.positionCustom])
                layerCustomizeAdapter.submitList(viewModel.itemNavList[viewModel.positionNavSelected])
            }
        }
    }

    private fun handleRandomLayer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val (pathRandom, isMoreColors) = viewModel.setClickRandomLayer()
            withContext(Dispatchers.Main) {
                Glide.with(this@CustomizeCharacterActivity).load(pathRandom)
                    .into(viewModel.imageViewList[viewModel.positionCustom])
                layerCustomizeAdapter.submitList(viewModel.itemNavList[viewModel.positionNavSelected])
                if (isMoreColors) {
                    colorLayerCustomizeAdapter.submitList(viewModel.colorItemNavList[viewModel.positionNavSelected])
                    binding.rcvColor.smoothScrollToPosition(viewModel.colorItemNavList[viewModel.positionNavSelected].indexOfFirst { it.isSelected })
                }
            }
        }
    }

    private fun handleChangeColorLayer(position: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Lấy path màu mới cho item đang được chọn
            val pathColor = viewModel.setClickChangeColor(position)

            // 2. ⭐ Update màu cho TẤT CẢ items trong rcvLayer
            viewModel.updateAllItemsColor(position)

            withContext(Dispatchers.Main) {
                // 3. Update ảnh trong canvas chính (layoutCustomLayer)
                if (pathColor != "") {
                    Glide.with(this@CustomizeCharacterActivity)
                        .load(pathColor)
                        .into(viewModel.imageViewList[viewModel.positionCustom])
                }

                // 4. Update highlight trong rcvColor
                colorLayerCustomizeAdapter.submitList(viewModel.colorItemNavList[viewModel.positionNavSelected])

                // 5. ⭐ Refresh rcvLayer với data mới (tất cả items đã đổi màu)
                // Sử dụng .toList() để tạo list mới, giúp DiffUtil detect changes
                val newList = viewModel.itemNavList[viewModel.positionNavSelected].toList()
                android.util.Log.d(
                    "CustomizeScroll",
                    "submitList called - list size: ${newList.size}"
                )

                layerCustomizeAdapter.submitList(newList) {
                    android.util.Log.d("CustomizeScroll", "submitList callback - list committed")

                    // 6. ⭐ Scroll rcvLayer to center the last clicked layer position IMMEDIATELY
                    if (lastClickedLayerPosition >= 0) {
                        val layoutManager = binding.rcvLayer.layoutManager

                        if (layoutManager is androidx.recyclerview.widget.GridLayoutManager) {
                            val spanCount = layoutManager.spanCount

                            // Calculate row position
                            val rowPosition = (lastClickedLayerPosition / spanCount) * spanCount

                            // Calculate offset to center the row on screen
                            val recyclerHeight = binding.rcvLayer.height

                            // Use estimated item height if view not yet laid out
                            val itemView =
                                layoutManager.findViewByPosition(lastClickedLayerPosition)
                            val itemHeight =
                                itemView?.height ?: (recyclerHeight / 5) // Estimate ~1/5 of screen

                            // Center the item vertically: (recyclerHeight / 2) - (itemHeight / 2)
                            val centerOffset = (recyclerHeight / 2) - (itemHeight / 2)

                            android.util.Log.d(
                                "CustomizeScroll",
                                "INSTANT scroll to position $lastClickedLayerPosition - row: $rowPosition, offset: $centerOffset"
                            )

                            // Immediate scroll without animation
                            layoutManager.scrollToPositionWithOffset(rowPosition, centerOffset)
                        } else {
                            // Fallback - but shouldn't happen
                            binding.rcvLayer.scrollToPosition(lastClickedLayerPosition)
                        }
                    }
                }
            }
        }
    }

    private fun handleClickBottomNavigation(positionBottomNavigation: Int) {
        if (positionBottomNavigation == viewModel.positionNavSelected) return
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.setPositionNavSelected(positionBottomNavigation)
            viewModel.setPositionCustom(viewModel.dataCustomize.value!!.layerList[positionBottomNavigation].positionCustom)
            viewModel.setClickBottomNavigation(positionBottomNavigation)
            withContext(Dispatchers.Main) { checkStatusColor()  }
        }
    }

    private fun confirmExit() {
        val dialog =
            YesNoDialog(this, R.string.exit_cus, R.string.haven_t_saved_it_yet_do_you_want_to_exit)
        LanguageHelper.setLocale(this)
        dialog.show()
        dialog.binding.nativeAds.visible()
        Admob.getInstance().loadNativeAd(
            this,
            getString(R.string.native_dialog_custom),
            dialog.binding.nativeAds,
            R.layout.ads_native_medium_btn_bottom_2_dialog
        )

        dialog.onYesClick = {
            dialog.dismiss()
            showInterAll { finish() }
        }
        dialog.onNoClick = {
            dialog.dismiss()
            hideNavigation(false)
        }
    }

    private fun handleSave() {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.saveImageFromView(this@CustomizeCharacterActivity, binding.layoutCustomLayer)
                .collect { result ->
                    when (result) {
                        is SaveState.Loading -> showLoading()

                        is SaveState.Error -> {
                            dismissLoading()
                            withContext(Dispatchers.Main) {
                                showToast(R.string.save_failed_please_try_again)
                            }
                        }

                        is SaveState.Success -> {
                            when (viewModel.statusFrom) {
                                ValueKey.EDIT -> {
                                    viewModel.updateEditCharacter(
                                        this@CustomizeCharacterActivity,
                                        result.path
                                    )
                                    dismissLoading()
                                    withContext(Dispatchers.Main) {
                                        logEvent("click_item_${viewModel.positionSelected}_edit")
                                        // ✅ 1) GỬI RESULT VỀ ViewActivity NGAY (nhưng chưa finish)
                                        val data = android.content.Intent().apply {
                                            putExtra("NEW_PATH", result.path)
                                        }
                                        setResult(RESULT_OK, data)

                                        // ✅ 2) VẪN SANG AddCharacterActivity như bạn muốn
                                        showInterAll {
                                            startIntentRightToLeft(AddCharacterActivity::class.java, result.path)
                                        }
                                    }
                                }

                                else -> {
                                    viewModel.addCharacterToEditList(
                                        this@CustomizeCharacterActivity,
                                        result.path
                                    )
                                    dismissLoading()
                                    withContext(Dispatchers.Main) {
                                        logEvent("click_item_${viewModel.positionSelected}_done")
                                        showInterAll {
                                            startIntentRightToLeft(
                                                AddCharacterActivity::class.java,
                                                result.path
                                            )
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
        }
    }

    private fun handleReset() {
        val dialog = YesNoDialog(
            this@CustomizeCharacterActivity,
            R.string.reset,
            R.string.change_your_whole_design_are_you_sure
        )
        LanguageHelper.setLocale(this)
        dialog.show()
        dialog.onYesClick = {
            dialog.dismiss()
            lifecycleScope.launch(Dispatchers.IO) {
                val pathDefault = viewModel.setClickReset()
                withContext(Dispatchers.Main) {
                    viewModel.imageViewList.forEach { imageView ->
                        Glide.with(this@CustomizeCharacterActivity).clear(imageView)
                    }
                    Glide.with(this@CustomizeCharacterActivity).load(pathDefault)
                        .into(viewModel.imageViewList[viewModel.dataCustomize.value!!.layerList.first().positionCustom])
                    layerCustomizeAdapter.submitList(viewModel.itemNavList[viewModel.positionNavSelected])
                    colorLayerCustomizeAdapter.submitList(viewModel.colorItemNavList[viewModel.positionNavSelected])
                    showInterAll { hideNavigation(false) }
                }
            }
        }
        dialog.onNoClick = {
            dialog.dismiss()
            hideNavigation(false)
        }
    }

    private fun handleRandomAllLayer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val timeStart = System.currentTimeMillis()
            val isOutTurn = viewModel.setClickRandomFullLayer()

            withContext(Dispatchers.Main) {
                viewModel.pathSelectedList.forEachIndexed { index, path ->
                    Glide.with(this@CustomizeCharacterActivity)
                        .load(path)
                        .into(viewModel.imageViewList[index])
                }
                layerCustomizeAdapter.submitList(viewModel.itemNavList[viewModel.positionNavSelected])
                colorLayerCustomizeAdapter.submitList(viewModel.colorItemNavList[viewModel.positionNavSelected])
                if (isOutTurn) binding.btnRandom.invisible()
                val timeEnd = System.currentTimeMillis()
                showInterAll {
                    dLog("time random all : ${timeEnd - timeStart}")
                }

            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.setIsCreated(false)
    }

    @SuppressLint("GestureBackNavigation", "MissingSuperCall")
    override fun onBackPressed() {
        confirmExit()
    }

    fun initNativeCollab() {
        Admob.getInstance().loadNativeCollapNotBanner(this,getString(R.string.native_collap_custom),binding.flNativeCollab)

    }

    override fun initAds() {
        initNativeCollab()
    }

    override fun onRestart() {
        super.onRestart()
         initNativeCollab()

    }

    override fun onStart() {
        super.onStart()
        android.util.Log.d("CustomizeLifecycle", "onStart() called")
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("CustomizeLifecycle", "onResume() called")
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d("CustomizeLifecycle", "onPause() called")
    }
//
//    override fun onWindowFocusChanged(hasFocus: Boolean) {
//        super.onWindowFocusChanged(hasFocus)
//        if (hasFocus) {
//            applyUiCustomize()
//            hideNavigation(true)
//
//            window.decorView.removeCallbacks(reHideRunnable)
//            window.decorView.postDelayed(reHideRunnable, 1500)
//        } else {
//            window.decorView.removeCallbacks(reHideRunnable)
//        }
//    }

//    private val reHideRunnable = Runnable {
//        applyUiCustomize()
//        hideNavigation(true)
//    }
//    @Suppress("DEPRECATION")
//    private fun applyUiCustomize() {
//        // Cho phép app tự vẽ màu system bar
//        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
//
//        // Transparent status bar
//        window.statusBarColor = android.graphics.Color.TRANSPARENT
//        window.navigationBarColor = android.graphics.Color.TRANSPARENT
//
//        // Flags
//        window.decorView.systemUiVisibility =
//            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
//                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
//                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
//                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
//                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                     View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//        // nếu muốn icon status bar đen thì thêm:
//        // or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//    }
}