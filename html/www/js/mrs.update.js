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
	var DOWNLOAD_REPOSITORY_URL = "https://bitbucket.org/Jelurida/metro/downloads/";
	var index = 0;
	var bundles = [
		{alias: "mrsVersion", status: "release", prefix: "metro-client-", ext: "zip"},
		{alias: "mrsBetaVersion", status: "beta", prefix: "metro-client-", ext: "zip"},
		{alias: "mrsVersionWin", status: "release", prefix: "metro-client-", ext: "exe"},
		{alias: "mrsBetaVersionWin", status: "beta", prefix: "metro-client-", ext: "exe"},
		{alias: "mrsVersionMac", status: "release", prefix: "metro-installer-", ext: "dmg"},
		{alias: "mrsBetaVersionMac", status: "beta", prefix: "metro-installer-", ext: "dmg"},
		{alias: "mrsVersionLinux", status: "release", prefix: "metro-client-", ext: "sh"},
		{alias: "mrsBetaVersionLinux", status: "beta", prefix: "metro-client-", ext: "sh"}
	];
	MRS.isOutdated = false;

	MRS.checkAliasVersions = function() {
		if (MRS.downloadingBlockchain && !(MRS.state && MRS.state.apiProxy)) {
			$("#mrs_update_explanation").find("span").hide();
			$("#mrs_update_explanation_blockchain_sync").show();
			return;
		}

        // Load all version aliases in parallel and call checkForNewVersion() at the end
		index = 0;
		var versionInfoCall = [];
		for (var i=0; i<bundles.length; i++) {
			versionInfoCall.push(function(callback) {
				getVersionInfo(callback);
			});
		}
        async.parallel(versionInfoCall, function(err, results) {
            if (err == null) {
                MRS.logConsole("Version aliases: " + JSON.stringify(results));
            } else {
                MRS.logConsole("Version aliases lookup error " + err);
            }
			checkForNewVersion();
        });
	};

	function checkForNewVersion() {
        var installVersusNormal, installVersusBeta;
        if (MRS.mrsVersion && MRS.mrsVersion.versionNr) {
			installVersusNormal = MRS.versionCompare(MRS.state.version, MRS.mrsVersion.versionNr);
            $(".mrs_new_version_nr").html(MRS.mrsVersion.versionNr).show();
		}
		if (MRS.mrsBetaVersion && MRS.mrsBetaVersion.versionNr) {
			installVersusBeta = MRS.versionCompare(MRS.state.version, MRS.mrsBetaVersion.versionNr);
            $(".mrs_beta_version_nr").html(MRS.mrsBetaVersion.versionNr).show();
		}

		$("#mrs_update_explanation").find("> span").hide();
		$("#mrs_update_explanation_wait").attr("style", "display: none !important");
		if (installVersusNormal == -1 && installVersusBeta == -1) {
			MRS.isOutdated = true;
			$("#mrs_update").html($.t("outdated")).show();
			$("#mrs_update_explanation_new_choice").show();
		} else if (installVersusBeta == -1) {
			MRS.isOutdated = false;
			$("#mrs_update").html($.t("new_beta")).show();
			$("#mrs_update_explanation_new_beta").show();
		} else if (installVersusNormal == -1) {
			MRS.isOutdated = true;
			$("#mrs_update").html($.t("outdated")).show();
			$("#mrs_update_explanation_new_release").show();
		} else {
			MRS.isOutdated = false;
			$("#mrs_update_explanation_up_to_date").show();
		}
	}

	function verifyClientUpdate(e) {
		e.stopPropagation();
		e.preventDefault();
		var files = null;
		if (e.originalEvent.target.files && e.originalEvent.target.files.length) {
			files = e.originalEvent.target.files;
		} else if (e.originalEvent.dataTransfer.files && e.originalEvent.dataTransfer.files.length) {
			files = e.originalEvent.dataTransfer.files;
		}
		if (!files) {
			return;
		}
        var updateHashProgress = $("#mrs_update_hash_progress");
        updateHashProgress.css("width", "0%");
		updateHashProgress.show();
		var worker = new Worker("js/crypto/sha256worker.js");
		worker.onmessage = function(e) {
			if (e.data.progress) {
				$("#mrs_update_hash_progress").css("width", e.data.progress + "%");
			} else {
				$("#mrs_update_hash_progress").hide();
				$("#mrs_update_drop_zone").hide();

                var mrsUpdateResult = $("#mrs_update_result");
                if (e.data.sha256 == MRS.downloadedVersion.hash) {
					mrsUpdateResult.html($.t("success_hash_verification")).attr("class", " ");
				} else {
					mrsUpdateResult.html($.t("error_hash_verification")).attr("class", "incorrect");
				}

				$("#mrs_update_hash_version").html(MRS.downloadedVersion.versionNr);
				$("#mrs_update_hash_download").html(e.data.sha256);
				$("#mrs_update_hash_official").html(MRS.downloadedVersion.hash);
				$("#mrs_update_hashes").show();
				mrsUpdateResult.show();
				MRS.downloadedVersion = {};
				$("body").off("dragover.mrs, drop.mrs");
			}
		};

		worker.postMessage({
			file: files[0]
		});
	}

	MRS.downloadClientUpdate = function(status, ext) {
		var bundle;
		for (var i=0; i<bundles.length; i++) {
			bundle = bundles[i];
            if (bundle.status == status && bundle.ext == ext) {
				MRS.downloadedVersion = MRS[bundle.alias];
				break;
			}
		}
        if (!MRS.downloadedVersion) {
            MRS.logConsole("Cannot determine download version for alias " + bundle.alias);
            return;
        }
        var filename = bundle.prefix + MRS.downloadedVersion.versionNr + "." + bundle.ext;
        var fileurl = DOWNLOAD_REPOSITORY_URL + filename;
        var mrsUpdateExplanation = $("#mrs_update_explanation");
        if (window.java !== undefined) {
            window.java.popupHandlerURLChange(fileurl);
            mrsUpdateExplanation.html($.t("download_verification", { url: fileurl, hash: MRS.downloadedVersion.hash }));
            return;
        } else {
            $("#mrs_update_iframe").attr("src", fileurl);
        }
        mrsUpdateExplanation.hide();
        var updateDropZone = $("#mrs_update_drop_zone");
        updateDropZone.html($.t("drop_update_v2", { filename: filename }));
        updateDropZone.show();

        var body = $("body");
        body.on("dragover.mrs", function(e) {
            e.preventDefault();
            e.stopPropagation();

            if (e.originalEvent && e.originalEvent.dataTransfer) {
                e.originalEvent.dataTransfer.dropEffect = "copy";
            }
        });

        body.on("drop.mrs", function(e) {
            verifyClientUpdate(e);
        });

        updateDropZone.on("click", function(e) {
            e.preventDefault();
            $("#mrs_update_file_select").trigger("click");
        });

        $("#mrs_update_file_select").on("change", function(e) {
            verifyClientUpdate(e);
        });

		return false;
	};
	
    // Get latest version number and hash of version specified by the alias
    function getVersionInfo(callback) {
		var aliasName = bundles[index].alias;
		index ++;
        MRS.sendRequest("getAlias", {
            "aliasName": aliasName
        }, function (response) {
            if (response.aliasURI) {
                var token = response.aliasURI.trim().split(" ");
                if (token.length != 2) {
                    MRS.logConsole("Invalid token " + response.aliasURI + " for alias " + aliasName);
                    callback(null, null);
                    return;
                }
                MRS[aliasName] = { versionNr: token[0], hash: token[1] };
                callback(null, MRS[aliasName]);
            } else {
                callback(null, null);
            }
        });
    }
	return MRS;
}(MRS || {}, jQuery));