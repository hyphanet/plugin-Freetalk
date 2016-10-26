/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.tasks;

import freenet.support.codeshortification.IfNull;
import plugins.Freetalk.OwnIdentity;

/**
 * An OwnMessageTask is a task which is processed not only when it's processing time is due but also when it's owner posts a new message.
 */
// @IndexedField // I can't think of any query which would need to get all OwnMessageTask objects.
public abstract class OwnMessageTask extends PersistentTask {

	protected OwnMessageTask(OwnIdentity myOwner) {
		super(myOwner);
	}

	@Override public void databaseIntegrityTest() throws Exception {
		super.databaseIntegrityTest();
		
		checkedActivate(1);
		IfNull.thenThrow(mOwner, "mOwner");
	}

}
