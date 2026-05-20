package com.animal.avatar.charactor.maker.ui.add_character

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.core.base.BaseActivity
import com.animal.avatar.charactor.maker.core.extensions.checkPermissions
import com.animal.avatar.charactor.maker.core.extensions.goToSettings
import com.animal.avatar.charactor.maker.core.extensions.gone
import com.animal.avatar.charactor.maker.core.extensions.hideNavigation
import com.animal.avatar.charactor.maker.core.extensions.hideSoftKeyboard
import com.animal.avatar.charactor.maker.core.extensions.invisible
import com.animal.avatar.charactor.maker.core.extensions.loadImage
import com.animal.avatar.charactor.maker.core.extensions.loadNativeCollabAds
import com.animal.avatar.charactor.maker.core.extensions.openImagePicker
import com.animal.avatar.charactor.maker.core.extensions.requestPermission
import com.animal.avatar.charactor.maker.core.extensions.select
import com.animal.avatar.charactor.maker.core.extensions.setFont
import com.animal.avatar.charactor.maker.core.extensions.setImageActionBar
import com.animal.avatar.charactor.maker.core.extensions.showInterAll
import com.animal.avatar.charactor.maker.core.extensions.tap
import com.animal.avatar.charactor.maker.core.extensions.visible
import com.animal.avatar.charactor.maker.core.helper.BitmapHelper
import com.animal.avatar.charactor.maker.core.helper.UnitHelper
import com.animal.avatar.charactor.maker.core.utils.DataLocal
import com.animal.avatar.charactor.maker.core.utils.key.IntentKey
import com.animal.avatar.charactor.maker.core.utils.key.RequestKey
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.core.utils.state.SaveState
import com.animal.avatar.charactor.maker.data.model.draw.Draw
import com.animal.avatar.charactor.maker.data.model.draw.DrawableDraw
import com.animal.avatar.charactor.maker.databinding.ActivityAddCharacterBinding
import com.animal.avatar.charactor.maker.core.helper.InternetHelper
import com.animal.avatar.charactor.maker.dialog.ChooseColorDialog
import com.animal.avatar.charactor.maker.dialog.DialogSpeech
import com.animal.avatar.charactor.maker.dialog.YesNoDialog
import com.animal.avatar.charactor.maker.listener.listenerdraw.OnDrawListener
import com.animal.avatar.charactor.maker.ui.add_character.adapter.BackgroundColorAdapter
import com.animal.avatar.charactor.maker.ui.add_character.adapter.BackgroundImageAdapter
import com.animal.avatar.charactor.maker.ui.add_character.adapter.StickerAdapter
import com.animal.avatar.charactor.maker.ui.add_character.adapter.TextColorAdapter
import com.animal.avatar.charactor.maker.ui.add_character.adapter.TextFontAdapter
import com.animal.avatar.charactor.maker.ui.permission.PermissionViewModel
import com.animal.avatar.charactor.maker.ui.view.ViewActivity
import com.lvt.ads.util.Admob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.getValue
import kotlin.toString

class AddCharacterActivity : BaseActivity<ActivityAddCharacterBinding>() {
    private val viewModel: AddCharacterViewModel by viewModels()
    private val permissionViewModel: PermissionViewModel by viewModels()
    private val backgroundImageAdapter by lazy { BackgroundImageAdapter() }
    private val backgroundColorAdapter by lazy { BackgroundColorAdapter() }
    private val stickerAdapter by lazy { StickerAdapter() }
    private val speechAdapter by lazy { StickerAdapter() }
    private val textFontAdapter by lazy { TextFontAdapter(this) }
    private val textColorAdapter by lazy { TextColorAdapter() }

    // Photo Picker - NO PERMISSION REQUIRED
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            handleSetBackgroundImage(uri.toString(), 0)
        }
    }
    override fun onResume() {
        super.onResume()
        hideNavigation(false)
    }

    private val buttonNavigationList by lazy {
        arrayListOf(
            binding.btnBackground,
            binding.btnSticker,
            binding.btnSpeech,
            binding.btnText,
        )
    }

    private val layoutNavigationList by lazy {
        arrayListOf(
            binding.lnlBackground,
            binding.lnlSticker,
            binding.lnlSpeech,
            binding.scvText,
        )
    }

    override fun setViewBinding(): ActivityAddCharacterBinding {
        return ActivityAddCharacterBinding.inflate(LayoutInflater.from(this))
    }

    override fun initView() {
        viewModel.layoutParams = binding.flFunction.layoutParams as ViewGroup.MarginLayoutParams
        viewModel.originalMarginBottom = viewModel.layoutParams.topMargin  // Capture initial topMargin
        initRcv()
        initDrawView()
        initData()
        setupKeyboardLogging()
        setupBackPressHandler()

        // 🔒 FIX CỨNG VIVO ANDROID 8 AUTO FOCUS
        binding.main.post {
            binding.main.requestFocus()     // Cướp focus khỏi EditText
            binding.edtText.clearFocus()    // Đảm bảo edtText không focus
            hideSoftKeyboard()              // Ép keyboard tắt
            viewModel.setIsFocusEditText(false) // Reset state
        }
    }

    private var lastImeVisible = false

    private fun setupKeyboardLogging() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            Log.d("EditTextFlow", "WindowInsets: imeVisible=$imeVisible, imeHeight=$imeHeight")
            Log.d("EditTextFlow", "scvText.scrollY=${binding.scvText.scrollY}, scvText.canScrollVertically(1)=${binding.scvText.canScrollVertically(1)}, scvText.canScrollVertically(-1)=${binding.scvText.canScrollVertically(-1)}")

            // Handle keyboard visibility changes
            if (lastImeVisible && !imeVisible) {
                // Keyboard was visible, now it's hidden
                Log.d("EditTextFlow", "Keyboard dismissed by system (via window insets)")
                if (viewModel.isFocusEditText.value && binding.edtText.hasFocus()) {
                    Log.d("EditTextFlow", "EditText still has focus - back button dismissed keyboard")
                    // Back button was pressed while EditText had focus
                    viewModel.setIsFocusEditText(false)
                }
            }
            lastImeVisible = imeVisible

            insets
        }
    }

    private fun setupBackPressHandler() {
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("EditTextFlow", "OnBackPressedCallback.handleOnBackPressed called")
                Log.d("EditTextFlow", "isFocusEditText.value=${viewModel.isFocusEditText.value}")
                Log.d("EditTextFlow", "edtText.hasFocus()=${binding.edtText.hasFocus()}")

                if (viewModel.isFocusEditText.value || binding.edtText.hasFocus()) {
                    Log.d("EditTextFlow", "Back pressed with EditText focused - hiding keyboard via callback")
                    viewModel.setIsFocusEditText(false)
                } else {
                    Log.d("EditTextFlow", "Back pressed without EditText focused - showing confirmExit")
                    confirmExit()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        Log.d("EditTextFlow", "OnBackPressedCallback registered, enabled=${callback.isEnabled}")
    }
//    private fun setupKeyboardDetection() {
//        binding.main.viewTreeObserver.addOnGlobalLayoutListener {
//            val rect = android.graphics.Rect()
//            binding.main.getWindowVisibleDisplayFrame(rect)
//
//            val screenHeight = binding.main.rootView.height
//            val keypadHeight = screenHeight - rect.bottom
//
//            // If keyboard height is more than 15% of screen, keyboard is showing
//            val isKeyboardNowShowing = keypadHeight > screenHeight * 0.15
//
//            if (isKeyboardNowShowing != isKeyboardShowing) {
//                isKeyboardShowing = isKeyboardNowShowing
//
//                if (isKeyboardShowing && binding.edtText.hasFocus()) {
//                    // Keyboard just appeared
//                    adjustLayoutForKeyboard(keypadHeight)
//                } else if (!isKeyboardShowing) {
//                    // Keyboard just disappeared
//                    resetLayout()
//                }
//            }
//        }
//    }
//    private fun adjustLayoutForKeyboard(keyboardHeight: Int) {
//        binding.scvText.postDelayed({
//            // Scroll the ScrollView to show EditText
//            val location = IntArray(2)
//            binding.edtText.getLocationOnScreen(location)
//            val edtY = location[1]
//            val edtBottom = edtY + binding.edtText.height
//
//            val screenHeight = resources.displayMetrics.heightPixels
//            val visibleHeight = screenHeight - keyboardHeight
//
//            if (edtBottom > visibleHeight) {
//                // Calculate how much to scroll
//                val scrollAmount = edtBottom - visibleHeight + UnitHelper.dpToPx(this, 50)
//                binding.scvText.smoothScrollBy(0, scrollAmount)
//            }
//        }, 100)
//    }
//
//    private fun resetLayout() {
//        binding.scvText.smoothScrollTo(0, 0)
//    }



    override fun dataObservable() {
        binding.apply {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
//                        typeNavigation
                        viewModel.typeNavigation.collect { type ->
                            if (type != -1) {
                                setupTypeNavigation(type)
                            }
                        }
                    }

                    launch {
//                        typeBackground
                        viewModel.typeBackground.collect { type ->
                            if (type != -1) {
                                setupTypeBackground(type)
                            }
                        }
                    }

                    launch {
//                        isFocusEditText
                        viewModel.isFocusEditText.collect { status ->
                            Log.d("EditTextFlow", "isFocusEditText.collect: status=$status")
                            if (status) {

                                // Clear FLAG_LAYOUT_NO_LIMITS to allow adjustResize to work
                                Log.d("EditTextFlow", "Keyboard showing - clearing FLAG_LAYOUT_NO_LIMITS")
                                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                                viewModel.layoutParams.topMargin = UnitHelper.dpToPx(this@AddCharacterActivity, -160)
                                flFunction.layoutParams = viewModel.layoutParams
                                Log.d("EditTextFlow", "Layout adjusted - topMargin: ${viewModel.layoutParams.topMargin}")
                            } else {
                                // Scroll back to top
                                // 🔒 CHỈ reset layout KHI IME ĐÃ THẬT SỰ TẮT
                                    scvText.smoothScrollTo(0, 0)
                                    viewModel.layoutParams.topMargin = viewModel.originalMarginBottom
                                    flFunction.layoutParams = viewModel.layoutParams

                                hideSoftKeyboard()

                                edtText.clearFocus()

                                hideSoftKeyboard()
                                hideNavigation(false)
                                delay(300)
                                hideSoftKeyboard()



                                // Keyboard vẫn mở → ÉP TẮT

                                Log.d("EditTextFlow", "Layout reset complete - topMargin: ${viewModel.layoutParams.topMargin}")
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
                btnActionBarLeft.tap { confirmExit() }
                btnActionBarCenter.tap { confirmReset() }
                btnActionBarRight.tap {

                    handleSave()

                }
            }
            btnBackgroundImage.tap { viewModel.setTypeBackground(ValueKey.IMAGE_BACKGROUND) }
            btnBackgroundColor.tap { viewModel.setTypeBackground(ValueKey.COLOR_BACKGROUND) }
            btnBackground.tap { viewModel.setTypeNavigation(ValueKey.BACKGROUND_NAVIGATION) }
            btnSticker.tap { viewModel.setTypeNavigation(ValueKey.STICKER_NAVIGATION) }
            btnSpeech.tap { viewModel.setTypeNavigation(ValueKey.SPEECH_NAVIGATION) }
            btnText.tap { viewModel.setTypeNavigation(ValueKey.TEXT_NAVIGATION) }

            edtText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    binding.tvGetText.text = p0.toString()
                }

                override fun afterTextChanged(p0: Editable?) {}
            })
            edtText.setOnEditorActionListener { textView, i, keyEvent ->
                if (i == EditorInfo.IME_ACTION_DONE) {
                    viewModel.setIsFocusEditText(false)
                    true
                } else {
                    false
                }
            }
            edtText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                Log.d("EditTextFlow", "edtText.onFocusChangeListener: hasFocus=$hasFocus")
                Log.d("EditTextFlow", "scvText.scrollY=${binding.scvText.scrollY}, scvText.height=${binding.scvText.height}")
                if (hasFocus) {
                    Log.d("EditTextFlow", "EditText gained focus - setting isFocusEditText=true")
                    viewModel.setIsFocusEditText(true)
                    // Let adjustResize handle layout - no programmatic scroll needed
                } else {
                    Log.d("EditTextFlow", "EditText lost focus - setting isFocusEditText=false")
                    // Only update if not already false (prevent recursive calls)
                    if (viewModel.isFocusEditText.value) {
                        viewModel.setIsFocusEditText(false)
                    }
                }
            }
            btnDoneText.tap {
                Log.d("EditTextFlow", "btnDoneText tapped")
                handleDoneText()
            }

            main.tap {
                Log.d("EditTextFlow", "main layout tapped - clearing focus")
                viewModel.setIsFocusEditText(false)
                clearFocus()
            }

            backgroundImageAdapter.apply {
                onAddImageClick = { checkStoragePermission() }
                onBackgroundImageClick = { path, position ->
                    if (path.startsWith("http") && !backgroundImageAdapter.isLoaded(path) && !InternetHelper.checkInternet(this@AddCharacterActivity)) {
                        YesNoDialog(this@AddCharacterActivity, R.string.error, R.string.please_check_your_internet, isError = true).show()
                    } else {
                        handleSetBackgroundImage(path, position)
                    }
                }
            }

            backgroundColorAdapter.apply {
                onChooseColorClick = { handleChooseColor() }
                onBackgroundColorClick = { color, position -> handleSetBackgroundColor(color, position) }
            }

            stickerAdapter.onItemClick = { path ->
                if (path.startsWith("http") && !stickerAdapter.isLoaded(path) && !InternetHelper.checkInternet(this@AddCharacterActivity)) {
                    YesNoDialog(this@AddCharacterActivity, R.string.error, R.string.please_check_your_internet, isError = true).show()
                } else {
                    addDrawable(path)
                }
            }

            speechAdapter.onItemClick = { path -> handleSpeech(path) }

            textFontAdapter.onTextFontClick = { font, position -> handleFontClick(font, position) }

            textColorAdapter.apply {
                onChooseColorClick = { handleChooseColor(true) }
                onTextColorClick = { color, position -> handleTextColorClick(color, position) }
            }
        }
    }

    override fun initActionBar() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.ic_back)
            setImageActionBar(btnActionBarCenter, R.drawable.ic_reset)
            setImageActionBar(btnActionBarRight, R.drawable.ic_save)
            btnActionBarRight.visible()

            // Căn giữa nút reset vào guideline
            val params = btnActionBarCenter.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.endToEnd = guideline.id
            params.startToStart = guideline.id
            params.horizontalBias = 0.5f
            params.marginEnd = 0
            btnActionBarCenter.layoutParams = params
        }
    }

    override fun initText() {
        binding.apply {
            tvBackgroundImage.select()
            tvBackgroundColor.select()

            // Apply gradient to tvText, tvFont, tvColor

        }
    }

    private fun initRcv() {
        binding.apply {
            rcvBackgroundImage.apply {
                adapter = backgroundImageAdapter
                itemAnimator = null
            }

            rcvBackgroundColor.apply {
                adapter = backgroundColorAdapter
                itemAnimator = null
            }

            rcvSticker.apply {
                adapter = stickerAdapter
                itemAnimator = null
            }

            rcvSpeech.apply {
                adapter = speechAdapter
                itemAnimator = null
            }

            rcvFont.apply {
                adapter = textFontAdapter
                itemAnimator = null
            }

            rcvTextColor.apply {
                adapter = textColorAdapter
                itemAnimator = null
            }
        }
    }

    private fun initData() {
        lifecycleScope.launch(Dispatchers.IO) {
            showLoading()
            viewModel.loadDataDefault(this@AddCharacterActivity)
            viewModel.updatePathDefault(intent.getStringExtra(IntentKey.INTENT_KEY) ?: "")
            addDrawable(viewModel.pathDefault, true)

            withContext(Dispatchers.Main) {
                viewModel.setTypeNavigation(ValueKey.BACKGROUND_NAVIGATION)
                viewModel.setTypeBackground(ValueKey.IMAGE_BACKGROUND)
                backgroundImageAdapter.submitList(viewModel.backgroundImageList)
                backgroundColorAdapter.submitList(viewModel.backgroundColorList)
                stickerAdapter.submitList(viewModel.stickerList)
                speechAdapter.submitList(viewModel.speechList)
                textFontAdapter.submitListReset(viewModel.textFontList)
                textColorAdapter.submitListReset(viewModel.textColorList)
                // Set initial text color and font to match selected items
                binding.edtText.setFont(viewModel.textFontList.first().color)
                binding.edtText.setTextColor(viewModel.textColorList[1].color)
                binding.tvGetText.setTextColor(viewModel.textColorList[1].color)
                delay(200)
                binding.drawView.autoSelectFirstDraw()

                clearFocus()
                binding.drawView.autoSelectFirstDraw()
                dismissLoading(true)
            }
        }
    }

    private fun addDrawable(
        path: String,
        isCharacter: Boolean = false,
        bitmapText: Bitmap? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            val targetSize = 512
            val bitmapDefault = if (bitmapText == null) {
                try {
                    Glide.with(this@AddCharacterActivity)
                        .load(path)
                        .submit(targetSize, targetSize)
                        .get()
                        .toBitmap()
                } catch (e: Exception) {
                    android.util.Log.e("AddCharacter", "Failed to load drawable: $path", e)
                    return@launch
                }
            } else bitmapText
            val drawableEmoji = viewModel.loadDrawableEmoji(this@AddCharacterActivity, bitmapDefault, isCharacter)

            withContext(Dispatchers.Main) {
                drawableEmoji.let { binding.drawView.addDraw(it) }
            }
        }
    }

    private fun initDrawView() {
        binding.drawView.apply {
            setConstrained(true)
            setLocked(false)
            setOnDrawListener(object : OnDrawListener {
                override fun onAddedDraw(draw: Draw) {
                    Log.d("EditTextFlow", "DrawView: onAddedDraw")
                    viewModel.updateCurrentCurrentDraw(draw)
                    viewModel.addDrawView(draw)
                    viewModel.setIsFocusEditText(false)
                }

                override fun onClickedDraw(draw: Draw) {
                    Log.d("EditTextFlow", "DrawView: onClickedDraw")
                    viewModel.setIsFocusEditText(false)
                }

                override fun onDeletedDraw(draw: Draw) {
                    Log.d("EditTextFlow", "DrawView: onDeletedDraw")
                    viewModel.deleteDrawView(draw)
                    viewModel.setIsFocusEditText(false)
                }

                override fun onDragFinishedDraw(draw: Draw) {
                    Log.d("EditTextFlow", "DrawView: onDragFinishedDraw")
                    viewModel.setIsFocusEditText(false)
                }

                override fun onTouchedDownDraw(draw: Draw) {
                    Log.d("EditTextFlow", "DrawView: onTouchedDownDraw")
                    viewModel.updateCurrentCurrentDraw(draw)
                    viewModel.setIsFocusEditText(false)
                }

                override fun onZoomFinishedDraw(draw: Draw) {}

                override fun onFlippedDraw(draw: Draw) {
                    Log.d("EditTextFlow", "DrawView: onFlippedDraw")
                    viewModel.setIsFocusEditText(false)
                }

                override fun onDoubleTappedDraw(draw: Draw) {}

                override fun onHideOptionIconDraw() {}

                override fun onUndoDeleteDraw(draw: List<Draw?>) {}

                override fun onUndoUpdateDraw(draw: List<Draw?>) {}

                override fun onUndoDeleteAll() {}

                override fun onRedoAll() {}

                override fun onReplaceDraw(draw: Draw) {}

                override fun onEditText(draw: DrawableDraw) {}

                override fun onReplace(draw: Draw) {}
            })
        }
    }

    private fun setupTypeBackground(type: Int) {
        binding.apply {

            when (type) {
                ValueKey.IMAGE_BACKGROUND -> {
                    rcvBackgroundImage.visible()
                    rcvBackgroundColor.gone()
                    setupSelectedTabBackground(btnBackgroundImage, tvBackgroundImage, imvFocusImage, subTabImage, isLeftTab = true)
                    setupUnselectedTabBackground(btnBackgroundColor, tvBackgroundColor, imvFocusColor, subTabColor, isLeftTab = false)
                    //backgroundImageAdapter.submitList(viewModel.backgroundImageList)
                    backgroundImageAdapter.submitList(viewModel.backgroundImageList)

                }

                ValueKey.COLOR_BACKGROUND -> {
                    rcvBackgroundImage.gone()
                    rcvBackgroundColor.visible()
                    setupSelectedTabBackground(btnBackgroundColor, tvBackgroundColor, imvFocusColor, subTabColor, isLeftTab = false)
                    setupUnselectedTabBackground(btnBackgroundImage, tvBackgroundImage, imvFocusImage, subTabImage, isLeftTab = true)
                    //backgroundColorAdapter.submitList(viewModel.backgroundColorList)
                    backgroundColorAdapter.submitList(viewModel.backgroundColorList)

                }

                else -> {}
            }
        }
    }

    private fun setupSelectedTabBackground(
        tabView: View,
        textView: android.widget.TextView,
        focusImage: android.widget.ImageView,
        subTab: View,
        isLeftTab: Boolean
    ) {
        // Set weight = 1.6
        val params = tabView.layoutParams as android.widget.LinearLayout.LayoutParams
        params.weight = 1.0f
        params.topMargin = 0
        tabView.layoutParams = params

        // Set text size = 18sp
        textView.textSize = 14f

        // Apply gradient color from top to bottom - WHITE gradient for selected

            val textHeight = textView.lineHeight.toFloat()
//            val shader = LinearGradient(
//                0f, 0f, 0f, textHeight,
//                Color.parseColor("#FFFFFF"),
//                Color.parseColor("#FFFFFF"),
//                Shader.TileMode.CLAMP
//            )
//            textView.paint.shader = shader
//            textView.invalidate()


        textView.setTextColor(Color.parseColor("#FFFFFF"))

        // Show selected_tab drawable
        focusImage.setImageResource(R.drawable.selected_tab)
        focusImage.scaleX = 1f
        focusImage.visible()

        // Hide subTab
        subTab.gone()
    }

    private fun setupUnselectedTabBackground(
        tabView: View,
        textView: android.widget.TextView,
        focusImage: android.widget.ImageView,
        subTab: View,
        isLeftTab: Boolean
    ) {
        // Set weight = 1
        val params = tabView.layoutParams as android.widget.LinearLayout.LayoutParams
        params.weight = 1f
        params.topMargin = UnitHelper.dpToPx(this, 0f).toInt()
        tabView.layoutParams = params

        // Set text size = 14sp, color = colorPrimary
        textView.textSize = 14f

        // Apply RED gradient for unselected

            val textHeight = textView.lineHeight.toFloat()
//            val shader = LinearGradient(
//                0f, 0f, 0f, textHeight,
//                Color.parseColor("#FF7396"),
//                Color.parseColor("#FF7396"),
//                Shader.TileMode.CLAMP
//            )
//            textView.paint.shader = shader
//            textView.invalidate()



        textView.setTextColor(Color.parseColor("#FF7396"))

        // Show un_selected_tab drawable
        focusImage.setImageResource(R.drawable.un_selected_tab)
        // Flip horizontally if on left side
        focusImage.scaleX = if (isLeftTab) -1f else 1f
        focusImage.visible()

        // Show subTab
        subTab.gone()
    }

    private fun setupTypeNavigation(type: Int) {
        buttonNavigationList.forEachIndexed { index, button ->
            val (res, status) = if (index == type) {
                DataLocal.bottomNavigationSelected[index] to true
            } else {
                DataLocal.bottomNavigationNotSelect[index] to false
            }

            button.setImageResource(res)
            layoutNavigationList[index].isVisible = status
        }

        // Show sectionTab and spaceSectionTab only when Background navigation is selected
        binding.apply {
            if (type == ValueKey.BACKGROUND_NAVIGATION) {
                sectionTab.visible()
                spaceSectionTab.visible()
            } else {
                sectionTab.gone()
                spaceSectionTab.invisible()
            }
        }
    }

    private fun confirmExit() {
        viewModel.setIsFocusEditText(false)
        val dialog = YesNoDialog(this, R.string.exit, R.string.haven_t_saved_it_yet_do_you_want_to_exit)
        dialog.show()

        fun dismissDialog() {
            dialog.dismiss()
            hideNavigation(false)
        }
        dialog.onNoClick = {
            dismissDialog()
        }
        dialog.onYesClick = {
            dismissDialog()
            showInterAll{
            finish()}
        }
    }

    private fun confirmReset() {
        viewModel.setIsFocusEditText(false)
        val dialog = YesNoDialog(this, R.string.reset, R.string.change_your_whole_design_are_you_sure)
        dialog.show()

        fun dismissDialog() {
            dialog.dismiss()
            hideNavigation(false)
        }

        dialog.onNoClick = {
            dismissDialog()
        }

        dialog.onYesClick = {
            dismissDialog()
            lifecycleScope.launch {
                showLoading()
                withContext(Dispatchers.IO) {
                    viewModel.loadDataDefault(this@AddCharacterActivity)
                    viewModel.resetDraw()
                }
                binding.drawView.removeAllDraw()
                binding.imvBackground.setImageBitmap(null)
                binding.imvBackground.setBackgroundColor(getColor(R.color.transparent))
                binding.edtText.setText("")
                binding.edtText.setFont(viewModel.textFontList.first().color)
                binding.edtText.setTextColor(viewModel.textColorList[1].color)
                binding.tvGetText.setTextColor(viewModel.textColorList[1].color) // Thêm dòng này
                addDrawable(viewModel.pathDefault, true)
                backgroundImageAdapter.submitList(viewModel.backgroundImageList)
                backgroundColorAdapter.submitList(viewModel.backgroundColorList)
                stickerAdapter.submitList(viewModel.stickerList)
                speechAdapter.submitList(viewModel.speechList)
                textFontAdapter.submitListReset(viewModel.textFontList)
                textColorAdapter.submitListReset(viewModel.textColorList)
                dismissLoading(true)
                showInterAll {  }
            }
        }
    }

    private fun handleSetBackgroundImage(path: String, position: Int) {
        binding.imvBackground.setBackgroundColor(getColor(R.color.transparent))
        loadImage(this, path, binding.imvBackground)
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.updateBackgroundImageSelected(position)
            withContext(Dispatchers.Main) {
                backgroundImageAdapter.submitItem(position, viewModel.backgroundImageList)
            }
        }
    }

    private fun checkStoragePermission() {
        // Use Photo Picker - NO PERMISSION REQUIRED
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun handleChooseColor(isTextColor: Boolean = false) {
        val dialog = ChooseColorDialog(this)

        dialog.show()

        fun dismissDialog() {
            dialog.dismiss()
            hideNavigation(false)
        }

        dialog.onCloseEvent = {
            dismissDialog()
        }

        dialog.onDoneEvent = { color ->
            dismissDialog()
            Log.d("AddCharacterActivity", "Color picker selected color: ${String.format("#%06X", 0xFFFFFF and color)}, isTextColor=$isTextColor")
            if (!isTextColor) {
                Log.d("AddCharacterActivity", "Calling handleSetBackgroundColor with position 0")
                handleSetBackgroundColor(color, 0)
            } else {
                Log.d("AddCharacterActivity", "Calling handleTextColorClick with position 0")
                handleTextColorClick(color, 0)
            }
        }
    }

    private fun handleSpeech(path: String) {
        val dialog = DialogSpeech(this, path)
        dialog.show()
        dialog.onDoneClick = { bitmap ->
            dialog.dismiss()
            hideNavigation(false)
            if (bitmap != null) {
                addDrawable("", false, bitmap)
            }
        }
    }

    private fun handleSetBackgroundColor(color: Int, position: Int) {
        Log.d("AddCharacterActivity", "handleSetBackgroundColor called: color=${String.format("#%06X", 0xFFFFFF and color)}, position=$position")
        binding.apply {
            imvBackground.setImageBitmap(null)
            imvBackground.setBackgroundColor(color)
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("AddCharacterActivity", "Before updateBackgroundColorSelected: list[0].color=${String.format("#%06X", 0xFFFFFF and viewModel.backgroundColorList[0].color)}")
                viewModel.updateBackgroundColorSelected(position)
                Log.d("AddCharacterActivity", "After updateBackgroundColorSelected: list[0].color=${String.format("#%06X", 0xFFFFFF and viewModel.backgroundColorList[0].color)}, list[0].isSelected=${viewModel.backgroundColorList[0].isSelected}")
                withContext(Dispatchers.Main) {
                    backgroundColorAdapter.submitItem(position, viewModel.backgroundColorList)
                }
            }
        }
    }

    private fun handleFontClick(font: Int, position: Int) {
        binding.apply {
            edtText.hint = SpannableString(getString(R.string.hello_world))
            edtText.setFont(font)
            tvGetText.setFont(font)
            viewModel.updateTextFontSelected(position)
            textFontAdapter.submitItem(position, viewModel.textFontList)
        }
    }

    private fun handleTextColorClick(color: Int, position: Int) {
        Log.d("AddCharacterActivity", "handleTextColorClick called: color=${String.format("#%06X", 0xFFFFFF and color)}, position=$position")
        binding.apply {
            edtText.setTextColor(color)
            tvGetText.setTextColor(color)
            Log.d("AddCharacterActivity", "Before updateTextColorSelected: list[0].color=${String.format("#%06X", 0xFFFFFF and viewModel.textColorList[0].color)}")
            viewModel.updateTextColorSelected(position)
            Log.d("AddCharacterActivity", "After updateTextColorSelected: list[0].color=${String.format("#%06X", 0xFFFFFF and viewModel.textColorList[0].color)}, list[0].isSelected=${viewModel.textColorList[0].isSelected}")
            textColorAdapter.submitItem(position, viewModel.textColorList)
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun handleDoneText() {
        viewModel.setIsFocusEditText(false)
        binding.apply {
            if (edtText.text.toString().trim() == "") {
                showToast(getString(R.string.null_edt))
            } else {
                tvGetText.text = edtText.text.toString().trim()
                val bitmap = BitmapHelper.getBitmapFromEditText(tvGetText)
                val drawableEmoji = viewModel.loadDrawableEmoji(this@AddCharacterActivity, bitmap, isText = true)
                binding.drawView.addDraw(drawableEmoji)

                // Reset
                val font = viewModel.textFontList.first().color
                val color = viewModel.textColorList[1].color

                edtText.text = null
                edtText.setFont(font)
                edtText.setTextColor(color)

                viewModel.updateTextFontSelected(0)
                viewModel.updateTextColorSelected(1)

                textFontAdapter.submitListReset(viewModel.textFontList)
                textColorAdapter.submitListReset(viewModel.textColorList)

                tvGetText.text = ""
                tvGetText.setFont(font)
                tvGetText.setTextColor(color)
            }
        }
    }

    private fun clearFocus() {
        binding.drawView.hideSelect()
    }

    private fun handleSave() {
        binding.apply {
            clearFocus()
            lifecycleScope.launch(Dispatchers.IO) {
                showLoading()
                delay(200)
                viewModel.saveImageFromView(this@AddCharacterActivity, flSave).collect { result ->
                    when (result) {
                        is SaveState.Loading -> showLoading()

                        is SaveState.Error -> {
                            dismissLoading(true)
                            withContext(Dispatchers.Main) {
                                showToast(R.string.save_failed_please_try_again)
                            }
                        }

                        is SaveState.Success -> {
                            val intent = Intent(this@AddCharacterActivity, ViewActivity::class.java)
                            intent.putExtra(IntentKey.INTENT_KEY, result.path)
                            intent.putExtra(IntentKey.STATUS_KEY, ValueKey.MY_DESIGN_TYPE)
                            intent.putExtra(IntentKey.TYPE_KEY, ValueKey.TYPE_SUCCESS)
                            val options = ActivityOptions.makeCustomAnimation(
                                this@AddCharacterActivity, R.anim.slide_in_right, R.anim.slide_out_left
                            )
                            dismissLoading(true)
                            withContext(Dispatchers.Main){
                                showInterAll { startActivity(intent, options.toBundle()) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RequestKey.STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                permissionViewModel.updateStorageGranted(sharePreference, true)
                openImagePicker()
            } else {
                permissionViewModel.updateStorageGranted(sharePreference, false)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RequestKey.PICK_IMAGE_REQUEST_CODE && resultCode == RESULT_OK) {
            val selectedImageUri: Uri? = data?.data
            handleSetBackgroundImage(selectedImageUri.toString(), 0)
        }
    }

    @Deprecated("Use OnBackPressedCallback instead")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        Log.d("EditTextFlow", "DEPRECATED onBackPressed called - this should NOT happen if callback is working")
        // Don't call super or handle anything - let the callback handle it
        // This method should not be called if the OnBackPressedCallback is working properly
    }

    fun initNativeCollab() {
        Admob.getInstance().loadNativeCollapNotBanner(this,getString(R.string.native_cl_bg),binding.flNativeCollab)
    }

    override fun initAds() {
        initNativeCollab()
    }

    override fun onRestart() {
        super.onRestart()
        initNativeCollab()
    }

    // Custom Input View Functions
    private fun setupCustomInput() {
       // val customInputView = binding.customInputLayout.root

        // Set height to 1/4 of screen
//        customInputView.post {
//            val screenHeight = resources.displayMetrics.heightPixels
//            val params = customInputView.layoutParams
//            params.height = screenHeight / 4
//            customInputView.layoutParams = params
//        }

        // Disable system keyboard for edtText
        binding.edtText.showSoftInputOnFocus = true

//        // Setup edtText click listener
//        binding.edtText.setOnClickListener {
//           // hideSystemKeyboard()
//          //  showCustomInput()
//        }

//        binding.edtText.setOnFocusChangeListener { _, hasFocus ->
//            if (hasFocus) {
//               // hideSystemKeyboard()
//                //showCustomInput()
//            }
//        }

        // Setup all letter buttons
        setupLetterButtons()

//        // Setup control buttons
//        binding.customInputLayout.btnSpace.tap { appendText(" ") }
//        binding.customInputLayout.btnDelete.tap { deleteLastChar() }
//        binding.customInputLayout.btnDone.tap { hideCustomInput() }
    }

    private fun setupLetterButtons() {
        val letters = listOf(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
            "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
            "U", "V", "W", "X", "Y", "Z"
        )

//        letters.forEach { letter ->
//            val buttonId = resources.getIdentifier("btn$letter", "id", packageName)
//            val button = binding.customInputLayout.root.findViewById<android.widget.Button>(buttonId)
//            button?.tap { appendText(letter) }
//        }
    }

//    private fun showCustomInput() {
//        binding.customInputLayout.root.visible()
//    }
//
//    private fun hideCustomInput() {
//        binding.customInputLayout.root.gone()
//    }

    private fun hideSystemKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.edtText.windowToken, 0)
    }

    private fun appendText(text: String) {
        val currentText = binding.edtText.text.toString()
        binding.edtText.setText(currentText + text)
        binding.edtText.setSelection(binding.edtText.text.length)
    }

    private fun deleteLastChar() {
        val currentText = binding.edtText.text.toString()
        if (currentText.isNotEmpty()) {
            binding.edtText.setText(currentText.dropLast(1))
            binding.edtText.setSelection(binding.edtText.text.length)
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyUiCustomize()
            hideNavigation(false)

            window.decorView.removeCallbacks(reHideRunnable)
            window.decorView.postDelayed(reHideRunnable, 1500)
        } else {
            window.decorView.removeCallbacks(reHideRunnable)
        }
    }

    private val reHideRunnable = Runnable {
        applyUiCustomize()
        hideNavigation(false)
    }
    @Suppress("DEPRECATION")
    private fun applyUiCustomize() {
        // Cho phép app tự vẽ màu system bar
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        // Transparent status bar
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Flags
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        // nếu muốn icon status bar đen thì thêm:
        // or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }
}