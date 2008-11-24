package plugins.Freetalk;

import freenet.support.Executor;
import freenet.support.TransferThread;

public abstract class FTMessageFetcher extends TransferThread {

	public FTMessageFetcher(Executor myExecutor, String myName) {
		super(myExecutor, myName);
	}

}
