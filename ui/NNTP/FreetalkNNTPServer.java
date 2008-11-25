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
import freenet.node.Node;
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

	private Node node;
	private Freetalk freetalk;

	private int port;
	private String bindTo;
	private String allowedHosts;

	private NetworkInterface iface;
	private volatile boolean shutdown;
	private Thread serverThread;

	private ArrayList<FreetalkNNTPHandler> clientHandlers;

	public FreetalkNNTPServer(Node myNode, Freetalk ft, int port, String bindTo, String allowedHosts) {
		node = myNode;
		freetalk = ft;
		this.port = port; /* TODO: As soon as Freetalk has a configuration class, read it from there */
		this.bindTo = bindTo; /* TODO: As soon as Freetalk has a configuration class, read it from there */
		this.allowedHosts = allowedHosts; /* TODO: As soon as Freetalk has a configuration class, read it from there */
		shutdown = false;
		clientHandlers = new ArrayList<FreetalkNNTPHandler>();
		node.executor.execute(this, "Freetalk NNTP Server");
	}

	public void terminate() {
		shutdown = true;
		try {
			iface.close();
		}
		catch (IOException e) {
			Logger.error(this, "Error shutting down NNTP server", e);
		}
		
		try {
			serverThread.join();
		}
		catch(InterruptedException e) {
			Thread.currentThread().interrupt(); /* Some HOWTO on the web said that we have to do this here */
		}
	}

	public void run() {
		try {
			serverThread = Thread.currentThread();
			iface = NetworkInterface.create(port, bindTo, allowedHosts,
											node.executor, true);
			/* FIXME: NetworkInterface.accept() currently does not support being interrupted by Thread.interrupt(),
			 * shutdown works by timeout. This sucks and should be changed. As long as it is still like that,
			 * we have to use a low timeout. */
			iface.setSoTimeout(1000);
			while (!shutdown) {
				try {
					Socket clientSocket = iface.accept();
					FreetalkNNTPHandler handler = new FreetalkNNTPHandler(freetalk, clientSocket);
					node.executor.execute(handler, "Freetalk NNTP Client " + clientSocket.getInetAddress());

					clientHandlers.add(handler);
				}
				catch (SocketTimeoutException e) {
					// ignore
				}
				/* FIXME: This catch block is unreachable according to my java compiler, Benjamin you should uncomment it and see if your 
				 * Eclipse also underlines the IOException yellow and tells you that it's unreachable. If not, its misconfigured. 
				catch (IOException e) {
					Logger.error(this, "Error in NNTP server", e);
				}
				*/
				
				// Remove disconnected clients from the list
				for (Iterator<FreetalkNNTPHandler> i = clientHandlers.iterator(); i.hasNext(); ) {
					FreetalkNNTPHandler handler = i.next();
					if (!handler.isAlive()) {
						i.remove();
					}
				}
			}
		}
		catch (IOException e) {
			Logger.error(this, "Unable to start NNTP server", e);
		}
		
		finally {
			terminateHandlers();
		}
	}
	
	private void terminateHandlers() {
		synchronized(clientHandlers) {
		// Close client sockets
		for (Iterator<FreetalkNNTPHandler> i = clientHandlers.iterator(); i.hasNext(); ) {
			FreetalkNNTPHandler handler = (FreetalkNNTPHandler) i.next();
			handler.terminate();
		}
		}
	}
}
