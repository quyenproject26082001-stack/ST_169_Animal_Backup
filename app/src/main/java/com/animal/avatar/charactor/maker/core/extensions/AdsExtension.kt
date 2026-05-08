package com.animal.avatar.charactor.maker.core.extensions

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.nativead.NativeAd
import com.lvt.ads.callback.InterCallback
import com.lvt.ads.callback.NativeCallback
import com.lvt.ads.event.AdmobEvent
import com.lvt.ads.util.Admob
import com.animal.avatar.charactor.maker.core.helper.UnitHelper
import java.lang.ref.WeakReference

fun Activity.showInterAll(onFinishInter: () -> Unit) {
    val weakRef = WeakReference(this)
    Admob.getInstance().showInterAll(this, object : InterCallback() {
        override fun onNextAction() {
            super.onNextAction()
            if (weakRef.get() != null) onFinishInter.invoke()
        }
    })
}

//fun Activity.showInterAll() {
//    Admob.getInstance().showInterAll(this, object : InterCallback() {
//        override fun onNextAction() {
//            super.onNextAction()
//
//        }
//    })
//}

fun Activity.showInterAllWait(onFinishInter: () -> Unit) {
    val weakRef = WeakReference(this)
    Admob.getInstance().setOpenActivityAfterShowInterAds(false)
    Admob.getInstance().showInterAll(this, object : InterCallback() {
        override fun onNextAction() {
            super.onNextAction()
            Admob.getInstance().setOpenActivityAfterShowInterAds(true)
            if (weakRef.get() != null) onFinishInter.invoke()
        }
    })
}

fun Activity.loadNativeAds(id: String, nativeValue: ((NativeAd?) -> Unit) = {}){
    Admob.getInstance().loadNativeAd(this, id, object : NativeCallback() {
        override fun onNativeAdLoaded(nativeAd: NativeAd?) {
            super.onNativeAdLoaded(nativeAd)
            nativeValue.invoke(nativeAd)
        }
    })
}
fun Activity.loadNativeCollabAds(id: String, layout: FrameLayout) {
    Admob.getInstance().loadNativeCollap(this, id, layout)
}

fun Activity.loadNativeCollabAds(id: Int, layout: FrameLayout) {
    Admob.getInstance().loadNativeCollap(this, getString(id), layout)
}

fun Activity.loadNativeCollabAds(
    id: Any,
    layout: FrameLayout,
    view: View,
    bottomFailed: Int = 0,
    bottomLoadSuccess: Int = 82,
) {
    val marginBottom = UnitHelper.pxToDpInt(this, bottomFailed)
    val marginBottomLoadSuccess = UnitHelper.pxToDpInt(this, bottomLoadSuccess)
    val adsId = when(id){
        is String -> id
        is Int -> getString(id)
        else -> return
    }
    val params = view.layoutParams as ViewGroup.MarginLayoutParams

    Admob.getInstance().loadNativeCollap(this, adsId, layout, object : NativeCallback() {
        override fun onAdFailedToLoad() {
            super.onAdFailedToLoad()
            params.bottomMargin = marginBottom
            view.layoutParams = params
        }

        override fun onNativeAdLoaded(nativeAd: NativeAd?) {
            super.onNativeAdLoaded(nativeAd)
            params.bottomMargin = marginBottomLoadSuccess
            view.layoutParams = params
        }

    })
}

fun Activity.loadNativeCollabAds(
    id: Int,
    layout: FrameLayout,
    view: View,
    bottomFailed: Int = 0,
    bottomLoadSuccess: Int = 82,
) {
    val marginBottom = UnitHelper.pxToDpInt(this, bottomFailed)
    val marginBottomLoadSuccess = UnitHelper.pxToDpInt(this, bottomLoadSuccess)

    Admob.getInstance().loadNativeCollap(this, getString(id), layout, object : NativeCallback() {
        override fun onAdFailedToLoad() {
            super.onAdFailedToLoad()
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = marginBottom
            view.layoutParams = params
        }

        override fun onNativeAdLoaded(nativeAd: NativeAd?) {
            super.onNativeAdLoaded(nativeAd)
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = marginBottomLoadSuccess
            view.layoutParams = params
        }

    })
}

fun Activity.logEvent(nameEvent: String){
    AdmobEvent.logEvent(this, nameEvent, null)
}