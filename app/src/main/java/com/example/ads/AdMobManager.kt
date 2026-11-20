package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object AdMobConfig {
    // Official Google AdMob Test Ad Unit IDs (used during development/debug)
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // User's Production StudyWin India AdMob Ad Unit IDs
    const val PROD_BANNER_UNIT_ID = "ca-app-pub-6545218519929279/1965994726"
    const val PROD_REWARDED_UNIT_ID = "ca-app-pub-6545218519929279/9899043803"
}

class AdMobManager private constructor() {
    private val initialized = AtomicBoolean(false)
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null

    private val _useTestAds = MutableStateFlow(true)
    val useTestAds: StateFlow<Boolean> = _useTestAds.asStateFlow()

    private val _bannerUnitId = MutableStateFlow(AdMobConfig.PROD_BANNER_UNIT_ID)
    private val _rewardedUnitId = MutableStateFlow(AdMobConfig.PROD_REWARDED_UNIT_ID)
    private val _interstitialUnitId = MutableStateFlow(AdMobConfig.TEST_INTERSTITIAL_UNIT_ID)

    private val _isRewardedLoaded = MutableStateFlow(false)
    val isRewardedLoaded: StateFlow<Boolean> = _isRewardedLoaded.asStateFlow()

    private val _isRewardedLoading = MutableStateFlow(false)
    val isRewardedLoading: StateFlow<Boolean> = _isRewardedLoading.asStateFlow()

    private val _lastAdStatus = MutableStateFlow("")
    val lastAdStatus: StateFlow<String> = _lastAdStatus.asStateFlow()

    fun initializeOnce(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext) {
                Log.d(TAG, "Google Mobile Ads SDK initialized")
            }
        }
    }

    fun updateAdConfig(
        useTestAds: Boolean,
        bannerId: String,
        rewardedId: String,
        interstitialId: String
    ) {
        _useTestAds.value = useTestAds
        if (bannerId.isNotBlank()) _bannerUnitId.value = bannerId
        if (rewardedId.isNotBlank()) _rewardedUnitId.value = rewardedId
        if (interstitialId.isNotBlank()) _interstitialUnitId.value = interstitialId
    }

    fun activeBannerUnitId(): String =
        if (_useTestAds.value) AdMobConfig.TEST_BANNER_UNIT_ID else _bannerUnitId.value

    fun activeRewardedUnitId(): String =
        if (_useTestAds.value) AdMobConfig.TEST_REWARDED_UNIT_ID else _rewardedUnitId.value

    fun activeInterstitialUnitId(): String =
        if (_useTestAds.value) AdMobConfig.TEST_INTERSTITIAL_UNIT_ID else _interstitialUnitId.value

    fun preloadRewardedAd(context: Context, userId: String = "", placement: String = "general") {
        if (_isRewardedLoading.value || rewardedAd != null) return
        _isRewardedLoading.value = true
        val unitId = activeRewardedUnitId()
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context.applicationContext,
            unitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (userId.isNotBlank()) {
                        val ssv = ServerSideVerificationOptions.Builder()
                            .setUserId(userId)
                            .setCustomData(placement)
                            .build()
                        ad.setServerSideVerificationOptions(ssv)
                    }
                    rewardedAd = ad
                    _isRewardedLoading.value = false
                    _isRewardedLoaded.value = true
                    _lastAdStatus.value = "Rewarded Ad ready"
                }

                override fun onAdFailedToLoad(loadError: LoadAdError) {
                    rewardedAd = null
                    _isRewardedLoading.value = false
                    _isRewardedLoaded.value = false
                    _lastAdStatus.value = "Ad unavailable (${loadError.message})"
                    Log.w(TAG, "Rewarded ad failed to load: ${loadError.message}")
                }
            }
        )
    }

    /**
     * Strictly enforces AdMob Rewarded Ad completion before triggering [onRewardEarned].
     * Uses an AtomicBoolean guard so duplicate callbacks can never grant duplicate rewards.
     */
    fun showRewardedAd(
        activity: Activity?,
        userId: String,
        placement: String,
        onRewardEarned: (rewardToken: String, unitId: String) -> Unit,
        onAdSkippedOrClosed: () -> Unit,
        onAdUnavailable: (String) -> Unit
    ) {
        if (activity == null || activity.isFinishing) {
            onAdUnavailable("Activity unavailable for displaying ad")
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            preloadRewardedAd(activity, userId, placement)
            onAdUnavailable("Rewarded ad is loading or unavailable right now. Please try again in a moment.")
            return
        }

        val unitId = activeRewardedUnitId()
        if (userId.isNotBlank()) {
            ad.setServerSideVerificationOptions(
                ServerSideVerificationOptions.Builder()
                    .setUserId(userId)
                    .setCustomData(placement)
                    .build()
            )
        }

        val rewardEarned = AtomicBoolean(false)
        val rewardToken = UUID.randomUUID().toString()

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                _isRewardedLoaded.value = false
                preloadRewardedAd(activity, userId, placement)
                if (rewardEarned.get()) {
                    onRewardEarned(rewardToken, unitId)
                } else {
                    onAdSkippedOrClosed()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                _isRewardedLoaded.value = false
                preloadRewardedAd(activity, userId, placement)
                onAdUnavailable("Ad failed to display: ${adError.message}")
            }
        }

        ad.show(activity) {
            // Only mark reward earned when AdMob confirms completion
            rewardEarned.compareAndSet(false, true)
        }
    }

    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null) return
        val unitId = activeInterstitialUnitId()
        InterstitialAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitialIfReady(activity: Activity?, onDone: () -> Unit) {
        val ad = interstitialAd
        if (activity == null || ad == null) {
            onDone()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                onDone()
            }
        }
        ad.show(activity)
    }

    companion object {
        private const val TAG = "AdMobManager"

        @Volatile
        private var INSTANCE: AdMobManager? = null

        fun getInstance(): AdMobManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdMobManager().also { INSTANCE = it }
            }
    }
}

/**
 * Reusable AdMob Banner composable that handles lifecycle pause/resume/destroy cleanly
 * and collapses without errors if an ad fails to load.
 */
@Composable
fun AdBannerFooter(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    var adFailed by remember(adUnitId) { mutableStateOf(false) }
    if (adFailed) return

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        adFailed = true
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { adView ->
            adView.resume()
        },
        onRelease = { adView ->
            adView.pause()
            adView.destroy()
        }
    )
}
