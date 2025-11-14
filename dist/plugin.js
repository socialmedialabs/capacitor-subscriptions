var capacitorSubscriptions = (function (exports, core) {
    'use strict';

    const Subscriptions = core.registerPlugin('Subscriptions', {
        web: () => Promise.resolve().then(function () { return web; }).then(m => new m.SubscriptionsWeb()),
    });

    class SubscriptionsWeb extends core.WebPlugin {
        constructor() {
            super(...arguments);
            this.listeners = {};
        }
        removeEventListener(eventName, listenerFunc) {
            if (!this.listeners[eventName])
                return;
            const index = this.listeners[eventName].indexOf(listenerFunc);
            if (index > -1) {
                this.listeners[eventName].splice(index, 1);
            }
        }
        async echo(options) {
            console.log('ECHO', options);
            return options;
        }
        async getProductDetails(options) {
            console.log('getProductDetails', options);
            return {
                responseCode: -1,
                responseMessage: 'Incompatible with web',
            };
        }
        async purchaseProduct(options) {
            console.log('purchaseProduct', options);
            return {
                responseCode: -1,
                responseMessage: 'Incompatible with web',
            };
        }
        async getCurrentEntitlements(options) {
            console.log('getCurrentEntitlements');
            return {
                responseCode: -1,
                responseMessage: 'Incompatible with web',
                data: []
            };
        }
        async getLatestTransaction(options) {
            console.log('getLatestTransaction', options);
            return {
                responseCode: -1,
                responseMessage: 'Incompatible with web',
            };
        }
        async refundLatestTransaction(options) {
            return {
                responseCode: -1,
                responseMessage: 'Incompatible with web',
            };
        }
        manageSubscriptions() {
            console.log('manageSubscriptions');
        }
        async setApiVerificationDetails(options) {
            console.log('setApiVerificationDetails', options);
        }
        addListener(eventName, listenerFunc) {
            if (!this.listeners[eventName]) {
                this.listeners[eventName] = [];
            }
            this.listeners[eventName].push(listenerFunc);
            const handle = {
                remove: () => {
                    this.removeEventListener(eventName, listenerFunc);
                    return Promise.resolve();
                }
            };
            return Promise.resolve(handle);
        }
    }

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        SubscriptionsWeb: SubscriptionsWeb
    });

    exports.Subscriptions = Subscriptions;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
