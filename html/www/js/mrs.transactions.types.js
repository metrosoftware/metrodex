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

/**
 * @depends {mrs.js}
 */
var MRS = (function(MRS, $, undefined) {
    // If you add new mandatory attributes, please make sure to add them to
    // MRS.loadTransactionTypeConstants as well (below)
    MRS.transactionTypes = {
        0: {
            'title': "Payment",
            'i18nKeyTitle': 'payment',
            'iconHTML': "<i class='ion-calculator'></i>",
            'subTypes': {
                0: {
                    'title': "Ordinary Payment",
                    'i18nKeyTitle': 'ordinary_payment',
                    'iconHTML': "<i class='fa fa-money'></i>",
                    'receiverPage': 'transactions'
                }
            }
        },
        1: {
            'title': "Messaging/Voting/Aliases",
            'i18nKeyTitle': 'messaging_voting_aliases',
            'iconHTML': "<i class='fa fa-envelope-square'></i>",
            'subTypes': {
                0: {
                    'title': "Arbitrary Message",
                    'i18nKeyTitle': 'arbitrary_message',
                    'iconHTML': "<i class='fa fa-envelope-o'></i>",
                    'receiverPage': 'messages'
                },
                1: {
                    'title': "Alias Assignment",
                    'i18nKeyTitle': 'alias_assignment',
                    'iconHTML': "<i class='fa fa-bookmark'></i>"
                },
                2: {
                    'title': "Poll Creation",
                    'i18nKeyTitle': 'poll_creation',
                    'iconHTML': "<i class='fa fa-check-square-o'></i>"
                },
                3: {
                    'title': "Vote Casting",
                    'i18nKeyTitle': 'vote_casting',
                    'iconHTML': "<i class='fa fa-check'></i>"
                },
                4: {
                    'title': "Hub Announcement",
                    'i18nKeyTitle': 'hub_announcement',
                    'iconHTML': "<i class='ion-radio-waves'></i>"
                },
                5: {
                    'title': "Account Info",
                    'i18nKeyTitle': 'account_info',
                    'iconHTML': "<i class='fa fa-info'></i>"
                },
                6: {
                    'title': "Alias Sale/Transfer",
                    'i18nKeyTitle': 'alias_sale_transfer',
                    'iconHTML': "<i class='fa fa-tag'></i>",
                    'receiverPage': "aliases"
                },
                7: {
                    'title': "Alias Buy",
                    'i18nKeyTitle': 'alias_buy',
                    'iconHTML': "<i class='fa fa-money'></i>",
                    'receiverPage': "aliases"
                },
                8: {
                    'title': "Alias Deletion",
                    'i18nKeyTitle': 'alias_deletion',
                    'iconHTML': "<i class='fa fa-times'></i>"
                },
                9: {
                    'title': "Transaction Approval",
                    'i18nKeyTitle': 'transaction_approval',
                    'iconHTML': "<i class='fa fa-gavel'></i>",
                    'receiverPage': "transactions"
                },
                10: {
                    'title': "Account Property",
                    'i18nKeyTitle': 'account_property',
                    'iconHTML': "<i class='fa fa-gavel'></i>",
                    'receiverPage': "transactions"
                },
                11: {
                    'title': "AccountPropertyDelete",
                    'i18nKeyTitle': 'account_property_delete',
                    'iconHTML': "<i class='fa fa-question'></i>",
                    'receiverPage': "transactions"
                }
            }
        },
        2: {
            'title': "Shuffling",
            'i18nKeyTitle': 'shuffling',
            'iconHTML': '<i class="fa fa-random"></i>',
            'subTypes': {
                0: {
                    'title': "Shuffling Creation",
                    'i18nKeyTitle': 'shuffling_creation',
                    'iconHTML': '<i class="fa fa-plus"></i>'
                },
                1: {
                    'title': "Shuffling Registration",
                    'i18nKeyTitle': 'shuffling_registration',
                    'iconHTML': '<i class="fa fa-link"></i>'
                },
                2: {
                    'title': "Shuffling Processing",
                    'i18nKeyTitle': 'shuffling_processing',
                    'iconHTML': '<i class="fa fa-cog"></i>'
                },
                3: {
                    'title': "Shuffling Recipients",
                    'i18nKeyTitle': 'shuffling_recipients',
                    'iconHTML': '<i class="fa fa-spoon"></i>'
                },
                4: {
                    'title': "Shuffling Verification",
                    'i18nKeyTitle': 'shuffling_verification',
                    'iconHTML': '<i class="fa fa-check-square"></i>'
                },
                5: {
                    'title': "Shuffling Cancellation",
                    'i18nKeyTitle': 'shuffling_cancellation',
                    'iconHTML': '<i class="fa fa-thumbs-down"></i>'
                }
            }
        },
        3: {
            'title': "Account Control",
            'i18nKeyTitle': 'account_control',
            'iconHTML': '<i class="fa fa-dashboard"></i>',
            'subTypes': {
                0: {
                    'title': "Balance Leasing",
                    'i18nKeyTitle': 'balance_leasing',
                    'iconHTML': '<i class="fa fa-arrow-circle-o-right"></i>',
                    'receiverPage': "transactions"
                },
                1: {
                    'title': "Mandatory Approval",
                    'i18nKeyTitle': 'phasing_only',
                    'iconHTML': '<i class="fa fa-gavel"></i>',
                    'receiverPage': "transactions"
                },
                2: {
                    'title': "Keep Treasure",
                    'i18nKeyTitle': 'keep_treasure',
                    'iconHTML': '<i class="ion-locked"></i>',
                    'receiverPage': "transactions"
                }
            }
        },
        4: {
            'title': "Coinbase",
            'i18nKeyTitle': 'coinbase',
            'iconHTML': "<i class='ion-hammer'></i>",
            'subTypes': {
                0: {
                    'title': "Ordinary Payment",
                    'i18nKeyTitle': 'ordinary_coinbase',
                    'iconHTML': "<i class='fa fa-money'></i>",
                    'receiverPage': 'transactions'
                }
            }
        }
        /*
        5: {
            'title': "Asset Exchange",
            'i18nKeyTitle': 'asset_exchange',
            'iconHTML': '<i class="fa fa-signal"></i>',
            'subTypes': {
                0: {
                    'title': "Asset Issuance",
                    'i18nKeyTitle': 'asset_issuance',
                    'iconHTML': '<i class="fa fa-bullhorn"></i>'
                },
                1: {
                    'title': "Asset Transfer",
                    'i18nKeyTitle': 'asset_transfer',
                    'iconHTML': '<i class="ion-arrow-swap"></i>',
                    'receiverPage': "transfer_history"
                },
                2: {
                    'title': "Ask Order Placement",
                    'i18nKeyTitle': 'ask_order_placement',
                    'iconHTML': '<i class="ion-arrow-graph-down-right"></i>',
                    'receiverPage': "open_orders"
                },
                3: {
                    'title': "Bid Order Placement",
                    'i18nKeyTitle': 'bid_order_placement',
                    'iconHTML': '<i class="ion-arrow-graph-up-right"></i>',
                    'receiverPage': "open_orders"
                },
                4: {
                    'title': "Ask Order Cancellation",
                    'i18nKeyTitle': 'ask_order_cancellation',
                    'iconHTML': '<i class="fa fa-times"></i>',
                    'receiverPage': "open_orders"
                },
                5: {
                    'title': "Bid Order Cancellation",
                    'i18nKeyTitle': 'bid_order_cancellation',
                    'iconHTML': '<i class="fa fa-times"></i>',
                    'receiverPage': "open_orders"
                },
                6: {
                    'title': "Dividend Payment",
                    'i18nKeyTitle': 'dividend_payment',
                    'iconHTML': '<i class="fa fa-gift"></i>',
                    'receiverPage': "transactions"
                },
                7: {
                    'title': "Delete Asset Shares",
                    'i18nKeyTitle': 'delete_asset_shares',
                    'iconHTML': '<i class="fa fa-remove"></i>',
                    'receiverPage': "transactions"
                }
            }
        },
        */
    };

    MRS.subtype = {};

    MRS.loadTransactionTypeConstants = function(response) {
        if (response.burningAccountId) {
            $.each(response.transactionTypes, function(typeIndex, type) {
                if (!(typeIndex in MRS.transactionTypes)) {
                    MRS.transactionTypes[typeIndex] = {
                        'title': "Unknown",
                        'i18nKeyTitle': 'unknown',
                        'iconHTML': '<i class="fa fa-question-circle"></i>',
                        'subTypes': {}
                    }
                }
                $.each(type.subtypes, function(subTypeIndex, subType) {
                    if (!(subTypeIndex in MRS.transactionTypes[typeIndex]["subTypes"])) {
                        MRS.transactionTypes[typeIndex]["subTypes"][subTypeIndex] = {
                            'title': "Unknown",
                            'i18nKeyTitle': 'unknown',
                            'iconHTML': '<i class="fa fa-question-circle"></i>'
                        }
                    }
                    MRS.transactionTypes[typeIndex]["subTypes"][subTypeIndex]["serverConstants"] = subType;
                });
            });
            MRS.subtype = response.transactionSubTypes;
        }
    };

    MRS.isOfType = function(transaction, type_str) {
        if (!MRS.subtype[type_str]) {
            $.growl($.t("unsupported_transaction_type"));
            return;
        }
        return transaction.type == MRS.subtype[type_str].type && transaction.subtype == MRS.subtype[type_str].subtype;
    };
    
    return MRS;
}(isNode ? client : MRS || {}, jQuery));

if (isNode) {
    module.exports = MRS;
}