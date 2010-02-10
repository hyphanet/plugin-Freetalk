package plugins.Freetalk;

import com.db4o.ext.ExtObjectContainer;

import freenet.support.Logger;

/**
 * This is the base class for all classes which are stored in the Freetalk database.<br /><br />
 * 
 * It provides common functions which are needed for storing, updating, retrieving and deleting objects.
 * 
 * @author xor (xor@freenetproject.org)
 */
public abstract class Persistent {
	
	protected transient IdentityManager mIdentityManager;
	
	protected transient MessageManager mMessageManager;
	
	protected transient ExtObjectContainer mDB;
	

	/**
	 * Has to be called after retrieving a persistent Object from the database to initialize its transient fields:<br />
	 * Transient fields are NOT stored in the database. They are references to objects such as the IdentityManager.
	 */
	protected final void initializeTransient(Freetalk myFreetalk) {
		mMessageManager = myFreetalk.getMessageManager();
		mIdentityManager = mMessageManager.getIdentityManager();
		mDB = myFreetalk.getDatabase();
	}

	/**
	 * Used to check whether an object is active before storing it.<br /><br />
	 * 
	 * Logs an error if the object is not active.<br /><br />
	 * 
	 * Activates the object to the specified depth.<br /><br />
	 */
	protected final void checkedActivate(Object object, int depth) {
		if(mDB.isStored(object)) {
			if(!mDB.isActive(object))
				Logger.error(this, "Trying to store a non-active object: " + object);
				
			mDB.activate(object, depth);
		}
	}
	
	/**
	 * Checks whether an object is stored in the database and deletes it if it is.
	 * If it was not found in the database, an error is logged.<br /><br />
	 * 
	 * This is to be used as an integrity check in deleteWithoutCommit() implementations. 
	 */
	protected final void checkedDelete(Object object) {
		if(mDB.isStored(object))
			mDB.delete(object);
		else
			Logger.error(this, "Trying to delete a inexistent object: " + object);
	}
	
	
	/**
	 * Checks whether the given object is stored in the database already and throws a RuntimeException if it is not.<br /><br />
	 * 
	 * This function is to be used as an integrity check in storeWithoutCommit() implementations which require that objects to which
	 * this object references have been stored already.
	 */
	protected final void throwIfNotStored(Object object) {
		if(object == null) {
			Logger.error(this, "Mandatory object is null!");
			throw new RuntimeException("Mandatory object is null!"); 
		}
		
		if(!mDB.isStored(object)) {
			Logger.error(this, "Mandatory object is not stored: " + object);
			throw new RuntimeException("Mandatory object is not stored: " + object);
		}
	}

	protected final void rollbackAndThrow(RuntimeException error) {
		mDB.rollback(); Logger.error(this, "ROLLED BACK!", error);
		throw error;
	}
	
	protected void storeWithoutCommit() {
		try {		
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(this, 1);
			mDB.store(this);
		}
		catch(RuntimeException e) {
			rollbackAndThrow(e);
		}
	}
	
	protected void deleteWithoutCommit() {
		try {
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(this, 1);
			checkedDelete(this);
		}
		catch(RuntimeException e) {
			rollbackAndThrow(e);
		}
	}
}
