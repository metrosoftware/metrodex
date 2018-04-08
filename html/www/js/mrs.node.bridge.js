const options = {
    url: "http://localhost:6876", // URL of Metro remote node
    secretPhrase: "", // Secret phrase of the current account
    isTestNet: false, // Select testnet or mainnet
    adminPassword: "" // Node admin password
};

exports.init = function(params) {
    if (!params) {
        return this;
    }
    options.url = params.url;
    options.secretPhrase = params.secretPhrase;
    options.isTestNet = params.isTestNet;
    options.adminPassword = params.adminPassword;
    return this;
};

exports.load = function(callback) {
    // jsdom is necessary to define the window object on which jquery relies
    require("jsdom").env("", function(err, window) {
        try {
            if (err) {
                console.error(err);
                return;
            }
            console.log("Started");

            // Load the necessary node modules and assign them to the global scope
            // the Metro client wasn't designed with modularity in mind therefore we need
            // to include every 3rd party library function in the global scope
            global.jQuery = require("jquery")(window);
            jQuery.growl = function(msg) { console.log("growl: " + msg)}; // disable growl messages
            jQuery.t = function(text) { return text; }; // Disable the translation functionality
            global.$ = global.jQuery; // Needed by extensions.js
            global.crypto = require("crypto");
            global.CryptoJS = require("crypto-js");
            global.async = require("async");
            global.pako = require("pako");
            var jsbn = require('jsbn');
            global.BigInteger = jsbn.BigInteger;
            global.window = window;
            window.console = console;

            // Mock other objects on which the client depends
            global.document = {};
            global.isNode = true; // for code which has to execute differently by node compared to browser
            global.navigator = {};
            navigator.userAgent = "";

            // Now load some Metro specific libraries into the global scope
            global.MetroAddress = require('./util/metroaddress');
            global.curve25519 = require('./crypto/curve25519');
            global.curve25519_ = require('./crypto/curve25519_');
            require('./util/extensions');
            global.converters = require('./util/converters');

            // Now start loading the client itself
            // The main challenge is that in node every JavaScript file is a module with it's own scope
            // however the Metro client relies on a global browser scope which defines the MRS object
            // The idea here is to gradually compose the MRS object by adding functions from each
            // JavaScript file into the existing global.client scope
            global.client = {};
            global.client.isTestNet = options.isTestNet;
            global.client = Object.assign(client, require('./mrs.encryption'));
            global.client = Object.assign(client, require('./mrs.feature.detection'));
            global.client = Object.assign(client, require('./mrs.transactions.types'));
            global.client = Object.assign(client, require('./mrs.constants'));
            global.client = Object.assign(client, require('./mrs.console'));
            global.client = Object.assign(client, require('./mrs.util'));
            client.getModuleConfig = function() {
                return options;
            };
            global.client = Object.assign(client, require('./mrs'));
            global.client = Object.assign(client, require('./mrs.server'));
            setCurrentAccount(options.secretPhrase);

            // Now load the constants locally since we cannot trust the remote node to
            // return the correct constants.
            var constants = require('./data/constants');
            global.client.processConstants(constants);
            callback(global.client);
        } catch (e) {
            console.log(e.message);
            console.log(e.stack);
            throw e;
        }
    });
};

function setCurrentAccount(secretPhrase) {
    client.account = client.getAccountId(secretPhrase);
    client.accountRS = converters.convertNumericToRSAccountFormat(client.account);
    client.accountInfo = {}; // Do not cache the public key
    client.resetEncryptionState();
}
exports.setCurrentAccount = setCurrentAccount;