/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 * Copyright © 2016-2017 Jelurida IP B.V.                                     *
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
	MRS.loadContacts = function() {
		MRS.contacts = {};
		MRS.storageSelect("contacts", null, function (error, contacts) {
			if (contacts && contacts.length) {
				$.each(contacts, function (index, contact) {
					MRS.contacts[contact.account] = contact;
				});
				MRS.logConsole("Loaded " + contacts.length + " contacts");
			}
		});
	};

	MRS.pages.contacts = function() {
		$("#contacts_table_container").show();
		$("#contact_page_database_error").hide();
		if (MRS.isExportContactsAvailable()) {
			$("#export_contacts_button").show();
		} else {
			$("#export_contacts_button").hide();
		}
		MRS.storageSelect("contacts", null, function (error, contacts) {
			var rows = "";
			if (contacts && contacts.length) {
				contacts.sort(function (a, b) {
					if (a.name.toLowerCase() > b.name.toLowerCase()) {
						return 1;
					} else if (a.name.toLowerCase() < b.name.toLowerCase()) {
						return -1;
					} else {
						return 0;
					}
				});
				$.each(contacts, function (index, contact) {
					var contactDescription = contact.description;
					if (contactDescription.length > 100) {
						contactDescription = contactDescription.substring(0, 100) + "...";
					} else if (!contactDescription) {
						contactDescription = "-";
					}
					rows += "<tr>" +
						"<td><a href='#' data-toggle='modal' data-target='#update_contact_modal' data-contact='" + String(contact.id).escapeHTML() + "'>" + contact.name.escapeHTML() + "</a></td>" +
						"<td><a href='#' data-user='" + MRS.getAccountFormatted(contact, "account") + "' class='show_account_modal_action user_info'>" + MRS.getAccountFormatted(contact, "account") + "</a></td>" +
						"<td>" + (contact.email ? contact.email.escapeHTML() : "-") + "</td>" +
						"<td>" + contactDescription.escapeHTML() + "</td>" +
						"<td style='white-space:nowrap'>" +
						"<a class='btn btn-xs btn-default' href='#' data-toggle='modal' data-target='#send_money_modal' data-contact='" + String(contact.name).escapeHTML() + "'>" + $.t("send") + " " + MRS.constants.COIN_SYMBOL + "</a>&nbsp;" +
						"<a class='btn btn-xs btn-default' href='#' data-toggle='modal' data-target='#send_message_modal' data-contact='" + String(contact.name).escapeHTML() + "'>" + $.t("message") + "</a>&nbsp;" +
						"<a class='btn btn-xs btn-default' href='#' data-toggle='modal' data-target='#delete_contact_modal' data-contact='" + String(contact.id).escapeHTML() + "'>" + $.t("delete") + "</a>" +
						"</td>" +
					"</tr>";
				});
			}
			MRS.dataLoaded(rows);
		});
	};

	MRS.forms.addContact = function($modal) {
		var data = MRS.getFormData($modal.find("form:first"));
		data.account_id = String(data.account_id);
		if (!data.name) {
			return {
				"error": $.t("error_contact_name_required")
			};
		} else if (!data.account_id) {
			return {
				"error": $.t("error_account_id_required")
			};
		}

		if (MRS.isNumericAccount(data.name) || MRS.isRsAccount(data.name)) {
			return {
				"error": $.t("error_contact_name_alpha")
			};
		}

		if (data.email && !/@/.test(data.email)) {
			return {
				"error": $.t("error_email_address")
			};
		}

		if (data.account_id.charAt(0) == '@') {
			var convertedAccountId = $modal.find("input[name=converted_account_id]").val();
			if (convertedAccountId) {
				data.account_id = convertedAccountId;
			} else {
				return {
					"error": $.t("error_account_id")
				};
			}
		}
		var address;
		if (MRS.isRsAccount(data.account_id)) {
			data.account_rs = data.account_id;
			address = new MetroAddress();
			if (address.set(data.account_rs)) {
				data.account = address.account_id();
			} else {
				return {
					"error": $.t("error_account_id")
				};
			}
		} else {
			address = new MetroAddress();
			if (address.set(data.account_id)) {
				data.account_rs = address.toString();
			} else {
				return {
					"error": $.t("error_account_id")
				};
			}
		}

		MRS.sendRequest("getAccount", {
			"account": data.account_id
		}, function(response) {
			if (!response.errorCode) {
				if (response.account != data.account || response.accountRS != data.account_rs) {
					return {
						"error": $.t("error_account_id")
					};
				}
			}
		}, { isAsync: false });

		var $btn = $modal.find("button.btn-primary:not([data-dismiss=modal], .ignore)");

		MRS.storageSelect("contacts", [{
			"account": data.account_id
		}, {
			"name": data.name
		}], function (error, contacts) {
			if (contacts && contacts.length) {
				if (contacts[0].name == data.name) {
					$modal.find(".error_message").html($.t("error_contact_name_exists")).show();
				} else {
					$modal.find(".error_message").html($.t("error_contact_account_id_exists")).show();
				}
				$btn.button("reset");
				$modal.modal("unlock");
			} else {
				MRS.storageInsert("contacts", "name", {
					name: data.name,
					email: data.email,
					account: data.account_id,
					accountRS: data.account_rs,
					description: data.description
				}, function () {
					MRS.contacts[data.account_id] = {
						name: data.name,
						email: data.email,
						account: data.account_id,
						accountRS: data.account_rs,
						description: data.description
					};
					setTimeout(function () {
						$btn.button("reset");
						$modal.modal("unlock");
						$modal.modal("hide");
						$.growl($.t("success_contact_add"), {
							"type": "success"
						});
						if (MRS.currentPage == "contacts") {
							MRS.loadPage("contacts");
						} else if (MRS.currentPage == "messages" && MRS.selectedContext) {
							var heading = MRS.selectedContext.find("h4.list-group-item-heading");
							if (heading.length) {
								heading.html(data.name.escapeHTML());
							}
							MRS.selectedContext.data("context", "messages_sidebar_update_context");
						}
					}, 50);
				}, true);
			}
		});
	};

	$("#update_contact_modal").on("show.bs.modal", function(e) {
		var $invoker = $(e.relatedTarget);
		var contactId = parseInt($invoker.data("contact"), 10);
		if (!contactId && MRS.selectedContext) {
			var accountId = MRS.selectedContext.data("account");
			var dbKey = (MRS.isRsAccount(accountId) ? "accountRS" : "account");
			var dbQuery = {};
			dbQuery[dbKey] = accountId;
			MRS.storageSelect("contacts", [dbQuery], function(error, contact) {
				contact = contact[0];
				$("#update_contact_id").val(contact.id);
				$("#update_contact_name").val(contact.name);
				$("#update_contact_email").val(contact.email);
				$("#update_contact_account_id").val(contact.accountRS);
				$("#update_contact_description").val(contact.description);
			});
		} else {
			$("#update_contact_id").val(contactId);
			MRS.storageSelect("contacts", [{
				"id": contactId
			}], function(error, contact) {
				contact = contact[0];
				$("#update_contact_name").val(contact.name);
				$("#update_contact_email").val(contact.email);
				$("#update_contact_account_id").val(contact.accountRS);
				$("#update_contact_description").val(contact.description);
			});
		}
	});

	MRS.forms.updateContact = function($modal) {
		var data = MRS.getFormData($modal.find("form:first"));
		data.account_id = String(data.account_id);
		if (!data.name) {
			return {
				"error": $.t("error_contact_name_required")
			};
		} else if (!data.account_id) {
			return {
				"error": $.t("error_account_id_required")
			};
		}

		if (data.account_id.charAt(0) == '@') {
			var convertedAccountId = $modal.find("input[name=converted_account_id]").val();
			if (convertedAccountId) {
				data.account_id = convertedAccountId;
			} else {
				return {
					"error": $.t("error_account_id")
				};
			}
		}
		var contactId = parseInt($("#update_contact_id").val(), 10);
		if (!contactId) {
			return {
				"error": $.t("error_contact")
			};
		}
		var address;
		if (MRS.isRsAccount(data.account_id)) {
			data.account_rs = data.account_id;
			address = new MetroAddress();
			if (address.set(data.account_rs)) {
				data.account = address.account_id();
			} else {
				return {
					"error": $.t("error_account_id")
				};
			}
		} else {
			address = new MetroAddress();
			if (address.set(data.account_id)) {
				data.account_rs = address.toString();
			} else {
				return {
					"error": $.t("error_account_id")
				};
			}
		}

		MRS.sendRequest("getAccount", {
			"account": data.account_id
		}, function(response) {
			if (!response.errorCode) {
				if (response.account != data.account_id || response.accountRS != data.account_rs) {
					return {
						"error": $.t("error_account_id")
					};
				}
			}
		}, { isAsync: false });

		var $btn = $modal.find("button.btn-primary:not([data-dismiss=modal])");
		MRS.storageSelect("contacts", [{
			"account": data.account_id
		}], function(error, contacts) {
			if (contacts && contacts.length && contacts[0].id != contactId) {
				$modal.find(".error_message").html($.t("error_contact_exists")).show();
				$btn.button("reset");
				$modal.modal("unlock");
			} else {
				MRS.storageUpdate("contacts", {
					name: data.name,
					email: data.email,
					account: data.account_id,
					accountRS: data.account_rs,
					description: data.description
				}, [{
					"id": contactId
				}], function() {
					if (contacts.length && data.account_id != contacts[0].accountId) {
						delete MRS.contacts[contacts[0].accountId];
					}
					MRS.contacts[data.account_id] = {
						name: data.name,
						email: data.email,
						account: data.account_id,
						accountRS: data.account_rs,
						description: data.description
					};

					setTimeout(function() {
						$btn.button("reset");
						$modal.modal("unlock");
						$modal.modal("hide");
						$.growl($.t("success_contact_update"), {
							"type": "success"
						});

						if (MRS.currentPage == "contacts") {
							MRS.loadPage("contacts");
						} else if (MRS.currentPage == "messages" && MRS.selectedContext) {
							var heading = MRS.selectedContext.find("h4.list-group-item-heading");
							if (heading.length) {
								heading.html(data.name.escapeHTML());
							}
						}
					}, 50);
				});
			}
		});
	};

	$("#delete_contact_modal").on("show.bs.modal", function(e) {
		var $invoker = $(e.relatedTarget);
		var contactId = $invoker.data("contact");
		$("#delete_contact_id").val(contactId);
		MRS.storageSelect("contacts", [{
			"id": contactId
		}], function(error, contact) {
			contact = contact[0];
			$("#delete_contact_name").html(contact.name.escapeHTML());
			$("#delete_contact_account_id").val(MRS.getAccountFormatted(contact, "account"));
		});
	});

	MRS.forms.deleteContact = function() {
		var id = parseInt($("#delete_contact_id").val(), 10);
		MRS.storageDelete("contacts", [{
			"id": id
		}], function() {
			delete MRS.contacts[$("#delete_contact_account_id").val()];
			setTimeout(function() {
				$.growl($.t("success_contact_delete"), {
					"type": "success"
				});

				if (MRS.currentPage == "contacts") {
					MRS.loadPage("contacts");
				}
			}, 50);
		});
		return {
			"stop": true
		};
	};

	MRS.exportContacts = function() {
		if (MRS.contacts && (Object.keys(MRS.contacts).length > 0)) {
			var contacts_download = document.createElement('a');
			contacts_download.href = 'data:text/json,' + JSON.stringify( MRS.contacts );
			contacts_download.target = '_blank';
			contacts_download.download = 'contacts.json';
			document.body.appendChild(contacts_download);
			contacts_download.click();
			document.body.removeChild(contacts_download);
		} else {
			$.growl($.t("error_no_contacts_available"), {"type":"warning"}).show();
		}
	};
	$("#export_contacts_button").on("click", function() {
		MRS.exportContacts();
	});

	MRS.importContacts = function(imported_contacts) {
		$.each(imported_contacts, function(index, imported_contact) {
			MRS.storageSelect("contacts", [{
				"account": imported_contact.account
			}, {
				"name": imported_contact.name
			}], function(error, contacts) {
				if (contacts && contacts.length) {
					if (contacts[0].name == imported_contact.name) {
						$.growl(imported_contact.name + ' - ' + $.t("error_contact_name_exists"), {"type":"warning"}).show();
					} else {
						$.growl(imported_contact.account + ' - ' + $.t("error_contact_account_id_exists"), {"type":"warning"}).show();
					}
				} else {
					MRS.storageInsert("contacts", "name", {
						name: imported_contact.name,
						email: imported_contact.email,
						account: imported_contact.account,
						accountRS: imported_contact.accountRS,
						description: imported_contact.description
					}, function() {
						MRS.contacts[imported_contact.account] = {
							name: imported_contact.name,
							email: imported_contact.email,
							account: imported_contact.account,
							accountRS: imported_contact.accountRS,
							description: imported_contact.description
						};

						setTimeout(function() {
							$.growl(imported_contact.name + ' - ' + $.t("success_contact_add"), {
								"type": "success"
							});
							if (MRS.currentPage == "contacts") {
								MRS.loadPage("contacts");
							} else if (MRS.currentPage == "messages" && MRS.selectedContext) {
								var heading = MRS.selectedContext.find("h4.list-group-item-heading");
								if (heading.length) {
									heading.html(imported_contact.name.escapeHTML());
								}
								MRS.selectedContext.data("context", "messages_sidebar_update_context");
							}
						}, 50);
					}, true);
				}
			});
		});
	};

	var importContactsButtonField = $("#import_contacts_button_field");
    importContactsButtonField.css({'display':'none'});
	importContactsButtonField.on("change", function(button_event) {
		button_event.preventDefault();
            var importContactsButtonField = $("#import_contacts_button_field");
            var file = importContactsButtonField[0].files[0];
            var reader = new FileReader();
            reader.onload = function (read_event) {
                var imported_contacts = JSON.parse(read_event.target.result);
                MRS.importContacts(imported_contacts);
            };
            reader.readAsText(file);
            var button = importContactsButtonField;
            button.replaceWith(button.clone(true) ); // Recreate button to clean it
		return false;
	});

	$("#import_contacts_button").on("click", function() {
		if (window.FileReader) {
            $("#import_contacts_button_field").click();
        } else if (window.java) {
            var result = java.readContactsFile();
            var contacts = JSON.parse(result);
            if (contacts.error) {
                if (contacts.type == 1) {
                    $.growl($.t(contacts.error, { file: contacts.file, folder: contacts.folder }));
                } else {
                    $.growl(contacts.error);
                }
            } else {
                MRS.importContacts(contacts);
            }
        }
	});

	return MRS;
}(MRS || {}, jQuery));