package plugins.Freetalk.util;

public final class MimeTypeParseException extends Exception {

	private static final long serialVersionUID = 1L;

	MimeTypeParseException(String string) {
		super("Unknown mime type: " + string);
	}

}
