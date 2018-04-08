var loader = require("./loader");
var config = loader.config;

loader.load(function(MRS) {
    var data = {
        recipient: MRS.getAccountIdFromPublicKey(config.recipientPublicKey),
        secretPhrase: config.secretPhrase,
        encryptedMessageIsPrunable: "true"
    };
    data = Object.assign(
        data,
        MRS.getMandatoryParams(),
        MRS.encryptMessage(MRS, "message to recipient", config.secretPhrase, config.recipientPublicKey, false)
    );
    MRS.sendRequest("sendMessage", data, function (response) {
        MRS.logConsole("sendMessage1 response:" + JSON.stringify(response));
        // Now send a response message
        var senderSecretPhrase = "rshw9abtpsa2";
        loader.setCurrentAccount(senderSecretPhrase); // change the account which submits the transactions
        var data = {
            recipient: MRS.getAccountId(config.secretPhrase),
            secretPhrase: senderSecretPhrase,
            encryptedMessageIsPrunable: "true"
        };
        data = Object.assign(
            data,
            MRS.getMandatoryParams(),
            MRS.encryptMessage(MRS, "response message", senderSecretPhrase, MRS.getPublicKey(converters.stringToHexString(config.secretPhrase), false), false)
        );
        MRS.sendRequest("sendMessage", data, function (response) {
            MRS.logConsole("sendMessage2 response:" + JSON.stringify(response));
        });
    });
});