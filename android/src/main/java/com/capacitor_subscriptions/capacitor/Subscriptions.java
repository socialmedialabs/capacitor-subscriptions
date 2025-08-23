package com.capacitor_subscriptions.capacitor;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

// Google Play Billing imports
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

// Capacitor imports
import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.PluginCall;

// Java utilities
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Subscriptions {

    private final Activity activity;
    public Context context;
    private final BillingClient billingClient;
    private int billingClientIsConnected = 0;

    private String apiEndpoint = "";
    private String jwt = "";

    public Subscriptions(SubscriptionsPlugin plugin, BillingClient billingClient) {
        Logger.info("Subscriptions:init start");
        this.billingClient = billingClient;
        this.billingClient.startConnection(
                new BillingClientStateListener() {
                    @Override
                    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            billingClientIsConnected = 1;
                            Logger.info("Subscriptions:Billing setup finished OK");
                        } else {
                            billingClientIsConnected = billingResult.getResponseCode();
                            Logger.error("Subscriptions:Billing setup failed code=" + billingResult.getResponseCode() + " msg=" + billingResult.getDebugMessage());
                        }
                    }

                    @Override
                    public void onBillingServiceDisconnected() {
                        // Try to restart the connection on the next request to
                        // Google Play by calling the startConnection() method.
                        Logger.warn("Subscriptions:Billing service disconnected");
                    }
                }
            );
        this.activity = plugin.getActivity();
        this.context = plugin.getContext();
        Logger.info("Subscriptions:init done");
    }

    public String echo(String value) {
        Log.i("Echo", value);
        return value;
    }

    public void setApiVerificationDetails(String apiEndpoint, String jwt, String productId) {
        this.apiEndpoint = apiEndpoint;
        this.jwt = jwt;

        Log.i("SET-VERIFY", "Verification values updated");
    }

    public void getProductDetails(String productIdentifier, PluginCall call) {
        JSObject response = new JSObject();

        if (billingClientIsConnected == 1) {
            QueryProductDetailsParams.Product productToFind = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productIdentifier)
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

            QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(productToFind))
                .build();

            billingClient.queryProductDetailsAsync(queryProductDetailsParams, (billingResult, productDetailsResult) -> {
                try {
                    List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                    ProductDetails productDetails = productDetailsList.get(0);
                    String productId = productDetails.getProductId();
                    String title = productDetails.getTitle();
                    String desc = productDetails.getDescription();
                    Log.i("productIdentifier", productId);
                    Log.i("displayName", title);
                    Log.i("desc", desc);

                    List<ProductDetails.SubscriptionOfferDetails> subscriptionOfferDetails = productDetails.getSubscriptionOfferDetails();

                    String price = null;
                    if (subscriptionOfferDetails != null && !subscriptionOfferDetails.isEmpty()) {
                        ProductDetails.SubscriptionOfferDetails firstOffer = subscriptionOfferDetails.get(0);
                        if (firstOffer.getPricingPhases() != null && firstOffer.getPricingPhases().getPricingPhaseList() != null && !firstOffer.getPricingPhases().getPricingPhaseList().isEmpty()) {
                            price = firstOffer.getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
                        }
                    }

                    JSObject data = new JSObject();
                    data.put("productIdentifier", productId);
                    data.put("displayName", title);
                    data.put("description", desc);
                    data.put("price", price);

                    response.put("responseCode", 0);
                    response.put("responseMessage", "Successfully found the product details for given productIdentifier");
                    response.put("data", data);
                } catch (Exception e) {
                    Log.e("Err", e.toString());
                    response.put("responseCode", 1);
                    response.put("responseMessage", "Could not find a product matching the given productIdentifier");
                }

                call.resolve(response);
            });
        } else if (billingClientIsConnected == 2) {
            response.put("responseCode", 2);
            response.put("responseMessage", "Android: BillingClient failed to initialise");
            call.resolve(response);
        } else {
            response.put("responseCode", billingClientIsConnected);
            response.put("responseMessage", "Android: BillingClient is still initialising");
            call.resolve(response);
        }
    }

    public void getLatestTransaction(String productIdentifier, PluginCall call) {
        JSObject response = new JSObject();

        if (billingClientIsConnected == 1) {
            QueryPurchasesParams queryPurchasesParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

            billingClient.queryPurchasesAsync(queryPurchasesParams, (billingResult, purchases) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getProducts().contains(productIdentifier)) {
                            JSObject data = new JSObject();
                            try {
                                data.put("transaction", new JSObject(purchase.getOriginalJson()));
                                data.put("productIdentifier", purchase.getProducts().get(0));
                                data.put("transactionId", purchase.getOrderId());
                                data.put("purchaseToken", purchase.getPurchaseToken());

                                response.put("responseCode", 0);
                                response.put("responseMessage", "Successfully found transaction");
                                response.put("data", data);
                                call.resolve(response);
                                return;
                            } catch (Exception e) {
                                Log.e("Transaction", "Error parsing purchase data: " + e.getMessage());
                            }
                        }
                    }
                }

                response.put("responseCode", 3);
                response.put("responseMessage", "No transaction found");
                call.resolve(response);
            });
        } else {
            response.put("responseCode", billingClientIsConnected);
            response.put("responseMessage", "BillingClient not connected");
            call.resolve(response);
        }
    }

    public void getCurrentEntitlements(PluginCall call) {
        JSObject response = new JSObject();

        if (billingClientIsConnected == 1) {
            QueryPurchasesParams queryPurchasesParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

            billingClient.queryPurchasesAsync(queryPurchasesParams, (billingResult, purchaseList) -> {
                try {
                    int amountOfPurchases = purchaseList.size();

                    if (amountOfPurchases > 0) {
                        ArrayList<JSObject> entitlements = new ArrayList<JSObject>();
                        for (int i = 0; i < purchaseList.size(); i++) {
                            Purchase currentPurchase = purchaseList.get(i);

                            String orderId = currentPurchase.getOrderId();
                            String expiryDate = this.getExpiryDateFromApi(orderId);

                            String dateFormat = "dd-MM-yyyy hh:mm";
                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.getDefault());
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTimeInMillis(Long.parseLong((String.valueOf(currentPurchase.getPurchaseTime()))));

                            entitlements.add(
                                new JSObject()
                                    .put("productIdentifier", currentPurchase.getProducts().get(0))
                                    .put("expiryDate", expiryDate)
                                    .put("originalStartDate", simpleDateFormat.format(calendar.getTime()))
                                    .put("originalId", orderId)
                                    .put("transactionId", orderId)
                                    .put("purchaseToken", currentPurchase.getPurchaseToken())
                            );
                        }

                        response.put("responseCode", 0);
                        response.put("responseMessage", "Successfully found all entitlements across all product types");
                        response.put("data", entitlements);
                    } else {
                        Log.i("No Purchases", "No active subscriptions found");
                        response.put("responseCode", 1);
                        response.put("responseMessage", "No entitlements were found");
                    }

                    call.resolve(response);
                } catch (Exception e) {
                    Log.e("Error", e.toString());
                    response.put("responseCode", 2);
                    response.put("responseMessage", e.toString());
                    call.resolve(response);
                }
            });
        }
    }

    public void purchaseProduct(String productIdentifier, String accountId, PluginCall call) {
        JSObject response = new JSObject();

        if (billingClientIsConnected == 1) {
            QueryProductDetailsParams.Product productToFind = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productIdentifier)
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

            QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(productToFind))
                .build();

            billingClient.queryProductDetailsAsync(queryProductDetailsParams, (billingResult1, productDetailsResult) -> {
                try {
                    List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                    ProductDetails productDetails = productDetailsList.get(0);
                    List<ProductDetails.SubscriptionOfferDetails> offerDetails = productDetails.getSubscriptionOfferDetails();
                    if (offerDetails == null || offerDetails.isEmpty()) {
                        throw new IllegalStateException("No offers available for this subscription");
                    }

                    BillingFlowParams.ProductDetailsParams productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerDetails.get(0).getOfferToken())
                        .build();

                    BillingFlowParams.Builder builder = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(Collections.singletonList(productDetailsParams));
                    if (accountId != null) {
                        builder.setObfuscatedAccountId(accountId);
                    }
                    BillingFlowParams billingFlowParams = builder.build();
                    BillingResult result = billingClient.launchBillingFlow(this.activity, billingFlowParams);

                    Log.i("RESULT", result.toString());
                    response.put("responseCode", 0);
                    response.put("responseMessage", "Successfully opened native popover");
                } catch (Exception e) {
                    Logger.error(e.getMessage());
                    response.put("responseCode", 1);
                    response.put("responseMessage", "Failed to open native popover");
                }

                call.resolve(response);
            });
        }
    }

    private String getExpiryDateFromApi(String transactionId) {
        try {
            // Compile request to verify purchase token
            URI uri = new URI(this.apiEndpoint);
            URL obj = uri.toURL();
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setRequestProperty("Authorization", "Bearer " + this.jwt);
            con.setDoOutput(true);

            // JSON-Body erzeugen
            String body = "{\"transaction_id\": \"" + transactionId + "\"}";

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Try to receive response from server
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder googleResponse = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    googleResponse.append(responseLine.trim());
                    Log.i("Response Line", responseLine);
                }

                // If the response was successful, extract expiryDate and put it in our response data property
                if (con.getResponseCode() == 200) {
                    JSObject postResponseJSON = new JSObject(googleResponse.toString());

                    String dateFormat = "yyyy-MM-dd HH:mm:ss";
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.getDefault());

                    // Direkt den gewünschten Schlüssel auslesen
                    String expiryString = postResponseJSON.getString("expiryDate");
                    Date date = simpleDateFormat.parse(expiryString);

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);

                    Log.i("EXPIRY", simpleDateFormat.format(calendar.getTime()));
                    return simpleDateFormat.format(calendar.getTime());
                } else {
                    return null;
                }
            } catch (Exception e) {
                Logger.error(e.getMessage());
            }
        } catch (Exception e) {
            Logger.error(e.getMessage());
        }

        // If the method manages to each this far before already returning, just return null
        // because something went wrong
        return null;
    }
}
