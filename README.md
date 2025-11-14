# capacitor-subscriptions

[![npm version](https://img.shields.io/npm/v/@socialmedialabs/capacitor-subscriptions.svg)](https://www.npmjs.com/package/@socialmedialabs/capacitor-subscriptions)
[![npm downloads](https://img.shields.io/npm/dm/@socialmedialabs/capacitor-subscriptions.svg)](https://www.npmjs.com/package/@socialmedialabs/capacitor-subscriptions)
[![license](https://img.shields.io/github/license/socialmedialabs/capacitor-subscriptions)](./LICENSE)
[![platforms](https://img.shields.io/badge/platforms-iOS%2015%2B%20%7C%20Android%2023%2B-green)](#)

StoreKit 2 and Google Play Billing v8 subscription utilities for Capacitor 7.

- ✅ Capacitor 7 compatible (`@capacitor/core` peer dependency)
- ✅ StoreKit 2 (iOS 15+) with automatic transaction finishing and `AppStore.sync` support
- ✅ Google Play Billing Library v8 with auto-acknowledge purchases and pending purchase events
- ✅ Promise-based helpers for product metadata, entitlements, and transaction history

## Installation

```bash
npm install @socialmedialabs/capacitor-subscriptions
```

Then sync native platforms:

```bash
npx cap sync
```

## Quick Start

### 1. Bootstrap the plugin

```ts
import { Capacitor } from '@capacitor/core';
import { Subscriptions } from '@socialmedialabs/capacitor-subscriptions';

export async function bootstrapSubscriptions(apiUrl: string, authToken: string) {
  const platform = Capacitor.getPlatform();

  if (platform === 'android') {
    await Subscriptions.setApiVerificationDetails({
      apiEndpoint: `${apiUrl}/subscription/expiry`,
      jwt: authToken,
      productId: 'com.example.app.pro.monthly',
    });
  }

  const entitlements = await Subscriptions.getCurrentEntitlements({
    sync: platform === 'ios',
  });

  if (entitlements.responseCode === 0 && entitlements.data?.length) {
    // User has at least one active subscription
  }
}
```

- `setApiVerificationDetails` lets the Android bridge call your backend so it can talk to the Google Play Developer API. The backend is expected to return JSON with an `expiryDate` ISO string for the provided `transaction_id`. The `productId` value is required by the current native implementation even though it is not yet used.
- Pass a JWT via the `jwt` field to secure backend communication. The Android bridge injects it as a `Bearer` token in the `Authorization` header so your server can authenticate each request.
- The `sync` flag triggers `AppStore.sync()` on iOS and is ignored on Android.

## Android

- Targets `compileSdkVersion`/`targetSdkVersion` 35 and `minSdkVersion` 23 (see `android/build.gradle`).
- Google Play Billing Library v8 is bundled. Pending purchases are enabled via `PendingPurchasesParams` and automatic service reconnection.
- Call `setApiVerificationDetails` before retrieving entitlements so expiry dates can be fetched from your backend. The JWT is forwarded as a `Bearer` token in the `Authorization` header.
- `purchaseProduct` resolves with `{ responseCode: 0, responseMessage: 'Successfully opened native popover' }` on success. Pass `acknowledgePurchases: false` if you want to acknowledge purchases yourself.
- `getCurrentEntitlements` returns dates formatted with the device locale/time zone (currently `dd-MM-yyyy hh:mm`). Normalise them in your app if you need ISO strings.
- Listen for purchase updates (including pending states):

```ts
import { Subscriptions } from '@socialmedialabs/capacitor-subscriptions';

const removeListener = await Subscriptions.addListener('ANDROID-PURCHASE-RESPONSE', payload => {
  if (payload.pending) {
    // The purchase is awaiting confirmation (e.g. pending family approval)
    return;
  }

  if (payload.successful) {
    // Refresh entitlements or trigger server-side validation
  } else {
    console.warn('Purchase failed', payload);
  }
});

// Later, e.g. on component unmount:
await removeListener.remove();
```

- Open Google Play subscription management for a specific product:

```ts
import { Subscriptions } from '@socialmedialabs/capacitor-subscriptions';

await Subscriptions.manageSubscriptions({
  productIdentifier: 'com.example.app.pro.monthly',
  packageName: 'com.example.app',
});
```

Treat the call as fire-and-forget on Android; the current implementation opens the Play Store intent without resolving the JavaScript promise.

## iOS

- Requires iOS 15+ because the plugin is built on StoreKit 2.
- The plugin finishes outstanding transactions in the background (`Transaction.updates` / `Transaction.unfinished`).
- `purchaseProduct` resolves with a StoreKit-oriented payload:

```ts
import { Subscriptions } from '@socialmedialabs/capacitor-subscriptions';

const purchase = await Subscriptions.purchaseProduct({
  productIdentifier: 'com.example.app.pro.monthly',
  accountId: userAppAccountUuid, // must be a UUID string if provided
});

if (purchase.successful) {
  // purchase.receipt contains the base64 App Store receipt
} else {
  console.warn('Purchase not completed', purchase.message);
}
```

- `getCurrentEntitlements({ sync: true })` first calls `AppStore.sync()` and returns an array of verified transactions (including ISO `expiryDate` values when available).
- `getLatestTransaction` returns the latest verified transaction for a product, including the base64 receipt.
- `manageSubscriptions()` opens the Apple subscriptions management page.
- `refundLatestTransaction` exists in the native implementation but is not exposed through the Capacitor bridge in v1.0.17, so calling it from JavaScript currently throws `UNIMPLEMENTED`.

## Web Platform

All native methods resolve with `{ responseCode: -1, responseMessage: 'Incompatible with web' }`. Use web-specific fallbacks if you ship a PWA.

## API Overview

See the generated [api-docs.md](api-docs.md) for the complete type signatures. Highlights:

| Method | Platforms | Notes |
| --- | --- | --- |
| `getProductDetails({ productIdentifier })` | iOS, Android | Returns localized `price`, `displayName`, and `description`. |
| `purchaseProduct({ productIdentifier, accountId?, acknowledgePurchases? })` | iOS, Android | iOS resolves with `{ successful, message, ... }`; Android resolves with `{ responseCode, responseMessage }`. |
| `getCurrentEntitlements({ sync? })` | iOS, Android | `sync` triggers `AppStore.sync()` on iOS; Android ignores the flag and returns currently owned Play subscriptions. |
| `getLatestTransaction({ productIdentifier })` | iOS, Android | Returns the latest verified transaction. Android includes the raw `purchase` JSON and `purchaseToken` instead of an expiry date. |
| `manageSubscriptions()` | iOS | Opens the Apple manage subscriptions page. |
| `manageSubscriptions({ productIdentifier, packageName })` | Android | Opens the Google Play subscriptions screen for the given product (fire-and-forget). |
| `setApiVerificationDetails({ apiEndpoint, jwt, productId })` | Android | Configures the backend endpoint for Play Store expiry verification (required for expiry data). |
| `addListener('ANDROID-PURCHASE-RESPONSE', listener)` | Android | Emits purchase success, failure, and pending payloads. |
| `refundLatestTransaction({ productIdentifier })` | iOS (native only) | Not exported to JavaScript in v1.0.17. |
| `echo({ value })` | iOS, Android | Development helper that echoes the provided string. |

## Usage Examples

### Retrieve localized product details

```ts
import { Subscriptions } from '@socialmedialabs/capacitor-subscriptions';

export async function loadPricing(productIdentifier: string) {
  const details = await Subscriptions.getProductDetails({ productIdentifier });

  if (details.responseCode !== 0 || !details.data) {
    throw new Error(details.responseMessage ?? 'Unknown billing error');
  }

  return {
    displayName: details.data.displayName,
    price: details.data.price,
  };
}
```

### Determine the most recent transaction expiry

```ts
import { Subscriptions } from '@socialmedialabs/capacitor-subscriptions';

export async function getSubscriptionExpiry(productIdentifier: string) {
  const latest = await Subscriptions.getLatestTransaction({ productIdentifier });

  if (latest.responseCode === 0 && latest.data?.expiryDate) {
    return new Date(latest.data.expiryDate);
  }

  // On Android combine latest.data.purchaseToken with your backend to resolve the expiry.
  return undefined;
}
```

### Trigger a purchase and refresh entitlements

```ts
import { Capacitor } from '@capacitor/core';
import { Subscriptions } from '@socialmedialabs/capacitor-subscriptions';

export async function purchaseSubscription(productIdentifier: string) {
  const result = await Subscriptions.purchaseProduct({ productIdentifier });

  if (Capacitor.getPlatform() === 'ios') {
    if (!result.successful) {
      throw new Error(result.message);
    }
  } else {
    if (result.responseCode !== 0) {
      throw new Error(result.responseMessage ?? 'Billing error');
    }
  }

  // Refresh entitlements regardless of platform
  return Subscriptions.getCurrentEntitlements({ sync: Capacitor.getPlatform() === 'ios' });
}
```

## Known Limitations

- Android expiry dates depend on your backend calling the Google Play Developer API and returning an `expiryDate` ISO string. Without it the field remains `null`.
- The TypeScript declarations now include `setApiVerificationDetails`, but `manageSubscriptions` still returns `any`; wrap it in a helper if you rely on platform-specific payloads.
- `manageSubscriptions` on Android opens the Play Store intent without resolving the JavaScript promise. Treat the call as fire-and-forget.
- `refundLatestTransaction` is not wired to the JavaScript bridge yet and therefore cannot be used from Capacitor code.
- The web implementation only returns `UNIMPLEMENTED` placeholders.

## Development

```bash
npm run build        # clean → docgen → tsc → rollup
npm run verify       # runs iOS, Android, and web build checks
npm pack             # inspect the published bundle
```

## License

MIT © socialmedialabs.de
