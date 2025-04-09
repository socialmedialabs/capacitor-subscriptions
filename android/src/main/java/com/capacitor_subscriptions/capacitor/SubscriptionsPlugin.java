package com.capacitor_subscriptions.capacitor;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
// Google Play Billing imports
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchaseState;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryPurchasesParams;
// Capacitor imports
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Collections;
// Optional imports für bessere Fehlerbehandlung
import java.util.List;

@CapacitorPlugin(name = "Subscriptions")
public class SubscriptionsPlugin extends Plugin {

    private Subscriptions implementation;

    private BillingClient billingClient;

    private Boolean acknowledgePurchases = true;

    public SubscriptionsPlugin() {}

    // This listener is fired upon completing the billing flow, it is vital to call the acknowledgePurchase
    // method on the billingClient, with the purchase token otherwise Google will automatically cancel the subscription
    // shortly after the purchase
    private final PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
        JSObject response = new JSObject();

        if (purchases != null && !purchases.isEmpty()) {
            for (Purchase currentPurchase : purchases) {
                if (
                    this.acknowledgePurchases &&
                    !currentPurchase.isAcknowledged() &&
                    billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                    currentPurchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                ) {
                    AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(currentPurchase.getPurchaseToken())
                        .build();

                    billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult1 -> {
                        Log.i("Purchase ack", currentPurchase.getOriginalJson());

                        response.put("successful", true);
                        try {
                            response.put("purchase", new JSObject(currentPurchase.getOriginalJson()));
                        } catch (Exception e) {
                            Log.e("Purchase Response", "Error parsing purchase data: " + e.getMessage());
                            response.put("purchase", currentPurchase.getPurchaseToken());
                        }

                        notifyListeners("ANDROID-PURCHASE-RESPONSE", response);
                    });
                } else if (
                    !this.acknowledgePurchases &&
                    billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                    currentPurchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                ) {
                    response.put("successful", true);
                    try {
                        response.put("purchase", new JSObject(currentPurchase.getOriginalJson()));
                    } catch (Exception e) {
                        response.put("purchase", currentPurchase.getPurchaseToken());
                    }
                    notifyListeners("ANDROID-PURCHASE-RESPONSE", response);
                }
            }
        } else {
            response.put("successful", false);
            response.put("errorCode", billingResult.getResponseCode());
            response.put("debugMessage", billingResult.getDebugMessage());
            notifyListeners("ANDROID-PURCHASE-RESPONSE", response);
        }
    };

    @Override
    public void load() {
        this.billingClient = BillingClient.newBuilder(getContext()).setListener(purchasesUpdatedListener).enablePendingPurchases().build();
        implementation = new Subscriptions(this, billingClient);
    }

    @PluginMethod
    public void setApiVerificationDetails(PluginCall call) {
        String apiEndpoint = call.getString("apiEndpoint");
        String jwt = call.getString("jwt");
        String productId = call.getString("productId");

        if (apiEndpoint != null && jwt != null && productId != null) {
            implementation.setApiVerificationDetails(apiEndpoint, jwt, productId);
            Log.i("SET-VERIFY", "Verification values updated");
            call.resolve();
        } else {
            call.reject("Missing required parameters");
        }
    }

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    @PluginMethod
    public void getProductDetails(PluginCall call) {
        String productIdentifier = call.getString("productIdentifier");

        if (productIdentifier == null) {
            call.reject("Must provide a productID");
        }

        implementation.getProductDetails(productIdentifier, call);
    }

    @PluginMethod
    public void purchaseProduct(PluginCall call) {
        String productIdentifier = call.getString("productIdentifier");
        String accountId = call.getString("accountId");

        this.acknowledgePurchases = call.getBoolean("acknowledgePurchases") != null
            ? call.getBoolean("acknowledgePurchases")
            : Boolean.TRUE;

        if (productIdentifier == null) {
            call.reject("Must provide a productID");
        }

        implementation.purchaseProduct(productIdentifier, accountId, call);
    }

    @PluginMethod
    public void getLatestTransaction(PluginCall call) {
        String productIdentifier = call.getString("productIdentifier");

        if (productIdentifier == null) {
            call.reject("Must provide a productID");
        }

        implementation.getLatestTransaction(productIdentifier, call);
    }

    @PluginMethod
    public void getCurrentEntitlements(PluginCall call) {
        implementation.getCurrentEntitlements(call);
    }

    @PluginMethod
    public void manageSubscriptions(PluginCall call) {
        String productIdentifier = call.getString("productIdentifier");
        String jwt = call.getString("jwt");

        if (productIdentifier == null) {
            call.reject("Must provide a productID");
        }

        if (jwt == null) {
            call.reject("Must provide a bundleID");
        }

        Intent browserIntent = new Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions?sku=" + productIdentifier + "&package=" + jwt)
        );
        getActivity().startActivity(browserIntent);
    }
}
