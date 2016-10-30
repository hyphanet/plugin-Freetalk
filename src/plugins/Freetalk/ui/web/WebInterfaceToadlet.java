package plugins.Freetalk.ui.web;

import java.io.IOException;
import java.net.URI;

import plugins.Freetalk.Freetalk;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.node.NodeClientCore;
import freenet.support.api.HTTPRequest;

public abstract class WebInterfaceToadlet extends Toadlet implements LinkEnabledCallback {
	
	final String pageTitle;
	final WebInterface webInterface;
	final NodeClientCore core;

	protected WebInterfaceToadlet(HighLevelSimpleClient client, WebInterface wi, NodeClientCore core, String pageTitle) {
		super(client);
		this.pageTitle = pageTitle;
		this.webInterface = wi;
		this.core = core;
	}

	abstract WebPage makeWebPage(HTTPRequest req, ToadletContext context) throws RedirectException;
	
	@Override
	public String path() {
		return Freetalk.PLUGIN_URI + "/" + pageTitle;
	}

	@Override public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx)
			throws ToadletContextClosedException, IOException, RedirectException {
	    if(!ctx.checkFullAccess(this))
	        return;
		
		String ret;
		WebPage page = makeWebPage(req, ctx);
		ret = page.toHTML(ctx);
		writeHTMLReply(ctx, 200, "OK", ret);
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
	    if(!ctx.checkFullAccess(this))
	        return;
		
		String pass = request.getPartAsString("formPassword", 32);
		if ((pass.length() == 0) || !pass.equals(core.formPassword)) {
			writeHTMLReply(ctx, 403, "Forbidden", "Invalid form password.");
			return;
		}

		String ret;
		WebPage page = makeWebPage(request, ctx);
		ret = page.toHTML(ctx);
		writeHTMLReply(ctx, 200, "OK", ret);
	}

	public String getURI() {
		return Freetalk.PLUGIN_URI + "/" + pageTitle;
	}

	@Override public boolean isEnabled(ToadletContext ctx) {
		return webInterface.getFreetalk().wotConnected();
	}
	
}
