package plugins.Freetalk;


public class Quoting {

	public static String getFullQuote(Message message) {
		return "[quote=\""+message.getAuthor().getFreetalkAddress() + "\"]\n" + message.getText() + "\n[/quote]\n";
	}
}
