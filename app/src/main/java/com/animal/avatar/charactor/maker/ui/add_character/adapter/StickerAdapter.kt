package com.animal.avatar.charactor.maker.ui.add_character.adapter

import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.animal.avatar.charactor.maker.core.base.BaseAdapter
import com.animal.avatar.charactor.maker.core.extensions.tap
import com.animal.avatar.charactor.maker.data.model.SelectedModel
import com.animal.avatar.charactor.maker.databinding.ItemStickerBinding

class StickerAdapter : BaseAdapter<SelectedModel, ItemStickerBinding>(ItemStickerBinding::inflate) {
    var onItemClick: ((String) -> Unit) = {}

    private val loadedPaths = mutableSetOf<String>()

    fun isLoaded(path: String) = path in loadedPaths

    override fun onBind(binding: ItemStickerBinding, item: SelectedModel, position: Int) {
        binding.apply {
            Glide.with(root)
                .load(item.path)
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        loadedPaths.add(item.path)
                        return false
                    }
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean) = false
                })
                .into(imvSticker)
            root.tap { onItemClick.invoke(item.path) }
        }
    }
}