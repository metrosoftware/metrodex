var loader = require("./loader");
var config = loader.config;

loader.load(function(MRS) {
    const decimals = 2;
    var quantity = 2.5;
    var price = 1.3;
    var data = {
        asset: "6094526212840718212", // testnet Megasset
        quantityQNT: MRS.convertToQNT(quantity, decimals),
        priceMQT: MRS.calculatePricePerWholeQNT(MRS.convertToMQT(price), decimals),
        secretPhrase: config.secretPhrase
    };
    data = Object.assign(
        data,
        MRS.getMandatoryParams()
    );
    MRS.sendRequest("placeAskOrder", data, function (response) {
        MRS.logConsole(JSON.stringify(response));
    });
});
