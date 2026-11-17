package n7.glyph.ui.support

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Supporter", the one purchase in every n7 app: a Google Play in-app product [PRODUCT_ID] (one-time, not consumed),
 * a cup of coffee for the developer. It never locks the game: the player gets the thank-you on the matrix
 * ([SupporterToy]) and one small extra the app chooses.
 *
 * The purchase is remembered in the app's preferences, so the toy service (no billing there) reads it with
 * [Supporter.state], and is checked against Play on every [connect]: a refund takes the extra back.
 */
public class Supporter(context: Context) {

    public companion object {
        public const val PRODUCT_ID: String = "supporter"
        private const val TAG = "Supporter"
        private const val PREFS = "n7_supporter"
        private const val KEY_OWNED = "owned"
        private const val KEY_THANKED = "thanked"

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Whether this player bought Supporter, as last confirmed by Play. Cheap: one preference. */
        public fun owned(context: Context): Boolean = prefs(context).getBoolean(KEY_OWNED, false)

        /** The purchase as [SupporterToy] sees it. */
        public fun state(context: Context): SupporterState = object : SupporterState {
            private val p = prefs(context)
            override val owned: Boolean get() = p.getBoolean(KEY_OWNED, false)
            override var thanked: Boolean
                get() = p.getBoolean(KEY_THANKED, false)
                set(value) { p.edit().putBoolean(KEY_THANKED, value).apply() }
        }
    }

    private val prefs = prefs(context)

    private val _price = MutableStateFlow<String?>(null)

    /** Localised price from Play, or null while unknown (the product is not set up yet, offline). */
    public val price: StateFlow<String?> = _price

    private val _owned = MutableStateFlow(prefs.getBoolean(KEY_OWNED, false))
    public val owned: StateFlow<Boolean> = _owned

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.forEach(::handle)
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Connects to Play and refreshes the price and the purchase. Call from onResume. */
    public fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else Log.w(TAG, "Billing setup: ${result.debugMessage}")
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Call from onDestroy. */
    public fun disconnect() {
        client.endConnection()
    }

    /** Opens the Google Play purchase sheet. Nothing happens while the price is unknown. */
    public fun buy(activity: Activity) {
        val product = details ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun refresh() {
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(query) { _, result ->
            details = result.productDetailsList.firstOrNull()
            _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val bought = purchases.any { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            save(bought)
            purchases.forEach(::handle)
        }
    }

    private fun handle(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        save(true)
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { r ->
                Log.d(TAG, "Acknowledge: ${r.responseCode}")
            }
        }
    }

    private fun save(bought: Boolean) {
        val e = prefs.edit().putBoolean(KEY_OWNED, bought)
        // A refund and a new purchase later earn a new thank-you.
        if (!bought) e.putBoolean(KEY_THANKED, false)
        e.apply()
        _owned.value = bought
    }
}
