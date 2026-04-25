package com.animal.avatar.charactor.maker.ui.maker_character

import android.view.LayoutInflater
import com.animal.avatar.charactor.maker.R
import com.animal.avatar.charactor.maker.core.base.BaseActivity
import com.animal.avatar.charactor.maker.core.extensions.handleBackLeftToRight
import com.animal.avatar.charactor.maker.core.extensions.loadNativeCollabAds
import com.animal.avatar.charactor.maker.core.extensions.select
import com.animal.avatar.charactor.maker.core.extensions.setImageActionBar
import com.animal.avatar.charactor.maker.core.extensions.setTextActionBar
import com.animal.avatar.charactor.maker.core.extensions.showInterAll
import com.animal.avatar.charactor.maker.core.extensions.startIntentRightToLeft
import com.animal.avatar.charactor.maker.core.extensions.tap
import com.animal.avatar.charactor.maker.core.utils.key.IntentKey
import com.animal.avatar.charactor.maker.databinding.ActivityMakerCharacterBinding
import com.animal.avatar.charactor.maker.ui.choose_character.ChooseCharacterActivity
import com.lvt.ads.util.Admob

class MakerCharacterActivity : BaseActivity<ActivityMakerCharacterBinding>() {

    override fun setViewBinding(): ActivityMakerCharacterBinding {
        return ActivityMakerCharacterBinding.inflate(LayoutInflater.from(this))
    }

    override fun initView() {}

    override fun viewListener() {
        binding.apply {
            actionBar.btnActionBarLeft.tap { showInterAll { handleBackLeftToRight() } }
            btnCatMaker.tap(800) {
                showInterAll {  startIntentRightToLeft(ChooseCharacterActivity::class.java, IntentKey.DATA_TYPE_KEY, IntentKey.DATA_TYPE_CAT)}
            }
            btnDragonMaker.tap(800) {
                showInterAll {  startIntentRightToLeft(ChooseCharacterActivity::class.java, IntentKey.DATA_TYPE_KEY, IntentKey.DATA_TYPE_DRAGON)}
            }
            btnDogMaker.tap(800) {
                showInterAll {  startIntentRightToLeft(ChooseCharacterActivity::class.java, IntentKey.DATA_TYPE_KEY, IntentKey.DATA_TYPE_DOG)}
            }
            btnPonyMaker.tap(800) {
                showInterAll {  startIntentRightToLeft(ChooseCharacterActivity::class.java, IntentKey.DATA_TYPE_KEY, IntentKey.DATA_TYPE_PONY)}
            }
            btnAnimalMaker.tap(800) {
                showInterAll {  startIntentRightToLeft(ChooseCharacterActivity::class.java, IntentKey.DATA_TYPE_KEY, IntentKey.DATA_TYPE_ANIMAL)}
            }
        }
    }

    override fun initActionBar() {
        binding.actionBar.apply {
            setImageActionBar(btnActionBarLeft, R.drawable.ic_back)
            setTextActionBar(tvCenter, getString(R.string.character_maker))
            tvCenter.select()
        }
    }

    override fun initAds() {
        loadNativeCollabAds(R.string.native_cl_chooseAnimal, binding.flNativeCollab, )
        Admob.getInstance().loadNativeAd(
            this,
            getString(R.string.native_chooseAnimal),
            binding.nativeAds,
            R.layout.ads_native_banner
        )
    }

    override fun onRestart() {
        super.onRestart()
        loadNativeCollabAds(R.string.native_cl_chooseAnimal, binding.flNativeCollab, )

    }


}
