package com.animal.avatar.charactor.maker.ui.choose_character

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.lvt.ads.event.AdmobEvent
import com.lvt.ads.util.Admob
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.ui.customize.CustomizeCharacterActivity
import com.animal.avatar.charactor.maker.ui.home.DataViewModel
import com.animal.avatar.charactor.maker.ui.random_character.RandomCharacterActivity
import com.animal.avatar.charactor.maker.core.base.BaseActivity
import com.animal.avatar.charactor.maker.core.extensions.handleBackLeftToRight
import com.animal.avatar.charactor.maker.core.extensions.hideNavigation
import com.animal.avatar.charactor.maker.core.extensions.loadNativeCollabAds
import com.animal.avatar.charactor.maker.core.extensions.select
import com.animal.avatar.charactor.maker.core.extensions.setImageActionBar
import com.animal.avatar.charactor.maker.core.extensions.setTextActionBar
import com.animal.avatar.charactor.maker.core.extensions.showInterAll
import com.animal.avatar.charactor.maker.core.extensions.tap
import com.animal.avatar.charactor.maker.core.extensions.startIntentRightToLeft
import com.animal.avatar.charactor.maker.core.extensions.visible
import com.animal.avatar.charactor.maker.core.helper.InternetHelper
import com.animal.avatar.charactor.maker.core.utils.key.IntentKey
import com.animal.avatar.charactor.maker.core.utils.state.HandleState
import com.animal.avatar.charactor.maker.databinding.ActivityChooseCharacterBinding
import kotlinx.coroutines.launch

class ChooseCharacterActivity : BaseActivity<ActivityChooseCharacterBinding>() {
    private val viewModel: ChooseCharacterViewModel by viewModels()
    private val dataViewModel: DataViewModel by viewModels()
    private val chooseCharacterAdapter by lazy { ChooseCharacterAdapter() }
    private var hasCheckedInternet = false
    private var currentDataType = IntentKey.DATA_TYPE_DEFAULT

    override fun setViewBinding(): ActivityChooseCharacterBinding {
        return ActivityChooseCharacterBinding.inflate(LayoutInflater.from(this))
    }

    override fun initView() {
        lifecycleScope.launch { showLoading() }
        initRcv()
        binding.actionBar.tvCenter.select()
        currentDataType = intent.getIntExtra(IntentKey.DATA_TYPE_KEY, IntentKey.DATA_TYPE_DEFAULT)
        if (currentDataType != IntentKey.DATA_TYPE_DEFAULT) {
            dataViewModel.loadDataByType(this, currentDataType)
        } else {
            dataViewModel.ensureData(this)
        }
    }

    override fun dataObservable() {
        lifecycleScope.launch {
            val flow = if (currentDataType != IntentKey.DATA_TYPE_DEFAULT)
                dataViewModel.filteredData
            else
                dataViewModel.allData

            flow.collect { data ->
                if (data.isNotEmpty()) {
                    chooseCharacterAdapter.submitList(data)
                    dismissLoading()
                    checkInternetForAPICharacters(data)
                }
            }
        }
    }

    private fun checkInternetForAPICharacters(data: ArrayList<com.animal.avatar.charactor.maker.data.model.custom.CustomizeModel>) {
        // Only check once per activity lifecycle
        if (hasCheckedInternet) return
        hasCheckedInternet = true

        android.util.Log.d("ChooseCharacter", "========================================")
        android.util.Log.d("ChooseCharacter", "checkInternetForAPICharacters called")
        android.util.Log.d("ChooseCharacter", "Total characters in data: ${data.size}")

        // Check if API characters are already loaded
        val hasAPICharacters = data.any { it.isFromAPI }
        val apiCount = data.count { it.isFromAPI }
        val localCount = data.count { !it.isFromAPI }

        android.util.Log.d("ChooseCharacter", "API characters: $apiCount")
        android.util.Log.d("ChooseCharacter", "Local characters: $localCount")
        android.util.Log.d("ChooseCharacter", "hasAPICharacters: $hasAPICharacters")

        // Only show notification if API characters are NOT loaded yet
        if (!hasAPICharacters) {
            android.util.Log.d("ChooseCharacter", "No API characters - checking internet...")
            InternetHelper.checkInternet(this) { state ->
                android.util.Log.d("ChooseCharacter", "Internet check result: $state")
                if (state != HandleState.SUCCESS) {
                    android.util.Log.d("ChooseCharacter", "❌ No internet - SHOWING DIALOG")
                    // No internet and no API characters loaded - notify user
                    val dialog = com.animal.avatar.charactor.maker.dialog.YesNoDialog(
                        this@ChooseCharacterActivity,
                        R.string.notification,
                        R.string.internet_required_for_more_characters,
                        isError = true  // Shows only OK button
                    )
                    dialog.show()
                    dialog.onYesClick = {
                        dialog.dismiss()
                        hideNavigation()
                    }
                } else {
                    android.util.Log.d("ChooseCharacter", "✓ Has internet - no dialog")
                }
            }
        } else {
            android.util.Log.d("ChooseCharacter", "✓ API characters already loaded - no dialog")
        }
        android.util.Log.d("ChooseCharacter", "========================================")
    }

    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarLeft.tap { showInterAll { handleBackLeftToRight() } }
        }
        chooseCharacterAdapter.onItemClick = { position ->
            val dataSource = if (currentDataType != IntentKey.DATA_TYPE_DEFAULT)
                dataViewModel.filteredData.value
            else
                dataViewModel.allData.value
            val selectedCharacter = dataSource.getOrNull(position)
            val needsInternet = selectedCharacter?.isFromAPI ?: false

            // Log AdMob event with detailed information
            val bundle = Bundle()
            bundle.putString("character_name", selectedCharacter?.dataName ?: "unknown")
            bundle.putString("avatar_path", selectedCharacter?.avatar ?: "unknown")
            bundle.putInt("position", position)
            bundle.putBoolean("is_from_api", needsInternet)
            AdmobEvent.logEvent(this@ChooseCharacterActivity, "click_character_item", bundle)

            android.util.Log.d("ChooseCharacter", "========================================")
            android.util.Log.d("ChooseCharacter", "Item clicked: position $position")

            android.util.Log.d("ChooseCharacter", "Character isFromAPI: $needsInternet")
            android.util.Log.d("ChooseCharacter", "Character name: ${selectedCharacter?.dataName}")

            if (needsInternet) {
                android.util.Log.d("ChooseCharacter", "API character - checking internet...")
                InternetHelper.checkInternet(this) { state ->
                    if (state == HandleState.SUCCESS) {
                        showInterAll { navigateToCustomize(position) }
                    } else {
                        // Show No Internet dialog
                        val dialog = com.animal.avatar.charactor.maker.dialog.YesNoDialog(
                            this@ChooseCharacterActivity,
                            R.string.error,
                            R.string.please_check_your_internet,
                            isError = true
                        )
                        dialog.show()
                        dialog.onYesClick = {
                            dialog.dismiss()
                            hideNavigation()
                        }
                    }
                }
            } else {
                android.util.Log.d("ChooseCharacter", "Local character - navigating directly")
                android.util.Log.d("ChooseCharacter", "========================================")
                showInterAll { navigateToCustomize(position) }
            }
        }
    }

    override fun initActionBar() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.ic_back)
            val title = when (currentDataType) {
                IntentKey.DATA_TYPE_CAT -> getString(R.string.cat_maker)
                IntentKey.DATA_TYPE_DRAGON -> getString(R.string.dragon_maker)
                IntentKey.DATA_TYPE_DOG -> getString(R.string.dog_maker)
                IntentKey.DATA_TYPE_PONY -> getString(R.string.pony_maker)
                IntentKey.DATA_TYPE_ANIMAL -> getString(R.string.animal_maker)
                else -> getString(R.string.category)
            }
            setTextActionBar(tvCenter, title)
        }
    }

    private fun navigateToCustomize(position: Int) {
        val intent = android.content.Intent(this, CustomizeCharacterActivity::class.java)
        intent.putExtra(IntentKey.INTENT_KEY, position)
        intent.putExtra(IntentKey.DATA_TYPE_KEY, currentDataType)
        val option = android.app.ActivityOptions.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
        startActivity(intent, option.toBundle())
    }

    private fun initRcv() {
        binding.rcvCharacter.apply {
            adapter = chooseCharacterAdapter
            itemAnimator = null
        }
    }

    fun initNativeCollab() {
        Admob.getInstance().loadNativeCollapNotBanner(this,getString(R.string.native_cl_category),binding.flNativeCollab)
    }

    override fun initAds() {
        initNativeCollab()
        Admob.getInstance().loadNativeAd(
            this,
            getString(R.string.native_category),
            binding.nativeAds,
            R.layout.ads_native_banner
        )
    }

    override fun onRestart() {
        super.onRestart()
        initNativeCollab()
    }

}