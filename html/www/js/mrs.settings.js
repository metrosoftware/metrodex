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
var MRS = (function(MRS, $) {
	MRS.defaultSettings = {
		"submit_on_enter": "0",
		"animate_forging": "1",
        "animate_mining": "1",
        "console_log": "0",
		"fee_warning": "100000000000",
		"amount_warning": "10000000000000",
		"asset_transfer_warning": "10000",
		"24_hour_format": "1",
		"language": "en",
		"regional_format": "default",
		"enable_plugins": "0",
		"items_page": "15",
		"admin_password": "",
		"max_mtr_decimals": "2",
		"fake_entity_warning": "1"
	};

	MRS.defaultColors = {
		"header": "#524b98",
		"sidebar": "#F4F4F4",
		"boxes": "#6d64cc"
	};

	MRS.languages = {
		"de": "Deutsch",                 // german
		"en": "English",                 // english
		"es-es": "Español",              // spanish
		"ca": "Català",                  // catalan
		"fi": "Suomi (Experimental)",    // finnish
		"fr": "Français",                // french
		"gl": "Galego (Experimental)",   // galician
		"el": "Ελληνικά (Experimental)", // greek
		"sh": "Hrvatski (Experimental)", // croatian
		"hi": "हिन्दी (Experimental)",  // hindi
		"id": "Bahasa Indonesia",        // indonesian
		"it": "Italiano",                // italian
		"ja": "日本語",                   // japanese
		"lt": "Lietuviškai",             // lithuanian
		"nl": "Nederlands",              // dutch
		"cs": "Čeština (Beta)",          // czech
		"sk": "Slovensky (Beta)",        // slovakian
		"pt-pt": "Português",            // portugese
		"pt-br": "Português Brasileiro", // portugese, brazilian
		"sr": "Српски (Experimental)",   // serbian, cyrillic
		"sr-cs": "Srpski (Experimental)",// serbian, latin
		"bg": "Български",               // bulgarian
		"ro": "Român",                   // romanian
		"tr": "Türk (Experimental)",     // turkish
		"uk": "Yкраiнска",               // ukrainian
		"ru": "Русский",                 // russian
		"zh-cn": "中文 simplified",      // chinese simplified
		"zh-tw": "中文 traditional"      // chinese traditional
	};

	var userStyles = {};

	userStyles.header = {
		"blue": {
			"header_bg": "#3c8dbc",
			"logo_bg": "#367fa9",
			"link_bg_hover": "#357ca5"
		},
		"green": {
			"header_bg": "#29BB9C",
			"logo_bg": "#26AE91",
			"link_bg_hover": "#1F8E77"
		},
		"red": {
			"header_bg": "#cb4040",
			"logo_bg": "#9e2b2b",
			"link_bg_hover": "#9e2b2b",
			"toggle_icon": "#d97474"
		},
		"brown": {
			"header_bg": "#ba5d32",
			"logo_bg": "#864324",
			"link_bg_hover": "#864324",
			"toggle_icon": "#d3815b"
		},
		"purple": {
			"header_bg": "#86618f",
			"logo_bg": "#614667",
			"link_bg_hover": "#614667",
			"toggle_icon": "#a586ad"
		},
		"gray": {
			"header_bg": "#575757",
			"logo_bg": "#363636",
			"link_bg_hover": "#363636",
			"toggle_icon": "#787878"
		},
		"pink": {
			"header_bg": "#b94b6f",
			"logo_bg": "#8b3652",
			"link_bg_hover": "#8b3652",
			"toggle_icon": "#cc7b95"
		},
		"bright-blue": {
			"header_bg": "#2494F2",
			"logo_bg": "#2380cf",
			"link_bg_hover": "#36a3ff",
			"toggle_icon": "#AEBECD"
		},
		"dark-blue": {
			"header_bg": "#25313e",
			"logo_bg": "#1b252e",
			"link_txt": "#AEBECD",
			"link_bg_hover": "#1b252e",
			"link_txt_hover": "#fff",
			"toggle_icon": "#AEBECD"
		}
	};

	userStyles.sidebar = {
		"dark-gray": {
			"sidebar_bg": "#272930",
			"user_panel_txt": "#fff",
			"sidebar_top_border": "#1a1c20",
			"sidebar_bottom_border": "#2f323a",
			"menu_item_top_border": "#32353e",
			"menu_item_bottom_border": "#1a1c20",
			"menu_item_txt": "#c9d4f6",
			"menu_item_bg_hover": "#2a2c34",
			"menu_item_border_active": "#2494F2",
			"submenu_item_bg": "#2A2A2A",
			"submenu_item_txt": "#fff",
			"submenu_item_bg_hover": "#222222"
		},
		"dark-blue": {
			"sidebar_bg": "#34495e",
			"user_panel_txt": "#fff",
			"sidebar_top_border": "#142638",
			"sidebar_bottom_border": "#54677a",
			"menu_item_top_border": "#54677a",
			"menu_item_bottom_border": "#142638",
			"menu_item_txt": "#fff",
			"menu_item_bg_hover": "#3d566e",
			"menu_item_bg_active": "#2c3e50",
			"submenu_item_bg": "#ECF0F1",
			"submenu_item_bg_hover": "#E0E7E8",
			"submenu_item_txt": "#333333"
		}
	};

	userStyles.boxes = {
		"green": {
			"bg": "#34d2b1",
			"bg_gradient": "#87e5d1"
		},
		"red": {
			"bg": "#d25b5b",
			"bg_gradient": "#da7575"
		},
		"brown": {
			"bg": "#c76436",
			"bg_gradient": "#d3825d"
		},
		"purple": {
			"bg": "#8f6899",
			"bg_gradient": "#a687ad"
		},
		"gray": {
			"bg": "#5f5f5f",
			"bg_gradient": "#797979"
		},
		"pink": {
			"bg": "#be5779",
			"bg_gradient": "#cc7c96"
		},
		"bright-blue": {
			"bg": "#349cf3",
			"bg_gradient": "#64b3f6"
		},
		"dark-blue": {
			"bg": "#2b3949",
			"bg_gradient": "#3e5369"
		}

	};

    function isAmountWarning(key) {
        return key != "asset_transfer_warning" && key != "fake_entity_warning";
    }

    MRS.pages.settings = function() {
		for (var style in userStyles) {
			if (!userStyles.hasOwnProperty(style)) {
				continue;
			}
			var $dropdown = $("#" + style + "_color_scheme");
			$dropdown.empty();
			$dropdown.append("<li><a href='#' data-color=''><span class='color' style='background-color:" + MRS.defaultColors[style] + ";border:1px solid black;'></span>Default</a></li>");
			$.each(userStyles[style], function(key, value) {
				var bg = "";
				if (value.bg) {
					bg = value.bg;
				} else if (value.header_bg) {
					bg = value.header_bg;
				} else if (value.sidebar_bg) {
					bg = value.sidebar_bg;
				}
				$dropdown.append("<li><a href='#' data-color='" + key + "'><span class='color' style='background-color: " + bg + ";border:1px solid black;'></span> " + key.replace("-", " ") + "</a></li>");
			});

			var $span = $dropdown.closest(".btn-group.colors").find("span.text");
			var color = MRS.settings[style + "_color"];
			if (!color) {
				colorTitle = "Default";
			} else {
				var colorTitle = color.replace(/-/g, " ");
				colorTitle = colorTitle.replace(/\w\S*/g, function(txt) {
					return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
				});
			}
			$span.html(colorTitle);
		}

		for (var key in MRS.settings) {
			if (!MRS.settings.hasOwnProperty(key)) {
				continue;
			}
			var setting = $("#settings_" + key);
            if (/_warning/i.test(key) && isAmountWarning(key)) {
				if (setting.length) {
					setting.val(MRS.convertToMTR(MRS.settings[key]));
				}
			} else if (!/_color/i.test(key)) {
				if (setting.length) {
					setting.val(MRS.settings[key]);
				}
			}
		}
		if (MRS.database && MRS.database["name"] == "MRS_USER_DB") {
			$("#settings_db_warning").show();
		}
		MRS.pageLoaded();
	};

	function getCssGradientStyle(start, stop, vertical) {
		var startPosition = (vertical ? "left" : "top");
        var output = "";
		output += "background-image: -moz-linear-gradient(" + startPosition + ", " + start + ", " + stop + ");";
        output += "background-image: -ms-linear-gradient(" + startPosition + ", " + start + ", " + stop + ");";
		output += "background-image: -webkit-gradient(linear, " + (vertical ? "left top, right top" : "0 0, 0 100%") + ", from(" + start + "), to(" + stop + "));";
		output += "background-image: -webkit-linear-gradient(" + startPosition + ", " + start + ", " + stop + ");";
		output += "background-image: -o-linear-gradient(" + startPosition + ", " + start + ", " + stop + ");";
		output += "background-image: linear-gradient(" + startPosition + ", " + start + ", " + stop + ");";
		output += "filter: progid:dximagetransform.microsoft.gradient(startColorstr='" + start + "', endColorstr='" + stop + "', GradientType=" + (vertical ? "1" : "0") + ");";
		return output;
	}

	MRS.updateStyle = function(type, color) {
		var css = "";
		var colors;
		if ($.isPlainObject(color)) {
			colors = color;
		} else {
			colors = userStyles[type][color];
		}
		if (colors) {
			switch (type) {
				case "boxes":
					css += ".small-box { background: " + colors.bg + "; " + getCssGradientStyle(colors.bg, colors.bg_gradient, true) + " }";
					break;
				case "header":
					if (!colors.link_txt) {
						colors.link_txt = "#fff";
					}
					if (!colors.toggle_icon) {
						colors.toggle_icon = "#fff";
					}
					if (!colors.toggle_icon_hover) {
						colors.toggle_icon_hover = "#fff";
					}
					if (!colors.link_txt_hover) {
						colors.link_txt_hover = colors.link_txt;
					}
					if (!colors.link_bg_hover && colors.link_bg) {
						colors.link_bg_hover = colors.link_bg;
					}

					if (!colors.logo_bg) {
						css += ".header { background:" + colors.header_bg + " }";
						if (colors.header_bg_gradient) {
							css += ".header { " + getCssGradientStyle(colors.header_bg, colors.header_bg_gradient) + " }";
						}
						css += ".header .navbar { background: inherit }";
						css += ".header .logo { background: inherit }";
					} else {
						css += ".header .navbar { background:" + colors.header_bg + " }";
						if (colors.header_bg_gradient) {
							css += ".header .navbar { " + getCssGradientStyle(colors.header_bg, colors.header_bg_gradient) + " }";
						}
						css += ".header .logo { background: " + colors.logo_bg + " }";
						if (colors.logo_bg_gradient) {
							css += ".header .logo { " + getCssGradientStyle(colors.logo_bg, colors.logo_bg_gradient) + " }";
						}
					}
					css += ".header .navbar .nav a { color: " + colors.link_txt + (colors.link_bg ? "; background:" + colors.link_bg : "") + " }";
					css += ".header .navbar .nav > li > a:hover, .header .navbar .nav > li > a:focus, .header .navbar .nav > li > a:focus { color: " + colors.link_txt_hover + (colors.link_bg_hover ? "; background:" + colors.link_bg_hover : "") + " }";
					if (colors.link_bg_hover) {
						css += ".header .navbar .nav > li > a:hover { " + getCssGradientStyle(colors.link_bg_hover, colors.link_bg_hover_gradient) + " }";
					}
					css += ".header .navbar .nav > li > ul a { color: #444444; }";
					css += ".header .navbar .nav > li > ul a:hover {  color: " + colors.link_txt_hover + (colors.link_bg_hover ? "; background:" + colors.link_bg_hover : "") + " }";
					css += ".header .navbar .sidebar-toggle .icon-bar { background: " + colors.toggle_icon + " }";
					css += ".header .navbar .sidebar-toggle:hover .icon-bar { background: " + colors.toggle_icon_hover + " }";
					if (colors.link_border) {
						css += ".header .navbar .nav > li { border-left: 1px solid " + colors.link_border + " }";
					}
					if (colors.link_border_inset) {
						css += ".header .navbar .nav > li { border-right: 1px solid " + colors.link_border_inset + " }";
						css += ".header .navbar .nav > li:last-child { border-right:none }";
						css += ".header .navbar .nav { border-left: 1px solid " + colors.link_border_inset + " }";
					}
					if (colors.header_border) {
						css += ".header { border-bottom: 1px solid " + colors.header_border + " }";
					}
					break;
				case "sidebar":
					if (!colors.user_panel_link) {
						colors.user_panel_link = colors.user_panel_txt;
					}
					if (!colors.menu_item_bg) {
						colors.menu_item_bg = colors.sidebar_bg;
					}
					if (!colors.menu_item_bg_active) {
						colors.menu_item_bg_active = colors.menu_item_bg_hover;
					}
					if (!colors.menu_item_txt_hover) {
						colors.menu_item_txt_hover = colors.menu_item_txt;
					}
					if (!colors.menu_item_txt_active) {
						colors.menu_item_txt_active = colors.menu_item_txt_hover;
					}
					if (!colors.menu_item_border_active && colors.menu_item_border_hover) {
						colors.menu_item_border_active = colors.menu_item_border_hover;
					}
					if (!colors.menu_item_border_size) {
						colors.menu_item_border_size = 1;
					}
					css += ".left-side { background: " + colors.sidebar_bg + " }";
					css += ".left-side .user-panel > .info { color: " + colors.user_panel_txt + " }";
					if (colors.user_panel_bg) {
						css += ".left-side .user-panel { background: " + colors.user_panel_bg + " }";
						if (colors.user_panel_bg_gradient) {
							css += ".left-side .user-panel { " + getCssGradientStyle(colors.user_panel_bg, colors.user_panel_bg_gradient) + " }";
						}
					}
					css += ".left-side .user-panel a { color:" + colors.user_panel_link + " }";
					if (colors.sidebar_top_border || colors.sidebar_bottom_border) {
						css += ".left-side .sidebar > .sidebar-menu { " + (colors.sidebar_top_border ? "border-top: 1px solid " + colors.sidebar_top_border + "; " : "") + (colors.sidebar_bottom_border ? "border-bottom: 1px solid " + colors.sidebar_bottom_border : "") + " }";
					}
					css += ".left-side .sidebar > .sidebar-menu > li > a { background: " + colors.menu_item_bg + "; color: " + colors.menu_item_txt + (colors.menu_item_top_border ? "; border-top:1px solid " + colors.menu_item_top_border : "") + (colors.menu_item_bottom_border ? "; border-bottom: 1px solid " + colors.menu_item_bottom_border : "") + " }";
					if (colors.menu_item_bg_gradient) {
						css += ".left-side .sidebar > .sidebar-menu > li > a { " + getCssGradientStyle(colors.menu_item_bg, colors.menu_item_bg_gradient) + " }";
					}
					css += ".left-side .sidebar > .sidebar-menu > li.active > a { background: " + colors.menu_item_bg_active + "; color: " + colors.menu_item_txt_active + (colors.menu_item_border_active ? "; border-left: " + colors.menu_item_border_size + "px solid " + colors.menu_item_border_active : "") + " }";
					if (colors.menu_item_border_hover || colors.menu_item_border_active) {
						css += ".left-side .sidebar > .sidebar-menu > li > a { border-left: " + colors.menu_item_border_size + "px solid transparent }";
					}
					if (colors.menu_item_bg_active_gradient) {
						css += ".left-side .sidebar > .sidebar-menu > li.active > a { " + getCssGradientStyle(colors.menu_item_bg_active, colors.menu_item_bg_active_gradient) + " }";
					}
					css += ".left-side .sidebar > .sidebar-menu > li > a:hover { background: " + colors.menu_item_bg_hover + "; color: " + colors.menu_item_txt_hover + (colors.menu_item_border_hover ? "; border-left: " + colors.menu_item_border_size + "px solid " + colors.menu_item_border_hover : "") + " }";
					if (colors.menu_item_bg_hover_gradient) {
						css += ".left-side .sidebar > .sidebar-menu > li > a:hover { " + getCssGradientStyle(colors.menu_item_bg_hover, colors.menu_item_bg_hover_gradient) + " }";
					}
					css += ".sidebar .sidebar-menu .treeview-menu > li > a { background: " + colors.submenu_item_bg + "; color: " + colors.submenu_item_txt + (colors.submenu_item_top_border ? "; border-top:1px solid " + colors.submenu_item_top_border : "") + (colors.submenu_item_bottom_border ? "; border-bottom: 1px solid " + colors.submenu_item_bottom_border : "") + " }";
					if (colors.submenu_item_bg_gradient) {
						css += ".sidebar .sidebar-menu .treeview-menu > li > a { " + getCssGradientStyle(colors.submenu_item_bg, colors.submenu_item_bg_gradient) + " }";
					}
					css += ".sidebar .sidebar-menu .treeview-menu > li > a:hover { background: " + colors.submenu_item_bg_hover + "; color: " + colors.submenu_item_txt_hover + " }";
					if (colors.submenu_item_bg_hover_gradient) {
						css += ".sidebar .sidebar-menu .treeview-menu > li > a:hover { " + getCssGradientStyle(colors.submenu_item_bg_hover, colors.submenu_item_bg_hover_gradient) + " }";
					}
					break;
			}
		}

		var $style = $("#user_" + type + "_style");
		if ($style[0].styleSheet) {
			$style[0].styleSheet.cssText = css;
		} else {
			$style.text(css);
		}
	};

	$("ul.color_scheme_editor").on("click", "li a", function(e) {
		e.preventDefault();
		var color = $(this).data("color");
		var scheme = $(this).closest("ul").data("scheme");
		var $span = $(this).closest(".btn-group.colors").find("span.text");
		if (!color) {
			colorTitle = "Default";
		} else {
			var colorTitle = color.replace(/-/g, " ");
			colorTitle = colorTitle.replace(/\w\S*/g, function(txt) {
				return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
			});
		}
		$span.html(colorTitle);
		if (color) {
			MRS.updateSettings(scheme + "_color", color);
			MRS.updateStyle(scheme, color);
		} else {
			MRS.updateSettings(scheme + "_color");
			MRS.updateStyle(scheme);
		}
	});

	MRS.createLangSelect = function() {
		// Build language select box for settings page, login
		var $langSelBoxes = $('select[name="language"]');
		$langSelBoxes.empty();
		$.each(MRS.languages, function(code, name) {
			$langSelBoxes.append('<option value="' + code + '">' + name + '</option>');
		});
		$langSelBoxes.val(MRS.settings['language']);
	};

	MRS.createRegionalFormatSelect = function() {
		// Build language select box for settings page, login
		var $regionalFormatSelBoxes = $('select[name="regional_format"]');
		$regionalFormatSelBoxes.empty();
		$regionalFormatSelBoxes.append("<option value='default'>" + $.t("use_browser_default") + "</option>");
		var localeKeys = MRS.getLocaleList();
        for (var i=0; i < localeKeys.length; i++) {
			$regionalFormatSelBoxes.append("<option value='" + localeKeys[i] + "'>" + MRS.getLocaleName(localeKeys[i]) + "</option>");
		}
		$regionalFormatSelBoxes.val(MRS.settings["regional_format"]);
	};

	MRS.getSettings = function(isAccountSpecific) {
		if (!MRS.account) {
			MRS.settings = MRS.defaultSettings;
			if (MRS.getStrItem("language")) {
				MRS.settings["language"] = MRS.getStrItem("language");
			}
			MRS.createLangSelect();
			MRS.createRegionalFormatSelect();
			MRS.applySettings();
		} else {
            async.waterfall([
                function(callback) {
					MRS.storageSelect("data", [{
						"id": "settings"
					}], function (error, result) {
						if (result && result.length) {
							MRS.settings = $.extend({}, MRS.defaultSettings, JSON.parse(result[0].contents));
						} else {
							MRS.storageInsert("data", "id", {
								id: "settings",
								contents: "{}"
							});
							MRS.settings = MRS.defaultSettings;
						}
						MRS.logConsole("User settings for account " + MRS.convertNumericToRSAccountFormat(MRS.account));
						for (var setting in MRS.defaultSettings) {
							if (!MRS.defaultSettings.hasOwnProperty(setting)) {
								continue;
							}
							var value = MRS.settings[setting];
							var status = (MRS.defaultSettings[setting] !== value ? "modified" : "default");
							if (setting.search("password") >= 0) {
								value = new Array(value.length + 1).join('*');
							}
							MRS.logConsole(setting + " = " + value + " [" + status + "]");
						}
						MRS.applySettings();
						callback(null);
					});
                },
                function(callback) {
                    for (var schema in MRS.defaultColors) {
						if (!MRS.defaultColors.hasOwnProperty(schema)) {
							continue;
						}
                        var color = MRS.settings[schema + "_color"];
                        if (color) {
                            MRS.updateStyle(schema, color);
                        }
                    }
                    callback(null);
                },
                function(callback) {
                    if (isAccountSpecific) {
                        MRS.loadPlugins();
						if(!MRS.getUrlParameter("page") || MRS.getUrlParameter("page") == "dashboard") {
							MRS.getAccountInfo();
							MRS.getInitialTransactions();
						}
                    }
                    callback(null);
                }
            ], function(err, result) {});

		}
	};

	MRS.applySettings = function(key) {
	    if (!key || key == "language") {
			if ($.i18n.language != MRS.settings["language"]) {
				$.i18n.changeLanguage(MRS.settings["language"], function() {
					$("[data-i18n]").i18n();
				});
				if (key) {
					MRS.setStrItem('i18next_lng', MRS.settings["language"]);
				}
			}
		}

		if (!key || key == "submit_on_enter") {
			if (MRS.settings["submit_on_enter"] == "1") {
				$(".modal form:not('#decrypt_note_form_container')").on("submit.onEnter", function(e) {
					e.preventDefault();
					MRS.submitForm($(this).closest(".modal"));
				});
			} else {
				$(".modal form").off("submit.onEnter");
			}
		}

		if (!key || key == "animate_forging") {
            var forgingIndicator = $("#forging_indicator");
            if (MRS.settings["animate_forging"] == "1") {
				forgingIndicator.addClass("animated");
			} else {
				forgingIndicator.removeClass("animated");
			}
		}

        if (!key || key == "animate_mining") {
            var miningIndicator = $("#mining_indicator");
            if (MRS.settings["animate_mining"] == "1") {
                miningIndicator.addClass("animated");
            } else {
                miningIndicator.removeClass("animated");
            }
        }

		if (!key || key == "items_page") {
			MRS.itemsPerPage = parseInt(MRS.settings["items_page"], 10);
		}

		if (!MRS.downloadingBlockchain) {
			if (!key || key == "console_log") {
				if (MRS.settings["console_log"] == "0") {
					$("#show_console").hide();
				} else {
					$("#show_console").show();
				}
			}
		}

		if (key == "24_hour_format") {
			var $dashboard_dates = $("#dashboard_table").find("a[data-timestamp]");
			$.each($dashboard_dates, function() {
				$(this).html(MRS.formatTimestamp($(this).data("timestamp")));
			});
		}

		if (!key || key == "admin_password") {
			if (MRS.settings["admin_password"] != "") {
				MRS.updateForgingStatus();
			}
		}

        if (!key || key == "admin_password") {
            if (MRS.settings["admin_password"] != "") {
                MRS.updateMiningStatus();
            }
        }
	};

	MRS.updateSettings = function(key, value) {
		if (key) {
			MRS.settings[key] = value;
			if (key == "language") {
				MRS.setStrItem("language", value);
			}
		}

		MRS.storageUpdate("data", {
			contents: JSON.stringify(MRS.settings)
		}, [{
			id: "settings"
		}]);
		MRS.applySettings(key);
	};

    MRS.initSettings = function() {
        $("#settings_box select, #welcome_panel select[name='language'], #settings_admin_password").on("change", function(e) {
            e.preventDefault();
            MRS.updateSettings($(this).attr("name"), $(this).val());
        });

        $("#settings_box").find("input[type=text]").on("input", function() {
            var key = $(this).attr("name");
            var value = $(this).val();
            if (/_warning/i.test(key) && isAmountWarning(key)) {
                value = MRS.convertToMQT(value);
            }
            MRS.updateSettings(key, value);
        });

        $("#settings_form").submit(function(event) {
            event.preventDefault();
        });
    };

	return MRS;
}(MRS || {}, jQuery));