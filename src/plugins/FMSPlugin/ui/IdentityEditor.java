/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin.ui;

import java.util.Date;
import java.util.List;

import plugins.FMSPlugin.FMS;
import plugins.FMSPlugin.FMSIdentity;
import plugins.FMSPlugin.FMSOwnIdentity;
import plugins.FMSPlugin.FMSPlugin;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;

import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class IdentityEditor {
		
	/* === own identities ==================== */
	
	public static final String makeOwnIdentitiesPage(FMS fms, HTTPRequest request) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);

		HTMLNode box = fms.pm.getInfobox("Own Identities");
		HTMLNode boxContent = fms.pm.getContentNode(box);
		contentNode.addChild(box);

		ObjectSet<FMSOwnIdentity> ownIdentities = fms.db_config.queryByExample(FMSOwnIdentity.class);
		if (ownIdentities.size() == 0) {
			boxContent.addChild("p", "You have no own identity yet, you should create one...");
		} else {

			HTMLNode identitiesTable = boxContent.addChild("table");
			HTMLNode row = identitiesTable.addChild("tr");
			row.addChild("th", "Name");
			row.addChild("th", "Request URI");
			row.addChild("th", "Insert URI");
			row.addChild("th", "Publish TrustList ?");
			row.addChild("th", "Last update");
			row.addChild("th");

			while (ownIdentities.hasNext()) {
				FMSOwnIdentity id = ownIdentities.next();
				row = identitiesTable.addChild("tr");
				row.addChild("td", id.getNickName());
				row.addChild("td", new String[]{"title"}, new String[]{id.getRequestURI()}, id.getRequestURI().substring(0, 35)+"...");
				row.addChild("td", new String[]{"title"}, new String[]{id.getInsertURI()}, id.getInsertURI().substring(0, 15)+"...");
				row.addChild("td", id.doesPublishTrustList()?"yes":"no");
				HTMLNode lastUpdateCell = row.addChild("td");
				if (id.getLastInsert() == null) {
					lastUpdateCell.addChild("p", "Insert in progress...");
				} else if (id.getLastInsert().equals(new Date(0))) {
					lastUpdateCell.addChild("p", "Never");
				} else {
					lastUpdateCell.addChild(new HTMLNode("a", "href", "/" + id.getRequestURI().toString(), id.getLastInsert().toString()));
				}
				HTMLNode deleteCell = row.addChild("td");
				HTMLNode deleteForm = fms.pr.addFormChild(deleteCell, FMSPlugin.SELF_URI + "/deleteOwnIdentity", "deleteForm");
				deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "identity", id.getRequestURI()});
				deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete", "Delete" });
			}
		}

		contentNode.addChild(createNewOwnIdentityBox(fms));

		return pageNode.generate();
	}
	
	/* === new own identity ================== */
	
	public static final String makeNewOwnIdentityPage(FMS fms, String nick, String requestUri, String insertUri, boolean publish, List<String> errors) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild(createNewOwnIdentityBox(fms, nick, requestUri, insertUri, publish, errors));
		return pageNode.generate();
	}

	private static final HTMLNode createNewOwnIdentityBox(FMS fms) {
		return createNewOwnIdentityBox(fms, "", "", "", true, null);
	}

	private static final HTMLNode createNewOwnIdentityBox(FMS fms, String nick, String requestUri, String insertUri, boolean publish, List<String> errors) {
		HTMLNode addBox = fms.pm.getInfobox("New Identity");
		HTMLNode addContent = fms.pm.getContentNode(addBox);

		if (errors != null) {
			HTMLNode errorBox = fms.pm.getInfobox("infobox-alert", "Typo");
			HTMLNode errorContent = fms.pm.getContentNode(errorBox);
			for (String s : errors) {
				errorContent.addChild("#", s);
				errorContent.addChild("br");
			}
			addContent.addChild(errorBox);
		}

		HTMLNode addForm = fms.pr.addFormChild(addContent, FMSPlugin.SELF_URI + "/createownidentity", "addForm");

		HTMLNode table = addForm.addChild("table", "class", "column");
		HTMLNode tr1 = table.addChild("tr");
		tr1.addChild("td", "width", "10%", "Nick:\u00a0");
		HTMLNode cell12 = tr1.addChild("td", "width", "90%");
		cell12.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "nick", "70", nick });

		HTMLNode tr2 = table.addChild("tr");
		tr2.addChild("td", "Request\u00a0URI:\u00a0");
		HTMLNode cell22= tr2.addChild("td");
		cell22.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "requestURI", "70", requestUri });

		HTMLNode tr3 = table.addChild("tr");
		tr3.addChild("td", "Insert\u00a0URI:\u00a0");
		HTMLNode cell32= tr3.addChild("td");
		cell32.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "insertURI", "70", insertUri });
		
		HTMLNode tr4 = table.addChild("tr");
		tr4.addChild("td", "Publish\u00a0trust\u00a0list:\u00a0");
		HTMLNode cell42= tr4.addChild("td");
		if (publish)
			cell42.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", "publishTrustList", "true", "checked" });
		else
			cell42.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "publishTrustList", "false" });
		addForm.addChild("br");
		addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "create", "Create a new identity !" });
		return addBox;
	}
	
	/* === delete own identity =============== */
	
	public static String makeDeleteOwnIdentityPage(FMS fms, String requestUri, List<String> err) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild(deleteOwnIdentityBox(fms, "nick", requestUri, "insertUri", false, err));
		return pageNode.generate();
	}
	
	private static final HTMLNode deleteOwnIdentityBox(FMS fms, String nick, String requestUri, String insertUri, boolean publish, List<String> errors) {
		HTMLNode addBox = fms.pm.getInfobox("Delete Identity");
		HTMLNode addContent = fms.pm.getContentNode(addBox);

		if (errors != null) {
			HTMLNode errorBox = fms.pm.getInfobox("infobox-alert", "Typo");
			HTMLNode errorContent = fms.pm.getContentNode(errorBox);
			for (String s : errors) {
				errorContent.addChild("#", s);
				errorContent.addChild("br");
			}
			addContent.addChild(errorBox);
		}

		HTMLNode addForm = fms.pr.addFormChild(addContent, FMSPlugin.SELF_URI + "/deleteOwnIdentity", "deleteForm");
		addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "confirmed", "true"});
		addForm.addChild("#", "Nick : ");
		addForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "nick", "70", nick });
		addForm.addChild("br");
		addForm.addChild("#", "Request URI : ");
		addForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "identity", "70", requestUri });
		addForm.addChild("br");
		addForm.addChild("#", "Insert URI : ");
		addForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "insertURI", "70", insertUri });
		addForm.addChild("br");
		addForm.addChild("#", "Publish trust list ");
		if (publish)
			addForm.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", "publishTrustList", "true", "checked" });
		else
			addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "publishTrustList", "false" });
		addForm.addChild("br");
		addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete", "Delete identity" });
		return addBox;
	}
	
	/* === others  identities ================ */
	
	public final static String makeKnownIdentitiesPage(FMS fms, HTTPRequest request) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);

		HTMLNode box = fms.pm.getInfobox("Known Identities");
		HTMLNode boxContent = fms.pm.getContentNode(box);
		contentNode.addChild(box);

		ObjectSet<FMSIdentity> identities = fms.db_config.queryByExample(FMSIdentity.class);

		HTMLNode identitiesTable = boxContent.addChild("table", "border", "0");
		HTMLNode row = identitiesTable.addChild("tr");
		row.addChild("th", "Name");
		row.addChild("th", "Request URI");
		row.addChild("th");

		while (identities.hasNext()) {
			FMSIdentity id = identities.next();
			if (id instanceof FMSOwnIdentity)
				continue;
			row = identitiesTable.addChild("tr");
			row.addChild("td", id.getNickName());
			row.addChild("td",  id.getRequestURI());
			HTMLNode deleteCell = row.addChild("td");
			HTMLNode deleteForm = fms.pr.addFormChild(deleteCell, FMSPlugin.SELF_URI + "/deleteIdentity", "deleteForm");
			deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "identity", id.getRequestURI()});
			deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete", "Delete" });
		}

		contentNode.addChild(createNewKnownIdentityBox(fms));

		return pageNode.generate();
	}
	
	/* === new others identities ============= */
	
	public static final String makeNewKnownIdentityPage(FMS fms, String requestUri, List<String> errors) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild(createNewKnownIdentityBox(fms, requestUri, errors));
		contentNode.addChild("#", "makeNewIdentitiesPagecc");
		return pageNode.generate();
	}
	
	private static final HTMLNode createNewKnownIdentityBox(FMS fms) {
		return createNewKnownIdentityBox(fms, "", null);
	}

	private static final HTMLNode createNewKnownIdentityBox(FMS fms, String requestUri, List<String> errors) {
		HTMLNode addBox = fms.pm.getInfobox("Add Identity");
		HTMLNode addContent = fms.pm.getContentNode(addBox);

		if (errors != null) {
			HTMLNode errorBox = fms.pm.getInfobox("infobox-alert", "Typo");
			HTMLNode errorContent = fms.pm.getContentNode(errorBox);
			for (String s : errors) {
				errorContent.addChild("#", s);
				errorContent.addChild("br");
			}
			addContent.addChild(errorBox);
		}

		HTMLNode addForm = fms.pr.addFormChild(addContent, FMSPlugin.SELF_URI + "/addknownidentity", "addForm");

		addForm.addChild("#", "Request URI : ");
		addForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "requestURI", "70", requestUri });
		addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "create", "Add identity !" });
		return addBox;
	}
	
	/* delete */
	public static String makeDeleteKnownIdentityPage(FMS fms, String requestUri, List<String> err) {
		HTMLNode pageNode = fms.getPageNode();
		HTMLNode contentNode = fms.pm.getContentNode(pageNode);
		contentNode.addChild(deleteKnownIdentityBox(fms, "nick", requestUri, "insertUri", false, err));
		return pageNode.generate();
	}
	
	private static final HTMLNode deleteKnownIdentityBox(FMS fms, String nick, String requestUri, String insertUri, boolean publish, List<String> errors) {
		HTMLNode addBox = fms.pm.getInfobox("Delete Identity");
		HTMLNode addContent = fms.pm.getContentNode(addBox);

		if (errors != null) {
			HTMLNode errorBox = fms.pm.getInfobox("infobox-alert", "Typo");
			HTMLNode errorContent = fms.pm.getContentNode(errorBox);
			for (String s : errors) {
				errorContent.addChild("#", s);
				errorContent.addChild("br");
			}
			addContent.addChild(errorBox);
		}

		HTMLNode addForm = fms.pr.addFormChild(addContent, FMSPlugin.SELF_URI + "/deleteIdentity", "deleteForm");
		addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "confirmed", "true"});
		addForm.addChild("#", "Nick : ");
		addForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "nick", "70", nick });
		addForm.addChild("br");
		addForm.addChild("#", "Request URI : ");
		addForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "identity", "70", requestUri });
		addForm.addChild("br");
		addForm.addChild("#", "Insert URI : ");
		addForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "insertURI", "70", insertUri });
		addForm.addChild("br");
		addForm.addChild("#", "Publish trust list ");
		if (publish)
			addForm.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", "publishTrustList", "true", "checked" });
		else
			addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "publishTrustList", "false" });
		addForm.addChild("br");
		addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete", "Delete identity" });
		return addBox;
	}

	
	/* === utils ============================= */
	
	public static final void checkNick(List<String> err, String nick) {
		if (nick.length() == 0) {
			err.add("Nick can not be empty.");
		} else if (nick.length() > 128) {
			err.add("Nick to long. 127 chars should be enougth...");
		}
	}
	
	public static final void checkInsertURI(List<String> err, String insertUri) {
		if (insertUri.length() == 0) {
			err.add("Insert URI is missing");
			return;
		}
	}
	
	public static final void checkRequestURI(List<String> err, String requestUri) {
		if (requestUri.length() == 0) {
			err.add("Request URI is missing");
			return;
		}
	}
	
	public static final void addNewOwnIdentity(ObjectContainer db, FMSOwnIdentity identity, List<String> err) {
		try {
			db.store(identity);
			db.commit();
		} catch (Throwable t) {
			Logger.error(IdentityEditor.class, "Error while adding Identity: " + t.getMessage(), t);
			err.add("Error while adding Identity: " + t.getMessage());
			db.rollback();
		}
	}
	
	public static final void addNewKnownIdentity(ObjectContainer db, FMSIdentity identity, List<String> err) {
		try {
			db.store(identity);
			db.commit();
		} catch (Throwable t) {
			Logger.error(IdentityEditor.class, "Error while adding Identity: " + t.getMessage(), t);
			err.add("Error while adding Identity: " + t.getMessage());
			db.rollback();
		}
	}
	
	public static void deleteIdentity(FMS fms, String requestUri, List<String> err) {
		FMSIdentity templateId = new FMSIdentity(null, requestUri);
		
		ObjectSet<FMSIdentity> toDelete = fms.db_config.queryByExample(templateId);
		if (toDelete.size() > 0) {
			for (FMSIdentity id:toDelete) {
				fms.db_config.delete(id);
			}
			fms.db_config.commit();
		} else {
			err.add("Identity »"+requestUri+"« not found, nothing deleted");
		}
	}
}
