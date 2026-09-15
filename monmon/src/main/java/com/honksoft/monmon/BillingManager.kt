import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams

class BillingManager(private val context: Context) : PurchasesUpdatedListener {
  private var billingClient: BillingClient = BillingClient.newBuilder(context)
    .setListener(this)
    .enablePendingPurchases(
      PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()
    )
    .build()

  init {
    startConnection()
  }

  private fun startConnection() {
    billingClient.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
          // Connected
        }
      }
      override fun onBillingServiceDisconnected() {
        // Disconnected
      }
    })
  } // <-- Make sure this closing brace ends startConnection()

  fun launchTipFlow(activity: Activity, productId: String) {
    val productList = listOf(
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(productId)
        .setProductType(BillingClient.ProductType.INAPP)
        .build()
    )

    val params = QueryProductDetailsParams.newBuilder()
      .setProductList(productList)
      .build()

    billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
      val productDetailsList = queryProductDetailsResult.productDetailsList
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
        val productDetails = productDetailsList[0] // Safely grab the first item

        val flowParams = BillingFlowParams.newBuilder()
          .setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
              .setProductDetails(productDetails)
              .build()
          ))
          .build()

        billingClient.launchBillingFlow(activity, flowParams)
      }
    }
  } // <-- Make sure this closing brace ends launchTipFlow()

  // Move this completely outside of any other functions:
  override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
      for (purchase in purchases) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
          handleConsumablePurchase(purchase)
        }
      }
    }
  }

  private fun handleConsumablePurchase(purchase: Purchase) {
    val consumeParams = ConsumeParams.newBuilder()
      .setPurchaseToken(purchase.purchaseToken)
      .build()

    billingClient.consumeAsync(consumeParams) { billingResult, _ ->
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        // Tip successfully processed!
      }
    }
  }
}
