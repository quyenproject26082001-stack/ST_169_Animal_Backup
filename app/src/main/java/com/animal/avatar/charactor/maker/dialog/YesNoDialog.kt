package com.animal.avatar.charactor.maker.dialog

import android.app.Activity
import android.graphics.drawable.AnimationDrawable
import com.animal.avatar.charactor.maker.core.extensions.gone
import com.animal.avatar.charactor.maker.core.extensions.hideNavigation
import com.animal.avatar.charactor.maker.core.extensions.tap
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.core.base.BaseDialog
import com.animal.avatar.charactor.maker.core.extensions.strings
import com.animal.avatar.charactor.maker.databinding.DialogConfirmBinding


class YesNoDialog(
    val context: Activity, val title: Int, val description: Int, val isError: Boolean = false
) : BaseDialog<DialogConfirmBinding>(context, maxWidth = true, maxHeight = true) {
    override val layoutId: Int = R.layout.dialog_confirm
    override val isCancelOnTouchOutside: Boolean = false
    override val isCancelableByBack: Boolean = false

    var onNoClick: (() -> Unit) = {}
    var onYesClick: (() -> Unit) = {}
    var onDismissClick: (() -> Unit) = {}

    override fun initView() {
        initText()
        if (isError) {
            binding.btnNo.gone()
        }
        context.hideNavigation()
        binding.tvTitle.isSelected =true

        // Start loading animation for ic_dot
        binding.icDot.setImageResource(R.drawable.dot_loading_animation)
        val dotAnimation = binding.icDot.drawable as? AnimationDrawable
        dotAnimation?.start()
    }

    override fun initAction() {
        binding.apply {
            btnNo.tap { onNoClick.invoke() }
            btnYes.tap {
                onYesClick.invoke()
                if (isError) dismiss()
            }
            flOutSide.tap { onDismissClick.invoke() }
        }
    }

    override fun onDismissListener() {

    }

    private fun initText() {
        binding.apply {
            tvTitle.text = context.strings(title)
            tvDescription.text = context.strings(description)
            // Use "Ok" text for error dialogs (like internet check)
            if (isError) {
                btnYes.text = context.strings(R.string.ok)
            }
        }
    }
}