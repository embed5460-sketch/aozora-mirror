package app.meisaku.reader.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Google Play Billing 封装（骨架）。一次性内购「プレミアム解除」(非消耗型)，
 * 拥有后解锁全文振假名无限。流程：连接 → 查询商品(价格) → 发起购买 → 确认(acknowledge)
 * → 解锁；启动时 queryPurchases 恢复已购。
 *
 * 注意（开发期）：
 *  - 真实购买需 Play Console 创建商品 [PRODUCT_PREMIUM] 并把 APK 传到内部测试轨、
 *    用 License Tester 账号测试。商品未配置时 [state].available=false，UI 显示「準備中」。
 *  - 设备无 Google Play 服务（部分国行机）时连接会失败 → 同样降级为「準備中」，不崩。
 *  - 拥有时回调 [onPremiumOwned] 解锁；未拥有不强制关闭（保留调试开关）。退款/撤销的
 *    回收逻辑后续再补。
 */
class BillingManager(
    context: Context,
    private val onPremiumOwned: () -> Unit,
) {
    data class UiState(
        val connected: Boolean = false,
        val available: Boolean = false,
        val owned: Boolean = false,
        val price: String? = null,
    )

    private val _state = mutableStateOf(UiState())
    val state: State<UiState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var productDetails: ProductDetails? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                _state.value = _state.value.copy(connected = ok)
                if (ok) {
                    queryProduct()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(connected = false)
            }
        })
    }

    private fun queryProduct() = scope.launch {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_PREMIUM)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        val pd = result.productDetailsList?.firstOrNull()
        productDetails = pd
        _state.value = _state.value.copy(
            available = pd != null,
            price = pd?.oneTimePurchaseOfferDetails?.formattedPrice,
        )
    }

    private fun restorePurchases() = scope.launch {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = client.queryPurchasesAsync(params)
        result.purchasesList.forEach { handlePurchase(it) }
    }

    /** 发起购买；需 Activity 上下文。商品未就绪则忽略。 */
    fun launchPurchase(activity: Activity) {
        val pd = productDetails ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (PRODUCT_PREMIUM !in purchase.products) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        _state.value = _state.value.copy(owned = true)
        onPremiumOwned()
        if (!purchase.isAcknowledged) {
            scope.launch {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build(),
                )
            }
        }
    }

    companion object {
        /** Play Console 内购商品 ID（占位，待在 Console 创建同名商品）。 */
        const val PRODUCT_PREMIUM = "premium_unlock"
    }
}
