import { WebPlugin, PluginListenerHandle } from "@capacitor/core";
import type {
  SubscriptionsPlugin,
  ProductDetailsResponse,
  PurchaseProductResponse,
  CurrentEntitlementsResponse,
  LatestTransactionResponse,
  AndroidPurchasedTrigger,
  RefundLatestTransactionResponse
} from './definitions';

export class SubscriptionsWeb extends WebPlugin implements SubscriptionsPlugin {
  protected listeners: { [eventName: string]: ((response: any) => void)[] } = {};

  private removeEventListener(eventName: string, listenerFunc: (response: any) => void): void {
    if (!this.listeners[eventName]) return;

    const index = this.listeners[eventName].indexOf(listenerFunc);
    if (index > -1) {
      this.listeners[eventName].splice(index, 1);
    }
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async getProductDetails(options: { productIdentifier: string }): Promise<ProductDetailsResponse> {
    console.log('getProductDetails', options);
    return {
      responseCode: -1,
      responseMessage: 'Incompatible with web',
    };
  }

  async purchaseProduct(options: { productIdentifier: string, accountId?: string, acknowledgePurchases?: boolean }): Promise< PurchaseProductResponse > {
    console.log('purchaseProduct', options);
    return {
      responseCode: -1,
      responseMessage: 'Incompatible with web',
    };
  }

  async getCurrentEntitlements(options: { sync: boolean }): Promise< CurrentEntitlementsResponse > {
    options;
    console.log('getCurrentEntitlements');
    return {
      responseCode: -1,
      responseMessage: 'Incompatible with web',
      data: []
    };
  }

  async getLatestTransaction(options: { productIdentifier: string }): Promise<LatestTransactionResponse> {
    console.log('getLatestTransaction', options);
    return {
      responseCode: -1,
      responseMessage: 'Incompatible with web',
    };
  }

  async refundLatestTransaction(options: {productIdentifier: string}): Promise< RefundLatestTransactionResponse > {
    options;
    return {
      responseCode: -1,
      responseMessage: 'Incompatible with web',
    }
  }

  manageSubscriptions(): void {
    console.log('manageSubscriptions');
  }

  async setApiVerificationDetails(options: { apiEndpoint: string; jwt: string; productId: string }): Promise<void> {
    console.log('setApiVerificationDetails', options);
  }

  addListener(eventName: 'ANDROID-PURCHASE-RESPONSE', listenerFunc: (response: AndroidPurchasedTrigger) => void): Promise<PluginListenerHandle> {
    if (!this.listeners[eventName]) {
      this.listeners[eventName] = [];
    }
    this.listeners[eventName].push(listenerFunc);

    const handle: PluginListenerHandle = {
      remove: () => {
        this.removeEventListener(eventName, listenerFunc);
        return Promise.resolve();
      }
    };

    return Promise.resolve(handle);
  }
}