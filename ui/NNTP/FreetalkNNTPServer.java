/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.Freetalk;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import freenet.io.NetworkInterface;
import freenet.support.Logger;

/**
 * How to implement this:
 * - Getting a board list: mMessageManager.boardIterator()
 * - Getting messages in a board: mMessageManager.threadIterator()
 * - Getting replies to a message: message.childrenIterator()
 * - Things which might be missing: Functions in FTMessage and FTidentity for getting UIDs which are compatible to NNTP, plus functions
 * in FTMessageManager/FTIdentityManager for retrieving by those UIDs. Ask for them and they will be implemented.
 */
public class FreetalkNNTPServer implements Runnable {

	private Freetalk freetalk;

	private int port;
	private String bindTo;
	private String allowedHosts;

	private NetworkInterface iface;
	private boolean shutdown;

	private ArrayList clientHandlers;

	public FreetalkNNTPServer(Freetalk ft, int port, String bindTo, String allowedHosts) {
		freetalk = ft;
		this.port = port;
		this.bindTo = bindTo;
		this.allowedHosts = allowedHosts;
		shutdown = false;
		clientHandlers = new ArrayList();
	}

	public void terminate() {
		shutdown = true;
		try {
			iface.close();
		}
		catch (IOException e) {
			Logger.error(this, "Error shutting down NNTP server: " + e.getMessage());
		}

		// Close client sockets
		for (Iterator i = clientHandlers.iterator(); i.hasNext(); ) {
			FreetalkNNTPHandler handler = (FreetalkNNTPHandler) i.next();
			handler.terminate();
		}
	}

	public void run() {
		try {
			iface = NetworkInterface.create(port, bindTo, allowedHosts,
											freetalk.pr.getNode().executor, true);
			iface.setSoTimeout(10000);
			while (!shutdown) {
				try {
					Socket clientSocket = iface.accept();
					FreetalkNNTPHandler handler = new FreetalkNNTPHandler(freetalk, clientSocket);
					Thread thread = new Thread(handler);
					thread.start();

					clientHandlers.add(handler);
				}
				catch (SocketTimeoutException e) {
					// ignore
				}
				catch (IOException e) {
					Logger.error(this, "Error in NNTP server: " + e.getMessage());
				}

				// Remove disconnected clients from the list
				for (Iterator i = clientHandlers.iterator(); i.hasNext(); ) {
					FreetalkNNTPHandler handler = (FreetalkNNTPHandler) i.next();
					if (!handler.isAlive()) {
						i.remove();
					}
				}
			}
		}
		catch (IOException e) {
			Logger.error(this, "Unable to start NNTP server: " + e.getMessage());
		}
	}
}
