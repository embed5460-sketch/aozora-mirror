package app.meisaku.reader.data

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.rewarded.RewardedAd

/**
 * Google AdMob 封装（骨架）。两种广告：
 *  - 开屏（App Open）：仅冷启动展示一次，之后从后台切回前台不再弹（[showAppOpenOnColdStart]）。
 *  - 激励（Rewarded）：用户在「今日免费注音用尽」横幅点「看广告」→ 看完解锁**当前这本**当天的注音
 *    （[showRewarded] 的 onReward 由调用方接 [FuriganaQuota.unlockBook]）。
 *
 * 设计要点：
 *  - **premium 用户不加载、不展示任何广告**（[isPremium] 实时查询）。
 *  - **debug 包一律用 Google 官方测试广告位 ID**（点测试广告不会封号）；release 才用真实 ID。
 *    判定用 ApplicationInfo.FLAG_DEBUGGABLE，无需 BuildConfig。
 *  - 无 GMS / 加载失败时静默降级（横幅退回纯文字提示，开屏直接跳过），不崩。
 *  - 冷启动开屏：广告异步加载，onCreate 时通常还没就绪 → 记下请求时刻，加载完成且仍在
 *    [COLD_START_DEADLINE_MS] 窗口内、Activity 在场、未展示过，才展示；超时则放弃（不在用户已
 *    进入阅读后才迟到弹出）。
 */
class AdsManager(
    context: Context,
    private val isPremium: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val debuggable =
        (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val appOpenUnit = if (debuggable) TEST_APP_OPEN else REAL_APP_OPEN
    private val rewardedUnit = if (debuggable) TEST_REWARDED else REAL_REWARDED

    /** 供 UI 即时反应「激励广告是否就绪」。 */
    data class UiState(val rewardedReady: Boolean = false)

    private val _state = mutableStateOf(UiState())
    val state: State<UiState> = _state

    private var initialized = false
    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoading = false
    private var rewardedAd: RewardedAd? = null
    private var rewardedLoading = false
    private var showingFullScreen = false

    // 冷启动开屏状态
    private var coldStartActivity: Activity? = null
    private var coldStartRequestedAt = 0L
    private var coldStartConsumed = false

    /** 进程级初始化 MobileAds，并预加载首批广告。重复调用安全。 */
    fun initialize() {
        if (initialized) return
        initialized = true
        if (debuggable) {
            // 测试环境额外保险：模拟器/本机即便误用真实位也只出测试广告。
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build(),
            )
        }
        MobileAds.initialize(appContext) {
            if (!isPremium()) {
                loadAppOpen()
                loadRewarded()
            }
        }
    }

    private fun request() = AdRequest.Builder().build()

    // ---- 开屏（App Open）: 仅冷启动 ----

    private fun loadAppOpen() {
        if (isPremium() || appOpenLoading || appOpenAd != null) return
        appOpenLoading = true
        AppOpenAd.load(
            appContext, appOpenUnit, request(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenLoading = false
                    appOpenAd = ad
                    maybeShowColdStart()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenLoading = false
                    appOpenAd = null
                }
            },
        )
    }

    /**
     * MainActivity 冷启动时调用：登记 Activity 并尝试展示。广告若尚未加载完成，会在加载回调里
     * （仍在时限内）补展示一次。premium 直接消费掉冷启动名额、不展示。
     */
    fun showAppOpenOnColdStart(activity: Activity) {
        if (coldStartConsumed) return
        if (isPremium()) { coldStartConsumed = true; return }
        coldStartActivity = activity
        coldStartRequestedAt = SystemClock.elapsedRealtime()
        initialize()
        maybeShowColdStart()
    }

    /** Activity 销毁时解绑，避免泄漏。 */
    fun detachActivity(activity: Activity) {
        if (coldStartActivity === activity) coldStartActivity = null
    }

    private fun maybeShowColdStart() {
        if (coldStartConsumed || showingFullScreen) return
        val activity = coldStartActivity ?: return
        val ad = appOpenAd ?: return
        if (SystemClock.elapsedRealtime() - coldStartRequestedAt > COLD_START_DEADLINE_MS) {
            coldStartConsumed = true // 迟到了，放弃，不打扰已在阅读的用户
            return
        }
        coldStartConsumed = true
        showingFullScreen = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null; showingFullScreen = false
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null; showingFullScreen = false
            }
        }
        ad.show(activity)
    }

    // ---- 激励（Rewarded）: 看广告解锁当前作品 ----

    private fun loadRewarded() {
        if (isPremium() || rewardedLoading || rewardedAd != null) return
        rewardedLoading = true
        RewardedAd.load(
            appContext, rewardedUnit, request(),
            object : com.google.android.gms.ads.rewarded.RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewardedAd = ad
                    _state.value = UiState(rewardedReady = true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    rewardedAd = null
                    _state.value = UiState(rewardedReady = false)
                }
            },
        )
    }

    /**
     * 展示激励广告。用户看完获得奖励时回调 [onReward]（解锁当前作品由调用方接）；
     * 展示结束后自动预加载下一条。广告未就绪时返回 false（调用方据此提示「準備中」）。
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit): Boolean {
        val ad = rewardedAd ?: run { loadRewarded(); return false }
        rewardedAd = null
        _state.value = UiState(rewardedReady = false)
        showingFullScreen = true
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showingFullScreen = false
                if (earned) onReward()
                loadRewarded() // 预加载下一条
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showingFullScreen = false
                loadRewarded()
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { earned = true })
        return true
    }

    companion object {
        // 真实广告位（release）。账号 pub-7291783059496091 / App「名作文庫」。
        private const val REAL_APP_OPEN = "ca-app-pub-7291783059496091/5899715008"
        private const val REAL_REWARDED = "ca-app-pub-7291783059496091/3273551664"

        // Google 官方测试广告位（debug 专用，点击不计费、不封号）。
        private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
        private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

        private const val COLD_START_DEADLINE_MS = 6_000L
    }
}
