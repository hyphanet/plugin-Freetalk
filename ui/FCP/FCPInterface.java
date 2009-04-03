/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.FCP;

import java.util.Iterator;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

/**
 * FCP message format:
 *   Command=name
 *   ...
 */
public final class FCPInterface implements FredPluginFCP {

	private final Freetalk mFreetalk;

	public FCPInterface(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
	}

    /**
     * @param replysender interface to send a reply
     * @param params parameters passed in, can be null
     * @param data a bucket of data passed in, can be null
     * @param access 0: direct call (plugin to plugin), 1: FCP restricted access,  2: FCP full access
     */
	public void handle(PluginReplySender replysender, SimpleFieldSet params, Bucket data, int accesstype) {
	    try {
    	    if (params == null) {
                throw new Exception("Empty message received");
            }

            String message = params.get("Message");
            if (message == null || message.trim().length() == 0) {
                throw new Exception("Specified message is empty");
            }

            if (message.equals("Ping")) {
                handlePing(replysender, params);
            } else if (message.equals("ListBoards")) {
                handleListBoards(replysender, params);
            } else {
                throw new Exception("Unknown message (" + message + ")");
            }
        } catch (Exception e) {
            Logger.error(this, e.toString());
            try {
                if (!(e instanceof PluginNotFoundException)) {
                    replysender.send(errorMessageFCP(params.get("Message"), e), data);
                }
            } catch (PluginNotFoundException e1) {
                Logger.normal(this, "Connection to request sender lost", e1);
            }
        }
	}

    /**
     * Handle ListBoards command.
     * Send a number of Board messages and finally an EndListBoards message.
     * Format:
     *   Message=Board
     *   Name=name
     *   MessageCount=123
     *   FirstSeenDate=utcMillis      (optional)
     *   LatestMessageDate=utcMillis  (optional)
     */
    private void handleListBoards(PluginReplySender replysender, SimpleFieldSet params)
    throws PluginNotFoundException
    {
        synchronized(mFreetalk.getMessageManager()) {
            Iterator<Board> boards = mFreetalk.getMessageManager().boardIterator();
            while(boards.hasNext()) {
                Board board = boards.next();

                SimpleFieldSet sfs = new SimpleFieldSet(true);
                sfs.putOverwrite("Message", "Board");
                sfs.putOverwrite("Name", board.getName());
                sfs.put("MessageCount", board.messageCount());
                if (board.getFirstSeenDate() != null) {
                    sfs.put("FirstSeenDate", board.getFirstSeenDate().getTime());
                }
                if (board.getLatestMessageDate() != null) {
                    sfs.put("LatestMessageDate", board.getLatestMessageDate().getTime());
                }

                replysender.send(sfs);
            }
        }

        // EndListBoards message
        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "EndListBoards");
        replysender.send(sfs);
    }

	/**
	 * Simple Ping command handler. Returns a Pong.
	 * Format:
	 *   Message=Pong
	 *   UTCMillis=utcMillis
	 */
    private void handlePing(PluginReplySender replysender, SimpleFieldSet params)
    throws PluginNotFoundException
    {
        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Pong");
        sfs.put("UTCMillis", System.currentTimeMillis());
        replysender.send(sfs);
    }

    /**
     * Sends an error message to the client.
     * Format:
     *   Message=Error
     *   OriginalMessage=msg or null
     *   Description=msg or null
     */
    private SimpleFieldSet errorMessageFCP(String originalMessage, Exception e) {

        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putOverwrite("Message", "Error");
        sfs.putOverwrite("OriginalMessage", (originalMessage == null) ? "null" : originalMessage);
        sfs.putOverwrite("Description", (e.getLocalizedMessage() == null) ? "null" : e.getLocalizedMessage());
        e.printStackTrace();
        return sfs;
    }
}
