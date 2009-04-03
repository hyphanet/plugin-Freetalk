/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.FCP;

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

            if(message.equals("Ping")) {
                replysender.send(handlePing(params), data);
            } else {
                throw new Exception("Unknown message (" + message + ")");
            }
        }
        catch (Exception e) {
            Logger.error(this, e.toString());
            try {
                replysender.send(errorMessageFCP(params.get("Message"), e), data);
            } catch (PluginNotFoundException e1) {
                Logger.normal(this, "Connection to request sender lost", e1);
            }
        }
	}

	/**
	 * Simple Ping command handler. Returns a Pong.
	 */
    private SimpleFieldSet handlePing(SimpleFieldSet params) {
        SimpleFieldSet sfs = new SimpleFieldSet(true);

        sfs.putAppend("Message", "Pong");
        sfs.put("utcMillis", System.currentTimeMillis());

        return sfs;
    }

    private SimpleFieldSet errorMessageFCP(String originalMessage, Exception e) {
        
        SimpleFieldSet sfs = new SimpleFieldSet(true);
        sfs.putAppend("Message", "Error");
        sfs.putAppend("OriginalMessage", (originalMessage == null) ? "null" : originalMessage);
        sfs.putAppend("Description", (e.getLocalizedMessage() == null) ? "null" : e.getLocalizedMessage());
        e.printStackTrace();
        return sfs;
    }
}
