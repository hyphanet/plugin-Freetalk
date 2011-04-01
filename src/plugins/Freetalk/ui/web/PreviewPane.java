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

import plugins.Freetalk.OwnIdentity;
import plugins.Freetalk.Quoting;
import freenet.clients.http.RedirectException;
import freenet.l10n.BaseL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Creates a preview pane suitable for inclusion on {@link NewThreadPage}s and
 * {@link NewReplyPage}s.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class PreviewPane extends WebPageImpl {
	
	private final String mSubject;
	private final String mText;
	
	public PreviewPane(WebInterface myWebInterface, OwnIdentity viewer, HTTPRequest request, String messageSubject, String messageText) {
		super(myWebInterface, viewer, request);
		mSubject = messageSubject;
		mText = messageText;
	}
	
	@Override
	public void make() throws RedirectException {
		throw new UnsupportedOperationException("Use get()");
	}
	
	public HTMLNode get() {
		HTMLNode authorBox = getContentBox(l10n().getString("NewThreadPage.ThreadBox.Author"));
		authorBox.addChild("b", mOwnIdentity.getFreetalkAddress());
				
		HTMLNode messageBox = getContentBox(l10n().getString("PreviewPane.Header.Preview", "subject", mSubject));
		HTMLNode messageBodyNode = messageBox.addChild("div", "class", "body");
		Quoting.TextElement element = Quoting.parseText(mText);
		ThreadPage.elementsToHTML(messageBodyNode, element.mChildren, mOwnIdentity, mFreetalk.getIdentityManager());
		
		HTMLNode previewNode = new HTMLNode("div", "class", "message");
		previewNode.addChild(authorBox);
		previewNode.addChild(messageBox);

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
