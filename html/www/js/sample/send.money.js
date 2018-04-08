var loader = require("./loader");
var config = loader.config;

loader.load(function(MRS) {
    var data = {
        recipient: MRS.getAccountIdFromPublicKey(config.recipientPublicKey), // public key to account id
        recipientPublicKey: config.recipientPublicKey, // Optional - public key announcement to init a new account
        amountMQT: MRS.convertToMQT("1.234"), // MTR to MQT conversion
        secretPhrase: config.secretPhrase,
        encryptedMessageIsPrunable: "true" // Optional - make the attached message prunable
    };
    // Compose the request data
    data = Object.assign(
        data,
        MRS.getMandatoryParams(),
        MRS.encryptMessage(MRS, "note to myself", config.secretPhrase, MRS.getPublicKey(converters.stringToHexString(config.secretPhrase)), true),
        MRS.encryptMessage(MRS, "message to recipient", config.secretPhrase, config.recipientPublicKey, false)
    );
    // Submit the request to the remote node using the standard client function which performs local signing for transactions
    // and validates the data returned from the server.
    // This method will only send the passphrase to the server in requests for which the passphrase is required like startForging
    // It will never submit the passphrase for transaction requests
    MRS.sendRequest("sendMoney", data, function (response) {
        MRS.logConsole(JSON.stringify(response));
    });
});
