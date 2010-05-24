package plugins.Freetalk;


public class Quoting {

	public static String getFullQuote(Message message) {
		return message.getAuthor().getFreetalkAddress() + " wrote:\n" + quoteLines(message.getText());
	}

	/* TODO: Find an intelligent (email) wrapping code to limit the line length to 80.
	 * Maybe Frost has code for that which can be used here? 
	 * OTOH, we will use "[quote][/quote]" style quoting soon so we probably don't need this function anymore anyway. */
	private static String quoteLines(String text) {
		String[] lines = text.split("\r\n|\n");
		StringBuffer result = new StringBuffer(text.length() + lines.length * 2 + 16);
		
		for(String line : lines) {
			result.append("> ");
			result.append(line);
			result.append("\n");
		}
		
		return result.toString();
	}

}
