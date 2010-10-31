/*
 * Freetalk - PreviewPane.java - Copyright © 2010 David Roden
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugins.Freetalk.ui.web;

import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageMaker;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;

import plugins.Freetalk.Message;
import plugins.Freetalk.Message.TextElement;
import plugins.Freetalk.WoT.WoTIdentityManager;

/**
 * Creates a preview pane suitable for inclusion on {@link NewThreadPage}s and
 * {@link NewReplyPage}s.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class PreviewPane {

	/**
	 * Creates an infobox that contains a preview of the given message text.
	 *
	 * @param pageMaker
	 *            A page maker
	 * @param l10n
	 *            The l10n handler
	 * @param messageSubject
	 *            The subject of the message
	 * @param messageText
	 *            The text of the message
	 * @return An HTMLNode containing the preview
	 */
	public static HTMLNode createPreviewPane(PageMaker pageMaker, BaseL10n l10n, String messageSubject, String messageText, WoTIdentityManager identityManager) {
		HTMLNode previewNode = new HTMLNode("div", "class", "message");
		InfoboxNode infobox = pageMaker.getInfobox(l10n.getString("PreviewPane.Header.Preview", "subject", messageSubject));
		previewNode.addChild(infobox.outer);
		HTMLNode messageBodyNode = infobox.content.addChild("div", "class", "body");
		// FIXME: usage of static functions, need to add a separate class for bbcode parsing/converting?
		TextElement element = Message.parseText(messageText, "", "" ,20);
		ThreadPage.elementsToHTML(messageBodyNode, element.mChildren, identityManager);
		return previewNode;
	}

	/**
	 * Creates a “preview” button that can be included in arbitrary forms.
	 *
	 * @param l10n
	 *            The L10n handler
	 * @param inputName
	 *            The name of the input control
	 * @return A “preview” button
	 */
	public static HTMLNode createPreviewButton(BaseL10n l10n, String inputName) {
		return new HTMLNode("input", new String[] { "type", "name", "value" }, new String[] { "submit", inputName, l10n.getString("PreviewPane.Button.Preview") });
	}

}
