/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui;

import java.util.Iterator;
import java.util.List;

import plugins.Freetalk.FTIdentity;
import plugins.Freetalk.FTOwnIdentity;
import plugins.Freetalk.Freetalk;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

public class IdentityEditor {

	/* === own identities ==================== */
	
	public static final String makeOwnIdentitiesPage(Freetalk ft, HTTPRequest request) {
		
		HTMLNode pageNode = ft.getPageNode();
		HTMLNode contentNode = ft.mPageMaker.getContentNode(pageNode);

		HTMLNode box = ft.mPageMaker.getInfobox("Own Identities");
		HTMLNode boxContent = ft.mPageMaker.getContentNode(box);
		contentNode.addChild(box);

		Iterator<FTOwnIdentity> ownIdentities = ft.getIdentityManager().ownIdentityIterator();
		if (ownIdentities.hasNext() == false) {
			boxContent.addChild("p", "No own identities received from the WoT plugin yet. Please create one there and wait for 15 minutes until it appears here.");
		} else {

			HTMLNode identitiesTable = boxContent.addChild("table");
			HTMLNode row = identitiesTable.addChild("tr");
			row.addChild("th", "Name");
			row.addChild("th", "Request URI");
			row.addChild("th", "Insert URI");
			row.addChild("th", "Last update");
			row.addChild("th");

			while (ownIdentities.hasNext()) {
				FTOwnIdentity id = ownIdentities.next();
				row = identitiesTable.addChild("tr");
				row.addChild("td", id.getNickname());
				row.addChild("td", new String[]{"title"}, new String[]{id.getRequestURI().toACIIString()}, id.getRequestURI().toACIIString().substring(0, 35)+"...");
				row.addChild("td", new String[]{"title"}, new String[]{id.getInsertURI().toACIIString()}, id.getInsertURI().toACIIString().substring(0, 35)+"...");
				HTMLNode lastUpdateCell = row.addChild("td");
				/*
				if (id.getLastInsert() == null) {
					lastUpdateCell.addChild("p", "Insert in progress...");
				} else if (id.getLastInsert().equals(new Date(0))) {
					lastUpdateCell.addChild("p", "Never");
				} else {
					lastUpdateCell.addChild(new HTMLNode("a", "href", "/" + id.getRequestURI().toString(), id.getLastInsert().toString()));
				}
				*/
				/* FIXME: repair, i.e. make it use the WoT plugin */
				/*
				HTMLNode deleteCell = row.addChild("td");
				HTMLNode deleteForm = ft.mPluginRespirator.addFormChild(deleteCell, Freetalk.PLUGIN_URI + "/deleteOwnIdentity", "deleteForm");
				deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "identity", id.getRequestURI().toACIIString()});
				deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete", "Delete" });
				*/
			}
		}

		/* FIXME: repair, i.e. make it use the WoT plugin */
		/* contentNode.addChild(createNewOwnIdentityBox(ft)); */

		return pageNode.generate();
	}
	
	/* === new own identity ================== */
	
	public static final String makeNewOwnIdentityPage(Freetalk ft, String nick, String requestUri, String insertUri, boolean publish, List<String> errors) {
		HTMLNode pageNode = ft.getPageNode();
		HTMLNode contentNode = ft.mPageMaker.getContentNode(pageNode);
		contentNode.addChild(createNewOwnIdentityBox(ft, nick, requestUri, insertUri, publish, errors));
		return pageNode.generate();
	}

	private static final HTMLNode createNewOwnIdentityBox(Freetalk ft) {
		return createNewOwnIdentityBox(ft, "", "", "", true, null);
	}

	private static final HTMLNode createNewOwnIdentityBox(Freetalk ft, String nick, String requestUri, String insertUri, boolean publish, List<String> errors) {
		HTMLNode addBox = ft.mPageMaker.getInfobox("New Identity");
		HTMLNode addContent = ft.mPageMaker.getContentNode(addBox);

		if (errors != null) {
			HTMLNode errorBox = ft.mPageMaker.getInfobox("infobox-alert", "Typo");
			HTMLNode errorContent = ft.mPageMaker.getContentNode(errorBox);
			for (String s : errors) {
				errorContent.addChild("#", s);
				errorContent.addChild("br");
			}
			addContent.addChild(errorBox);
		}

		HTMLNode addForm = ft.mPluginRespirator.addFormChild(addContent, Freetalk.PLUGIN_URI + "/createownidentity", "addForm");

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
	
	public static String makeDeleteOwnIdentityPage(Freetalk ft, String requestUri, List<String> err) {
		HTMLNode pageNode = ft.getPageNode();
		HTMLNode contentNode = ft.mPageMaker.getContentNode(pageNode);
		contentNode.addChild(deleteOwnIdentityBox(ft, "nick", requestUri, "insertUri", false, err));
		return pageNode.generate();
	}
	
	private static final HTMLNode deleteOwnIdentityBox(Freetalk ft, String nick, String requestUri, String insertUri, boolean publish, List<String> errors) {
		HTMLNode deleteBox = ft.mPageMaker.getInfobox("Delete Identity");
		HTMLNode deleteContent = ft.mPageMaker.getContentNode(deleteBox);

		if (errors != null) {
			HTMLNode errorBox = ft.mPageMaker.getInfobox("infobox-alert", "Typo");
			HTMLNode errorContent = ft.mPageMaker.getContentNode(errorBox);
			for (String s : errors) {
				errorContent.addChild("#", s);
				errorContent.addChild("br");
			}
			deleteContent.addChild(errorBox);
		}

		HTMLNode deleteForm = ft.mPluginRespirator.addFormChild(deleteContent, Freetalk.PLUGIN_URI + "/deleteOwnIdentity", "deleteForm");
		deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "confirmed", "true"});
		deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "identity", requestUri});
		deleteForm.addChild("#", "Nick:\u00a0"+nick);
		deleteForm.addChild("br");
		deleteForm.addChild("#", "Request URI:\u00a0"+requestUri);
		deleteForm.addChild("br");
		deleteForm.addChild("#", "Insert URI:\u00a0"+insertUri);
		deleteForm.addChild("br");
		deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete", "Delete identity" });
		return deleteBox;
	}
	
	/* === others  identities ================ */
	
	public final static String makeKnownIdentitiesPage(Freetalk ft, HTTPRequest request) {
		HTMLNode pageNode = ft.getPageNode();
		HTMLNode contentNode = ft.mPageMaker.getContentNode(pageNode);

		HTMLNode box = ft.mPageMaker.getInfobox("Known Identities");
		HTMLNode boxContent = ft.mPageMaker.getContentNode(box);
		contentNode.addChild(box);

		Iterator<FTIdentity> identities = ft.getIdentityManager().iterator();

		HTMLNode identitiesTable = boxContent.addChild("table", "border", "0");
		HTMLNode row = identitiesTable.addChild("tr");
		row.addChild("th", "Name");
		row.addChild("th", "Request URI");
		row.addChild("th");

		while (identities.hasNext()) {
			FTIdentity id = identities.next();
			if (id instanceof FTOwnIdentity)
				continue;
			row = identitiesTable.addChild("tr");
			row.addChild("td", id.getNickname());
			row.addChild("td",  id.getRequestURI().toACIIString());
			HTMLNode deleteCell = row.addChild("td");
			/* FIXME: repair, i.e. make it use the WoT plugin */
			/*
			HTMLNode deleteForm = ft.mPluginRespirator.addFormChild(deleteCell, Freetalk.PLUGIN_URI + "/deleteIdentity", "deleteForm");
			deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "identity", id.getRequestURI().toACIIString()});
			deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete", "Delete" });
			*/
		}

		/* FIXME: repair, i.e. make it use the WoT plugin */
		/* contentNode.addChild(createNewKnownIdentityBox(ft)); */

		return pageNode.generate();
	}
	
	/* === new others identities ============= */
	
	public static final String makeNewKnownIdentityPage(Freetalk ft, String requestUri, List<String> errors) {
		HTMLNode pageNode = ft.getPageNode();
		HTMLNode contentNode = ft.mPageMaker.getContentNode(pageNode);
		contentNode.addChild(createNewKnownIdentityBox(ft, requestUri, errors));
		contentNode.addChild("#", "makeNewIdentitiesPagecc");
		return pageNode.generate();
	}
	
	private static final HTMLNode createNewKnownIdentityBox(Freetalk ft) {
		return createNewKnownIdentityBox(ft, "", null);
	}

	private static final HTMLNode createNewKnownIdentityBox(Freetalk ft, String requestUri, List<String> errors) {
		HTMLNode addBox = ft.mPageMaker.getInfobox("Add Identity");
		HTMLNode addContent = ft.mPageMaker.getContentNode(addBox);

		if (errors != null) {
			HTMLNode errorBox = ft.mPageMaker.getInfobox("infobox-alert", "Typo");
			HTMLNode errorContent = ft.mPageMaker.getContentNode(errorBox);
			for (String s : errors) {
				errorContent.addChild("#", s);
				errorContent.addChild("br");
			}
			addContent.addChild(errorBox);
		}

		HTMLNode addForm = ft.mPluginRespirator.addFormChild(addContent, Freetalk.PLUGIN_URI + "/addknownidentity", "addForm");

		addForm.addChild("#", "Request URI : ");
		addForm.addChild("input", new String[] { "type", "name", "size", "value" }, new String[] { "text", "requestURI", "70", requestUri });
		addForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "create", "Add identity !" });
		return addBox;
	}
	
	/* delete */
	public static String makeDeleteKnownIdentityPage(Freetalk ft, String requestUri, List<String> err) {
		HTMLNode pageNode = ft.getPageNode();
		HTMLNode contentNode = ft.mPageMaker.getContentNode(pageNode);
		contentNode.addChild(deleteKnownIdentityBox(ft, "nick", requestUri, "insertUri", false, err));
		return pageNode.generate();
	}
	
	private static final HTMLNode deleteKnownIdentityBox(Freetalk ft, String nick, String requestUri, String insertUri, boolean publish, List<String> errors) {
		HTMLNode deleteBox = ft.mPageMaker.getInfobox("Delete Identity");
		HTMLNode deleteContent = ft.mPageMaker.getContentNode(deleteBox);

		if (errors != null) {
			HTMLNode errorBox = ft.mPageMaker.getInfobox("infobox-alert", "Typo");
			HTMLNode errorContent = ft.mPageMaker.getContentNode(errorBox);
			for (String s : errors) {
				errorContent.addChild("#", s);
				errorContent.addChild("br");
			}
			deleteContent.addChild(errorBox);
		}

		HTMLNode deleteForm = ft.mPluginRespirator.addFormChild(deleteContent, Freetalk.PLUGIN_URI + "/deleteIdentity", "deleteForm");
		deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "confirmed", "true"});
		deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "identity", requestUri});
		deleteForm.addChild("#", "Nick:\u00a0"+nick);
		deleteForm.addChild("br");
		deleteForm.addChild("#", "Request URI:\u00a0"+requestUri);
		deleteForm.addChild("br");
		deleteForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete", "Delete identity" });
		return deleteBox;
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
	
	public static final void addNewOwnIdentity(Freetalk ft, FTOwnIdentity identity, List<String> err) {
		try {
			ft.getIdentityManager().addNewOwnIdentity(identity);
		} catch (Throwable t) {
			Logger.error(IdentityEditor.class, "Error while adding Identity: " + t.getMessage(), t);
			err.add("Error while adding Identity: " + t.getMessage());
		}
	}
	
	public static final void addNewKnownIdentity(Freetalk ft, FTIdentity identity, List<String> err) {
		try {
			ft.getIdentityManager().addNewIdentity(identity);
		} catch (Throwable t) {
			Logger.error(IdentityEditor.class, "Error while adding Identity: " + t.getMessage(), t);
			err.add("Error while adding Identity: " + t.getMessage());
		}
	}
	
	public static void deleteIdentity(Freetalk ft, String requestUri, List<String> err) {
		/*
		FTIdentity templateId = new FTIdentity(null, requestUri);
		
		ObjectSet<FTIdentity> toDelete = ft.db_config.queryByExample(templateId);
		if (toDelete.size() > 0) {
			for (FTIdentity id:toDelete) {
				ft.db_config.delete(id);
			}
			ft.db_config.commit();
		} else {
			err.add("Identity »"+requestUri+"« not found, nothing deleted");
		}*/
		
		// FIXME: Implement by using the identity manager.
	}
}
