package plugins.Freetalk.util;

import freenet.client.DefaultMIMETypes;

/**
 * Wrapper around Freenet's class {@link DefaultMIMETypes} to make it suitable as a replacement for
 * class javax.activation.MimeType, which has been deprecated as part of Java 9's deprecation of
 * module java.activation:
 * https://docs.oracle.com/javase/9/docs/api/deprecated-list.html
 * 
 * Unfortunately "deprecated" in terms of that means that the class will *not* be available unless
 * Java is started with "--add-modules java.activation", which we cannot expect Freenet to do, so we
 * need to replace it right away.
 * 
 * TODO: Code quality: Is there another class in the JRE to replace Java's MimeType with? */
public final class MimeType implements Cloneable {

	public static final String DEFAULT_MIME_TYPE = DefaultMIMETypes.DEFAULT_MIME_TYPE;

	private final String mMimeType;

	public MimeType(String string) throws MimeTypeParseException {
		if(DefaultMIMETypes.byName(string) == -1)
			throw new MimeTypeParseException(string);
		
		mMimeType = string;
	}

	@Override public MimeType clone() {
		try {
			return (MimeType)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public boolean equals(Object obj) {
		if(obj instanceof MimeType)
			return ((MimeType)obj).mMimeType.equals(mMimeType);
		
		return false;
	}

	@Override public int hashCode() {
		return mMimeType.hashCode();
	}

	@Override public String toString() {
		return mMimeType;
	}

}
