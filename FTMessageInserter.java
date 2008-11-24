/**
 * 
 */
package plugins.Freetalk;

import freenet.support.Executor;
import freenet.support.TransferThread;

/**
 * @author xor
 *
 */
public abstract class FTMessageInserter extends TransferThread {
	
	public FTMessageInserter(Executor myExecutor, String myName) {
		super(myExecutor, myName);
		// TODO Auto-generated constructor stub
	}

	public abstract void postMessage(FTOwnIdentity identity, FTMessage message);
	
}
