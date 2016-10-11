/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.HashMap;

import plugins.Freetalk.Persistent.IndexedClass;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.Logger;
import freenet.support.codeshortification.IfNull;

/* ATTENTION: This code is a duplicate of plugins.WoT.Config. Any changes there should also be done here! */

/**
 * Contains a HashMap<String, String> and HashMap<String, Integer> which maps configuration variable names to their values and stores them
 * in the database. Integer configuration values are stored separately because they might be needed very often per second and we should
 * save the time of converting String to Integer.
 * 
 * @author xor (xor@freenetproject.org)
 * @author Julien Cornuwel (batosai@freenetproject.org)
 */
@IndexedClass
public final class Configuration extends Persistent {

	/* Names of the config parameters */
	
	
	public static transient final String MINIMUM_TRUSTER_COUNT = "Introduction.MinimumTrusterCount";
	
	public static transient final String NNTP_SERVER_ENABLED = "NNTP.ServerEnabled";

	/** Parameter name for the {@code bindTo} parameter. */
	public static transient final String NNTP_SERVER_BINDTO = "NNTP.BindTo";

	/** Parameter name for the {@code allowedHosts} parameter. */
	public static transient final String NNTP_SERVER_ALLOWED_HOSTS = "NNTP.AllowedHosts";


	/**
	 * The database format version of this Freetalk-database.
	 * Stored in a primitive integer field to ensure that db4o does not lose it - I've observed the HashMaps to be null suddenly sometimes :(
	 */
	private int mDatabaseFormatVersion;
	
	/**
	 * The {@link HashMap} that contains all {@link String} configuration parameters
	 */
	private final HashMap<String, String> mStringParams;
	
	/**
	 * The {@link HashMap} that contains all {@link Integer} configuration parameters
	 */
	private final HashMap<String, Integer> mIntParams;
	
	/* These booleans are used for preventing the construction of log-strings if logging is disabled (for saving some cpu cycles) */
	
	private static transient volatile boolean logDEBUG = false;
	private static transient volatile boolean logMINOR = false;
	
	static {
		Logger.registerClass(Configuration.class);
	}
	

	/**
	 * Creates a new Config object and stores the default values in it.
	 */
	protected Configuration(Freetalk myFreetalk) {
		initializeTransient(myFreetalk);
		mDatabaseFormatVersion = Freetalk.DATABASE_FORMAT_VERSION;
		mStringParams = new HashMap<String, String>();
		mIntParams = new HashMap<String, Integer>();
		setDefaultValues(false);
	}
	
	@Override
	public void databaseIntegrityTest() throws Exception {
		checkedActivate(4);
		
		if(mDatabaseFormatVersion != Freetalk.DATABASE_FORMAT_VERSION)
			throw new IllegalStateException("FATAL: startupDatabaseIntegrityTest called with wrong database format version! is: " 
					+ mDatabaseFormatVersion + "; should be: " + Freetalk.DATABASE_FORMAT_VERSION);
		
		if(mStringParams == null)
			throw new NullPointerException("mStringParams==null");
		
		if(mIntParams == null)
			throw new NullPointerException("mIntParams==null");
		
		// TODO: Validate the other settings...
	}
	
	/**
	 * Loads an existing Config object from the database and adds any missing default values to it, creates and stores a new one if none exists.
	 * @return The config object.
	 */
	public static Configuration loadOrCreate(Freetalk myFreetalk, ExtObjectContainer db) {
		synchronized(Persistent.transactionLock(db)) {
			Configuration config;
			ObjectSet<Configuration> result = db.queryByExample(Configuration.class);
			
			if(result.size() == 0) {
				if(logDEBUG) Logger.debug(myFreetalk, "Creating new Config...");
				config = new Configuration(myFreetalk);
				config.storeAndCommit();
			}
			else {
				if(result.size() > 1) /* Do not throw, we do not want to prevent Freetalk from starting up. */
					Logger.error(myFreetalk, "Multiple config objects stored!");
				
				if(logDEBUG) Logger.debug(myFreetalk, "Loaded config.");
				config = result.next();
				config.initializeTransient(myFreetalk);
				config.checkedActivate(4);
				config.setDefaultValues(false);
				config.storeAndCommit();
			}
			
			return config;
		}
	}
	
	/**
	 * Stores the config object in the database. Please call this after any modifications to the config, it is not done automatically
	 * because the user interface will usually change many values at once.
	 */
	public synchronized void storeAndCommit() {
		synchronized(Persistent.transactionLock(mDB)) {
			try {
				// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
				mDB.store(mStringParams, 3);
				mDB.store(mIntParams, 3);
				checkedStore();
				checkedCommit(this);
			}
			catch(RuntimeException e) {
				checkedRollbackAndThrow(e);
			}
		}
	}
	
	public int getDatabaseFormatVersion() {
		// checkedActivate(1); // We fully activate the Config object when obtaining it from the database so we don't need this.
		return mDatabaseFormatVersion;
	}
	
	protected void setDatabaseFormatVersion(int newVersion) {
		// checkedActivate(1); // We fully activate the Config object when obtaining it from the database so we don't need this.
		if(newVersion <= mDatabaseFormatVersion)
			throw new RuntimeException("mDatabaseFormatVersion==" + mDatabaseFormatVersion + "; newVersion==" + newVersion);
		
		mDatabaseFormatVersion = newVersion;
	}
	
	/**
	 * Warning: This function is not synchronized, use it only in single threaded mode.
	 * 
	 * For being used to obtain the database version of a database which is a different one than the database which {@link Freetalk.getDatabase()} would return.
	 * 
	 * @return The Freetalk database format version of the given database. -1 if there is no Configuration stored in it or multiple configurations exist.
	 */
	@SuppressWarnings("deprecation")
	protected static int peekDatabaseFormatVersion(Freetalk myFreetalk, ExtObjectContainer myDatabase) {
		final Query query = myDatabase.query();
		query.constrain(Configuration.class);
		@SuppressWarnings("unchecked")
		ObjectSet<Configuration> result = (ObjectSet<Configuration>)query.execute(); 
		
		switch(result.size()) {
			case 1: {
				final Configuration config = (Configuration)result.next();
				config.initializeTransient(myFreetalk, myDatabase);
				// For the HashMaps to stay alive we need to activate to full depth.
				config.checkedActivate(4);
				return config.getDatabaseFormatVersion();
			}
			default:
				return -1;
		}
	}

	/**
	 * Sets a String configuration parameter. You have to call storeAndCommit to write it to disk.
	 * 
	 * @param key Name of the config parameter.
	 * @param value Value of the config parameter. Null to remove the setting.
	 */
	public synchronized void set(String key, String value) {
		IfNull.thenThrow(key, "Key");
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
		if(value != null)
			mStringParams.put(key, value);
		else
			mStringParams.remove(key);
	}
	
	/**
	 * Sets a boolean configuration parameter. You have to call storeAndCommit to write it to disk.
	 * 
	 * @param key Name of the config parameter.
	 * @param value Value of the config parameter.
	 */
	public synchronized void set(String key, boolean value) {
		IfNull.thenThrow(key, "Key");
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
	    mStringParams.put(key, Boolean.toString(value));
	}
	
	/**
	 * Sets an Integer configuration parameter and stores it in the database. You have to call storeAndCommit to write it to disk.
	 * 
	 * @param key Name of the config parameter.
	 * @param value Value of the config parameter.
	 */
	public synchronized void set(String key, int value) {
		IfNull.thenThrow(key, "Key");
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
		mIntParams.put(key, value);
	}

	/**
	 * Gets a String configuration parameter.
	 */
	public synchronized String getString(String key) {
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
		return mStringParams.get(key);
	}
	
	/**
	 * Gets an Integer configuration parameter.
	 */
	public synchronized int getInt(String key) {
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
		return mIntParams.get(key);
	}
	
	/**
	 * Gets a boolean configuration parameter.
	 */
	public synchronized boolean getBoolean(String key) {
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
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
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
		return mStringParams.containsKey(key);
	}
	
	/**
	 * Check wheter an Integer config parameter exists.
	 */
	public synchronized boolean containsInt(String key) {
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
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
		
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
		
		// TODO: there is a null pointer somewhere in here. i don't have the
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
		
		// checkedActivate(4); // We fully activate the Config object when obtaining it from the database so we don't need this.
		
		// TODO: there is a null pointer somewhere in here. i don't have the
		// time for fixing it right now
		return mIntParams.keySet().toArray(new String[mIntParams.size()]);
	}

	/**
	 * Add the default configuration values to the database.
	 * 
	 * @param overwrite If true, overwrite already set values with the default value.
	 */
	public synchronized void setDefaultValues(boolean overwrite) {		
		if(!containsInt(MINIMUM_TRUSTER_COUNT)) {
			set(MINIMUM_TRUSTER_COUNT, 5);
		}
		
		if (!containsBoolean(NNTP_SERVER_ENABLED)) {
			set(NNTP_SERVER_ENABLED, false);
		}
	}

}
