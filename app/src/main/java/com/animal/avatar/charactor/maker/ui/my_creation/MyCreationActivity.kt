package com.animal.avatar.charactor.maker.ui.my_creation

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.room.util.findColumnIndexBySuffix
import com.lvt.ads.util.Admob
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.core.base.BaseActivity
import com.animal.avatar.charactor.maker.core.extensions.checkPermissions
import com.animal.avatar.charactor.maker.core.extensions.goToSettings
import com.animal.avatar.charactor.maker.core.extensions.gone
import com.animal.avatar.charactor.maker.core.extensions.hideNavigation
import com.animal.avatar.charactor.maker.core.extensions.invisible
import com.animal.avatar.charactor.maker.core.extensions.loadNativeCollabAds
import com.animal.avatar.charactor.maker.core.extensions.requestPermission
import com.animal.avatar.charactor.maker.core.extensions.select
import com.animal.avatar.charactor.maker.core.extensions.setImageActionBar
import com.animal.avatar.charactor.maker.core.extensions.setTextActionBar
import com.animal.avatar.charactor.maker.core.extensions.tap

import com.animal.avatar.charactor.maker.core.extensions.startIntentWithClearTop
import com.animal.avatar.charactor.maker.core.extensions.visible
import com.animal.avatar.charactor.maker.core.helper.LanguageHelper
import com.animal.avatar.charactor.maker.core.helper.UnitHelper
import com.animal.avatar.charactor.maker.core.utils.key.IntentKey
import com.animal.avatar.charactor.maker.core.utils.key.RequestKey
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.core.utils.share.whatsapp.WhatsappSharingActivity
import com.animal.avatar.charactor.maker.core.utils.state.HandleState
import com.animal.avatar.charactor.maker.databinding.ActivityAlbumBinding
import com.animal.avatar.charactor.maker.dialog.YesNoDialog
import com.animal.avatar.charactor.maker.ui.home.HomeActivity
import com.animal.avatar.charactor.maker.ui.view.ViewActivity
import com.animal.avatar.charactor.maker.databinding.PopupMyAlbumBinding
import com.animal.avatar.charactor.maker.dialog.CreateNameDialog
import com.animal.avatar.charactor.maker.ui.my_creation.adapter.MyAvatarAdapter
import com.animal.avatar.charactor.maker.ui.my_creation.adapter.TypeAdapter
import com.animal.avatar.charactor.maker.ui.my_creation.fragment.MyAvatarFragment
import com.animal.avatar.charactor.maker.ui.my_creation.fragment.MyDesignFragment
import com.animal.avatar.charactor.maker.ui.my_creation.view_model.MyAvatarViewModel
import com.animal.avatar.charactor.maker.ui.my_creation.view_model.MyCreationViewModel
import com.animal.avatar.charactor.maker.ui.permission.PermissionViewModel
import kotlinx.coroutines.launch
import kotlin.text.replace

class MyCreationActivity : WhatsappSharingActivity<ActivityAlbumBinding>() {
    companion object {
        private var instanceRef: java.lang.ref.WeakReference<MyCreationActivity>? = null

        fun getInstance(): MyCreationActivity? = instanceRef?.get()
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef = null
    }

    private val viewModel: MyCreationViewModel by viewModels()
    private val permissionViewModel: PermissionViewModel by viewModels()

    private var myAvatarFragment: MyAvatarFragment? = null
    private var myDesignFragment: MyDesignFragment? = null
    private var isInSelectionMode = false
    private var isAllSelected = false

    override fun setViewBinding(): ActivityAlbumBinding {
        return ActivityAlbumBinding.inflate(LayoutInflater.from(this))
    }

    override fun initView() {
        // Store instance reference for ViewActivity to access
        instanceRef = java.lang.ref.WeakReference(this)

        viewModel.setTypeStatus(ValueKey.AVATAR_TYPE)
        viewModel.setStatusFrom(intent.getBooleanExtra(IntentKey.FROM_SAVE, false))

        // Hide action bar buttons by default (only show in selection mode)
        binding.actionBar.apply {
            btnActionBarRight.gone()
            btnActionBarNextRight.gone()
        }
        binding.lnlBottom.isSelected = true
    }

    override fun dataObservable() {
        binding.apply {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    launch {
                        viewModel.typeStatus.collect { type ->
                            if (type != -1) {
                                if (type == ValueKey.AVATAR_TYPE) {
                                    // MyAvatar selected
                                    binding.cvType.setBackgroundResource(R.drawable.bg_cvtype_avatar) // ảnh 1

                                    setupSelectedTab(
                                        btnMyPixel,
                                        tvSpace,
                                        imvFocusMyAvatar,
                                        subTabMyAvatar,
                                        isLeftTab = true
                                    )
                                    setupUnselectedTab(
                                        btnMyDesign,
                                        tvMyDesign,
                                        imvFocusMyDesign,
                                        subTabMyDesign,
                                        isLeftTab = false
                                    )
                                    showFragment(ValueKey.AVATAR_TYPE)
                                } else {
                                    // MyDesign selected
                                    binding.cvType.setBackgroundResource(R.drawable.bg_cvtype_design) // ảnh 1

                                    setupSelectedTab(
                                        btnMyDesign,
                                        tvMyDesign,
                                        imvFocusMyDesign,
                                        subTabMyDesign,
                                        isLeftTab = false
                                    )
                                    setupUnselectedTab(
                                        btnMyPixel,
                                        tvSpace,
                                        imvFocusMyAvatar,
                                        subTabMyAvatar,
                                        isLeftTab = true
                                    )
                                    showFragment(ValueKey.MY_DESIGN_TYPE)
                                }
                                // Update bottom buttons visibility when tab changes
                                updateBottomButtonsVisibility()
                            }

                        }
                    }
                    launch {
                        viewModel.downloadState.collect { state ->
                            when (state) {
                                HandleState.LOADING -> {
                                    showLoading()
                                }

                                HandleState.SUCCESS -> {
                                    dismissLoading()
                                    hideNavigation()
                                    showToast(R.string.download_success)
                                    // Auto-click back button to exit selection mode
                                    binding.actionBar.btnActionBarLeft.performClick()
                                }

                                else -> {
                                    dismissLoading()
                                    hideNavigation()
                                    showToast(R.string.download_failed_please_try_again_later)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun viewListener() {
        binding.apply {
            actionBar.apply {
                btnActionBarLeft.tap {
                    if (isInSelectionMode) {
                        // Exit selection mode
                        val avatarFragment =
                            supportFragmentManager.findFragmentByTag("MyAvatarFragment")
                        val designFragment =
                            supportFragmentManager.findFragmentByTag("MyDesignFragment")

                        when {
                            avatarFragment is MyAvatarFragment && avatarFragment.isVisible -> {
                                avatarFragment.resetSelectionMode()
                            }

                            designFragment is MyDesignFragment && designFragment.isVisible -> {
                                designFragment.resetSelectionMode()
                            }
                        }
                    } else {
                        startIntentWithClearTop(HomeActivity::class.java)
                    }
                }

                // Select All button
                btnActionBarRight.tap {
                    handleSelectAllFromCurrentFragment()
                }

                // Delete All button
                btnActionBarNextRight.tap {
                    handleDeleteSelectedFromCurrentFragment()
                }
            }

            btnMyPixel.tap { viewModel.setTypeStatus(ValueKey.AVATAR_TYPE) }
            btnMyDesign.tap { viewModel.setTypeStatus(ValueKey.MY_DESIGN_TYPE) }

            // WhatsApp, Telegram, and Download buttons in lnlBottom
            val layoutBottom = lnlBottom.getChildAt(0)
            layoutBottom.findViewById<View>(R.id.btnWhatsapp)?.tap(2500) {
                val selectedPaths = getSelectedPathsFromCurrentFragment()
                handleAddToWhatsApp(selectedPaths)
            }
            layoutBottom.findViewById<View>(R.id.btnTelegram)?.tap(2500) {
                val selectedPaths = getSelectedPathsFromCurrentFragment()
                handleAddToTelegram(selectedPaths)
            }
            layoutBottom.findViewById<View>(R.id.btnDownload)?.tap(2500) {
                handleDownloadFromCurrentFragment()
            }

            // Delete button in deleteSection         }
        }
    }

    private fun handleShareFromCurrentFragment() {
        val selectedPaths = getSelectedPathsFromCurrentFragment()
        handleShare(selectedPaths)
    }

    private fun handleDownloadFromCurrentFragment() {
        val selectedPaths = getSelectedPathsFromCurrentFragment()
        handleDownload(selectedPaths)
    }

    private fun handleSelectAllFromCurrentFragment() {
        val avatarFragment = supportFragmentManager.findFragmentByTag("MyAvatarFragment")
        val designFragment = supportFragmentManager.findFragmentByTag("MyDesignFragment")

        when {
            avatarFragment is MyAvatarFragment && avatarFragment.isVisible -> {
                if (isAllSelected) {
                    // Deselect all
                    avatarFragment.deselectAllItems()
                    isAllSelected = false
                    binding.actionBar.btnActionBarRight.setImageResource(R.drawable.ic_not_select_all)
                } else {
                    // Select all
                    avatarFragment.selectAllItems()
                    isAllSelected = true
                    binding.actionBar.btnActionBarRight.setImageResource(R.drawable.ic_select_all)
                }
            }

            designFragment is MyDesignFragment && designFragment.isVisible -> {
                if (isAllSelected) {
                    // Deselect all
                    designFragment.deselectAllItems()
                    isAllSelected = false
                    binding.actionBar.btnActionBarRight.setImageResource(R.drawable.ic_not_select_all)
                } else {
                    // Select all
                    designFragment.selectAllItems()
                    isAllSelected = true
                    binding.actionBar.btnActionBarRight.setImageResource(R.drawable.ic_select_all)
                }
            }
        }
    }

    private fun handleDeleteSelectedFromCurrentFragment() {
        val avatarFragment = supportFragmentManager.findFragmentByTag("MyAvatarFragment")
        val designFragment = supportFragmentManager.findFragmentByTag("MyDesignFragment")

        when {
            avatarFragment is MyAvatarFragment && avatarFragment.isVisible -> {
                avatarFragment.deleteSelectedItems()
            }

            designFragment is MyDesignFragment && designFragment.isVisible -> {
                designFragment.deleteSelectedItems()
            }
        }
    }

    private fun getSelectedPathsFromCurrentFragment(): ArrayList<String> {
        val avatarFragment = supportFragmentManager.findFragmentByTag("MyAvatarFragment")
        val designFragment = supportFragmentManager.findFragmentByTag("MyDesignFragment")

        return when {
            avatarFragment is MyAvatarFragment && avatarFragment.isVisible -> avatarFragment.getSelectedPaths()
            designFragment is MyDesignFragment && designFragment.isVisible -> designFragment.getSelectedPaths()
            else -> arrayListOf()
        }
    }

    override fun initActionBar() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.ic_back)
            setTextActionBar(tvCenter, getString(R.string.my_creation))

            // Select All button (btnActionBarRight) - resize to 24dp for select all icons
            val size24dp = (24 * resources.displayMetrics.density).toInt()
            val params = btnActionBarRight.layoutParams
            params.width = size24dp
            params.height = size24dp
            btnActionBarRight.layoutParams = params

            btnActionBarRight.setImageResource(R.drawable.ic_not_select_all)
            btnActionBarRight.gone()

            // Delete All button - hidden initially, only shown in selection mode
            btnActionBarNextRight.setImageResource(R.drawable.ic_delete_all)
            btnActionBarNextRight.gone()
        }
    }

    override fun initText() {
        binding.apply {
            tvSpace.select()
            tvMyDesign.select()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RequestKey.STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                permissionViewModel.updateStorageGranted(sharePreference, true)
                showToast(R.string.granted_storage)
            } else {
                permissionViewModel.updateStorageGranted(sharePreference, false)
            }
        }
    }

    fun handleShare(list: ArrayList<String>) {
        if (list.isEmpty()) {
            showToast(R.string.please_select_an_image)
            return
        }
        viewModel.shareImages(this, list)
    }

    fun handleAddToTelegram(list: ArrayList<String>) {
        if (list.isEmpty()) {
            showToast(R.string.please_select_an_image)
            return
        }
        viewModel.addToTelegram(this, list)
        // Auto-click back button to exit selection mode
        binding.actionBar.btnActionBarLeft.performClick()
    }

    fun handleAddToWhatsApp(list: ArrayList<String>) {
        if (list.size < 3) {
            showToast(R.string.limit_3_items)
            return
        }
        if (list.size > 30) {
            showToast(R.string.limit_30_items)
            return
        }

        val dialog = CreateNameDialog(this)
        LanguageHelper.setLocale(this)
        dialog.show()

        fun dismissDialog() {
            dialog.dismiss()
            hideNavigation()
        }
        dialog.onNoClick = {
            dismissDialog()
        }
        dialog.onDismissClick = {
            dismissDialog()
        }

        dialog.onYesClick = { packageName ->
            dismissDialog()
            viewModel.addToWhatsapp(this, packageName, list) { stickerPack ->
                if (stickerPack != null) {
                    addToWhatsapp(stickerPack)
                    // Auto-click back button to exit selection mode
                    binding.actionBar.btnActionBarLeft.performClick()
                }
            }
        }
    }

    fun handleDownload(list: ArrayList<String>) {
        if (list.isEmpty()) {
            showToast(R.string.please_select_an_image)
            return
        }
        viewModel.downloadFiles(this, list)
    }

    private fun showFragment(type: Int) {
        android.util.Log.d("MyCreationActivity", "🔄 showFragment() called with type=$type")
        android.util.Log.d(
            "MyCreationActivity",
            "  type == AVATAR_TYPE: ${type == ValueKey.AVATAR_TYPE}"
        )
        android.util.Log.d(
            "MyCreationActivity",
            "  type == MY_DESIGN_TYPE: ${type == ValueKey.MY_DESIGN_TYPE}"
        )

        val transaction = supportFragmentManager.beginTransaction()

        // Initialize fragments if null
        if (myAvatarFragment == null) {
            android.util.Log.d("MyCreationActivity", "  Creating NEW MyAvatarFragment")
            myAvatarFragment = MyAvatarFragment()
            transaction.add(R.id.frmList, myAvatarFragment!!, "MyAvatarFragment")
        }
        if (myDesignFragment == null) {
            android.util.Log.d("MyCreationActivity", "  Creating NEW MyDesignFragment")
            myDesignFragment = MyDesignFragment()
            transaction.add(R.id.frmList, myDesignFragment!!, "MyDesignFragment")
        }

        // Show/Hide based on type
        if (type == ValueKey.AVATAR_TYPE) {
            android.util.Log.d(
                "MyCreationActivity",
                "  ➡️ SHOWING MyAvatarFragment, HIDING MyDesignFragment"
            )
            myAvatarFragment?.let { transaction.show(it) }
            myDesignFragment?.let { transaction.hide(it) }
        } else {
            android.util.Log.d(
                "MyCreationActivity",
                "  ➡️ HIDING MyAvatarFragment, SHOWING MyDesignFragment"
            )
            myAvatarFragment?.let { transaction.hide(it) }
            myDesignFragment?.let { transaction.show(it) }
        }

        transaction.commit()
    }

    @SuppressLint("MissingSuperCall", "GestureBackNavigation")
    override fun onBackPressed() {
        startIntentWithClearTop(HomeActivity::class.java)
    }

    fun initNativeCollab() {
        Admob.getInstance().loadNativeCollapNotBanner(this,getString(R.string.native_cl_my_creation),binding.flNativeCollab)
    }
    override fun initAds() {
        initNativeCollab()
        Admob.getInstance().loadNativeAd(
            this,
            getString(R.string.native_creation),
            binding.nativeAds,
            R.layout.ads_native_banner
        )
    }

    override fun onRestart() {
        super.onRestart()
        android.util.Log.w(
            "MyCreationActivity",
            "🔄 onRestart() called - Activity restarting after being stopped"
        )
        android.util.Log.w(
            "MyCreationActivity",
            "Current tab: ${if (viewModel.typeStatus.value == ValueKey.AVATAR_TYPE) "MyAvatar" else "MyDesign"}"
        )
        android.util.Log.w("MyCreationActivity", "Selection mode: $isInSelectionMode")

        // Check permission status
        val hasPermission =
            checkPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        android.util.Log.w("MyCreationActivity", "📱 Storage permission: $hasPermission")

        // Exit selection mode when returning from another activity
        if (isInSelectionMode) {
            val avatarFragment = supportFragmentManager.findFragmentByTag("MyAvatarFragment")
            val designFragment = supportFragmentManager.findFragmentByTag("MyDesignFragment")

            when {
                avatarFragment is MyAvatarFragment && avatarFragment.isVisible -> {
                    android.util.Log.d(
                        "MyCreationActivity",
                        "Resetting MyAvatarFragment selection mode"
                    )
                    avatarFragment.resetSelectionMode()
                }

                designFragment is MyDesignFragment && designFragment.isVisible -> {
                    android.util.Log.d(
                        "MyCreationActivity",
                        "Resetting MyDesignFragment selection mode"
                    )
                    designFragment.resetSelectionMode()
                }
            }
            exitSelectionMode()
            initNativeCollab()
        }

        // initNativeCollab()
        android.util.Log.w("MyCreationActivity", "🔄 onRestart() END")
    }

    override fun onStart() {
        super.onStart()
        android.util.Log.w("MyCreationActivity", "🔵 onStart() called - Activity becoming visible")
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.w("MyCreationActivity", "🟢 onResume() called - Activity in foreground")
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.w("MyCreationActivity", "🟡 onPause() called - Activity losing focus")
    }

    override fun onStop() {
        super.onStop()
        android.util.Log.w("MyCreationActivity", "🔴 onStop() called - Activity no longer visible")
    }

    fun enterSelectionMode() {
        // ✅ Check if activity is destroyed before accessing binding
        if (isDestroyed || isFinishing) {
            android.util.Log.w("MyCreationActivity", "enterSelectionMode called but activity is destroyed/finishing - skipping")
            return
        }

        isInSelectionMode = true
        isAllSelected = false
        binding.actionBar.apply {
            // Show select all and delete all buttons
            btnActionBarRight.setImageResource(R.drawable.ic_not_select_all)
            btnActionBarRight.visible()
            btnActionBarNextRight.invisible()
        }
        updateBottomButtonsVisibility()
        android.util.Log.d("MyCreationActivity", "enterSelectionMode called - showing buttons")
    }

    fun exitSelectionMode() {
        // ✅ Check if activity is destroyed before accessing binding
        if (isDestroyed || isFinishing) {
            android.util.Log.w("MyCreationActivity", "exitSelectionMode called but activity is destroyed/finishing - skipping")
            return
        }

        isInSelectionMode = false
        isAllSelected = false
        binding.actionBar.apply {
            // Hide select all and delete all buttons
            btnActionBarRight.gone()
            btnActionBarNextRight.gone()
        }

        updateBottomButtonsVisibility()
        android.util.Log.d("MyCreationActivity", "exitSelectionMode called - hiding buttons")
    }

    private fun updateBottomButtonsVisibility() {
        // ✅ Check if activity is destroyed before accessing binding
        if (isDestroyed || isFinishing) {
            android.util.Log.w("MyCreationActivity", "updateBottomButtonsVisibility called but activity is destroyed/finishing")
            return
        }

        val layoutBottom = binding.lnlBottom.getChildAt(0)
        val btnWhatsapp = layoutBottom.findViewById<View>(R.id.btnWhatsapp)
        val btnTelegram = layoutBottom.findViewById<View>(R.id.btnTelegram)
        val btnDownload = layoutBottom.findViewById<View>(R.id.btnDownload)

        if (!isInSelectionMode) {
            // Not in selection mode: hide all bottom buttons
            btnWhatsapp?.gone()
            btnTelegram?.gone()
            btnDownload?.gone()
        } else if (viewModel.typeStatus.value == ValueKey.MY_DESIGN_TYPE) {
            // In My Design tab selection mode: show only Download button
            btnWhatsapp?.gone()
            btnTelegram?.gone()
            btnDownload?.visible()
        } else {
            // In My Pixel tab selection mode: show WhatsApp and Telegram
            btnWhatsapp?.visible()
            btnTelegram?.visible()
            btnDownload?.gone()
        }
    }

    private fun setupSelectedTab(
        tabView: View,
        textView: android.widget.TextView,
        focusImage: android.widget.ImageView,
        subTab: View,
        isLeftTab: Boolean
    ) {
        val params = tabView.layoutParams as android.widget.LinearLayout.LayoutParams
        params.weight = 1.0f
        params.topMargin = 0

        // nếu vẫn cần overlap thì giữ, còn không thì set về 0
        if (isLeftTab) params.marginEnd = 0 else params.marginStart = 0
        tabView.layoutParams = params

        // Text selected
        textView.textSize = 16f
        textView.paint.shader = null
        textView.post {
            val h = textView.lineHeight.toFloat()
//            val shader = LinearGradient(
//                0f, 0f, 0f, h,
//                Color.parseColor("#FFFFFF"),
//                Color.parseColor("#FFFFFF"),
//                Shader.TileMode.CLAMP
//            )
//            textView.paint.shader = shader
//            textView.invalidate()
            textView.setTextColor(Color.parseColor("#FFFFFF"))

        }

        // ❌ Không dùng background tab nữa
        focusImage.gone()
        subTab.gone()
    }


    private fun setupUnselectedTab(
        tabView: View,
        textView: android.widget.TextView,
        focusImage: android.widget.ImageView,
        subTab: View,
        isLeftTab: Boolean
    ) {
        val params = tabView.layoutParams as android.widget.LinearLayout.LayoutParams
        params.weight = 1f
        params.topMargin = 0

        // nếu vẫn cần overlap thì giữ, còn không thì set về 0
        if (isLeftTab) params.marginEnd = 0 else params.marginStart = 0
        tabView.layoutParams = params

        // Text unselected
        textView.textSize = 16f
        textView.paint.shader = null
        textView.post {
            val h = textView.lineHeight.toFloat()
//            val shader = LinearGradient(
//                0f, 0f, 0f, h,
//                Color.parseColor("#01579B"),
//                Color.parseColor("#01579B"),
//                Shader.TileMode.CLAMP
//            )
//            textView.paint.shader = shader
//            textView.invalidate()
            textView.setTextColor(Color.parseColor("#FF7396"))
        }

        // ❌ Không dùng background tab nữa
        focusImage.gone()
        subTab.gone()
    }

    // Public method to update select all icon based on selection state
    fun updateSelectAllIcon(allSelected: Boolean) {
        // ✅ Check if activity is destroyed before accessing binding
        if (isDestroyed || isFinishing) {
            android.util.Log.w("MyCreationActivity", "updateSelectAllIcon called but activity is destroyed/finishing")
            return
        }

        isAllSelected = allSelected
        if (allSelected) {
            binding.actionBar.btnActionBarRight.setImageResource(R.drawable.ic_select_all)
        } else {
            binding.actionBar.btnActionBarRight.setImageResource(R.drawable.ic_not_select_all)
        }
    }
}