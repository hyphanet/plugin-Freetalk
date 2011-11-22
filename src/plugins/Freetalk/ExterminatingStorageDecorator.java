package plugins.Freetalk;

import java.lang.String;
import java.io.File;

import com.db4o.io.StorageDecorator;
import com.db4o.io.BinConfiguration;
import com.db4o.io.BinDecorator;
import com.db4o.io.Bin;
import plugins.Freetalk.ExterminatingBinDecorator;


    class ExterminatingStorageDecorator extends StorageDecorator
    {
		private File target;
        public ExterminatingStorageDecorator(Storage storage, File toExterminate)
        {
			target = toExterminate;
		}

        protected Bin decorate(BinConfiguration config, Bin bin)
        {
            return new ExterminatingBinDecorator(bin, target);
        }

    }