package com.animal.avatar.charactor.maker.ui.random_character

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.animal.avatar.charactor.maker.core.base.BaseAdapter
import com.animal.avatar.charactor.maker.core.utils.key.ValueKey
import com.animal.avatar.charactor.maker.data.model.custom.SuggestionModel
import com.animal.avatar.charactor.maker.databinding.ItemRandomCharacterBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.animal.avatar.charactor.maker.core.extensions.gone
import com.animal.avatar.charactor.maker.core.extensions.invisible
import com.animal.avatar.charactor.maker.core.extensions.tap
import com.animal.avatar.charactor.maker.core.extensions.visible
import com.animal.avatar.charactor.maker.core.helper.MediaHelper
import com.animal.avatar.charactor.maker.core.utils.state.SaveState


class RandomCharacterAdapter(val context: Context) :
    BaseAdapter<SuggestionModel, ItemRandomCharacterBinding>(ItemRandomCharacterBinding::inflate) {
    var onItemClick: ((SuggestionModel) -> Unit) = {}

    private val activeJobs = mutableMapOf<ItemRandomCharacterBinding, kotlinx.coroutines.Job>()
    private val compositeSemaphore = Semaphore(2)

    override fun onBind(binding: ItemRandomCharacterBinding, item: SuggestionModel, position: Int) {
        binding.apply {
            activeJobs[binding]?.cancel()

            if (item.pathInternalRandom.isNotEmpty()) {
                sflShimmer.gone()
                sflShimmer.stopShimmer()
                imvImage.visible()
                Glide.with(root).load(item.pathInternalRandom).into(imvImage)
                root.tap { onItemClick.invoke(item) }
                return@apply
            }

            sflShimmer.visible()
            sflShimmer.startShimmer()
            imvImage.invisible()

            var width = ValueKey.WIDTH_BITMAP
            var height = ValueKey.HEIGHT_BITMAP

            val listBitmap: ArrayList<Bitmap> = arrayListOf()
            val handleExceptionCoroutine = CoroutineExceptionHandler { _, throwable ->
                throwable.printStackTrace()
            }

            val job = CoroutineScope(SupervisorJob() + Dispatchers.IO + handleExceptionCoroutine).launch {
                compositeSemaphore.withPermit {
                    val job1 = async {
                        val bitmapDefault = Glide.with(context).asBitmap()
                            .load(item.pathSelectedList.first())
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .submit().get()
                        width = (bitmapDefault.width / 2).coerceAtMost(256)
                        height = (bitmapDefault.height / 2).coerceAtMost(256)
                        bitmapDefault.recycle()

                        if (item.pathInternalRandom == "") {
                            item.pathSelectedList.forEach { path ->
                                listBitmap.add(
                                    Glide.with(context).asBitmap().load(path)
                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                        .submit(width, height).get()
                                )
                            }
                        }
                        return@async true
                    }

                    withContext(Dispatchers.Main) {
                        if (job1.await()) {
                            if (item.pathInternalRandom == "") {
                                val combinedBitmap = createBitmap(width, height)
                                val canvas = Canvas(combinedBitmap)

                                for (i in 0 until listBitmap.size) {
                                    val bitmap = listBitmap[i]
                                    val left = (width - bitmap.width) / 2f
                                    val top = (height - bitmap.height) / 2f
                                    canvas.drawBitmap(bitmap, left, top, null)
                                    bitmap.recycle()
                                }
                                listBitmap.clear()

                                MediaHelper.saveBitmapToInternalStorage(context, ValueKey.RANDOM_TEMP_ALBUM, combinedBitmap).collect { state ->
                                    when (state) {
                                        is SaveState.Loading -> {}
                                        is SaveState.Error -> {}
                                        is SaveState.Success -> {
                                            item.pathInternalRandom = state.path
                                        }
                                    }
                                }
                                combinedBitmap.recycle()
                            }

                            Glide.with(root).load(item.pathInternalRandom).listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable?>, isFirstResource: Boolean): Boolean {
                                    sflShimmer.stopShimmer()
                                    sflShimmer.gone()
                                    return false
                                }

                                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable?>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                    sflShimmer.stopShimmer()
                                    sflShimmer.gone()
                                    imvImage.visible()
                                    return false
                                }
                            }).into(imvImage)
                        }
                    }
                }
            }

            activeJobs[binding] = job
            root.tap { onItemClick.invoke(item) }
        }
    }

    fun cancelAllJobs() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}
