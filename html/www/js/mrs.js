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
 * @depends {3rdparty/jquery-2.1.0.js}
 * @depends {3rdparty/bootstrap.js}
 * @depends {3rdparty/big.js}
 * @depends {3rdparty/jsbn.js}
 * @depends {3rdparty/jsbn2.js}
 * @depends {3rdparty/pako.js}
 * @depends {3rdparty/webdb.js}
 * @depends {3rdparty/growl.js}
 * @depends {crypto/curve25519.js}
 * @depends {crypto/curve25519_.js}
 * @depends {crypto/passphrasegenerator.js}
 * @depends {crypto/sha256worker.js}
 * @depends {crypto/3rdparty/cryptojs/aes.js}
 * @depends {crypto/3rdparty/cryptojs/sha256.js}
 * @depends {crypto/3rdparty/jssha256.js}
 * @depends {util/converters.js}
 * @depends {util/extensions.js}
 * @depends {util/metroaddress.js}
 */
var MRS = (function(MRS, $, undefined) {
	"use strict";

	MRS.state = {};
	MRS.blocks = [];
	MRS.account = "";
	MRS.accountRS = "";
	MRS.publicKey = "";
	MRS.accountInfo = {};

	MRS.database = null;
	MRS.databaseSupport = false;
	MRS.databaseFirstStart = false;

	// Legacy database, don't use this for data storage
	MRS.legacyDatabase = null;
	MRS.legacyDatabaseWithData = false;

	MRS.serverConnect = false;
	MRS.peerConnect = false;

	MRS.settings = {};
	MRS.mobileSettings = {
	    is_check_remember_me: false,
		is_store_remembered_passphrase: (window["cordova"] !== undefined), // too early to use feature detection
	    is_simulate_app: false,
        is_testnet: false,
        remote_node_address: "",
        remote_node_port: 7886,
        is_remote_node_ssl: false,
        validators_count: 3,
        bootstrap_nodes_count: 5
    };
	MRS.contacts = {};

	MRS.isTestNet = MRS.isTestNet ? MRS.isTestNet : false;
	MRS.forgingStatus = MRS.constants.UNKNOWN;
	MRS.isAccountForging = false;
	MRS.isLeased = false;
	MRS.needsAdminPassword = true;
    MRS.upnpExternalAddress = null;
	MRS.ledgerTrimKeep = 0;

	MRS.lastBlockHeight = 0;
	MRS.lastLocalBlockHeight = 0;
	MRS.downloadingBlockchain = false;

	MRS.rememberPassword = false;
	MRS.selectedContext = null;

	MRS.currentPage = "dashboard";
	MRS.currentSubPage = "";
	MRS.pageNumber = 1;
	//MRS.itemsPerPage = 50;  /* Now set in mrs.settings.js */

	MRS.pages = {};
	MRS.incoming = {};
	MRS.setup = {};

	MRS.appVersion = "";
	MRS.appPlatform = "";
	MRS.assetTableKeys = [];

	MRS.lastProxyBlock = 0;
	MRS.lastProxyBlockHeight = 0;
    MRS.spinner = null;

    var stateInterval;
	var stateIntervalSeconds = 3;
	var isScanning = false;

	MRS.loadMobileSettings = function () {
		if (!window["localStorage"]) {
			return;
		}
		var mobileSettings = MRS.getJSONItem("mobile_settings");
		if (mobileSettings) {
            for (var setting in mobileSettings) {
                if (!mobileSettings.hasOwnProperty(setting)) {
                    continue;
                }
                MRS.mobileSettings[setting] = mobileSettings[setting];
            }
		}
        for (setting in MRS.mobileSettings) {
            if (!MRS.mobileSettings.hasOwnProperty(setting)) {
                continue;
            }
            MRS.logConsole("MRS.mobileSettings." + setting + " = " + MRS.mobileSettings[setting]);
        }
	};

	function initSpinner() {
        var opts = {
            lines: 13 // The number of lines to draw
            , length: 10 // The length of each line
            , width: 4 // The line thickness
            , radius: 20 // The radius of the inner circle
            , scale: 1 // Scales overall size of the spinner
            , corners: 1 // Corner roundness (0..1)
            , color: '#ffffff' // #rgb or #rrggbb or array of colors
            , opacity: 0.25 // Opacity of the lines
            , rotate: 0 // The rotation offset
            , direction: 1 // 1: clockwise, -1: counterclockwise
            , speed: 1 // Rounds per second
            , trail: 60 // Afterglow percentage
            , fps: 20 // Frames per second when using setTimeout() as a fallback for CSS
            , zIndex: 2e9 // The z-index (defaults to 2000000000)
            , className: 'spinner' // The CSS class to assign to the spinner
            , top: '50%' // Top position relative to parent
            , left: '50%' // Left position relative to parent
            , shadow: false // Whether to render a shadow
            , hwaccel: false // Whether to use hardware acceleration
            , position: 'absolute' // Element positioning
        };
        MRS.spinner = new Spinner(opts);
		console.log("Spinner initialized");
    }

    MRS.init = function() {
        i18next.use(i18nextXHRBackend)
            .use(i18nextLocalStorageCache)
            .use(i18nextBrowserLanguageDetector)
            .use(i18nextSprintfPostProcessor)
            .init({
                fallbackLng: "en",
                fallbackOnEmpty: true,
                lowerCaseLng: true,
                detectLngFromLocalStorage: true,
                resGetPath: "locales/__lng__/translation.json",
                compatibilityJSON: 'v1',
                compatibilityAPI: 'v1',
                debug: true
            }, function() {
                MRS.initSettings();

                jqueryI18next.init(i18next, $, {
                    handleName: "i18n"
                });

                initSpinner();
                MRS.spinner.spin($("#center")[0]);
                MRS.loadMobileSettings();
                if (MRS.isMobileApp()) {
                    $('body').css('overflow-x', 'auto');
                    initMobile();
                } else {
                    initImpl();
                }

                $("[data-i18n]").i18n();
                MRS.initClipboard();
                hljs.initHighlightingOnLoad();
            });
    };

    function initMobile() {
        var promise = new Promise(function(resolve, reject) {
            MRS.initRemoteNodesMgr(MRS.mobileSettings.is_testnet, resolve, reject);
        });
        promise.then(function() {
            MRS.remoteNodesMgr.findMoreNodes(true);
            initImpl();
        }).catch(function() {
            var msg = $.t("cannot_find_remote_nodes");
            console.log(msg);
            $.growl(msg);
			var loadConstantsPromise = new Promise(function(resolve) {
				console.log("load server constants");
				MRS.loadServerConstants(resolve);
			});
			loadConstantsPromise.then(function() {
				var mobileSettingsModal = $("#mobile_settings_modal");
				mobileSettingsModal.find("input[name=is_offline]").val("true");
				mobileSettingsModal.modal("show");
			});
        })
    }

    function applyBranding(constants) {
        document.title = constants.PROJECT_NAME;
        $("#mrs_version_info").text(constants.PROJECT_NAME + " " + $.t("version"));
        $(".help-about").text($.t("about") + " " + constants.PROJECT_NAME);
        $(".modal-title-info").text(constants.PROJECT_NAME + " " + $.t("info"));
        if (constants.PROJECT_NAME != "Metro") {
            $(".branding-message").html("<p>" + constants.PROJECT_NAME + " " + $.t("branding_message") + "<p>");
		}
	}

    function initImpl() {
		var loadConstantsPromise = new Promise(function(resolve) {
			console.log("load server constants");
			MRS.loadServerConstants(resolve);
		});
		loadConstantsPromise.then(function() {
            applyBranding(MRS.constants);
			var getStatePromise = new Promise(function(resolve) {
				console.log("calling getState");
				MRS.sendRequest("getState", {
					"includeCounts": "false"
				}, function (response) {
					console.log("getState response received");
					var isTestnet = false;
					var isOffline = false;
                    var customLoginWarning;
					var peerPort = 0;
					for (var key in response) {
						if (!response.hasOwnProperty(key)) {
							continue;
						}
						if (key == "isTestnet") {
							isTestnet = response[key];
						}
						if (key == "isOffline") {
							isOffline = response[key];
						}
						if (key == "customLoginWarning") {
                            customLoginWarning = response[key];
						}
						if (key == "peerPort") {
							peerPort = response[key];
						}
						if (key == "needsAdminPassword") {
							MRS.needsAdminPassword = response[key];
						}
						if (key == "upnpExternalAddress") {
							MRS.upnpExternalAddress = response[key];
						}
						if (key == "version") {
							MRS.appVersion = response[key];
						}
					}

					if (!isTestnet) {
						$(".testnet_only").hide();
					} else {
						MRS.isTestNet = true;
						var testnetWarningDiv = $("#testnet_warning");
						var warningText = testnetWarningDiv.text() + " The testnet peer port is " + peerPort + (isOffline ? ", the peer is working offline." : ".");
						MRS.logConsole(warningText);
						testnetWarningDiv.text(warningText);
						$(".testnet_only, #testnet_login, #testnet_warning").show();
					}
                    var customLoginWarningDiv = $(".custom_login_warning");
                    if (customLoginWarning) {
                        customLoginWarningDiv.text(customLoginWarning);
                        customLoginWarningDiv.show();
					} else {
						customLoginWarningDiv.hide();
					}

					if (MRS.isInitializePlugins()) {
						MRS.initializePlugins();
					}
					MRS.printEnvInfo();
					MRS.spinner.stop();
					console.log("getState response processed");
					resolve();
				});
			});

			getStatePromise.then(function() {
				console.log("continue initialization");
				var hasLocalStorage = false;
				try {
					//noinspection BadExpressionStatementJS
					window.localStorage && localStorage;
					hasLocalStorage = checkLocalStorage();
				} catch (err) {
					MRS.logConsole("localStorage is disabled, error " + err.message);
					hasLocalStorage = false;
				}

				if (!hasLocalStorage) {
					MRS.logConsole("localStorage is disabled, cannot load wallet");
					// TODO add visible warning
					return; // do not load client if local storage is disabled
				}

				if (!(navigator.userAgent.indexOf('Safari') != -1 &&
					navigator.userAgent.indexOf('Chrome') == -1) &&
					navigator.userAgent.indexOf('JavaFX') == -1) {
					// Don't use account based DB in Safari due to a buggy indexedDB implementation (2015-02-24)
					MRS.createLegacyDatabase();
				}

				if (MRS.mobileSettings.is_check_remember_me) {
					$("#remember_me").prop("checked", true);
				}
				MRS.getSettings(false);

				MRS.getState(function () {
					setTimeout(function () {
						MRS.checkAliasVersions();
					}, 5000);
				});

				$("body").popover({
					"selector": ".show_popover",
					"html": true,
					"trigger": "hover"
				});

				var savedPassphrase = MRS.getStrItem("savedPassphrase");
				if (!savedPassphrase) {
					MRS.showLockscreen();
				}
				MRS.setStateInterval(3);

				setInterval(MRS.checkAliasVersions, 1000 * 60 * 60);

				MRS.allowLoginViaEnter();
				MRS.automaticallyCheckRecipient();

				$("#dashboard_table, #transactions_table").on("mouseenter", "td.confirmations", function () {
					$(this).popover("show");
				}).on("mouseleave", "td.confirmations", function () {
					$(this).popover("destroy");
					$(".popover").remove();
				});

				_fix();

				$(window).on("resize", function () {
					_fix();

					if (MRS.currentPage == "asset_exchange") {
						MRS.positionAssetSidebar();
					}
				});
				// Enable all static tooltip components
				// tooltip components generated dynamically (for tables cells for example)
				// has to be enabled by activating this code on the specific widget
				$("[data-toggle='tooltip']").tooltip();

				console.log("done initialization");
				if (MRS.getUrlParameter("account")) {
					MRS.login(false, MRS.getUrlParameter("account"));
				} else if (savedPassphrase) {
					$("#remember_me").prop("checked", true);
					MRS.login(true, savedPassphrase, null, false, true);
				}
			});
		});
	}

    MRS.initClipboard = function() {
        var clipboard = new Clipboard('#copy_account_id');
        function onCopySuccess(e) {
            $.growl($.t("success_clipboard_copy"), {
                "type": "success"
            });
            e.clearSelection();
        }
        clipboard.on('success', onCopySuccess);
        clipboard.on('error', function(e) {
            if (window.java) {
                if (window.java.copyText(e.text)) {
                    onCopySuccess(e);
                    return;
                }
            }
            MRS.logConsole('Copy failed. Action: ' + e.action + '; Text: ' + e.text);

        });
    };

	function _fix() {
		var height = $(window).height() - $("body > .header").height();
		var content = $(".wrapper").height();

		$(".content.content-stretch:visible").width($(".page:visible").width());
		if (content > height) {
			$(".left-side, html, body").css("min-height", content + "px");
		} else {
			$(".left-side, html, body").css("min-height", height + "px");
		}
	}

	MRS.setStateInterval = function(seconds) {
		if (!MRS.isPollGetState()) {
			return;
		}
		if (seconds == stateIntervalSeconds && stateInterval) {
			return;
		}
		if (stateInterval) {
			clearInterval(stateInterval);
		}
		stateIntervalSeconds = seconds;
		stateInterval = setInterval(function() {
			MRS.getState(null);
			MRS.updateForgingStatus();
		}, 1000 * seconds);
	};

	var _firstTimeAfterLoginRun = false;
	var _prevLastProxyBlock = "0";

	MRS.getLastBlock = function() {
		return MRS.state.apiProxy ? MRS.lastProxyBlock : MRS.state.lastBlock;
	};

	MRS.handleBlockchainStatus = function(response, callback) {
		var firstTime = !("lastBlock" in MRS.state);
		var previousLastBlock = (firstTime ? "0" : MRS.state.lastBlock);

		MRS.state = response;
		var lastBlock = MRS.state.lastBlock;
		var height = response.apiProxy ? MRS.lastProxyBlockHeight : MRS.state.numberOfBlocks - 1;

		MRS.serverConnect = true;
		MRS.ledgerTrimKeep = response.ledgerTrimKeep;
		$("#sidebar_block_link").html(MRS.getBlockLink(height));
		if (firstTime) {
			$("#mrs_version").html(MRS.state.version).removeClass("loading_dots");
			MRS.getBlock(lastBlock, MRS.handleInitialBlocks);
		} else if (MRS.state.isScanning) {
			//do nothing but reset MRS.state so that when isScanning is done, everything is reset.
			isScanning = true;
		} else if (isScanning) {
			//rescan is done, now we must reset everything...
			isScanning = false;
			MRS.blocks = [];
			MRS.tempBlocks = [];
			MRS.getBlock(lastBlock, MRS.handleInitialBlocks);
			if (MRS.account) {
				MRS.getInitialTransactions();
				MRS.getAccountInfo();
			}
		} else if (previousLastBlock != lastBlock) {
			MRS.tempBlocks = [];
			if (MRS.account) {
				MRS.getAccountInfo();
			}
			MRS.getBlock(lastBlock, MRS.handleNewBlocks);
			if (MRS.account) {
				MRS.getNewTransactions();
				MRS.updateApprovalRequests();
			}
		} else {
			if (MRS.account) {
				MRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
					MRS.handleIncomingTransactions(unconfirmedTransactions, false);
				});
			}
		}
		if (MRS.account && !_firstTimeAfterLoginRun) {
			//Executed ~30 secs after login, can be used for tasks needing this condition state
			_firstTimeAfterLoginRun = true;
		}

		if (callback) {
			callback();
		}
	};

    MRS.connectionError = function(errorDescription) {
        MRS.serverConnect = false;
        var msg = $.t("error_server_connect", {url: MRS.getRequestPath()}) +
            (errorDescription ? " " + MRS.escapeRespStr(errorDescription) : "");
        $.growl(msg, {
            "type": "danger",
            "offset": 10
        });
        MRS.logConsole(msg);
    };

    MRS.getState = function(callback, msg) {
		if (msg) {
			MRS.logConsole("getState event " + msg);
		}
		MRS.sendRequest("getBlockchainStatus", {}, function(response) {
			if (response.errorCode) {
                MRS.connectionError(response.errorDescription);
			} else {
				var clientOptionsLink = $("#header_client_options_link");
                if (MRS.isMobileApp()) {
                    clientOptionsLink.html($.t("mobile_client"));
                }
				if (response.apiProxy) {
                    if (!MRS.isMobileApp()) {
                        if (response.isLightClient) {
                            clientOptionsLink.html($.t("light_client"));
                        } else {
                            clientOptionsLink.html($.t("roaming_client"));
                        }
                    }
					MRS.sendRequest("getBlocks", {
						"firstIndex": 0, "lastIndex": 0
					}, function(proxyBlocksResponse) {
						if (proxyBlocksResponse.errorCode) {
                            MRS.connectionError(proxyBlocksResponse.errorDescription);
						} else {
							_prevLastProxyBlock = MRS.lastProxyBlock;
							var prevHeight = MRS.lastProxyBlockHeight;
							MRS.lastProxyBlock = proxyBlocksResponse.blocks[0].block;
							MRS.lastProxyBlockHeight = proxyBlocksResponse.blocks[0].height;
							MRS.lastBlockHeight = MRS.lastProxyBlockHeight;
							MRS.incoming.updateDashboardBlocks(MRS.lastProxyBlockHeight - prevHeight);
							MRS.updateDashboardLastBlock(proxyBlocksResponse.blocks[0]);
							MRS.handleBlockchainStatus(response, callback);
                            MRS.updateDashboardMessage();
						}
					}, { isAsync: false });
					if (!MRS.isMobileApp()) {
						console.log("look for remote confirmation nodes");
						MRS.initRemoteNodesMgr(MRS.isTestnet);
					}
				} else {
					MRS.handleBlockchainStatus(response, callback);
				}
                var clientOptions = $(".client_options");
                if (MRS.isShowClientOptionsLink()) {
                    clientOptions.show();
                } else {
                    clientOptions.hide();
                }
				if (MRS.isShowRemoteWarning()) {
					$(".remote_warning").show();
				}
			}
			/* Checks if the client is connected to active peers */
			MRS.checkConnected();
			//only done so that download progress meter updates correctly based on lastFeederHeight
			if (MRS.downloadingBlockchain) {
				MRS.updateBlockchainDownloadProgress();
			}
		});
	};

	$("#logo, .sidebar-menu").on("click", "a", function(e, data) {
		if ($(this).hasClass("ignore")) {
			$(this).removeClass("ignore");
			return;
		}

		e.preventDefault();

		if ($(this).data("toggle") == "modal") {
			return;
		}

		var page = $(this).data("page");

		if (page == MRS.currentPage) {
			if (data && data.callback) {
				data.callback();
			}
			return;
		}

		$(".page").hide();

		$(document.documentElement).scrollTop(0);

		$("#" + page + "_page").show();

		$(".content-header h1").find(".loading_dots").remove();

        var $newActiveA;
        if ($(this).attr("id") && $(this).attr("id") == "logo") {
            $newActiveA = $("#dashboard_link").find("a");
		} else {
			$newActiveA = $(this);
		}
		var $newActivePageLi = $newActiveA.closest("li.treeview");

		$("ul.sidebar-menu > li.active").each(function(key, elem) {
			if ($newActivePageLi.attr("id") != $(elem).attr("id")) {
				$(elem).children("a").first().addClass("ignore").click();
			}
		});

		$("ul.sidebar-menu > li.sm_simple").removeClass("active");
		if ($newActiveA.parent("li").hasClass("sm_simple")) {
			$newActiveA.parent("li").addClass("active");
		}

		$("ul.sidebar-menu li.sm_treeview_submenu").removeClass("active");
		if($(this).parent("li").hasClass("sm_treeview_submenu")) {
			$(this).closest("li").addClass("active");
		}

		if (MRS.currentPage != "messages") {
			$("#inline_message_password").val("");
		}

		//MRS.previousPage = MRS.currentPage;
		MRS.currentPage = page;
		MRS.currentSubPage = "";
		MRS.pageNumber = 1;
		MRS.showPageNumbers = false;

		if (MRS.pages[page]) {
			MRS.pageLoading();
			MRS.resetNotificationState(page);
            var callback;
            if (data) {
				if (data.callback) {
					callback = data.callback;
				} else {
					callback = data;
				}
			} else {
				callback = undefined;
			}
            var subpage;
            if (data && data.subpage) {
                subpage = data.subpage;
			} else {
				subpage = undefined;
			}
			MRS.pages[page](callback, subpage);
		}
	});

	$("button.goto-page, a.goto-page").click(function(event) {
		event.preventDefault();
		MRS.goToPage($(this).data("page"), undefined, $(this).data("subpage"));
	});

	MRS.loadPage = function(page, callback, subpage) {
		MRS.pageLoading();
		MRS.pages[page](callback, subpage);
	};

	MRS.goToPage = function(page, callback, subpage) {
		var $link = $("ul.sidebar-menu a[data-page=" + page + "]");

		if ($link.length > 1) {
			if ($link.last().is(":visible")) {
				$link = $link.last();
			} else {
				$link = $link.first();
			}
		}

		if ($link.length == 1) {
			$link.trigger("click", [{
				"callback": callback,
				"subpage": subpage
			}]);
			MRS.resetNotificationState(page);
		} else {
			MRS.currentPage = page;
			MRS.currentSubPage = "";
			MRS.pageNumber = 1;
			MRS.showPageNumbers = false;

			$("ul.sidebar-menu a.active").removeClass("active");
			$(".page").hide();
			$("#" + page + "_page").show();
			if (MRS.pages[page]) {
				MRS.pageLoading();
				MRS.resetNotificationState(page);
				MRS.pages[page](callback, subpage);
			}
		}
	};

	MRS.pageLoading = function() {
		MRS.hasMorePages = false;

		var $pageHeader = $("#" + MRS.currentPage + "_page .content-header h1");
		$pageHeader.find(".loading_dots").remove();
		$pageHeader.append("<span class='loading_dots'><span>.</span><span>.</span><span>.</span></span>");
	};

	MRS.pageLoaded = function(callback) {
		var $currentPage = $("#" + MRS.currentPage + "_page");

		$currentPage.find(".content-header h1 .loading_dots").remove();

		if ($currentPage.hasClass("paginated")) {
			MRS.addPagination();
		}

		if (callback) {
			try {
                callback();
            } catch(e) { /* ignore since sometimes callback is not a function */ }
		}
	};

	MRS.addPagination = function () {
        var firstStartNr = 1;
		var firstEndNr = MRS.itemsPerPage;
		var currentStartNr = (MRS.pageNumber-1) * MRS.itemsPerPage + 1;
		var currentEndNr = MRS.pageNumber * MRS.itemsPerPage;

		var prevHTML = '<span style="display:inline-block;width:48px;text-align:right;">';
		var firstHTML = '<span style="display:inline-block;min-width:48px;text-align:right;vertical-align:top;margin-top:4px;">';
		var currentHTML = '<span style="display:inline-block;min-width:48px;text-align:left;vertical-align:top;margin-top:4px;">';
		var nextHTML = '<span style="display:inline-block;width:48px;text-align:left;">';

		if (MRS.pageNumber > 1) {
			prevHTML += "<a href='#' data-page='" + (MRS.pageNumber - 1) + "' title='" + $.t("previous") + "' style='font-size:20px;'>";
			prevHTML += "<i class='fa fa-arrow-circle-left'></i></a>";
		} else {
			prevHTML += '&nbsp;';
		}

		if (MRS.hasMorePages) {
			currentHTML += currentStartNr + "-" + currentEndNr + "&nbsp;";
			nextHTML += "<a href='#' data-page='" + (MRS.pageNumber + 1) + "' title='" + $.t("next") + "' style='font-size:20px;'>";
			nextHTML += "<i class='fa fa-arrow-circle-right'></i></a>";
		} else {
			if (MRS.pageNumber > 1) {
				currentHTML += currentStartNr + "+";
			} else {
				currentHTML += "&nbsp;";
			}
			nextHTML += "&nbsp;";
		}
		if (MRS.pageNumber > 1) {
			firstHTML += "&nbsp;<a href='#' data-page='1'>" + firstStartNr + "-" + firstEndNr + "</a>&nbsp;|&nbsp;";
		} else {
			firstHTML += "&nbsp;";
		}

		prevHTML += '</span>';
		firstHTML += '</span>';
		currentHTML += '</span>';
		nextHTML += '</span>';

		var output = prevHTML + firstHTML + currentHTML + nextHTML;
		var $paginationContainer = $("#" + MRS.currentPage + "_page .data-pagination");

		if ($paginationContainer.length) {
			$paginationContainer.html(output);
		}
	};

	$(document).on("click", ".data-pagination a", function(e) {
		e.preventDefault();
		MRS.goToPageNumber($(this).data("page"));
	});

	MRS.goToPageNumber = function(pageNumber) {
		/*if (!pageLoaded) {
			return;
		}*/
		MRS.pageNumber = pageNumber;

		MRS.pageLoading();

		MRS.pages[MRS.currentPage]();
	};

	function initUserDB() {
		MRS.storageSelect("data", [{
			"id": "asset_exchange_version"
		}], function(error, result) {
			if (!result || !result.length) {
				MRS.storageDelete("assets", [], function(error) {
					if (!error) {
						MRS.storageInsert("data", "id", {
							"id": "asset_exchange_version",
							"contents": 2
						});
					}
				});
			}
		});

		MRS.storageSelect("data", [{
			"id": "closed_groups"
		}], function(error, result) {
			if (result && result.length) {
				MRS.setClosedGroups(result[0].contents.split("#"));
			} else {
				MRS.storageInsert("data", "id", {
					id: "closed_groups",
					contents: ""
				});
			}
		});
		MRS.loadContacts();
		MRS.getSettings(true);
		MRS.updateNotifications();
		MRS.setUnconfirmedNotifications();
		MRS.setPhasingNotifications();
        MRS.setShufflingNotifications();
		var page = MRS.getUrlParameter("page");
		if (page) {
			page = page.escapeHTML();
			if (MRS.pages[page]) {
				MRS.goToPage(page);
			} else {
				$.growl($.t("page") + " " + page + " " + $.t("does_not_exist"), {
					"type": "danger",
					"offset": 50
				});
			}
		}
		if (MRS.getUrlParameter("modal")) {
			var urlParams = [];
			if (window.location.search && window.location.search.length > 1) {
				urlParams = window.location.search.substring(1).split('&');
			}
			var modalId = "#" + MRS.getUrlParameter("modal").escapeHTML();
			var modal = $(modalId);
			var attributes = {};
			if (modal[0]) {
				var isValidParams = true;
				for (var i = 0; i < urlParams.length; i++) {
					var paramKeyValue = urlParams[i].split('=');
					if (paramKeyValue.length != 2) {
						continue;
					}
					var key = paramKeyValue[0].escapeHTML();
					if (key == "account" || key == "modal") {
						continue;
					}
					var value = paramKeyValue[1].escapeHTML();
                    var input = modal.find("input[name=" + key + "]");
                    if (input[0]) {
						if (input.attr("type") == "text") {
							input.val(value);
						} else if (input.attr("type") == "checkbox") {
							var isChecked = false;
							if (value != "true" && value != "false") {
								isValidParams = false;
								$.growl($.t("value") + " " + value + " " + $.t("must_be_true_or_false") + " " + $.t("for") + " " + key, {
									"type": "danger",
									"offset": 50
								});
							} else if (value == "true") {
								isChecked = true;
							}
							if (isValidParams) {
								input.prop('checked', isChecked);
							}
						}
					} else if (modal.find("textarea[name=" + key + "]")[0]) {
						modal.find("textarea[name=" + key + "]").val(decodeURI(value));
					} else {
						attributes["data-" + key.toLowerCase().escapeHTML()] = String(value).escapeHTML();
					}
				}
				if (isValidParams) {
					var a = $('<a />');
					a.attr('href', '#');
					a.attr('data-toggle', 'modal');
					a.attr('data-target', modalId);
					Object.keys(attributes).forEach(function (key) {
						a.attr(key, attributes[key]);
					});
					$('body').append(a);
					a.click();
				}
			} else {
				$.growl($.t("modal") + " " + modalId + " " + $.t("does_not_exist"), {
					"type": "danger",
					"offset": 50
				});
			}
		}
	}

	MRS.initUserDBSuccess = function() {
		MRS.databaseSupport = true;
		initUserDB();
        MRS.logConsole("IndexedDB initialized");
    };

	MRS.initUserDBWithLegacyData = function() {
		var legacyTables = ["contacts", "assets", "data"];
		$.each(legacyTables, function(key, table) {
			MRS.legacyDatabase.select(table, null, function(error, results) {
				if (!error && results && results.length >= 0) {
					MRS.database.insert(table, results, function(error, inserts) {});
				}
			});
		});
		setTimeout(function(){ MRS.initUserDBSuccess(); }, 1000);
	};

	MRS.initLocalStorage = function() {
		MRS.database = null;
		MRS.databaseSupport = false;
		initUserDB();
        MRS.logConsole("local storage initialized");
    };

	MRS.createLegacyDatabase = function() {
		var schema = {};
		var versionLegacyDB = 2;

		// Legacy DB before switching to account based DBs, leave schema as is
		schema["contacts"] = {
			id: {
				"primary": true,
				"autoincrement": true,
				"type": "NUMBER"
			},
			name: "VARCHAR(100) COLLATE NOCASE",
			email: "VARCHAR(200)",
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			description: "TEXT"
		};
		schema["assets"] = {
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			asset: {
				"primary": true,
				"type": "VARCHAR(25)"
			},
			description: "TEXT",
			name: "VARCHAR(10)",
			decimals: "NUMBER",
			quantityQNT: "VARCHAR(15)",
			groupName: "VARCHAR(30) COLLATE NOCASE"
		};
		schema["data"] = {
			id: {
				"primary": true,
				"type": "VARCHAR(40)"
			},
			contents: "TEXT"
		};
		if (versionLegacyDB == MRS.constants.DB_VERSION) {
			try {
				MRS.legacyDatabase = new WebDB("MRS_USER_DB", schema, versionLegacyDB, 4, function(error) {
					if (!error) {
						MRS.legacyDatabase.select("data", [{
							"id": "settings"
						}], function(error, result) {
							if (result && result.length > 0) {
								MRS.legacyDatabaseWithData = true;
							}
						});
					}
				});
			} catch (err) {
                MRS.logConsole("error creating database " + err.message);
			}
		}
	};

	function createSchema(){
		var schema = {};

		schema["contacts"] = {
			id: {
				"primary": true,
				"autoincrement": true,
				"type": "NUMBER"
			},
			name: "VARCHAR(100) COLLATE NOCASE",
			email: "VARCHAR(200)",
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			description: "TEXT"
		};
		schema["assets"] = {
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			asset: {
				"primary": true,
				"type": "VARCHAR(25)"
			},
			description: "TEXT",
			name: "VARCHAR(10)",
			decimals: "NUMBER",
			quantityQNT: "VARCHAR(15)",
			groupName: "VARCHAR(30) COLLATE NOCASE"
		};
		schema["polls"] = {
			account: "VARCHAR(25)",
			accountRS: "VARCHAR(25)",
			name: "VARCHAR(100)",
			description: "TEXT",
			poll: "VARCHAR(25)",
			finishHeight: "VARCHAR(25)"
		};
		schema["data"] = {
			id: {
				"primary": true,
				"type": "VARCHAR(40)"
			},
			contents: "TEXT"
		};
		return schema;
	}

	function initUserDb(){
		MRS.logConsole("Database is open");
		MRS.database.select("data", [{
			"id": "settings"
		}], function(error, result) {
			if (result && result.length > 0) {
				MRS.logConsole("Settings already exist");
				MRS.databaseFirstStart = false;
				MRS.initUserDBSuccess();
			} else {
				MRS.logConsole("Settings not found");
				MRS.databaseFirstStart = true;
				if (MRS.legacyDatabaseWithData) {
					MRS.initUserDBWithLegacyData();
				} else {
					MRS.initUserDBSuccess();
				}
			}
		});
	}

	MRS.createDatabase = function (dbName) {
		if (!MRS.isIndexedDBSupported()) {
			MRS.logConsole("IndexedDB not supported by the rendering engine, using localStorage instead");
			MRS.initLocalStorage();
			return;
		}
		var schema = createSchema();
		MRS.assetTableKeys = ["account", "accountRS", "asset", "description", "name", "position", "decimals", "quantityQNT", "groupName"];
		MRS.pollsTableKeys = ["account", "accountRS", "poll", "description", "name", "finishHeight"];
		try {
			MRS.logConsole("Opening database " + dbName);
            MRS.database = new WebDB(dbName, schema, MRS.constants.DB_VERSION, 4, function(error, db) {
                if (!error) {
                    MRS.indexedDB = db;
                    initUserDb();
                } else {
                    MRS.logConsole("Error opening database " + error);
                    MRS.initLocalStorage();
                }
            });
            MRS.logConsole("Opening database " + MRS.database);
		} catch (e) {
			MRS.logConsole("Exception opening database " + e.message);
			MRS.initLocalStorage();
		}
	};

	/* Display connected state in Sidebar */
	MRS.checkConnected = function() {
		MRS.sendRequest("getPeers+", {
			"state": "CONNECTED"
		}, function(response) {
            var connectedIndicator = $("#connected_indicator");
            if (response.peers && response.peers.length) {
				MRS.peerConnect = true;
				connectedIndicator.addClass("connected");
                connectedIndicator.find("span").html($.t("Connected")).attr("data-i18n", "connected");
				connectedIndicator.show();
			} else {
				MRS.peerConnect = false;
				connectedIndicator.removeClass("connected");
				connectedIndicator.find("span").html($.t("Not Connected")).attr("data-i18n", "not_connected");
				connectedIndicator.show();
			}
		});
	};

	MRS.getRequestPath = function (noProxy) {
		var url = MRS.getRemoteNodeUrl();
		if (!MRS.state.apiProxy || noProxy) {
			return url + "/metro";
		} else {
			return url + "/metro-proxy";
		}
	};

	MRS.getAccountInfo = function(firstRun, callback, isAccountSwitch) {
		MRS.sendRequest("getAccount", {
			"account": MRS.account,
			"includeAssets": true,
			"includeLessors": true,
			"includeEffectiveBalance": true
		}, function(response) {
			var previousAccountInfo = MRS.accountInfo;
			MRS.accountInfo = response;
			if (response.errorCode) {
				MRS.logConsole("Get account info error (" + response.errorCode + ") " + response.errorDescription);
				$("#account_balance, #account_balance_sidebar, #account_nr_currencies, #account_message_count, #account_alias_count").html("0");
                MRS.updateDashboardMessage();
			} else {
				if (MRS.accountRS && MRS.accountInfo.accountRS != MRS.accountRS) {

					$.growl("Generated Reed Solomon address different from the one in the blockchain!", {
						"type": "danger"
					});
					MRS.accountRS = MRS.accountInfo.accountRS;
				}
                MRS.updateDashboardMessage();
                $("#account_balance, #account_balance_sidebar").html(MRS.formatStyledAmount(response.unconfirmedBalanceMQT));
                $("#account_balance_sidebar").attr('title', $.t("spendable_balance_sidebar"));
                if (response.balanceMQT && response.balanceMQT != response.unconfirmedBalanceMQT) {
                    $("#account_full_balance_sidebar").html("(" + MRS.formatStyledAmount(response.balanceMQT) + ")");
                    $("#account_full_balance_sidebar").attr('title', $.t("balance_sidebar"));
                }
                $("#account_forged_balance").html(MRS.formatStyledAmount(response.forgedBalanceMQT));

                if (MRS.isDisplayOptionalDashboardTiles()) {
                    // only show if happened within last week and not during account switch
                    var showAssetDifference = !isAccountSwitch &&
                        ((!MRS.downloadingBlockchain || (MRS.blocks && MRS.blocks[0] && MRS.state && MRS.state.time - MRS.blocks[0].timestamp < 60 * 60 * 24 * 7)));

                    // When switching account this query returns error
                    if (!isAccountSwitch) {
                        MRS.storageSelect("data", [{
                            "id": "asset_balances"
                        }], function (error, asset_balance) {
                            if (asset_balance && asset_balance.length) {
                                var previous_balances = asset_balance[0].contents;
                                if (!MRS.accountInfo.assetBalances) {
                                    MRS.accountInfo.assetBalances = [];
                                }
                                var current_balances = JSON.stringify(MRS.accountInfo.assetBalances);
                                if (previous_balances != current_balances) {
                                    if (previous_balances != "undefined" && typeof previous_balances != "undefined") {
                                        previous_balances = JSON.parse(previous_balances);
                                    } else {
                                        previous_balances = [];
                                    }
                                    MRS.storageUpdate("data", {
                                        contents: current_balances
                                    }, [{
                                        id: "asset_balances"
                                    }]);
                                    if (showAssetDifference) {
                                        MRS.checkAssetDifferences(MRS.accountInfo.assetBalances, previous_balances);
                                    }
                                }
                            } else {
                                MRS.storageInsert("data", "id", {
                                    id: "asset_balances",
                                    contents: JSON.stringify(MRS.accountInfo.assetBalances)
                                });
                            }
                        });
                    }

                    var i;
                    if ((firstRun || isAccountSwitch) && response.assetBalances) {
                        var assets = [];
                        var assetBalances = response.assetBalances;
                        var assetBalancesMap = {};
                        for (i = 0; i < assetBalances.length; i++) {
                            if (assetBalances[i].balanceQNT != "0") {
                                assets.push(assetBalances[i].asset);
                                assetBalancesMap[assetBalances[i].asset] = assetBalances[i].balanceQNT;
                            }
                        }
                        MRS.sendRequest("getLastTrades", {
                            "assets": assets
                        }, function (response) {
                            if (response.trades && response.trades.length) {
                                var assetTotal = 0;
                                for (i = 0; i < response.trades.length; i++) {
                                    var trade = response.trades[i];
                                    assetTotal += assetBalancesMap[trade.asset] * trade.priceMQT / 100000000;
                                }
                                $("#account_assets_balance").html(MRS.formatStyledAmount(new Big(assetTotal).toFixed(8)));
                                $("#account_nr_assets").html(response.trades.length);
                            } else {
                                $("#account_assets_balance").html(0);
                                $("#account_nr_assets").html(0);
                            }
                        });
                    } else {
                        if (!response.assetBalances) {
                            $("#account_assets_balance").html(0);
                            $("#account_nr_assets").html(0);
                        }
                    }

                    /* Display message count in top and limit to 100 for now because of possible performance issues*/
                    MRS.sendRequest("getBlockchainTransactions+", {
                        "account": MRS.account,
                        "type": 1,
                        "subtype": 0,
                        "firstIndex": 0,
                        "lastIndex": 99
                    }, function (response) {
                        if (response.transactions && response.transactions.length) {
                            if (response.transactions.length > 99)
                                $("#account_message_count").empty().append("99+");
                            else
                                $("#account_message_count").empty().append(response.transactions.length);
                        } else {
                            $("#account_message_count").empty().append("0");
                        }
                    });

                    MRS.sendRequest("getAliasCount+", {
                        "account": MRS.account
                    }, function (response) {
                        if (response.numberOfAliases != null) {
                            $("#account_alias_count").empty().append(response.numberOfAliases);
                        }
                    });

                    $(".optional_dashboard_tile").show();
                } else {
                    // Hide the optional tiles and move the block info tile to the first row
                    $(".optional_dashboard_tile").hide();
                    var blockInfoTile = $(".block_info_dashboard_tile").detach();
                    blockInfoTile.appendTo($(".dashboard_first_row"));
                }

                var leasingChange = false;
				if (MRS.lastBlockHeight) {
					var isLeased = MRS.lastBlockHeight >= MRS.accountInfo.currentLeasingHeightFrom;
					if (isLeased != MRS.IsLeased) {
						leasingChange = true;
						MRS.isLeased = isLeased;
					}
				}

				if (leasingChange ||
					(response.currentLeasingHeightFrom != previousAccountInfo.currentLeasingHeightFrom) ||
					(response.lessors && !previousAccountInfo.lessors) ||
					(!response.lessors && previousAccountInfo.lessors) ||
					(response.lessors && previousAccountInfo.lessors && response.lessors.sort().toString() != previousAccountInfo.lessors.sort().toString())) {
					MRS.updateAccountLeasingStatus();
				}

				MRS.updateAccountControlStatus();

				if (response.name) {
					$("#account_name").html(MRS.addEllipsis(MRS.escapeRespStr(response.name), 17)).removeAttr("data-i18n");
				} else {
					$("#account_name").html($.t("set_account_info"));
				}
			}

			if (firstRun) {
				$("#account_balance, #account_balance_sidebar, #account_assets_balance, #account_nr_assets, #account_nr_currencies, #account_message_count, #account_alias_count").removeClass("loading_dots");
			}

			if (callback) {
				callback();
			}
		});
	};

    MRS.updateDashboardMessage = function() {
        if (MRS.accountInfo.errorCode) {
            if (MRS.accountInfo.errorCode == 5) {
                if (MRS.downloadingBlockchain && !(MRS.state && MRS.state.apiProxy) && !MRS.state.isLightClient) {
                    if (MRS.newlyCreatedAccount) {
                        $("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html($.t("status_new_account", {
                                "account_id": MRS.escapeRespStr(MRS.accountRS),
                                "public_key": MRS.escapeRespStr(MRS.publicKey)
                            }) +
                            MRS.getPassphraseValidationLink() +
							"<br/><br/>" + MRS.blockchainDownloadingMessage() +
                            "<br/><br/>" + MRS.getFundAccountLink()).show();
                    } else {
                        $("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html(MRS.blockchainDownloadingMessage()).show();
                    }
                } else if (MRS.state && MRS.state.isScanning) {
                    $("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html($.t("status_blockchain_rescanning")).show();
                } else {
                    var message;
                    if (MRS.publicKey == "") {
                        message = $.t("status_new_account_no_pk_v2", {
                            "account_id": MRS.escapeRespStr(MRS.accountRS)
                        });
                        message += MRS.getPassphraseValidationLink();
                        if (MRS.downloadingBlockchain) {
                            message += "<br/><br/>" + MRS.blockchainDownloadingMessage();
                        }
                    } else {
                        message = $.t("status_new_account", {
                            "account_id": MRS.escapeRespStr(MRS.accountRS),
                            "public_key": MRS.escapeRespStr(MRS.publicKey)
                        });
                        message += MRS.getPassphraseValidationLink();
                        if (MRS.downloadingBlockchain) {
                            message += "<br/><br/>" + MRS.blockchainDownloadingMessage();
                        }
                        message += "<br/><br/>" + MRS.getFundAccountLink();
                    }
                    $("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html(message).show();
                }
            } else {
                $("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html(MRS.accountInfo.errorDescription ? MRS.escapeRespStr(MRS.accountInfo.errorDescription) : $.t("error_unknown")).show();
            }
        } else {
            if (MRS.downloadingBlockchain) {
                $("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html(MRS.blockchainDownloadingMessage()).show();
            } else if (MRS.state && MRS.state.isScanning) {
                $("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html($.t("status_blockchain_rescanning")).show();
            } else if (!MRS.accountInfo.publicKey) {
                var warning = MRS.publicKey != 'undefined' ? $.t("public_key_not_announced_warning", { "public_key": MRS.publicKey }) : $.t("no_public_key_warning");
                $("#dashboard_message").addClass("alert-danger").removeClass("alert-success").html(warning + " " + $.t("public_key_actions")).show();
            } else if (MRS.state.isLightClient) {
                $("#dashboard_message").addClass("alert-success").removeClass("alert-danger").html(MRS.blockchainDownloadingMessage()).show();
            } else {
                $("#dashboard_message").hide();
            }
        }
    };

	MRS.updateAccountLeasingStatus = function() {
		var accountLeasingLabel = "";
		var accountLeasingStatus = "";
		var nextLesseeStatus = "";
		if (MRS.accountInfo.nextLeasingHeightFrom < MRS.constants.MAX_INT_JAVA) {
			nextLesseeStatus = $.t("next_lessee_status", {
				"start": MRS.escapeRespStr(MRS.accountInfo.nextLeasingHeightFrom),
				"end": MRS.escapeRespStr(MRS.accountInfo.nextLeasingHeightTo),
				"account": String(MRS.convertNumericToRSAccountFormat(MRS.accountInfo.nextLessee)).escapeHTML()
			})
		}

		if (MRS.lastBlockHeight >= MRS.accountInfo.currentLeasingHeightFrom) {
			accountLeasingLabel = $.t("leased_out");
			accountLeasingStatus = $.t("balance_is_leased_out", {
				"blocks": String(MRS.accountInfo.currentLeasingHeightTo - MRS.lastBlockHeight).escapeHTML(),
				"end": MRS.escapeRespStr(MRS.accountInfo.currentLeasingHeightTo),
				"account": MRS.escapeRespStr(MRS.accountInfo.currentLesseeRS)
			});
			$("#lease_balance_message").html($.t("balance_leased_out_help"));
		} else if (MRS.lastBlockHeight < MRS.accountInfo.currentLeasingHeightTo) {
			accountLeasingLabel = $.t("leased_soon");
			accountLeasingStatus = $.t("balance_will_be_leased_out", {
				"blocks": String(MRS.accountInfo.currentLeasingHeightFrom - MRS.lastBlockHeight).escapeHTML(),
				"start": MRS.escapeRespStr(MRS.accountInfo.currentLeasingHeightFrom),
				"end": MRS.escapeRespStr(MRS.accountInfo.currentLeasingHeightTo),
				"account": MRS.escapeRespStr(MRS.accountInfo.currentLesseeRS)
			});
			$("#lease_balance_message").html($.t("balance_leased_out_help"));
		} else {
			accountLeasingStatus = $.t("balance_not_leased_out");
			$("#lease_balance_message").html($.t("balance_leasing_help"));
		}
		if (nextLesseeStatus != "") {
			accountLeasingStatus += "<br>" + nextLesseeStatus;
		}

		//no reed solomon available? do it myself? todo
        var accountLessorTable = $("#account_lessor_table");
        if (MRS.accountInfo.lessors) {
			if (accountLeasingLabel) {
				accountLeasingLabel += ", ";
				accountLeasingStatus += "<br /><br />";
			}

			accountLeasingLabel += $.t("x_lessor", {
				"count": MRS.accountInfo.lessors.length
			});
			accountLeasingStatus += $.t("x_lessor_lease", {
				"count": MRS.accountInfo.lessors.length
			});

			var rows = "";

			for (var i = 0; i < MRS.accountInfo.lessorsRS.length; i++) {
				var lessor = MRS.accountInfo.lessorsRS[i];
				var lessorInfo = MRS.accountInfo.lessorsInfo[i];
				var blocksLeft = lessorInfo.currentHeightTo - MRS.lastBlockHeight;
				var blocksLeftTooltip = "From block " + lessorInfo.currentHeightFrom + " to block " + lessorInfo.currentHeightTo;
				var nextLessee = "Not set";
				var nextTooltip = "Next lessee not set";
				if (lessorInfo.nextLesseeRS == MRS.accountRS) {
					nextLessee = "You";
					nextTooltip = "From block " + lessorInfo.nextHeightFrom + " to block " + lessorInfo.nextHeightTo;
				} else if (lessorInfo.nextHeightFrom < MRS.constants.MAX_INT_JAVA) {
					nextLessee = "Not you";
					nextTooltip = "Account " + MRS.getAccountTitle(lessorInfo.nextLesseeRS) +" from block " + lessorInfo.nextHeightFrom + " to block " + lessorInfo.nextHeightTo;
				}
				rows += "<tr>" +
					"<td>" + MRS.getAccountLink({ lessorRS: lessor }, "lessor") + "</td>" +
					"<td>" + MRS.escapeRespStr(lessorInfo.effectiveBalanceMTR) + "</td>" +
					"<td><label>" + String(blocksLeft).escapeHTML() + " <i class='fa fa-question-circle show_popover' data-toggle='tooltip' title='" + blocksLeftTooltip + "' data-placement='right' style='color:#4CAA6E'></i></label></td>" +
					"<td><label>" + String(nextLessee).escapeHTML() + " <i class='fa fa-question-circle show_popover' data-toggle='tooltip' title='" + nextTooltip + "' data-placement='right' style='color:#4CAA6E'></i></label></td>" +
				"</tr>";
			}

			accountLessorTable.find("tbody").empty().append(rows);
			$("#account_lessor_container").show();
			accountLessorTable.find("[data-toggle='tooltip']").tooltip();
		} else {
			accountLessorTable.find("tbody").empty();
			$("#account_lessor_container").hide();
		}

		if (accountLeasingLabel) {
			$("#account_leasing").html(accountLeasingLabel).show();
		} else {
			$("#account_leasing").hide();
		}

		if (accountLeasingStatus) {
			$("#account_leasing_status").html(accountLeasingStatus).show();
		} else {
			$("#account_leasing_status").hide();
		}
	};

	MRS.updateAccountControlStatus = function() {
		var onNoPhasingOnly = function() {
			$("#setup_mandatory_approval").show();
			$("#mandatory_approval_details").hide();
			delete MRS.accountInfo.phasingOnly;
		};
		if (MRS.accountInfo.accountControls && $.inArray('PHASING_ONLY', MRS.accountInfo.accountControls) > -1) {
			MRS.sendRequest("getPhasingOnlyControl", {
				"account": MRS.account
			}, function (response) {
				if (response && response.votingModel >= 0) {
					$("#setup_mandatory_approval").hide();
					$("#mandatory_approval_details").show();

					MRS.accountInfo.phasingOnly = response;
					var infoTable = $("#mandatory_approval_info_table");
					infoTable.find("tbody").empty();
					var data = {};
					var params = MRS.phasingControlObjectToPhasingParams(response);
					params.phasingWhitelist = params.phasingWhitelisted;
					MRS.getPhasingDetails(data, params);
					delete data.full_hash_formatted_html;
					if (response.minDuration) {
						data.minimum_duration_short = response.minDuration;
					}
					if (response.maxDuration) {
						data.maximum_duration_short = response.maxDuration;
					}
					if (response.maxFees) {
						data.maximum_fees = MRS.convertToMTR(response.maxFees);
					}
					infoTable.find("tbody").append(MRS.createInfoTable(data));
					infoTable.show();
				} else {
					onNoPhasingOnly();
				}
			});
		} else {
			onNoPhasingOnly();
		}
	};

	MRS.checkAssetDifferences = function(current_balances, previous_balances) {
		var current_balances_ = {};
		var previous_balances_ = {};

		if (previous_balances && previous_balances.length) {
			for (var k in previous_balances) {
                if (!previous_balances.hasOwnProperty(k)) {
                    continue;
                }
				previous_balances_[previous_balances[k].asset] = previous_balances[k].balanceQNT;
			}
		}

		if (current_balances && current_balances.length) {
			for (k in current_balances) {
                if (!current_balances.hasOwnProperty(k)) {
                    continue;
                }
				current_balances_[current_balances[k].asset] = current_balances[k].balanceQNT;
			}
		}

		var diff = {};

		for (k in previous_balances_) {
            if (!previous_balances_.hasOwnProperty(k)) {
                continue;
            }
			if (!(k in current_balances_)) {
				diff[k] = "-" + previous_balances_[k];
			} else if (previous_balances_[k] !== current_balances_[k]) {
                diff[k] = (new BigInteger(current_balances_[k]).subtract(new BigInteger(previous_balances_[k]))).toString();
			}
		}

		for (k in current_balances_) {
            if (!current_balances_.hasOwnProperty(k)) {
                continue;
            }
			if (!(k in previous_balances_)) {
				diff[k] = current_balances_[k]; // property is new
			}
		}

		var nr = Object.keys(diff).length;
		if (nr == 0) {
        } else if (nr <= 3) {
			for (k in diff) {
                if (!diff.hasOwnProperty(k)) {
                    continue;
                }
				MRS.sendRequest("getAsset", {
					"asset": k,
					"_extra": {
						"asset": k,
						"difference": diff[k]
					}
				}, function(asset, input) {
					if (asset.errorCode) {
						return;
					}
					asset.difference = input["_extra"].difference;
					asset.asset = input["_extra"].asset;
                    var quantity;
					if (asset.difference.charAt(0) != "-") {
						quantity = MRS.formatQuantity(asset.difference, asset.decimals);

						if (quantity != "0") {
							if (parseInt(quantity) == 1) {
								$.growl($.t("you_received_assets", {
									"name": MRS.escapeRespStr(asset.name)
								}), {
									"type": "success"
								});
							} else {
								$.growl($.t("you_received_assets_plural", {
									"name": MRS.escapeRespStr(asset.name),
									"count": quantity
								}), {
									"type": "success"
								});
							}
							MRS.loadAssetExchangeSidebar();
						}
					} else {
						asset.difference = asset.difference.substring(1);
						quantity = MRS.formatQuantity(asset.difference, asset.decimals);
						if (quantity != "0") {
							if (parseInt(quantity) == 1) {
								$.growl($.t("you_sold_assets", {
									"name": MRS.escapeRespStr(asset.name)
								}), {
									"type": "success"
								});
							} else {
								$.growl($.t("you_sold_assets_plural", {
									"name": MRS.escapeRespStr(asset.name),
									"count": quantity
								}), {
									"type": "success"
								});
							}
							MRS.loadAssetExchangeSidebar();
						}
					}
				});
			}
		} else {
			$.growl($.t("multiple_assets_differences"), {
				"type": "success"
			});
		}
	};

	MRS.updateBlockchainDownloadProgress = function() {
		var lastNumBlocks = 5000;
        var downloadingBlockchain = $('#downloading_blockchain');
        downloadingBlockchain.find('.last_num_blocks').html($.t('last_num_blocks', { "blocks": lastNumBlocks }));

		if (MRS.state.isLightClient) {
			downloadingBlockchain.hide();
		} else if (!MRS.serverConnect || !MRS.peerConnect) {
			downloadingBlockchain.show();
			downloadingBlockchain.find(".db_active").hide();
			downloadingBlockchain.find(".db_halted").show();
		} else {
			downloadingBlockchain.show();
			downloadingBlockchain.find(".db_halted").hide();
			downloadingBlockchain.find(".db_active").show();

			var percentageTotal = 0;
			var blocksLeft;
			var percentageLast = 0;
			if (MRS.state.lastBlockchainFeederHeight && MRS.state.numberOfBlocks <= MRS.state.lastBlockchainFeederHeight) {
				percentageTotal = parseInt(Math.round((MRS.state.numberOfBlocks / MRS.state.lastBlockchainFeederHeight) * 100), 10);
				blocksLeft = MRS.state.lastBlockchainFeederHeight - MRS.state.numberOfBlocks;
				if (blocksLeft <= lastNumBlocks && MRS.state.lastBlockchainFeederHeight > lastNumBlocks) {
					percentageLast = parseInt(Math.round(((lastNumBlocks - blocksLeft) / lastNumBlocks) * 100), 10);
				}
			}
			if (!blocksLeft || blocksLeft < parseInt(lastNumBlocks / 2)) {
				downloadingBlockchain.find(".db_progress_total").hide();
			} else {
				downloadingBlockchain.find(".db_progress_total").show();
				downloadingBlockchain.find(".db_progress_total .progress-bar").css("width", percentageTotal + "%");
				downloadingBlockchain.find(".db_progress_total .sr-only").html($.t("percent_complete", {
					"percent": percentageTotal
				}));
			}
			if (!blocksLeft || blocksLeft >= (lastNumBlocks * 2) || MRS.state.lastBlockchainFeederHeight <= lastNumBlocks) {
				downloadingBlockchain.find(".db_progress_last").hide();
			} else {
				downloadingBlockchain.find(".db_progress_last").show();
				downloadingBlockchain.find(".db_progress_last .progress-bar").css("width", percentageLast + "%");
				downloadingBlockchain.find(".db_progress_last .sr-only").html($.t("percent_complete", {
					"percent": percentageLast
				}));
			}
			if (blocksLeft) {
				downloadingBlockchain.find(".blocks_left_outer").show();
				downloadingBlockchain.find(".blocks_left").html($.t("blocks_left", { "numBlocks": blocksLeft }));
			}
		}
	};

	MRS.checkIfOnAFork = function() {
		if (!MRS.downloadingBlockchain) {
			var isForgingAllBlocks = true;
			if (MRS.blocks && MRS.blocks.length >= 10) {
				for (var i = 0; i < 10; i++) {
					if (MRS.blocks[i].generator != MRS.account) {
						isForgingAllBlocks = false;
						break;
					}
				}
			} else {
				isForgingAllBlocks = false;
			}

			if (isForgingAllBlocks) {
				$.growl($.t("fork_warning"), {
					"type": "danger"
				});
			}

            if (MRS.blocks && MRS.blocks.length > 0 && MRS.baseTargetPercent(MRS.blocks[0]) > 1000 && !MRS.isTestNet) {
                $.growl($.t("fork_warning_base_target"), {
                    "type": "danger"
                });
            }
		}
	};

    MRS.printEnvInfo = function() {
        MRS.logProperty("navigator.userAgent");
        MRS.logProperty("navigator.platform");
        MRS.logProperty("navigator.appVersion");
        MRS.logProperty("navigator.appName");
        MRS.logProperty("navigator.appCodeName");
        MRS.logProperty("navigator.hardwareConcurrency");
        MRS.logProperty("navigator.maxTouchPoints");
        MRS.logProperty("navigator.languages");
        MRS.logProperty("navigator.language");
        MRS.logProperty("navigator.userLanguage");
        MRS.logProperty("navigator.cookieEnabled");
        MRS.logProperty("navigator.onLine");
		if (window["cordova"]) {
			MRS.logProperty("device.model");
			MRS.logProperty("device.platform");
			MRS.logProperty("device.version");
		}
        MRS.logProperty("MRS.isTestNet");
        MRS.logProperty("MRS.needsAdminPassword");
    };

	$("#id_search").on("submit", function(e) {
		e.preventDefault();

		var id = $.trim($("#id_search").find("input[name=q]").val());

		if (MRS.isRsAccount(id)) {
			MRS.sendRequest("getAccount", {
				"account": id
			}, function(response, input) {
				if (!response.errorCode) {
					response.account = input.account;
					MRS.showAccountModal(response);
				} else {
					$.growl($.t("error_search_no_results"), {
						"type": "danger"
					});
				}
			});
		} else {
			if (!MRS.isNumericAccount(id)) {
				$.growl($.t("error_search_invalid"), {
					"type": "danger"
				});
				return;
			}
			MRS.sendRequest("getTransaction", {
				"transaction": id
			}, function(response, input) {
				if (!response.errorCode) {
					response.transaction = input.transaction;
					MRS.showTransactionModal(response);
				} else {
					MRS.sendRequest("getAccount", {
						"account": id
					}, function(response, input) {
						if (!response.errorCode) {
							response.account = input.account;
							MRS.showAccountModal(response);
						} else {
							MRS.sendRequest("getBlock", {
								"block": id,
                                "includeTransactions": "true",
								"includeExecutedPhased": "true"
							}, function(response) {
								if (!response.errorCode) {
									MRS.showBlockModal(response);
								} else {
                                    MRS.sendRequest("getBlock", {
                                        "height": id,
                                        "includeTransactions": "true",
                                        "includeExecutedPhased": "true"
                                    }, function(response) {
                                        if (!response.errorCode) {
                                            MRS.showBlockModal(response);
                                        } else {
                                            $.growl($.t("error_search_no_results"), {
                                                "type": "danger"
                                            });
                                        }
                                    });
								}
							});
						}
					});
				}
			});
		}
	});

	function checkLocalStorage() {
	    var storage;
	    var fail;
	    var uid;
	    try {
	        uid = String(new Date());
	        (storage = window.localStorage).setItem(uid, uid);
	        fail = storage.getItem(uid) != uid;
	        storage.removeItem(uid);
	        fail && (storage = false);
	    } catch (exception) {
	        MRS.logConsole("checkLocalStorage " + exception.message)
	    }
	    return storage;
	}

	return MRS;
}(isNode ? client : MRS || {}, jQuery));

if (isNode) {
    module.exports = MRS;
} else {
    $(document).ready(function() {
        console.log("document.ready");
        MRS.init();
    });
}
