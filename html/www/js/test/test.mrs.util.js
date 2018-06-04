/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 * Copyright © 2016-2017 Jelurida IP B.V.                                     *
 * Copyright © 2018 metro.software                                            *
 *                                                                            *
 * See the LICENSE.txt file at the top-level directory of this distribution   *
 * for licensing information.                                                 *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,*
 * no part of the Nxt software, including this file, may be copied, modified, *
 * propagated, or distributed except according to the terms contained in the  *
 * LICENSE.txt file.                                                          *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

QUnit.module("mrs.util");

QUnit.test("convertToMTR", function (assert) {
    assert.equal(MRS.convertToMTR(200000000), "2", "whole");
    assert.equal(MRS.convertToMTR(20000000), "0.2", "fraction");
    assert.equal(MRS.convertToMTR(-200000000), "-2", "negative");
    assert.equal(MRS.convertToMTR(-20000000), "-0.2", "fraction.negative");
    assert.equal(MRS.convertToMTR(-220000000), "-2.2", "whole.fraction.negative");
    assert.equal(MRS.convertToMTR(2), "0.00000002", "mqt");
    assert.equal(MRS.convertToMTR(-2), "-0.00000002", "mqt.negative");
    assert.equal(MRS.convertToMTR(new BigInteger(String(2))), "0.00000002", "input.object");
    assert.equal(MRS.convertToMTR("hi"), "0.00000188", "alphanumeric"); // strange behavior of BigInteger don't do that
    assert.throws(function () {
        MRS.convertToMTR(null);
    }, {
        "message": "Cannot read property 'compareTo' of null",
        "name": "TypeError"
    }, "null.value");
});

QUnit.test("format", function (assert) {
    assert.equal(MRS.format("12345"), Number("12345").toLocaleString(), "escaped");
    assert.equal(MRS.format("12345", true), Number(12345).toLocaleString(), "not.escaped");
    assert.equal(MRS.format("-12345", false), Number(-12345).toLocaleString(), "neg");
    assert.equal(MRS.format("-12345", true), Number("-12345").toLocaleString(), "neg.not.escaped");
    assert.equal(MRS.format("-12345.67", true), Number("-12345.67").toLocaleString(), "decimal.not.good"); // bug ?
    assert.equal(MRS.format({ amount: 1234, negative: '-', mantissa: ".567"}, true), Number(-1234.567).toLocaleString(), "object");
    assert.equal(MRS.format("12.34", false, 4), "12.3400", "zero.pad");
    assert.equal(MRS.format("12", false, 4), "12.0000", "zero.pad.whole");
    assert.equal(MRS.format("12.", false, 4), "12.0000", "zero.pad.whole");
    assert.equal(MRS.format("12.34567", false, 4), "12.34567", "zero.pad.not.necessary");
    assert.equal(MRS.format("12", false, 0), "12", "zero.to.pad");
});

QUnit.test("formatAmount", function (assert) {
    assert.equal(MRS.formatAmount("12345", false, false), "0.00012345", "mqt");
    assert.equal(MRS.formatAmount("12345", true, false), "0.00012345", "mqt.rounding");
    assert.equal(MRS.formatAmount("1234500000", false, false), "12.345", "string");
    assert.equal(MRS.formatAmount("1234500000", true, false), "12.345", "string.no.rounding");
    assert.equal(MRS.formatAmount(12.345, false, false), "12.345", "number");
    assert.equal(MRS.formatAmount(12.345, true, false), "12.35", "number.rounding");
    assert.equal(MRS.formatAmount(12.343, true, false), "12.34", "number.rounding");
    assert.equal(MRS.formatAmount("123456700000", false, true), Number("1234.567").toLocaleString(), "1000separator");
    assert.equal(MRS.formatAmount("123456700000000", true, true), Number("1234567").toLocaleString(), "metro.rounding");
    assert.equal(MRS.formatAmount("123456780000000", true, false), Number("1234567.8").toLocaleString(), "thousands.separator.escaped");
});

QUnit.test("formatVolume", function (assert) {
    assert.equal(MRS.formatVolume(1), "1 B", "byte");
    assert.equal(MRS.formatVolume(1000), "1'000 B", "thousand");
    assert.equal(MRS.formatVolume(1024), "1 KB", "kilo");
    assert.equal(MRS.formatVolume(1000000), "977 KB", "million");
    assert.equal(MRS.formatVolume(1024*1024), "1 MB", "million");
    assert.equal(MRS.formatVolume(2*1024*1024 + 3*1024 + 4), "2 MB", "combination");
});

QUnit.test("formatWeight", function (assert) {
    assert.equal(MRS.formatWeight(1), "1", "byte");
    assert.equal(MRS.formatWeight(1000), "1&#39;000", "thousand");
    assert.equal(MRS.formatWeight(12345), "12&#39;345", "number");
});

QUnit.test("calculateOrderPricePerWholeQNT", function (assert) {
    assert.equal(MRS.calculateOrderPricePerWholeQNT(100000000, 0), "1", "no.decimals.one");
    assert.equal(MRS.calculateOrderPricePerWholeQNT(1, 4), "0.0001", "fraction");
    assert.equal(MRS.calculateOrderPricePerWholeQNT(-123400000000, 8), "-123400000000", "eight.decimals");
    assert.equal(MRS.calculateOrderPricePerWholeQNT(-123400000000, 4), "-12340000", "four.decimals");
    assert.equal(MRS.calculateOrderPricePerWholeQNT(-123400000000, 0), "-1234", "no.decimals");
});

QUnit.test("formatOrderPricePerWholeQNT", function (assert) {
    assert.equal(MRS.formatOrderPricePerWholeQNT(100000000, 0), "1", "no.decimals.one");
    assert.equal(MRS.formatOrderPricePerWholeQNT(1, 4), "0.0001", "fraction");
    assert.equal(MRS.formatOrderPricePerWholeQNT(-123400000000, 8), Number("-123400000000".escapeHTML()).toLocaleString(), "eight.decimals");
    assert.equal(MRS.formatOrderPricePerWholeQNT(-123400000000, 4), Number("-12340000".escapeHTML()).toLocaleString(), "four.decimals");
    assert.equal(MRS.formatOrderPricePerWholeQNT(-123400000000, 0), Number("-1234".escapeHTML()).toLocaleString(), "no.decimals");
});

QUnit.test("calculatePricePerWholeQNT", function (assert) {
    assert.equal(MRS.calculatePricePerWholeQNT(100000000, 0), "100000000", "no.decimals.one");
    assert.equal(MRS.calculatePricePerWholeQNT(100000000, 4), "10000", "four.decimals");
    assert.equal(MRS.calculatePricePerWholeQNT(100000000, 8), "1", "eight.decimals");
    assert.equal(MRS.calculatePricePerWholeQNT(-123400000000, 8), "-1234".escapeHTML(), "eight.decimals");
    assert.equal(MRS.calculatePricePerWholeQNT(-123400000000, 4), "-12340000".escapeHTML(), "four.decimals");
    assert.equal(MRS.calculatePricePerWholeQNT(-123400000000, 0), "-123400000000".escapeHTML(), "no.decimals");
    assert.throws(function () {
        MRS.calculatePricePerWholeQNT(100000001, 8);
    }, "Invalid input.", "invalid.input");
});

QUnit.test("calculateOrderTotalMQT", function (assert) {
    assert.equal(MRS.calculateOrderTotalMQT(12, 34), "408", "multiplication");
});

QUnit.test("calculateOrderTotal", function (assert) {
    assert.equal(MRS.calculateOrderTotal(12, 34), "0.00000408", "multiplication");
});

QUnit.test("calculatePercentage", function (assert) {
    assert.equal(MRS.calculatePercentage(6, 15), "40.00", "pct1");
    assert.equal(MRS.calculatePercentage(5, 15), "33.33", "pct1");
    assert.equal(MRS.calculatePercentage(10, 15), "66.67", "pct3");
    assert.equal(MRS.calculatePercentage(10, 15, 0), "66.66", "pct3.round0");
    assert.equal(MRS.calculatePercentage(10, 15, 1), "66.67", "pct3.round1");
    assert.equal(MRS.calculatePercentage(10, 15, 2), "66.67", "pct3.round2");
    assert.equal(MRS.calculatePercentage(10, 15, 3), "66.67", "pct3.round3");
});

QUnit.test("amountToPrecision", function (assert) {
    assert.equal(MRS.amountToPrecision(12, 0), "12", "multiplication");
    assert.equal(MRS.amountToPrecision(12., 0), "12", "multiplication");
    assert.equal(MRS.amountToPrecision(12.0, 0), "12", "multiplication");
    assert.equal(MRS.amountToPrecision(12.345600, 4), "12.3456", "multiplication");
    assert.equal(MRS.amountToPrecision(12.3456, 4), "12.3456", "multiplication");
    assert.equal(MRS.amountToPrecision(12.3456, 3), "12.345", "multiplication");
    assert.equal(MRS.amountToPrecision(12.3456, 2), "12.34", "multiplication");
    assert.equal(MRS.amountToPrecision(12.3006, 2), "12.30", "multiplication");
});

QUnit.test("convertToMQT", function (assert) {
    assert.equal(MRS.convertToMQT(1), "100000000", "one");
    assert.equal(MRS.convertToMQT(1.), "100000000", "one.dot");
    assert.equal(MRS.convertToMQT(1.0), "100000000", "one.dot.zero");
    assert.equal(MRS.convertToMQT(.1), "10000000", "dot.one");
    assert.equal(MRS.convertToMQT(0.1), "10000000", "zero.dot.one");
    assert.equal(MRS.convertToMQT("0.00000001"), "1", "mqt");
    assert.throws(function () {
        MRS.convertToMQT(0.00000001); // since it's passed as 1e-8
    }, "Invalid input.", "invalid.input");
});

QUnit.test("convertToQNTf", function (assert) {
    assert.equal(MRS.convertToQNTf(1, 0), "1", "one");
    assert.equal(MRS.convertToQNTf(1, 3), "0.001", "milli");
    assert.equal(MRS.convertToQNTf(1000, 3), "1", "three.decimals");
    assert.equal(MRS.convertToQNTf(1234567, 3), "1234.567", "multi");
    assert.deepEqual(MRS.convertToQNTf(1234567, 3, true), { amount: "1234", mantissa: ".567" }, "object");
});

QUnit.test("convertToQNT", function (assert) {
    assert.equal(MRS.convertToQNT(1, 0), "1", "one");
    assert.equal(MRS.convertToQNT(1, 3), "1000", "thousand");
    assert.equal(MRS.convertToQNT(1000, 3), "1000000", "million");
    assert.equal(MRS.convertToQNT(1.234, 3), "1234", "multi");
    assert.equal(MRS.convertToQNT(0.1234, 4), "1234", "decimal");
    assert.throws(function() { MRS.convertToQNT(0.12345, 4) }, "Fraction can only have 4 decimals max.", "too.many.decimals");
});

QUnit.test("formatQuantity", function (assert) {
    assert.equal(MRS.formatQuantity(1, 0), "1", "one");
    assert.equal(MRS.formatQuantity(10000000, 3, true), Number("10000").toLocaleString(), "thousand");
    assert.equal(MRS.formatQuantity(1234, 2, true), Number("12.34").toLocaleString(), "thousand");
    assert.equal(MRS.formatQuantity(123456, 2, true), Number("1234.56").toLocaleString(), "thousand");
    assert.equal(MRS.formatQuantity(1234567, 2, true), Number("12345.67").toLocaleString(), "thousand");
});

QUnit.test("formatAmount", function (assert) {
    assert.equal(MRS.formatAmount(1), "1", "one");
    assert.equal(MRS.formatAmount(10000000, false, true), Number("10000000").toLocaleString(), "million");
    assert.equal(MRS.formatAmount(12.34, true), Number("12.34").toLocaleString(), "thousand");
    assert.equal(MRS.formatAmount(12.345, true), Number("12.35").toLocaleString(), "thousand");
});

QUnit.test("formatTimestamp", function (assert) {
    var date = new Date(0);
    assert.equal(MRS.formatTimestamp(0, true, true), date.toLocaleDateString(), "start.date");
});

QUnit.test("getAccountLink", function (assert) {
    MRS.contacts = {};

    assert.equal(MRS.getAccountLink({}, "dummy"), "/", "non.existing");
    assert.equal(MRS.getAccountLink({ entity: 5873880488492319831 }, "entity"), "<a href='#' data-user='MTR-XKA2-7VJU-VZSY-7R335' class='show_account_modal_action user-info'>/</a>", "numeric");
    assert.equal(MRS.getAccountLink({ entityRS: "MTR-XK4R-7VJU-6EQG-7R335" }, "entity"), "<a href='#' data-user='MTR-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>MTR-XK4R-7VJU-6EQG-7R335</a>", "RS");
    assert.equal(MRS.getAccountLink({ entity: 5873880488492319831, entityRS: "MTR-XK4R-7VJU-6EQG-7R335" }, "entity"), "<a href='#' data-user='MTR-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>MTR-XK4R-7VJU-6EQG-7R335</a>", "numeric.and.RS");
    MRS.contacts = { "MTR-XK4R-7VJU-6EQG-7R335": { name: "foo" }};
    assert.equal(MRS.getAccountLink({ entityRS: "MTR-XK4R-7VJU-6EQG-7R335" }, "entity"), "<a href='#' data-user='MTR-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>foo</a>", "contact");
    MRS.accountRS = "MTR-XK4R-7VJU-6EQG-7R335";
    assert.equal(MRS.getAccountLink({ entityRS: "MTR-XK4R-7VJU-6EQG-7R335" }, "entity"), "<a href='#' data-user='MTR-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>You</a>", "you");
    assert.equal(MRS.getAccountLink({ entityRS: "MTR-XK4R-7VJU-6EQG-7R335" }, "entity", "MTR-XK4R-7VJU-6EQG-7R335", "account"), "<a href='#' data-user='MTR-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>Account</a>", "force.account.name");
    assert.equal(MRS.getAccountLink({ entityRS: "MTR-XK4R-7VJU-6EQG-7R335" }, "entity", undefined, undefined, true), "<a href='#' data-user='MTR-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info'>MTR-XK4R-7VJU-6EQG-7R335</a>", "maintain.rs.format");
    assert.equal(MRS.getAccountLink({ entityRS: "MTR-XK4R-7VJU-6EQG-7R335" }, "entity", undefined, undefined, undefined, "btn btn-xs"), "<a href='#' data-user='MTR-XK4R-7VJU-6EQG-7R335' class='show_account_modal_action user-info btn btn-xs'>You</a>", "add.class");
    MRS.contacts = null;
    MRS.accountRS = null;
    MRS.constants.GENESIS = 1739068987193023818;
    MRS.constants.GENESIS_RS = "MTR-MR8N-2YLS-3MEQ-3CMAJ";
    assert.equal(MRS.getAccountLink({ entityRS: MRS.constants.GENESIS_RS }, "entity"), "<a href='#' data-user='MTR-MR8N-2YLS-3MEQ-3CMAJ' class='show_account_modal_action user-info'>Genesis</a>", "genesis");
});

QUnit.test("generateToken", function (assert) {
    MRS.constants.EPOCH_BEGINNING = 1385294400000;
    var token = MRS.generateToken("myToken", "rshw9abtpsa2");
    assert.ok(token.indexOf("e9cl0jgba7lnp7gke9rdp7hg3uvcl5cnd23") == 0);
    assert.equal(token.length, 160);
});

QUnit.test("utf8", function (assert) {
    // compare the two UTF8 conversion methods
    var str = "Hello World";
    var bytes1 = MRS.getUtf8Bytes(str);
    var bytes2 = MRS.strToUTF8Arr(str);
    assert.deepEqual(bytes1, bytes2);
    // Hebrew
    str = "אבג";
    bytes1 = MRS.getUtf8Bytes(str);
    bytes2 = MRS.strToUTF8Arr(str);
    assert.deepEqual(bytes1, bytes2);
    // Chinese Simplified
    str = "简体中文网页";
    bytes1 = MRS.getUtf8Bytes(str);
    bytes2 = MRS.strToUTF8Arr(str);
    assert.deepEqual(bytes1, bytes2);
    // Chinese Traditional
    str = "繁體中文網頁";
    bytes1 = MRS.getUtf8Bytes(str);
    bytes2 = MRS.strToUTF8Arr(str);
    assert.deepEqual(bytes1, bytes2);
});

QUnit.test("versionCompare", function (assert) {
    assert.equal(MRS.versionCompare("1.6.4", "1.7.5"), "-1", "after");
    assert.equal(MRS.versionCompare("1.7.5", "1.6.4"), "1", "before");
    assert.equal(MRS.versionCompare("1.6.4", "1.6.4"), "0", "same");
    assert.equal(MRS.versionCompare("1.6.4e", "1.6.5e"), "-1", "after.e");
    assert.equal(MRS.versionCompare("1.6.5e", "1.6.4e"), "1", "before.e");
    assert.equal(MRS.versionCompare("1.6.4e", "1.6.4e"), "0", "same.e");
    assert.equal(MRS.versionCompare("1.7.5", "1.8.0e"), "-1", "after.ga.vs.e");
    assert.equal(MRS.versionCompare("1.7.5e", "1.8.0"), "-1", "after.e.vs.ga");
    assert.equal(MRS.versionCompare("1.8.0e", "1.8.0"), "1", "same.e.before.ga");
});

QUnit.test("numberOfDecimals", function (assert) {
    MRS.getLocale();
    var rows = [{price: "1.23"}, {price: "1.234"}];
    assert.equal(MRS.getNumberOfDecimals(rows, "price"), 3, "no.callback");
    rows = [{price: "123000000"}, {price: "123400000"}];
    assert.equal(MRS.getNumberOfDecimals(rows, "price", function(val) {
        return MRS.formatAmount(val.price);
    }), 3, "with.callback");
});

QUnit.test("accountMatching", function (assert) {
    var regex = MRS.getRsAccountRegex("MTR");
    assert.equal(MRS.isRsAccountImpl("MTR-XK4R-7VJU-6EQG-7R335", regex), true, "valid.account");
    assert.equal(MRS.isRsAccountImpl("Mtr-xk4r-7VJU-6EQG-7R335", regex), true, "not.case.sensitive");
    assert.equal(MRS.isRsAccountImpl("MTR-XK4R-7VJU-6EQG-7R336", regex), true, "nor.rs.valid.still.valid");
    assert.equal(MRS.isRsAccountImpl("5873880488492319831", regex), false, "numeric.no.match");
    assert.equal(MRS.isRsAccountImpl("MTR-____-____-____-____-____-____", regex), true, "underscore_mask_again");
    regex = MRS.getNumericAccountRegex();
    assert.equal(MRS.isNumericAccountImpl("MTR-XK4R-7VJU-6EQG-7R335", regex), false, "non.numeric.no.match");
    assert.equal(MRS.isNumericAccountImpl("5873880488492319831", regex), true, "numeric.match");
});
