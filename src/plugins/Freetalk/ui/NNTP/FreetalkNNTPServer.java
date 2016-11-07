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
public final class FreetalkNNTPServer implements Runnable {

	private final Freetalk mFreetalk;

	/** Comma-separated list of addresses to bind to. */
	private final String mBindTo;
	/** Port to listen on for connections. */
	private final int mPort;
	/** Comma-separated list of hosts to accept connections from. */
	private final String mAllowedHosts;

	private NetworkInterface mInterface;
	
	private Thread mThread;
	private volatile boolean mIsRunning;

	private final ArrayList<FreetalkNNTPHandler> clientHandlers;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(FreetalkNNTPServer.class);
	}
	

	public FreetalkNNTPServer(Freetalk ft, int port, String bindTo, String allowedHosts) {
		mFreetalk = ft;
		mBindTo = bindTo;
		mPort = port;
		mAllowedHosts = allowedHosts;
		mThread = null;
		mIsRunning = false;
		clientHandlers = new ArrayList<FreetalkNNTPHandler>();
	}
	
	public void start() {
		if(logDEBUG) Logger.debug(this, "Starting...");
		mIsRunning = true;
		mFreetalk.getPluginRespirator().getNode().executor.execute(this, "Freetalk " + this.getClass().getSimpleName());
		synchronized(this) {
			while(mThread == null) {
				try {
					wait();
				} catch(InterruptedException e) { }
			}
		}
		if(logDEBUG) Logger.debug(this, "Started.");
	}

	/**
	 * Shut down the server and disconnect any currently-connected clients.
	 */
	public void terminate() {
		if(logDEBUG) Logger.debug(this, "Terminating...");
		
		mIsRunning = false;
		
		synchronized(this) {
			while(mThread != null) {
				try {
					// We cannot .join() the thread because it might be re-used by the thread-pool after run() exits.
					this.wait();
				} catch (InterruptedException e) { }
			}
		}
		
		if(logDEBUG) Logger.debug(this, "Terminated.");
	}

	/**
	 * Main server connection loop
	 */
	@Override public void run() {
		if(logDEBUG) Logger.debug(this, "Main loop started.");
		synchronized(this) {
			mThread = Thread.currentThread();
			notifyAll();
		}
		
		try {
			mInterface = NetworkInterface.create(mPort, mBindTo, mAllowedHosts, mFreetalk.getPluginRespirator().getNode().executor, true);
			/* TODO: NetworkInterface.accept() currently does not support being interrupted by Thread.interrupt(),
			 * shutdown works by timeout. This sucks and should be changed. As long as it is still like that,
			 * we have to use a low timeout. */
			mInterface.setSoTimeout(1000);
			while (mIsRunning) {
				final Socket clientSocket = mInterface.accept();
				if(clientSocket != null) { /* null is returned on timeout */
					try {
						acceptConnection(clientSocket);
					} catch(SocketException e) {
						Logger.error(this, "Accepting connection failed.", e);
					}
				}
				
				garbageCollectDisconnectedHandlers();
			}
		} catch (IOException e) {
			Logger.error(this, "Unable to start NNTP server", e);
		} finally {
			terminateHandlers(); // Does not throw.
			
			if(mInterface != null) {
				try {
					mInterface.close();
				} catch(IOException e) {}
			}
			
			if(logDEBUG) Logger.debug(this, "Main loop exiting...");
			synchronized(this) {
				mThread = null;
				notifyAll();
			}
		}
	}
	
	private void acceptConnection(Socket clientSocket) throws SocketException {
		final FreetalkNNTPHandler handler = new FreetalkNNTPHandler(mFreetalk, clientSocket);

		synchronized(clientHandlers) {
			clientHandlers.add(handler);
		}
		
		mFreetalk.getPluginRespirator().getNode().executor.execute(handler, "Freetalk NNTP Client " + clientSocket.getInetAddress());
		if(logDEBUG) Logger.debug(this, "Accepted an NNTP connection from " + clientSocket.getInetAddress());
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
	
	/**
	 * Does not throw RuntimeExceptions to make it safe to call during shutdown.
	 */
	private void terminateHandlers() {
		try {
		if(logDEBUG) Logger.debug(this, "Closing client handlers...");
		synchronized(clientHandlers) {
			// Close client sockets
			for (final FreetalkNNTPHandler handler : clientHandlers) {
				handler.terminate();
			}
			
			clientHandlers.clear();
		}
		} catch(RuntimeException e) {
			Logger.error(this, "Closing client handlers failed", e);
		}
	}
}
