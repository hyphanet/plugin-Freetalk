package plugins.Freetalk;

import static java.lang.Thread.sleep;
import static plugins.Freetalk.Freetalk.DATABASE_FILENAME;

import java.io.File;

import com.db4o.Db4o;
import com.db4o.ext.BackupInProgressException;
import com.db4o.ext.DatabaseClosedException;
import com.db4o.ext.Db4oIOException;
import com.db4o.ext.ExtObjectContainer;

import freenet.support.Logger;

final class BackupManager {

	static final String DATABASE_BACKUP1_FILENAME =     DATABASE_FILENAME + ".backup1";
	static final String DATABASE_BACKUP2_FILENAME =     DATABASE_FILENAME + ".backup2";
	static final String DATABASE_BACKUP3_FILENAME =     DATABASE_FILENAME + ".backup3";
	static final String DATABASE_BACKUPDUMMY_FILENAME = DATABASE_FILENAME + ".dummybackup";
	static final String DATABASE_BACKUPTEMP_FILENAME =  DATABASE_FILENAME + ".temp";

	private final File mUserDataDirectory;

	BackupManager(Freetalk ft) {
		mUserDataDirectory = ft.getUserDataDirectory();
	}

	/** 
	 * Do a backup. Throw away the other file after finishing.
	 * 
	 * Backups 1-3 are used sequentially. One is always non-existent.
	 * The non-existent is the new target and the older of the
	 * existing ones gets deleted.
	 */
	void backup(ExtObjectContainer db) {
		if(true) {
			throw new UnsupportedOperationException(
				"This function is unfinished, please resolve all TODOs before using it!");
			
			// TODO:
			// - Freetalk compensates db4o's lack of locking by using its own locks. Is that safe
			//   with regards to db.backup()? If not then this function should only be called
			//   while no Freetalk processing threads are running, or the function should be changed
			//   to take all of the locks.
			// - Replace the harcoded 3 file logic with a generic one which supports N files.
			//   Determine the oldest file by the modification date.
			// - The function is entirely untested, both with regards to unit tests as well as not
			//   even having run it once.
			// - Also resolve the below TODOs!
		}
		
		File backup1 = new File(mUserDataDirectory, DATABASE_BACKUP1_FILENAME);
		File backup2 = new File(mUserDataDirectory, DATABASE_BACKUP2_FILENAME);
		File backup3 = new File(mUserDataDirectory, DATABASE_BACKUP3_FILENAME);
		File backupdummy = new File(mUserDataDirectory, DATABASE_BACKUPDUMMY_FILENAME);
		File backuptemp = new File(mUserDataDirectory, DATABASE_BACKUPTEMP_FILENAME);
		File backup;
		File deprecated;
		if (!backup3.exists()) { // 1+2->3
			backup = backup3;
			deprecated = backup1;
		}
		else if (!backup1.exists()) { // 2+3->1
			backup = backup1;
			deprecated = backup2;
		}
		else {
			assert(!backup2.exists());
			backup = backup2;
			deprecated = backup3;
		}
		try {
			db.backup(backuptemp.getAbsolutePath());
		} catch (DatabaseClosedException e) {
			Logger.error(this, "Cannot backup: Database closed!", e);
		} catch (Db4oIOException e) {
			Logger.error(this, "Cannot backup: IoException!", e);
		} catch (BackupInProgressException e) {
			Logger.error(this, "Cannot backup: Another backup is already running!", e);
		}
		// make sure this backup finishes, then move it to the target location and remove the deprecated file.
		while (true) {
			try {
				db.backup(backupdummy.getAbsolutePath());
				// We only get here, if the backup throws no errors, else we get the appropriate
				// catch. Thus the backup is finished now and we can rename the file to the main
				// name.
				// TODO: Flush filesystem buffers before renaming to ensure the backup cannot be
				// invalid if the system crashes during backup.
				backuptemp.renameTo(backup);
				deprecated.delete();
				break;
			} catch (BackupInProgressException e) {
				// this is what we want. As soon as we don't get this
				// anymore, the backup finished.
				try {
					sleep(100);
				} catch(InterruptedException f) {
					// TODO: Check if db4o provides a way to interrupt the backup procedure, if yes
					// do so and return.
				}
			} catch (DatabaseClosedException e) {
				Logger.error(this, "Cannot backup: Database closed!", e);
				backuptemp.delete();
				break;
			} catch (Db4oIOException e) {
				Logger.error(this, "Cannot backup: IoException!", e);
				backuptemp.delete();
				break;
			}
		}
	}

	/** 
	 * Restore the database from the most recent backup file.
	 * 
	 * TODO: To find out if the db needs to be restored: Add a "dirty=true" flag to the Freetalk
	 * database at startup, remove it as the very last part of shutdown.
	 * Restore if dirty=true.
	 * This could e.g. be implemented by:
	 * - renaming the database file to "Freetalk.db4o.dirty" while Freetalk is running, and renaming
	 *   it back at shutdown. Make sure to flush the filesystem buffers after closing the database
	 *   and before renaming it back.
	 * - creating/deleting a "Freetalk.db4o.lock" file. This may be the better solution due to:
	 *   https://bugs.freenetproject.org/view.php?id=7001 */
	void restore(File file) {
		if(true) {
			throw new UnsupportedOperationException(
				"This function is unfinished, please resolve all TODOs before using it!");
			
			// TODO:
			// - Ensure this only is called if Freetalk isn't using the main database yet.
			// - Replace the harcoded 3 file logic with a generic one which supports N files.
			//   Determine the oldest file by the modification date!
			// - The function is entirely untested, both with regards to unit tests as well as not
			//   even having run it once.
			// - Also resolve the below TODOs!
		}
		
		File mostRecentBackup;
		File backup1 = new File(mUserDataDirectory, DATABASE_BACKUP1_FILENAME);
		File backup2 = new File(mUserDataDirectory, DATABASE_BACKUP2_FILENAME);
		File backup3 = new File(mUserDataDirectory, DATABASE_BACKUP3_FILENAME);
		File backupdummy = new File(mUserDataDirectory, DATABASE_BACKUPDUMMY_FILENAME);
		if (!backup2.exists() && backup1.exists()) {
			mostRecentBackup = backup1;
		} else if (!backup3.exists() && backup2.exists()) {
			mostRecentBackup = backup2;
		} else if (!backup1.exists() && backup3.exists()) {
			mostRecentBackup = backup3;
		} else if (backup1.exists() && backup2.exists() && backup3.exists()) {
			Logger.error(this, "Cannot distinguish between the backups. Choosing the first. Might be wrong. Sorry.");
			// TODO: Check the modified dates and choose the latest.
			mostRecentBackup = backup1;
		} else {
			assert(!backup1.exists());
			assert(!backup2.exists());
			assert(!backup3.exists());
			Logger.error(this, "No backup found. Sorry.");
			return;
		}
		ExtObjectContainer restorer = Db4o.openFile(mostRecentBackup.getAbsolutePath()).ext();
		try  {
			// TODO: The dummy file persists after this returns. Easiest fix would be this:
			// TODO: Just copy the file. backup() exists for the purpose of being able to do backups
			// while the database is in use. However restoring is not something which can happen
			// while the database is in use because we're restoring from a backup, not from a live
			// database. So the sole advantage of backup() isn't relevant here.
			// Just copying the file would have the advantage that we can get rid of the below
			// lengthy while() loop.
			restorer.backup(file.getAbsolutePath());
		} catch (DatabaseClosedException e) {
			Logger.error(this, "Cannot restore: Database closed!", e);
		} catch (Db4oIOException e) {
			Logger.error(this, "Cannot restore: IoException!", e);
		}
		// make sure this backup finishes.
		while (true) {
			try {
				restorer.backup(backupdummy.getAbsolutePath());
				// we only get here, if the backup throws no errors.
				// Else we get the appropriate catch and the loop continues.
				// TODO: Add a wait condition.
				break;
			} catch (BackupInProgressException e) {
				// this is what we want. As soon as we don't get this
				// anymore, we break.
				try {
					sleep(100);
				} catch(InterruptedException f) {
					// TODO: Check if db4o provides a way to interrupt the backup (= in our case
					// restore) procedure, if yes do so and return.
				}
			} catch (DatabaseClosedException e) {
				Logger.error(this, "Cannot restore: Database closed!", e);
				break;
			} catch (Db4oIOException e) {
				Logger.error(this, "Cannot restore: IoException!", e);
				break;
			}
			
		}
	}

}
