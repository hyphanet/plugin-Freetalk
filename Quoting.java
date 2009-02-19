package plugins.Freetalk;

import java.text.BreakIterator;
import java.util.Locale;

public class Quoting {

	public static String getFullQuote(Message message) {
		return message.getAuthor().getFreetalkAddress() + " wrote:\n" + quoteLines(message.getText());
	}

	private static String quoteLines(String text) {
		StringBuffer result = new StringBuffer(2*text.length());
		
		BreakIterator breaker = BreakIterator.getLineInstance(Locale.ENGLISH);
		breaker.setText(text);
		int beginIndex = breaker.first();
		int endIndex = breaker.next();

		while(endIndex != BreakIterator.DONE) {
			String line = text.substring(beginIndex, endIndex);
			
			result.append("> ");
			result.append(line);
			
			beginIndex = endIndex + 1;
			endIndex = breaker.next();
		}
		
		return result.toString();
	}

}
