import Capacitor
import Foundation
import StoreKit

/// Please read the Capacitor iOS Plugin Development Guide
/// here: https://capacitorjs.com/docs/plugins/ios
@objc(SubscriptionsPlugin)
public class SubscriptionsPlugin: CAPPlugin {

  // Allows us to execute the actual code from Subscriptions.swift file
  private let implementation = Subscriptions()

  @objc func echo(_ call: CAPPluginCall) {
    let value = call.getString("value") ?? ""

    call.resolve([
      "value": value
    ])
  }

  @available(iOS 15.0.0, *)
  @objc func getProductDetails(_ call: CAPPluginCall) {
    guard let productIdentifier = call.getString("productIdentifier") else {
      call.reject("Must provide a productID")
      return
    }

    Task {
      do {
        let response = await implementation.getProductDetails(productIdentifier)
        call.resolve(response)
      }
    }
  }

  @available(iOS 15.0.0, *)
  @objc func purchaseProduct(_ call: CAPPluginCall) {
    guard let productIdentifier = call.getString("productIdentifier") else {
      call.reject("Must provide a productID")
      return
    }
    let accountId = call.getString("accountId")

    Task {
      do {
        let response = await implementation.purchaseProduct(productIdentifier, accountId)
        call.resolve(response)
      }
    }
  }

  @available(iOS 15.0.0, *)
  @objc func getCurrentEntitlements(_ call: CAPPluginCall) {
    let sync = call.getBool("sync") ?? false
    Task {
      do {
        let response = await implementation.getCurrentEntitlements(sync: sync)
        call.resolve(response)
      }
    }
  }

  @available(iOS 15.0.0, *)
  @objc func getLatestTransaction(_ call: CAPPluginCall) {
    guard let productIdentifier = call.getString("productIdentifier") else {
      call.reject("Must provide a productID")
      return
    }

    Task {
      do {
        let response = await implementation.getLatestTransaction(productIdentifier)
        call.resolve(response)
      }
    }
  }

  @objc func manageSubscriptions(_ call: CAPPluginCall) {
    Task {
      do {
        try await implementation.manageSubscriptions()
        call.resolve(["Success": "Opened"])
      } catch {
        call.reject("Error showing Manage Subscriptions: \(error.localizedDescription)")
      }
    }
  }

}
