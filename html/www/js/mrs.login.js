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
	MRS.newlyCreatedAccount = false;

	MRS.allowLoginViaEnter = function() {
		$("#login_account_other").keypress(function(e) {
			if (e.which == '13') {
				e.preventDefault();
				var account = $("#login_account_other").val();
				MRS.login(false,account);
			}
		});
		$("#login_password").keypress(function(e) {
			if (e.which == '13') {
				e.preventDefault();
				var password = $("#login_password").val();
				MRS.login(true,password);
			}
		});
	};

	MRS.showLoginOrWelcomeScreen = function() {
		if (localStorage.getItem("logged_in")) {
			MRS.showLoginScreen();
		} else {
			MRS.showWelcomeScreen();
		}
	};

	MRS.showLoginScreen = function() {
		$("#account_phrase_custom_panel, #account_phrase_generator_panel, #welcome_panel, #custom_passphrase_link").hide();
		$("#account_phrase_custom_panel").find(":input:not(:button):not([type=submit])").val("");
		$("#account_phrase_generator_panel").find(":input:not(:button):not([type=submit])").val("");
        $("#login_account_other").mask(MRS.getAccountMask("*"));
		if (MRS.isMobileApp()) {
            $(".mobile-only").show();
        }
        $("#login_panel").show();
	};

	MRS.showWelcomeScreen = function() {
		$("#login_panel, #account_phrase_generator_panel, #account_phrase_custom_panel, #welcome_panel, #custom_passphrase_link").hide();
        if (MRS.isMobileApp()) {
            $(".mobile-only").show();
        }
		$("#welcome_panel").show();
	};

    MRS.createPassphraseToConfirmPassphrase = function() {
        if ($("#confirm_passphrase_warning").is(":checked")) {
            $('.step_2').hide();$('.step_3').show();
        } else {
            $("#confirm_passphrase_warning_container").css("background-color", "red");
		}
    };

	MRS.registerUserDefinedAccount = function() {
		$("#account_phrase_generator_panel, #login_panel, #welcome_panel, #custom_passphrase_link").hide();
		$("#account_phrase_generator_panel").find(":input:not(:button):not([type=submit])").val("");
		var accountPhraseCustomPanel = $("#account_phrase_custom_panel");
        accountPhraseCustomPanel.find(":input:not(:button):not([type=submit])").val("");
		accountPhraseCustomPanel.show();
		$("#registration_password").focus();
	};

	MRS.registerAccount = function() {
		$("#login_panel, #welcome_panel").hide();
		var accountPhraseGeneratorPanel = $("#account_phrase_generator_panel");
        accountPhraseGeneratorPanel.show();
		accountPhraseGeneratorPanel.find(".step_3 .callout").hide();

		var $loading = $("#account_phrase_generator_loading");
		var $loaded = $("#account_phrase_generator_loaded");
		if (MRS.isWindowPrintSupported()) {
            $(".paper-wallet-link-container").show();
		}

		//noinspection JSUnresolvedVariable
		if (window.crypto || window.msCrypto) {
			$loading.find("span.loading_text").html($.t("generating_passphrase_wait"));
		}

		$loading.show();
		$loaded.hide();

		if (typeof PassPhraseGenerator == "undefined") {
			$.when(
				$.getScript("js/crypto/passphrasegenerator.js")
			).done(function() {
				$loading.hide();
				$loaded.show();

				PassPhraseGenerator.generatePassPhrase("#account_phrase_generator_panel");
			}).fail(function() {
				alert($.t("error_word_list"));
			});
		} else {
			$loading.hide();
			$loaded.show();

			PassPhraseGenerator.generatePassPhrase("#account_phrase_generator_panel");
		}
	};

    $("#generator_paper_wallet_link").click(function(e) {
    	e.preventDefault();
        MRS.printPaperWallet($("#account_phrase_generator_panel").find(".step_2 textarea").val());
    });

	MRS.verifyGeneratedPassphrase = function() {
		var accountPhraseGeneratorPanel = $("#account_phrase_generator_panel");
        var password = $.trim(accountPhraseGeneratorPanel.find(".step_3 textarea").val());

		if (password != PassPhraseGenerator.passPhrase) {
			accountPhraseGeneratorPanel.find(".step_3 .callout").show();
		} else {
			MRS.newlyCreatedAccount = true;
			MRS.login(true,password);
			PassPhraseGenerator.reset();
			accountPhraseGeneratorPanel.find("textarea").val("");
			accountPhraseGeneratorPanel.find(".step_3 .callout").hide();
		}
	};

	$("#account_phrase_custom_panel").find("form").submit(function(event) {
		event.preventDefault();

		var password = $("#registration_password").val();
		var repeat = $("#registration_password_repeat").val();

		var error = "";

		if (password.length < 35) {
			error = $.t("error_passphrase_length");
		} else if (password.length < 50 && (!password.match(/[A-Z]/) || !password.match(/[0-9]/))) {
			error = $.t("error_passphrase_strength");
		} else if (password != repeat) {
			error = $.t("error_passphrase_match");
		}

		if (error) {
			$("#account_phrase_custom_panel").find(".callout").first().removeClass("callout-info").addClass("callout-danger").html(error);
		} else {
			$("#registration_password, #registration_password_repeat").val("");
			MRS.login(true,password);
		}
	});

	MRS.listAccounts = function() {
		var loginAccount = $('#login_account');
        loginAccount.empty();
		if (MRS.getStrItem("savedMetroAccounts") && MRS.getStrItem("savedMetroAccounts") != ""){
			$('#login_account_container').show();
			$('#login_account_container_other').hide();
			var accounts = MRS.getStrItem("savedMetroAccounts").split(";");
			$.each(accounts, function(index, account) {
				if (account != ''){
					$('#login_account')
					.append($("<li></li>")
						.append($("<a></a>")
							.attr("href","#")
							.attr("onClick","MRS.login(false,'"+account+"')")
							.text(account))
						.append($('<button data-dismiss="modal" class="close" type="button">×</button>')
							.attr("onClick","MRS.removeAccount('"+account+"')"))
					);
				}
			});
			var otherHTML = "<li><a href='#' data-i18n='other'>Other</a></li>";
			var $otherHTML = $(otherHTML);
			$otherHTML.click(function() {
				$('#login_account_container').hide();
				$('#login_account_container_other').show();
			});
			$otherHTML.appendTo(loginAccount);
		}
		else{
			$('#login_account_container').hide();
			$('#login_account_container_other').show();
		}
	};

	MRS.switchAccount = function(account) {
		// Reset security related state
		MRS.resetEncryptionState();
		MRS.setServerPassword(null);
		MRS.setAccountDetailsPassword(null);
		MRS.rememberPassword = false;
		MRS.account = "";
		MRS.accountRS = "";
		MRS.publicKey = "";
		MRS.accountInfo = {};

		// Reset other functional state
		$("#account_balance, #account_balance_sidebar, #account_nr_assets, #account_assets_balance, #account_nr_currencies, #account_message_count, #account_alias_count").html("0");
		$("#id_search").find("input[name=q]").val("");
		MRS.resetAssetExchangeState();
		MRS.resetPollsState();
		MRS.resetMessagesState();
		MRS.forgingStatus = MRS.constants.UNKNOWN;
		MRS.isAccountForging = false;
		MRS.selectedContext = null;

		// Reset plugins state
		MRS.activePlugins = false;
		MRS.numRunningPlugins = 0;
		$.each(MRS.plugins, function(pluginId) {
			MRS.determinePluginLaunchStatus(pluginId);
		});

		// Return to the dashboard and notify the user
		MRS.goToPage("dashboard");
        MRS.login(false, account, function() {
            $.growl($.t("switched_to_account", { account: account }))
        }, true);
	};

    $("#loginButtons").find(".btn").click(function (e) {
        e.preventDefault();
        var type = $(this).data("login-type");
        var readerId = $(this).data("reader");
        var reader = $("#" + readerId);
        if (reader.is(':visible') && type != "scan") {
            MRS.scanQRCode(readerId, function() {}); // turn off scanning
        }
        if (type == "account") {
            MRS.listAccounts();
            $('#login_password').parent().hide();
        } else if (type == "password") {
            $('#login_account_container').hide();
            $('#login_account_container_other').hide();
            $('#login_password').parent().show();
        } else if (type == "scan" && !reader.is(':visible')) {
            MRS.scanQRCode(readerId, function(text) {
                var metroAddress = new MetroAddress();
                if (metroAddress.set(text)) {
                    if ($("#remember_me").is(":checked")) {
                        rememberAccount(text);
                    }
                    MRS.login(false, text);
                } else {
                    MRS.login(true, text);
                }
            });
        }
    });

	MRS.removeAccount = function(account) {
		var accounts = MRS.getStrItem("savedMetroAccounts").replace(account+';','');
		if (accounts == '') {
			MRS.removeItem('savedMetroAccounts');
		} else {
			MRS.setStrItem("savedMetroAccounts", accounts);
		}
		MRS.listAccounts();
	};

    function rememberAccount(account) {
        var accountsStr = MRS.getStrItem("savedMetroAccounts");
        if (!accountsStr) {
            MRS.setStrItem("savedMetroAccounts", account + ";");
            return;
        }
        var accounts = accountsStr.split(";");
        if (accounts.indexOf(account) >= 0) {
            return;
        }
        MRS.setStrItem("savedMetroAccounts", accountsStr + account + ";");
    }

    // id can be either account id or passphrase
    MRS.login = function(isPassphraseLogin, id, callback, isAccountSwitch, isSavedPassphrase) {
		console.log("login isPassphraseLogin = " + isPassphraseLogin +
			", isAccountSwitch = " + isAccountSwitch +
			", isSavedPassphrase = " + isSavedPassphrase);
        MRS.spinner.spin($("#center")[0]);
        if (isPassphraseLogin && !isSavedPassphrase){
			var loginCheckPasswordLength = $("#login_check_password_length");
			if (!id.length) {
				$.growl($.t("error_passphrase_required_login"), {
					"type": "danger",
					"offset": 10
				});
                MRS.spinner.stop();
				return;
			} else if (!MRS.isTestNet && id.length < 12 && loginCheckPasswordLength.val() == 1) {
				loginCheckPasswordLength.val(0);
				var loginError = $("#login_error");
				loginError.find(".callout").html($.t("error_passphrase_login_length"));
				loginError.show();
                MRS.spinner.stop();
				return;
			}

			$("#login_password, #registration_password, #registration_password_repeat").val("");
			loginCheckPasswordLength.val(1);
		}

		console.log("login calling getBlockchainStatus");
		MRS.sendRequest("getBlockchainStatus", {}, function(response) {
			if (response.errorCode) {
			    MRS.connectionError(response.errorDescription);
                MRS.spinner.stop();
				console.log("getBlockchainStatus returned error");
				return;
			}
			console.log("getBlockchainStatus response received");
			MRS.state = response;
			var accountRequest;
			var requestVariable;
			if (isPassphraseLogin) {
				accountRequest = "getAccountId"; // Processed locally, not submitted to server
				requestVariable = {secretPhrase: id};
			} else {
				accountRequest = "getAccount";
				requestVariable = {account: id};
			}
			console.log("calling " + accountRequest);
			MRS.sendRequest(accountRequest, requestVariable, function(response, data) {
				console.log(accountRequest + " response received");
				if (!response.errorCode) {

					MRS.account = MRS.escapeRespStr(response.account);
					MRS.accountRS = MRS.escapeRespStr(response.accountRS);
					if (isPassphraseLogin) {
                        MRS.publicKey = MRS.getPublicKey(converters.stringToHexString(id));
                    } else {
                        MRS.publicKey = MRS.escapeRespStr(response.publicKey);
                    }
				}
				if (!isPassphraseLogin && response.errorCode == 5) {
					MRS.account = MRS.escapeRespStr(response.account);
					MRS.accountRS = MRS.escapeRespStr(response.accountRS);
				}
				if (!MRS.account) {
					$.growl($.t("error_find_account_id", { accountRS: (data && data.account ? String(data.account).escapeHTML() : "") }), {
						"type": "danger",
						"offset": 10
					});
                    MRS.spinner.stop();
					return;
				} else if (!MRS.accountRS) {
					$.growl($.t("error_generate_account_id"), {
						"type": "danger",
						"offset": 10
					});
                    MRS.spinner.stop();
					return;
				}

				MRS.sendRequest("getAccountPublicKey", {
					"account": MRS.account
				}, function(response) {
					if (response && response.publicKey && response.publicKey != MRS.generatePublicKey(id) && isPassphraseLogin) {
						$.growl($.t("error_account_taken"), {
							"type": "danger",
							"offset": 10
						});
                        MRS.spinner.stop();
						return;
					}

					var rememberMe = $("#remember_me");
					if (rememberMe.is(":checked") && isPassphraseLogin) {
						MRS.rememberPassword = true;
						MRS.setPassword(id);
						$(".secret_phrase, .show_secret_phrase").hide();
						$(".hide_secret_phrase").show();
					} else {
                        MRS.rememberPassword = false;
                        MRS.setPassword("");
                        $(".secret_phrase, .show_secret_phrase").show();
                        $(".hide_secret_phrase").hide();
                    }
					MRS.disablePluginsDuringSession = $("#disable_all_plugins").is(":checked");
					$("#sidebar_account_id").html(String(MRS.accountRS).escapeHTML());
					$("#sidebar_account_link").html(MRS.getAccountLink(MRS, "account", MRS.accountRS, "details", false, "btn btn-default btn-xs"));
					if (MRS.lastBlockHeight == 0 && MRS.state.numberOfBlocks) {
						MRS.checkBlockHeight(MRS.state.numberOfBlocks - 1);
					}
					if (MRS.lastBlockHeight == 0 && MRS.lastProxyBlockHeight) {
						MRS.checkBlockHeight(MRS.lastProxyBlockHeight);
					}
                    $("#sidebar_block_link").html(MRS.getBlockLink(MRS.lastBlockHeight));

					var passwordNotice = "";

					if (id.length < 35 && isPassphraseLogin) {
						passwordNotice = $.t("error_passphrase_length_secure");
					} else if (isPassphraseLogin && id.length < 50 && (!id.match(/[A-Z]/) || !id.match(/[0-9]/))) {
						passwordNotice = $.t("error_passphrase_strength_secure");
					}

					if (passwordNotice) {
						$.growl("<strong>" + $.t("warning") + "</strong>: " + passwordNotice, {
							"type": "danger"
						});
					}
					MRS.getAccountInfo(true, function() {
						if (MRS.accountInfo.currentLeasingHeightFrom) {
							MRS.isLeased = (MRS.lastBlockHeight >= MRS.accountInfo.currentLeasingHeightFrom && MRS.lastBlockHeight <= MRS.accountInfo.currentLeasingHeightTo);
						} else {
							MRS.isLeased = false;
						}
						MRS.updateForgingTooltip($.t("forging_unknown_tooltip"));
						MRS.updateForgingStatus(isPassphraseLogin ? id : null);
						if (MRS.isForgingSafe() && isPassphraseLogin) {
							var forgingIndicator = $("#forging_indicator");
							MRS.sendRequest("startForging", {
								"secretPhrase": id
							}, function (response) {
								if ("deadline" in response) {
									forgingIndicator.addClass("forging");
									forgingIndicator.find("span").html($.t("forging")).attr("data-i18n", "forging");
									MRS.forgingStatus = MRS.constants.FORGING;
									MRS.updateForgingTooltip(MRS.getForgingTooltip);
								} else {
									forgingIndicator.removeClass("forging");
									forgingIndicator.find("span").html($.t("not_forging")).attr("data-i18n", "not_forging");
									MRS.forgingStatus = MRS.constants.NOT_FORGING;
									MRS.updateForgingTooltip(response.errorDescription);
								}
								forgingIndicator.show();
							});
						}
                        MRS.updateMiningTooltip($.t("mining_unknown_tooltip"));
                        MRS.updateMiningStatus();
                            // var miningIndicator = $("#mining_indicator");
                            // MRS.sendRequest("getMining", {
                            //     "secretPhrase": id
                            // }, function (response) {
                            //     if ("getworkIsQueried" in response && response.getworkIsQueried && "secretPhrase" in response && response.secretPhrase) {
                            //         miningIndicator.addClass("mining");
                            //         miningIndicator.find("span").html($.t("mining")).attr("data-i18n", "mining");
                            //         MRS.miningStatus = MRS.constants.MINING;
                            //         MRS.updateMiningTooltip(MRS.getMiningTooltip);
                            //     } else if ("getworkIsQueried" in response && !response.getworkIsQueried || "secretPhrase" in response && !response.secretPhrase) {
                            //         miningIndicator.find("span").html($.t("not_mining")).attr("data-i18n", "not_mining");
                            //         MRS.miningStatus = MRS.constants.NOT_MINING;
                            //         MRS.setMiningIndicatorStatus(MRS.miningStatus);
                            //         MRS.updateMiningTooltip(MRS.getMiningTooltip);
                            //     } else if ("getworkIsQueried" in response && !response.getworkIsQueried && "secretPhrase" in response && response.secretPhrase) {
                            //         MRS.miningStatus = MRS.constants.MINING_ALLOWED;
                            //         miningIndicator.find("span").html($.t("mining_allowed")).attr("data-i18n", "mining_allowed");
                            //         MRS.setMiningIndicatorStatus(MRS.miningStatus);
                            //         MRS.updateMiningTooltip(MRS.getMiningTooltip);
                            //     }
                            //     miningIndicator.show();
                            // });

					}, isAccountSwitch);
					MRS.initSidebarMenu();
					MRS.unlock();

					if (MRS.isOutdated) {
						$.growl($.t("mrs_update_available"), {
							"type": "danger"
						});
					}

					if (!MRS.downloadingBlockchain) {
						MRS.checkIfOnAFork();
					}
					MRS.logConsole("User Agent: " + String(navigator.userAgent));
					if (navigator.userAgent.indexOf('Safari') != -1 &&
						navigator.userAgent.indexOf('Chrome') == -1 &&
						navigator.userAgent.indexOf('JavaFX') == -1) {
						// Don't use account based DB in Safari due to a buggy indexedDB implementation (2015-02-24)
						MRS.createDatabase("MRS_USER_DB");
						$.growl($.t("mrs_safari_no_account_based_db"), {
							"type": "danger"
						});
					} else {
						MRS.createDatabase("MRS_USER_DB_" + String(MRS.account));
					}
					if (callback) {
						callback();
					}

					$.each(MRS.pages, function(key) {
						if(key in MRS.setup) {
							MRS.setup[key]();
						}
					});

					$(".sidebar .treeview").tree();
					$('#dashboard_link').find('a').addClass("ignore").click();

					var accounts;
					if (rememberMe.is(":checked") || MRS.newlyCreatedAccount) {
						rememberAccount(MRS.accountRS);
					}

					$("[data-i18n]").i18n();

					/* Add accounts to dropdown for quick switching */
					var accountIdDropdown = $("#account_id_dropdown");
					accountIdDropdown.find(".dropdown-menu .switchAccount").remove();
					if (MRS.getStrItem("savedMetroAccounts") && MRS.getStrItem("savedMetroAccounts")!=""){
						accountIdDropdown.show();
						accounts = MRS.getStrItem("savedMetroAccounts").split(";");
						$.each(accounts, function(index, account) {
							if (account != ''){
								$('#account_id_dropdown').find('.dropdown-menu')
								.append($("<li class='switchAccount'></li>")
									.append($("<a></a>")
										.attr("href","#")
										.attr("style","font-size: 85%;")
										.attr("onClick","MRS.switchAccount('"+account+"')")
										.text(account))
								);
							}
						});
					} else {
						accountIdDropdown.hide();
					}

					MRS.updateApprovalRequests();
				});
			});
		});
	};

	$("#logout_button_container").on("show.bs.dropdown", function() {
		if (MRS.forgingStatus != MRS.constants.FORGING) {
			$(this).find("[data-i18n='logout_stop_forging']").hide();
		}
	});

	MRS.initPluginWarning = function() {
		if (MRS.activePlugins) {
			var html = "";
			html += "<div style='font-size:13px;'>";
			html += "<div style='background-color:#e6e6e6;padding:12px;'>";
			html += "<span data-i18n='following_plugins_detected'>";
			html += "The following active plugins have been detected:</span>";
			html += "</div>";
			html += "<ul class='list-unstyled' style='padding:11px;border:1px solid #e0e0e0;margin-top:8px;'>";
			$.each(MRS.plugins, function(pluginId, pluginDict) {
				if (pluginDict["launch_status"] == MRS.constants.PL_PAUSED) {
					html += "<li style='font-weight:bold;'>" + pluginDict["manifest"]["name"] + "</li>";
				}
			});
			html += "</ul>";
			html += "</div>";

			$('#lockscreen_active_plugins_overview').popover({
				"html": true,
				"content": html,
				"trigger": "hover"
			});

			html = "";
			html += "<div style='font-size:13px;padding:5px;'>";
			html += "<p data-i18n='plugin_security_notice_full_access'>";
			html += "Plugins are not sandboxed or restricted in any way and have full accesss to your client system including your Metro passphrase.";
			html += "</p>";
			html += "<p data-i18n='plugin_security_notice_trusted_sources'>";
			html += "Make sure to only run plugins downloaded from trusted sources, otherwise ";
			html += "you can loose your " + MRS.constants.COIN_SYMBOL + "! In doubt don't run plugins with accounts ";
			html += "used to store larger amounts of " + MRS.constants.COIN_SYMBOL + " now or in the future.";
			html += "</p>";
			html += "</div>";

			$('#lockscreen_active_plugins_security').popover({
				"html": true,
				"content": html,
				"trigger": "hover"
			});

			$("#lockscreen_active_plugins_warning").show();
		} else {
			$("#lockscreen_active_plugins_warning").hide();
		}
	};

	MRS.showLockscreen = function() {
		MRS.listAccounts();
		if (localStorage.getItem("logged_in")) {
			MRS.showLoginScreen();
		} else {
			MRS.showWelcomeScreen();
		}

		$("#center").show();
		if (!MRS.isShowDummyCheckbox) {
			$("#dummyCheckbox").hide();
		}
	};

	MRS.unlock = function() {
		if (!localStorage.getItem("logged_in")) {
			localStorage.setItem("logged_in", true);
		}
		$("#lockscreen").hide();
		$("body, html").removeClass("lockscreen");
		$("#login_error").html("").hide();
		$(document.documentElement).scrollTop = 0;
        MRS.spinner.stop();
    };

	MRS.logout = function(stopForging) {
		if (stopForging && MRS.forgingStatus == MRS.constants.FORGING) {
			var stopForgingModal = $("#stop_forging_modal");
            stopForgingModal.find(".show_logout").show();
			stopForgingModal.modal("show");
		} else {
			MRS.setDecryptionPassword("");
			MRS.setPassword("");
			//window.location.reload();
			window.location.href = window.location.pathname;
		}
	};

	$("#logout_clear_user_data_confirm_btn").click(function(e) {
		e.preventDefault();
		if (MRS.database) {
			//noinspection JSUnresolvedFunction
			indexedDB.deleteDatabase(MRS.database.name);
		}
		if (MRS.legacyDatabase) {
			//noinspection JSUnresolvedFunction
			indexedDB.deleteDatabase(MRS.legacyDatabase.name);
		}
		MRS.removeItem("logged_in");
		MRS.removeItem("savedMetroAccounts");
		MRS.removeItem("language");
        MRS.removeItem("savedPassphrase");
		MRS.localStorageDrop("data");
		MRS.localStorageDrop("polls");
		MRS.localStorageDrop("contacts");
		MRS.localStorageDrop("assets");
		MRS.logout();
	});

    MRS.setPassword = function(password) {
		MRS.setEncryptionPassword(password);
		MRS.setServerPassword(password);
        MRS.setAccountDetailsPassword(password);
        MRS.setAdvancedModalPassword(password);
        MRS.setTokenPassword(password);
		if (MRS.mobileSettings.is_store_remembered_passphrase) {
			MRS.setStrItem("savedPassphrase", password);
		} else {
			MRS.setStrItem("savedPassphrase", "");
		}
	};
	return MRS;
}(MRS || {}, jQuery));
