package com.animal.avatar.charactor.maker.ui.random_character

import android.app.ActivityOptions
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.ui.customize.CustomizeCharacterActivity
import com.animal.avatar.charactor.maker.ui.customize.CustomizeCharacterViewModel
import com.animal.avatar.charactor.maker.ui.home.DataViewModel
import com.animal.avatar.charactor.maker.core.base.BaseActivity
import com.animal.avatar.charactor.maker.core.extensions.dLog
import com.animal.avatar.charactor.maker.core.extensions.eLog
import com.animal.avatar.charactor.maker.core.extensions.handleBackLeftToRight
import com.animal.avatar.charactor.maker.core.extensions.hideNavigation
import com.animal.avatar.charactor.maker.core.extensions.loadNativeCollabAds
import com.animal.avatar.charactor.maker.core.extensions.setImageActionBar
import com.animal.avatar.charactor.maker.core.extensions.setTextActionBar
import com.animal.avatar.charactor.maker.core.extensions.showInterAll
import com.animal.avatar.charactor.maker.core.extensions.tap
import com.animal.avatar.charactor.maker.core.extensions.startIntentRightToLeft
import com.animal.avatar.charactor.maker.core.extensions.visible
import com.animal.avatar.charactor.maker.core.helper.InternetHelper
import com.animal.avatar.charactor.maker.core.helper.MediaHelper
import com.animal.avatar.charactor.maker.core.utils.key.IntentKey
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.data.model.custom.SuggestionModel
import com.animal.avatar.charactor.maker.databinding.ActivityRandomCharacterBinding
import com.animal.avatar.charactor.maker.dialog.YesNoDialog
import com.lvt.ads.util.Admob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.get
import kotlin.getValue
import kotlin.text.compareTo

class RandomCharacterActivity : BaseActivity<ActivityRandomCharacterBinding>() {
    private val viewModel: RandomCharacterViewModel by viewModels()
    private val dataViewModel: DataViewModel by viewModels()
    private val customizeCharacterViewModel: CustomizeCharacterViewModel by viewModels()
    private val randomCharacterAdapter by lazy { RandomCharacterAdapter(this) }

    override fun setViewBinding(): ActivityRandomCharacterBinding {
        return ActivityRandomCharacterBinding.inflate(LayoutInflater.from(this))
    }

    override fun initView() {
        dataViewModel.ensureData(this)
    }

    override fun dataObservable() {
        lifecycleScope.launch {
            lifecycleScope.launch {
                dataViewModel.allData.collect { data ->
                    if (data.isNotEmpty()) {
                        initData()
                    }
                }
            }
        }
    }

    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarLeft.tap { showInterAll{handleBackLeftToRight()} }

        }

        randomCharacterAdapter.onItemClick = { model -> handleItemClick(model)}

    }

    override fun initActionBar() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.ic_back)
            setTextActionBar(tvCenter, getString(R.string.quick_maker_in))
            tvCenter.isSelected =true
        }
    }

    private fun initData() {
        val handleExceptionCoroutine = CoroutineExceptionHandler { _, throwable ->
            eLog("initData: ${throwable.message}")
            CoroutineScope(Dispatchers.Main).launch {
                dismissLoading()
                val dialogExit = YesNoDialog(this@RandomCharacterActivity, R.string.error, R.string.an_error_occurred)
                dialogExit.show()
                dialogExit.onNoClick = {
                    dialogExit.dismiss()
                    finish()
                }
                dialogExit.onYesClick = {
                    dialogExit.dismiss()
                    hideNavigation()
                    startIntentRightToLeft(
                        RandomCharacterActivity::class.java, customizeCharacterViewModel.positionSelected
                    )
                    finish()
                }
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO + handleExceptionCoroutine).launch {
            showLoading()
            // Get data from list
            val deferred1 = async {
                val timeStart1 = System.currentTimeMillis()
                val hasInternet = InternetHelper.isInternetAvailable(this@RandomCharacterActivity)

                // Filter data: if no internet, show only local data (isFromAPI = false)
                val filteredData = if (hasInternet) {
                    dataViewModel.allData.value
                } else {
                    dataViewModel.allData.value.filter { !it.isFromAPI }
                }

                dLog("==========================================================")
                dLog("RandomCharacter: Starting to process ${filteredData.size} characters")
                dLog("Total data available: ${dataViewModel.allData.value.size}")
                dLog("Has Internet: $hasInternet")
                dLog("Filtered to local only: ${!hasInternet}")
                dLog("==========================================================")

                for (i in 0 until filteredData.size) {
                    try {
                        dLog("---------- Processing Character $i ----------")
                        val currentData = filteredData[i]
                        customizeCharacterViewModel.positionSelected = dataViewModel.allData.value.indexOf(currentData)

                        // ✅ Check if character was found in allData
                        if (customizeCharacterViewModel.positionSelected < 0) {
                            eLog("❌ Character not found in allData: ${currentData.dataName}, skipping")
                            continue
                        }

                        dLog("Character name: ${currentData.dataName}")
                        dLog("Avatar path: ${currentData.avatar}")
                        dLog("Layer count: ${currentData.layerList.size}")
                        dLog("Is from API: ${currentData.isFromAPI}")

                        customizeCharacterViewModel.setDataCustomize(currentData)
                        customizeCharacterViewModel.updateAvatarPath(currentData.avatar)

                        customizeCharacterViewModel.resetDataList()
                        customizeCharacterViewModel.addValueToItemNavList()
                        customizeCharacterViewModel.setItemColorDefault()
                        customizeCharacterViewModel.setBottomNavigationListDefault()

                        for (j in 0 until ValueKey.RANDOM_QUANTITY) {
                            customizeCharacterViewModel.setClickRandomFullLayer()
                            val suggestion = customizeCharacterViewModel.getSuggestionList()
                            dLog("Generated random $j for character $i - Avatar: ${suggestion.avatarPath}")
                            viewModel.updateRandomList(suggestion)
                        }
                        dLog("✓ Character $i completed successfully")
                    } catch (e: Exception) {
                        eLog("✗ ERROR processing character $i: ${e.message}")
                        e.printStackTrace()
                    }
                }
                viewModel.upsideDownList()

                dLog("==========================================================")
                dLog("RandomCharacter: Finished processing")
                dLog("Total time: ${System.currentTimeMillis() - timeStart1}ms")
                dLog("Final random list size: ${viewModel.randomList.size}")
                dLog("==========================================================")
                return@async true
            }

            withContext(Dispatchers.Main) {
                if (deferred1.await()) {
                    dismissLoading()
                    initRcv()
                }
            }
        }
    }

    private fun initRcv() {
        binding.rcvRandomCharacter.apply {
            adapter = randomCharacterAdapter
            itemAnimator = null

            // ✅ PERFORMANCE OPTIMIZATIONS
            // Cache more ViewHolders to avoid recreating them
            setItemViewCacheSize(20)

            // Use a shared RecycledViewPool for better performance
            setRecycledViewPool(androidx.recyclerview.widget.RecyclerView.RecycledViewPool().apply {
                setMaxRecycledViews(0, 30)
            })

            // Enable drawing cache (deprecated but can help on older devices)
            isDrawingCacheEnabled = true
            setHasFixedSize(true) // All items have the same size

        }
        dLog("==========================================================")
        dLog("initRcv: Submitting ${viewModel.randomList.size} items to adapter")
        viewModel.randomList.forEachIndexed { index, item ->
            dLog("Item $index: Avatar=${item.avatarPath}, Layers=${item.pathSelectedList.size}")
        }
        dLog("==========================================================")
        randomCharacterAdapter.submitList(viewModel.randomList)
    }

    private fun handleItemClick(model: SuggestionModel) {
        customizeCharacterViewModel.positionSelected = dataViewModel.allData.value.indexOfFirst { it.avatar == model.avatarPath }

        // ✅ Check if character was found
        if (customizeCharacterViewModel.positionSelected < 0) {
            eLog("❌ Character not found for avatar: ${model.avatarPath}")
            showToast(R.string.an_error_occurred)
            return
        }

        // ✅ FIX: Use isFromAPI flag from character data instead of position
        val selectedCharacter = dataViewModel.allData.value.getOrNull(customizeCharacterViewModel.positionSelected)
        viewModel.setIsDataAPI(selectedCharacter?.isFromAPI ?: false)
        viewModel.checkDataInternet(this@RandomCharacterActivity) {
            lifecycleScope.launch {
                showLoading()
                withContext(Dispatchers.IO) {
                    MediaHelper.writeModelToFile(this@RandomCharacterActivity, ValueKey.SUGGESTION_FILE_INTERNAL, model)
                }
                val intent = Intent(this@RandomCharacterActivity, CustomizeCharacterActivity::class.java)
                intent.putExtra(IntentKey.INTENT_KEY, customizeCharacterViewModel.positionSelected)
                intent.putExtra(IntentKey.STATUS_FROM_KEY, ValueKey.SUGGESTION)
                val option = ActivityOptions.makeCustomAnimation(
                    this@RandomCharacterActivity,
                    R.anim.slide_out_left,
                    R.anim.slide_in_right
                )
                dismissLoading()
                showInterAll { startActivity(intent, option.toBundle()) }
            }
        }
    }

    fun initNativeCollab() {
        Admob.getInstance().loadNativeCollapNotBanner(this,getString(R.string.native_cl_suggest),binding.flNativeCollab)
    }

    override fun initAds() {
        initNativeCollab()
        Admob.getInstance().loadNativeAd(
            this,
            getString(R.string.native_suggest),
            binding.nativeAds,
            R.layout.ads_native_banner
        )
    }

    override fun onRestart() {
        super.onRestart()
       initNativeCollab()
    }

//    override fun onWindowFocusChanged(hasFocus: Boolean) {
//        super.onWindowFocusChanged(hasFocus)
//        if (hasFocus) {
//            applyUiCustomize()
//            hideNavigation(true)
//
//            window.decorView.removeCallbacks(reHideRunnable)
//            window.decorView.postDelayed(reHideRunnable, 2000)
//        } else {
//            window.decorView.removeCallbacks(reHideRunnable)
//        }
//    }
//
//    private val reHideRunnable = Runnable {
//        applyUiCustomize()
//        hideNavigation(true)
//    }
//
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
//        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//        // nếu muốn icon status bar đen thì thêm:
//        // or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ Cancel all pending image processing jobs to prevent memory leaks
        randomCharacterAdapter.cancelAllJobs()
    }
}