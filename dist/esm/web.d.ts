import { WebPlugin, PluginListenerHandle } from "@capacitor/core";
import type { SubscriptionsPlugin, ProductDetailsResponse, PurchaseProductResponse, CurrentEntitlementsResponse, LatestTransactionResponse, AndroidPurchasedTrigger, RefundLatestTransactionResponse } from './definitions';
export declare class SubscriptionsWeb extends WebPlugin implements SubscriptionsPlugin {
    protected listeners: {
        [eventName: string]: ((response: any) => void)[];
    };
    private removeEventListener;
    echo(options: {
        value: string;
    }): Promise<{
        value: string;
    }>;
    getProductDetails(options: {
        productIdentifier: string;
    }): Promise<ProductDetailsResponse>;
    purchaseProduct(options: {
        productIdentifier: string;
        accountId?: string;
        acknowledgePurchases?: boolean;
    }): Promise<PurchaseProductResponse>;
    getCurrentEntitlements(options: {
        sync: boolean;
    }): Promise<CurrentEntitlementsResponse>;
    getLatestTransaction(options: {
        productIdentifier: string;
    }): Promise<LatestTransactionResponse>;
    refundLatestTransaction(options: {
        productIdentifier: string;
    }): Promise<RefundLatestTransactionResponse>;
    manageSubscriptions(): void;
    setApiVerificationDetails(options: {
        apiEndpoint: string;
        jwt: string;
        productId: string;
    }): Promise<void>;
    addListener(eventName: 'ANDROID-PURCHASE-RESPONSE', listenerFunc: (response: AndroidPurchasedTrigger) => void): Promise<PluginListenerHandle>;
}
