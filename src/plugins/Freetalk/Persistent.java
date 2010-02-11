package plugins.Freetalk;

import java.util.Hashtable;

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
	
	protected transient Freetalk mFreetalk;
	
	protected transient ExtObjectContainer mDB;
	
	protected transient static final Hashtable<Class<? extends Persistent>, String[]> mIndexedFields = new Hashtable<Class<? extends Persistent>, String[]>();

	protected static final void registerIndexedFields(Class<? extends Persistent> clazz, String[] fields) {
		mIndexedFields.put(clazz, fields);
	}
	
	protected static final Hashtable<Class<? extends Persistent>, String[]> getIndexedFields() {
		return mIndexedFields;
	}
	
	/**
	 * Must be called once after obtaining this object from the database before using any getter or setter member functions
	 * and before calling storeWithoutCommit / deleteWithoutCommit.
	 * Transient fields are NOT stored in the database. They are references to objects such as the IdentityManager.
	 */
	public void initializeTransient(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
		mDB = mFreetalk.getDatabase();
	}
	
	protected final void activate(int depth) {
		mDB.activate(this, depth);
	}

	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Used by storeWithoutCommit/deleteWithoutCommit to check whether an object is active before storing it.<br /><br />
	 * 
	 * Logs an error if the object is not active.<br /><br />
	 * 
	 * Activates the object to the specified depth.<br /><br />
	 */
	protected final void checkedActivate(Object object, int depth) {
		if(mDB.isStored(object)) {
			if(!mDB.isActive(object))
				Logger.error(this, "Trying to store a non-active object: " + object);
				
			activate(depth);
		}
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Same as a call to {@link checkedActivate(this, depth)}
	 */
	protected final void checkedActivate(int depth) {
		checkedActivate(this, depth);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Used by storeWithoutCommit for actually storing the object.<br /><br />
	 * 
	 * Currently does not any additional checks, it is used to 
	 * @param object
	 */
	protected final void checkedStore(Object object) {
		mDB.store(object);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Same as a call to {@link checkedStore(this)}
	 */
	protected final void checkedStore() {
		mDB.store(this);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
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
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Same as a call to {@link checkedDelete()}
	 */
	protected final void checkedDelete() {
		checkedDelete(this);
	}
	
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
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

	/**
	 * Rolls back the current transaction, logs the passed exception and throws it.
	 * To be used in try/catch blocks in storeWithoutCommit/deleteWithoutCommit.
	 */
	public static final void rollbackAndThrow(ExtObjectContainer db, Object loggingObject, RuntimeException error) {
		db.rollback(); Logger.error(loggingObject, "ROLLED BACK!", error);
		throw error;
	}
	
	protected final void rollbackAndThrow(RuntimeException error) {
		rollbackAndThrow(mDB, this, error);
	}
	

	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * When your extending class needs a different activation depth for store than 1, you have to override storeWithoutCommit() and make it call this function.
	 * If you need to store other objects than this object (that is member objects) then you might want to copy the body of this function so that 
	 * checkedActivate() is not called twice.
	 * 
	 * @param activationDepth The desired activation depth.
	 */
	public void storeWithoutCommit(int activationDepth) {
		try {		
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(activationDepth);
			checkedStore(); // There is no checkedStore()
		}
		catch(RuntimeException e) {
			rollbackAndThrow(e);
		}
	}
	
	/**
	 * This is one of the only public functions which outside classes should use. It is used for storing the object.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) { try { object.storeWithoutCommit(); mDB.commit(); } catch(RuntimeException e) { Persistent.rollbackAndThrow(mDB, this, e); } } 
	 */
	public void storeWithoutCommit() {
		storeWithoutCommit(1);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * When your extending class needs a different activation depth for store than 1, you have to override storeWithoutCommit() and make it call this function.
	 * If you need to store other objects than this object (that is member objects) then you might want to copy the body of this function so that 
	 * checkedActivate() is not called twice.
	 * 
	 * @param activationDepth The desired activation depth.
	 */
	public void deleteWithoutCommit(int activationDepth) {
		try {
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(activationDepth);
			checkedDelete(this);
		}
		catch(RuntimeException e) {
			rollbackAndThrow(e);
		}
	}
	
	/**
	 * This is one of the only public functions which outside classes should use. It is used for deleting the object.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) { try { object.deleteWithoutCommit(); mDB.commit(); } catch(RuntimeException e) { Persistent.rollbackAndThrow(mDB, this, e); } }
	 */
	public void deleteWithoutCommit() {
		deleteWithoutCommit(1);
	}
	
	public static final void commit(ExtObjectContainer db, Object loggingObject) {
		db.commit();
		Logger.debug(loggingObject, "COMMITED.");
	}
	
	public final void commit(Object loggingObject) {
		commit(mDB, loggingObject);
	}
}
