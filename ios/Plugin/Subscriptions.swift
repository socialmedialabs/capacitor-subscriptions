import Capacitor
import Foundation
import StoreKit
import UIKit

@objc public class Subscriptions: NSObject {

  override init() {
    super.init()
    if #available(iOS 15.0.0, *) {
      let transactionListener = listenForTransactions()
      let unfinishedListener = finishTransactions()
    } else {
      // Fallback on earlier versions
    }
  }

  // When the subscription renews at the end of the month, a transaction will
  // be queued for when the app is next opened. This listener handles any transactions
  // within the queue and finishes verified purchases to clear the queue and prevent
  // any bugs or performance issues occuring
  @available(iOS 15.0.0, *)
  private func listenForTransactions() -> Task<Void, Error> {
    return Task.detached {

      //Iterate through any transactions that don't come from a direct call to `purchase()`.
      for await verification in Transaction.updates {

        guard
          let transaction: Transaction = self.checkVerified(verification)
            as? Transaction
        else {
          print("checkVerified failed")
          return

        }

        await transaction.finish()
        print("Transaction finished and removed from paymentQueue - Transactions.updates")
      }

    }
  }

  @available(iOS 15.0.0, *)
  private func finishTransactions() -> Task<Void, Error> {
    return Task.detached {

      //Iterate through any transactions that don't come from a direct call to `purchase()`.
      for await verification in Transaction.unfinished {

        guard
          let transaction: Transaction = self.checkVerified(verification)
            as? Transaction
        else {
          print("checkVerified failed")
          return

        }

        await transaction.finish()
        print("Transaction finished and removed from paymentQueue - transactions.unfinished")
      }
    }
  }

  @available(iOS 15.0.0, *)
  @objc public func getProductDetails(_ productIdentifier: String) async -> PluginCallResultData {

    guard let product: Product = await getProduct(productIdentifier) as? Product else {
      return [
        "responseCode": 1,
        "responseMessage": "Could not find a product matching the given productIdentifier",
      ]
    }

    let displayName = product.displayName
    let description = product.description
    let price = product.displayPrice

    return [
      "responseCode": 0,
      "responseMessage": "Successfully found the product details for given productIdentifier",
      "data": [
        "productIdentifier": productIdentifier,
        "displayName": displayName,
        "description": description,
        "price": price,
      ],
    ]
  }

  @available(iOS 15.0.0, *)
  @objc public func purchaseProduct(_ productIdentifier: String, _ accountId: String?) async
    -> PluginCallResultData
  {

    do {
      guard let product: Product = await getProduct(productIdentifier) as? Product else {
        return [
          "successful": false,
          "message": "Could not find a product matching the given productIdentifier",
        ]
      }

      var purchaseOptions = Set<Product.PurchaseOption>()
      if let accountId = accountId,
        let accountUUID = UUID(uuidString: accountId)
      {
        let appAccountId = Product.PurchaseOption.appAccountToken(accountUUID)
        purchaseOptions.insert(appAccountId)
      }

      let result: Product.PurchaseResult = try await product.purchase(options: purchaseOptions)

      switch result {
      case .success(let verification):
        // Versuchen, die Transaction zu verifizieren
        guard let transaction = checkVerified(verification) as? Transaction else {
          return [
            "successful": false,
            "message": "Product purchased but transaction failed verification",
          ]
        }

        // Kauf abschließen (finish)
        await transaction.finish()

        // Wir bauen nun ein Detail-Objekt, analog zum Android-Ansatz:
        // transaction.id, transaction.originalID, transaction.productID, etc.
        // iOS hat kein "orderId" wie Google, daher nimm `transactionId` oder `originalId`.
        var purchaseToken = ""
        var receiptString = ""
        if let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
          FileManager.default.fileExists(atPath: appStoreReceiptURL.path)
        {
          do {
            let receiptData = try Data(contentsOf: appStoreReceiptURL, options: .alwaysMapped)
            purchaseToken = receiptData.base64EncodedString()
            receiptString = receiptData.base64EncodedString(options: [
              Data.Base64EncodingOptions.endLineWithCarriageReturn
            ])
          } catch {
            // Receipt nicht lesbar
          }
        }

        // Kaufdatum formatieren (optional)
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        let purchaseDateStr = dateFormatter.string(from: transaction.originalPurchaseDate)

        // EXPIRATION (falls vorhanden)
        var expiryDateStr = ""
        if let expirationDate = transaction.expirationDate {
          expiryDateStr = dateFormatter.string(from: expirationDate)
        }

        // Daten zurückgeben
        return [
          "successful": true,
          "message": "Purchase successful",
          "transactionId": String(transaction.id),  // Achtung: transaction.id ist eine UInt64
          "originalId": transaction.originalID,  // die "höhere" ID
          "productId": transaction.productID,
          "purchaseDate": purchaseDateStr,
          "purchaseToken": purchaseToken,
          "expiryDate": expiryDateStr,
          "receipt": receiptString
        ]

      case .userCancelled:
        return [
          "successful": false,
          "message": "User closed the native popover before purchasing",
        ]

      case .pending:
        return [
          "successful": false,
          "message": "Product purchase is pending (e.g. parental controls)",
        ]

      default:
        return [
          "successful": false,
          "message": "Unknown error occurred in the purchasing process",
        ]
      }

    } catch {
      print(error.localizedDescription)
      return [
        "successful": false,
        "message": "An unknown error occurred whilst in the purchasing process",
      ]
    }
  }

  @available(iOS 15.0.0, *)
  @objc public func getCurrentEntitlements(sync: Bool) async -> PluginCallResultData {

    do {

      if sync {
        do {
          try await AppStore.sync()
        } catch {
          // Fehlerbehandlung
          print(error.localizedDescription)
        }
      }

      var transactions: [Any] = []

      //            Loop through each verification result in currentEntitlements, verify the transaction
      //            then add it to the transactionDictionary if verified.
      for await verification in Transaction.currentEntitlements {

        let transaction: Transaction? = checkVerified(verification) as? Transaction
        if transaction != nil {

          transactions.append(
            [
              "productIdentifier": transaction!.productID,
              "originalStartDate": transaction!.originalPurchaseDate,
              "originalId": transaction!.originalID,
              "transactionId": transaction!.id,
              "expiryDate": transaction!.expirationDate,
            ]
          )

        }

      }

      //            If we have one or more entitlements in transactionDictionary
      //            we want the response to include it in the data property
      if transactions.count > 0 {

        let response =
          [
            "responseCode": 0,
            "responseMessage": "Successfully found all entitlements across all product types",
            "data": transactions,
          ] as [String: Any]

        return response

        //             Otherwise - no entitlements were found
      } else {
        return [
          "responseCode": 1,
          "responseMessage": "No entitlements were found",
        ]
      }

    }

  }

  @available(iOS 15.0.0, *)
  @objc public func getLatestTransaction(_ productIdentifier: String) async -> PluginCallResultData
  {

    do {
      guard let product: Product = await getProduct(productIdentifier) as? Product else {
        return [
          "responseCode": 1,
          "responseMessage": "Could not find a product matching the given productIdentifier",
        ]

      }

      let latestTransaction = await product.latestTransaction
      guard let transaction: Transaction = checkVerified(latestTransaction) as? Transaction else {
        // The user hasn't purchased this product.
        return [
          "responseCode": 2,
          "responseMessage":
            "No transaction for given productIdentifier, or it could not be verified",
        ]
      }
       
      print("expiration" + String(decoding: formatDate(transaction.expirationDate)!, as: UTF8.self))
      print("transaction.expirationDate", transaction.expirationDate!)
      print("transaction.originalID", transaction.originalID)

      var receiptString = ""

      if let appStoreReceiptURL = Bundle.main.appStoreReceiptURL,
        FileManager.default.fileExists(atPath: appStoreReceiptURL.path)
      {

        do {
          let receiptData = try Data(contentsOf: appStoreReceiptURL, options: .alwaysMapped)
          print("Receipt Data: ", receiptData)

          receiptString = receiptData.base64EncodedString(options: [
            Data.Base64EncodingOptions.endLineWithCarriageReturn
          ])
          print("Receipt String: ", receiptString)

          // Read receiptData.
        } catch { print("Couldn't read receipt data with error: " + error.localizedDescription) }
      }

      return [
        "responseCode": 0,
        "responseMessage": "Latest transaction found",
        "data": [
          "productIdentifier": transaction.productID,
          "originalStartDate": transaction.originalPurchaseDate,
          "originalId": transaction.originalID,
          "transactionId": transaction.id,
          "expiryDate": transaction.expirationDate!,
          "purchaseToken": receiptString,
          "receipt": receiptString
        ],
      ]

    }

  }

  @available(iOS 15.0.0, *)
  @objc public func refundLatestTransaction(_ productIdentifier: String) async
    -> PluginCallResultData
  {

    do {
      guard let product: Product = await getProduct(productIdentifier) as? Product else {
        return [
          "responseCode": 1,
          "responseMessage": "Could not find a product matching the given productIdentifier",
        ]

      }

      let latestTransaction = await product.latestTransaction
      guard let transaction: Transaction = checkVerified(latestTransaction) as? Transaction else {
        // The user hasn't purchased this product.
        return [
          "responseCode": 2,
          "responseMessage":
            "No transaction for given productIdentifier, or it could not be verified",
        ]
      }

      guard let scene = await UIApplication.shared.connectedScenes.first as? UIWindowScene else {
        return [
          "responseCode": 4,
          "responseMessage": "Problem getting UIScene",
        ]
      }
      try await transaction.beginRefundRequest(in: scene)
    } catch {
      print("Error:" + error.localizedDescription)
      return [
        "responseCode": 3,
        "responseMessage": "Unknown problem trying to refund latest transaction",
      ]
    }
    return [
      "responseCode": 0
    ]
  }

  @available(iOS 15.0.0, *)
  @objc public func manageSubscriptions() async {

    let manageTransactions: UIWindowScene
    await UIApplication.shared.open(URL(string: "https://apps.apple.com/account/subscriptions")!)

  }

  @available(iOS 15.0.0, *)
  @objc private func formatDate(_ date: Date?) -> Data? {

    let df = DateFormatter()
    df.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    return df.string(for: date)?.data(using: String.Encoding.utf8)!

  }

  @available(iOS 15.0.0, *)
  @objc private func updateTrialDate(_ productId: String, _ formattedDate: Data?) {

    let keyChainUpdateParams: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: productId,
    ]

    let keyChainUpdateValue: [String: Any] = [
      kSecValueData as String: formattedDate!
    ]

    let updateStatusCode = SecItemUpdate(
      keyChainUpdateParams as CFDictionary, keyChainUpdateValue as CFDictionary)
    let updateStatusMessage = SecCopyErrorMessageString(updateStatusCode, nil)

    print("updateStatusCode in SecItemUpdate", updateStatusCode)
    print("updateStatusMessage in SecItemUpdate", updateStatusMessage!)

  }

  @available(iOS 15.0.0, *)
  @objc private func getProduct(_ productIdentifier: String) async -> Any? {

    do {
      let products = try await Product.products(for: [productIdentifier])
      if products.count > 0 {
        let product = products[0]
        return product
      }
      return nil
    } catch {
      return nil
    }

  }

  @available(iOS 15.0.0, *)
  @objc private func checkVerified(_ vr: Any?) -> Any? {

    switch vr as? VerificationResult<Transaction> {
    case .verified(let safe):
      return safe
    case .unverified:
      return nil
    default:
      return nil
    }

  }

}
