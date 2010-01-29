/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.HashMap;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;

import freenet.support.Logger;

/* ATTENTION: This code is a duplicate of plugins.WoT.Config. Any changes there should also be done here! */

/**
 * Contains a HashMap<String, String> and HashMap<String, Integer> which maps configuration variable names to their values and stores them
 * in the database. Integer configuration values are stored separately because they might be needed very often per second and we should
 * save the time of converting String to Integer.
 * 
 * @author xor (xor@freenetproject.org), Julien Cornuwel (batosai@freenetproject.org)
 */
public final class Config {

	/* Names of the config parameters */
	
	public static final String DATABASE_FORMAT_VERSION = "DatabaseFormatVersion";
	
	public static final String MINIMUM_TRUSTER_COUNT = "Introduction.MinimumTrusterCount";
	
	public static final String NNTP_SERVER_ENABLED = "NNTP.ServerEnabled";

	/** Parameter name for the {@code bindTo} parameter. */
	public static final String NNTP_SERVER_BINDTO = "NNTP.BindTo";

	/** Parameter name for the {@code allowedHosts} parameter. */
	public static final String NNTP_SERVER_ALLOWED_HOSTS = "NNTP.AllowedHosts";

	/**
	 * The HashMap that contains all cofiguration parameters
	 */
	private final HashMap<String, String> mStringParams;
	
	private final HashMap<String, Integer> mIntParams;
	
	private transient Freetalk mFreetalk;
	
	private transient ExtObjectContainer mDB;

	/**
	 * Creates a new Config object and stores the default values in it.
	 */
	protected Config(Freetalk myFreetalk, ExtObjectContainer db) {
		mFreetalk = myFreetalk;
		mDB = db;
		mStringParams = new HashMap<String, String>();
		mIntParams = new HashMap<String, Integer>();
		setDefaultValues(false);
	}
	
	protected void initializeTransient(Freetalk myFreetalk, ExtObjectContainer db) {
		mFreetalk = myFreetalk;
		mDB = db;
	}
	
	/**
	 * Loads an existing Config object from the database and adds any missing default values to it, creates and stores a new one if none exists.
	 * @return The config object.
	 */
	public static Config loadOrCreate(Freetalk myFreetalk, ExtObjectContainer db) {
		synchronized(db.lock()) {
			Config config;
			ObjectSet<Config> result = db.queryByExample(Config.class);
			
			if(result.size() == 0) {
				Logger.debug(myFreetalk, "Creating new Config...");
				config = new Config(myFreetalk, db);
				config.storeAndCommit();
			}
			else {
				if(result.size() > 1) /* Do not throw, we do not want to prevent Freetalk from starting up. */
					Logger.error(myFreetalk, "Multiple config objects stored!");
				
				Logger.debug(myFreetalk, "Loaded config.");
				config = result.next();
				config.initializeTransient(myFreetalk, db);
				config.setDefaultValues(false);
			}
			
			return config;
		}
	}
	
	/**
	 * Stores the config object in the database. Please call this after any modifications to the config, it is not done automatically
	 * because the user interface will usually change many values at once.
	 */
	public synchronized void storeAndCommit() {
		synchronized(mDB.lock()) {
			try {
				DBUtil.checkedActivate(mDB, this, 3);
				
				mDB.store(mStringParams, 3);
				mDB.store(mIntParams, 3);
				mDB.store(this);
				mDB.commit();
			}
			catch(RuntimeException e) {
				DBUtil.rollbackAndThrow(mDB, this, e);
			}
		}
	}

	/**
	 * Sets a String configuration parameter. You have to call storeAndCommit to write it to disk.
	 * 
	 * @param key Name of the config parameter.
	 * @param value Value of the config parameter.
	 */
	public synchronized void set(String key, String value) {
		mStringParams.put(key, value);
	}
	
	/**
	 * Sets a boolean configuration parameter. You have to call storeAndCommit to write it to disk.
	 * 
	 * @param key Name of the config parameter.
	 * @param value Value of the config parameter.
	 */
	public synchronized void set(String key, boolean value) {
	    mStringParams.put(key, Boolean.toString(value));
	}
	
	/**
	 * Sets an Integer configuration parameter and stores it in the database. You have to call storeAndCommit to write it to disk.
	 * 
	 * @param key Name of the config parameter.
	 * @param value Value of the config parameter.
	 */
	public synchronized void set(String key, int value) {
		mIntParams.put(key, value);
	}

	/**
	 * Gets a String configuration parameter.
	 */
	public synchronized String getString(String key) {
		return mStringParams.get(key);
	}
	
	/**
	 * Gets an Integer configuration parameter.
	 */
	public synchronized int getInt(String key) {
		return mIntParams.get(key);
	}
	
	/**
	 * Gets a boolean configuration parameter.
	 */
	public synchronized boolean getBoolean(String key) {
	    return Boolean.valueOf( mStringParams.get(key) );
	}

	/**
	 * Check wheter a boolean config parameter exists.
	 */
	public synchronized boolean containsBoolean(String key) {
	    return containsString(key);
	}

	/**
	 * Check wheter a String config parameter exists.
	 */
	public synchronized boolean containsString(String key) {
		return mStringParams.containsKey(key);
	}
	
	/**
	 * Check wheter an Integer config parameter exists.
	 */
	public synchronized boolean containsInt(String key) {
		return mIntParams.containsKey(key);
	}

	/**
	 * Get all valid String configuration keys.
	 * 
	 * @return A String array containing a copy of all keys in the database at
	 *         the point of calling the function. Changes to the array do not
	 *         change the database.
	 */
	public synchronized String[] getAllStringKeys() {
		/* We return a copy of the keySet. If we returned an iterator of the
		 * keySet, modifications on the configuration HashMap would be reflected
		 * in the iterator. This might lead to problems if the configuration is
		 * modified while someone is using an iterator returned by this
		 * function. Further the iterator would allow the user to delete keys
		 * from the configuration.
		 */

		// FIXME: there is a null pointer somewhere in here. i don't have the
		// time for fixing it right now
		return mStringParams.keySet().toArray(new String[mStringParams.size()]);
	}
	
	/**
	 * Get all valid String configuration keys.
	 * 
	 * @return A String array containing a copy of all keys in the database at
	 *         the point of calling the function. Changes to the array do not
	 *         change the database.
	 */
	public synchronized String[] getAllIntKeys() {
		/* We return a copy of the keySet. If we returned an iterator of the
		 * keySet, modifications on the configuration HashMap would be reflected
		 * in the iterator. This might lead to problems if the configuration is
		 * modified while someone is using an iterator returned by this
		 * function. Further the iterator would allow the user to delete keys
		 * from the configuration.
		 */

		// FIXME: there is a null pointer somewhere in here. i don't have the
		// time for fixing it right now
		return mIntParams.keySet().toArray(new String[mIntParams.size()]);
	}

	/**
	 * Add the default configuration values to the database.
	 * 
	 * @param overwrite If true, overwrite already set values with the default value.
	 */
	public synchronized void setDefaultValues(boolean overwrite) {
		/* Do not overwrite, it shall only be overwritten when the database has been converted to a new format */
		if(!containsInt(DATABASE_FORMAT_VERSION)) {
			set(DATABASE_FORMAT_VERSION, Freetalk.DATABASE_FORMAT_VERSION);
		}
		
		if(!containsInt(MINIMUM_TRUSTER_COUNT)) {
			set(MINIMUM_TRUSTER_COUNT, 5);
		}
		
		if (!containsBoolean(NNTP_SERVER_ENABLED)) {
		    set(NNTP_SERVER_ENABLED, true);
		}
	}
}
