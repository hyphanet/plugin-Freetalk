package plugins.Freetalk;

import freenet.keys.FreenetURI;

public abstract class MessageURI {

	public abstract FreenetURI getFreenetURI();
	
	public abstract String getMessageID();

	@Override
	public abstract boolean equals(Object obj);

	@Override
	public abstract String toString();
	
}
