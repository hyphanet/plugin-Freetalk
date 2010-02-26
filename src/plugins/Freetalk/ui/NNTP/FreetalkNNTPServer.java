/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;

import plugins.Freetalk.Freetalk;
import freenet.io.NetworkInterface;
import freenet.node.Node;
import freenet.support.Logger;

/**
 * NNTP server.
 *
 * The server runs in a background thread so it can wait for
 * connections from clients.  Each handler runs in its own thread as
 * well.  Use terminate() to shut everything down.
 *
 * @author Benjamin Moody
 * @author xor (xor@freenetproject.org)
 */
public class FreetalkNNTPServer implements Runnable {

	private final Node mNode;
	private final Freetalk mFreetalk;

	/** Comma-separated list of addresses to bind to. */
	private final String mBindTo;
	/** Port to listen on for connections. */
	private final int mPort;
	/** Comma-separated list of hosts to accept connections from. */
	private final String mAllowedHosts;

	private NetworkInterface iface;
	private volatile boolean shutdown;
	private boolean shutdownFinished;

	private final ArrayList<FreetalkNNTPHandler> clientHandlers;

	public FreetalkNNTPServer(Node myNode, Freetalk ft, int port, String bindTo, String allowedHosts) {
		mNode = myNode;
		mFreetalk = ft;
		mBindTo = bindTo;
		mPort = port;
		mAllowedHosts = allowedHosts;
		shutdown = shutdownFinished = false;
		clientHandlers = new ArrayList<FreetalkNNTPHandler>();
	}
	
	public void start() {
		mNode.executor.execute(this, "Freetalk NNTP Server");
		Logger.debug(this, "Started.");
	}

	/**
	 * Shut down the server and disconnect any currently-connected clients.
	 */
	public void terminate() {
		shutdown = true;
		try {
			iface.close();
		}
		catch (IOException e) {
			Logger.error(this, "Error shutting down NNTP server", e);
		}
		
		synchronized (this) {
			while (!shutdownFinished) {
				try {
					wait();
				}
				catch (InterruptedException e) {
					// ignore
				}
			}
		}
	}

	/**
	 * Main server connection loop
	 */
	public void run() {
		try {
			iface = NetworkInterface.create(mPort, mBindTo, mAllowedHosts, mNode.executor, true);
			/* FIXME: NetworkInterface.accept() currently does not support being interrupted by Thread.interrupt(),
			 * shutdown works by timeout. This sucks and should be changed. As long as it is still like that,
			 * we have to use a low timeout. */
			iface.setSoTimeout(1000);
			while (!shutdown) {
				final Socket clientSocket = iface.accept();
				if(clientSocket != null) { /* null is returned on timeout */
					try {
						acceptConnection(clientSocket);
					} catch(SocketException e) {
						Logger.error(this, "Accepting connection failed.", e);
					}
				}
				
				garbageCollectDisconnectedHandlers();
			}

			Logger.debug(this, "NNTP Server exiting...");
			iface.close();
		}
		catch (IOException e) {
			Logger.error(this, "Unable to start NNTP server", e);
		}
		
		finally {
			terminateHandlers();

			synchronized (this) {
				shutdownFinished = true;
				notify();
			}
		}
	}
	
	private void acceptConnection(Socket clientSocket) throws SocketException {
		final FreetalkNNTPHandler handler = new FreetalkNNTPHandler(mFreetalk, clientSocket);

		synchronized(clientHandlers) {
			clientHandlers.add(handler);
		}
		
		mNode.executor.execute(handler, "Freetalk NNTP Client " + clientSocket.getInetAddress());
		Logger.debug(this, "Accepted an NNTP connection from " + clientSocket.getInetAddress());
	}
	
	private void garbageCollectDisconnectedHandlers() {
		synchronized(clientHandlers) {
			for (final Iterator<FreetalkNNTPHandler> i = clientHandlers.iterator(); i.hasNext(); ) {
				final FreetalkNNTPHandler handler = i.next();
				if (!handler.isAlive()) {
					i.remove();
				}
			}
		}
	}
	
	private void terminateHandlers() {
		Logger.debug(this, "Closing client handlers...");
		synchronized(clientHandlers) {
			// Close client sockets
			for (final FreetalkNNTPHandler handler : clientHandlers) {
				handler.terminate();
			}
			
			clientHandlers.clear();
		}
	}
}
