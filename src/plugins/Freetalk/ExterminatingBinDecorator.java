package plugins.Freetalk;

import java.io.File;

import com.db4o.io.StorageDecorator;
import com.db4o.io.BinConfiguration;
import com.db4o.io.BinDecorator;
import com.db4o.io.Bin;

class ExterminatingBinDecorator extends BinDecorator {
	
	private File target;
	private Bin _bin;

	public ExterminatingBinDecorator(Bin bin, File toExterminate) {
		target = toExterminate;
		_bin = bin;
	}
	public void close() {
		_bin.close();
		// Implement here your notification-mechanism
		// For example with events etc
		if (target.exists()) {
			target.delete();
				}
	}
}
