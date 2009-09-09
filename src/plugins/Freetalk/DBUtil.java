package plugins.Freetalk;

import com.db4o.ext.ExtObjectContainer;

import freenet.support.Logger;

public class DBUtil {

	/**
	 * Used to check whether an object is active before storing it.
	 * 
	 * Logs an error if the object is not active.
	 * 
	 * Activates the object to the specified depth.
	 */
	public static void checkedActivate(ExtObjectContainer db, Object object, int depth) {
		if(db.isStored(object)) {
			if(!db.isActive(object))
				Logger.error(object, "Trying to store a non-active object!");
				
			db.activate(object, depth);
		}
	}
	
	public static void checkedDelete(ExtObjectContainer db, Object object) {
		if(db.isStored(object))
			db.delete(object);
		else
			Logger.error(object, "Trying to delete a inexistent object!");
	}
	
	public static void rollbackAndThrow(ExtObjectContainer db, Object object, RuntimeException error) {
		db.rollback(); Logger.error(object, "ROLLED BACK!", error);
		throw error;
	}
}
