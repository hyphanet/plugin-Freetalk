/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.FTIdentityManager;
import plugins.Freetalk.FTMessageManager;
import plugins.Freetalk.Freetalk;

import java.net.Socket;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;

import freenet.support.Logger;

public class FreetalkNNTPHandler implements Runnable {

	private FTIdentityManager mIdentityManager;
	private FTMessageManager mMessageManager;

	private Socket socket;
	private BufferedReader in;
	private PrintStream out;

	public FreetalkNNTPHandler(Freetalk ft, Socket socket) {
		mIdentityManager = ft.getIdentityManager();
		mMessageManager = ft.getMessageManager();
		this.socket = socket;
	}

	public boolean isAlive() {
		return !socket.isClosed();
	}

	public synchronized void terminate() {
		try {
			socket.close();
		}
		catch (IOException e) {
			// ignore
		}
	}

	private void printStatusLine(String line) {
		out.print(line);
		out.print("\r\n");
		out.flush();
	}

	private synchronized void handleCommand(String line) throws IOException {
		String[] tokens = line.split("[ \t\r\n]+");
		if (tokens.length == 0)
			return;

		if (tokens[0].equalsIgnoreCase("QUIT")) {
			printStatusLine("205 Have a nice day.");
			socket.close();
		}
		else {
			printStatusLine("500 Command not recognized");
		}
	}

	public void run() {
		try {
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			String line;

			in = new BufferedReader(new InputStreamReader(is));
			out = new PrintStream(os);

			printStatusLine("200 Welcome to Freetalk");
			while (!socket.isClosed()) {
				line = in.readLine();
				if (line != null)
					handleCommand(line);
			}
		}
		catch (IOException e) {
			Logger.error(this, "Error in NNTP handler: " + e.getMessage());
		}
	}
}
