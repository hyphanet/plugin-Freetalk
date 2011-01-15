package plugins.Freetalk;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import freenet.support.URLEncoder;


public class Quoting {
	
	public static enum TextElementType {
		Error,
		PlainText,
		Quote,
		Bold,
		Italic,
		Code,
		Link;
	
		public static TextElementType fromString(String tag) {
			if (tag.equals(""))
				return PlainText;
			else if (tag.equalsIgnoreCase("quote"))
				return Quote;
			else if (tag.equalsIgnoreCase("b"))
				return Bold;
			else if (tag.equalsIgnoreCase("i"))
				return Italic;
			else if (tag.equalsIgnoreCase("code"))
				return Code;
			else if (tag.equalsIgnoreCase("link") || tag.equalsIgnoreCase("url") || tag.equalsIgnoreCase("uri"))
				return Link;
	
			return Error;
		}
	}

	public final static class TextElement {
		public final TextElementType mType;
		
		// FIXME: For [tag key1=value1 key2=value2], fill with the key/value pairs
		// This will require quite some modification of the parser but allow [quote author=name message=messageID] and various
		// other beautiful bbcodes
		public final Hashtable<String, String> mAttributes;
		
		public String mContent;
		public final List<TextElement> mChildren;
		public int mConsumedLength;
	
		public TextElement(TextElementType myType) {
			mType = myType;
			mAttributes = new Hashtable<String, String>();
			mContent = "";
			mChildren = new ArrayList<TextElement>();
			mConsumedLength = 0;
		}
	
		public TextElement(String tag) {
			this(TextElementType.fromString(tag));
		}
	
		public String getContentText() {
			// own content + that of our children
			final StringBuilder result = new StringBuilder(mConsumedLength);
			result.append(mContent);
	
			for(final TextElement t : mChildren)
				result.append(t.getContentText());
	
			return result.toString();
		}
	}

	public static String getFullQuote(Message message) {
		return "[quote author=\""+message.getAuthor().getFreetalkAddress() + "\" message=\"" + message.getID() + "\"]\n" + message.getText() + "\n[/quote]\n";
	}

	// FIXME: Make this actually support the [quote Author=blah Message=bleh] stuff .. Right now it only understands [quote=author-freetalk-address]
	// FIXME: Make the UI support it as well
	public static final Quoting.TextElement parseText(String currentText, String tag, String arg, int maxRecursion) {
		if (maxRecursion < 0) {
			return new Quoting.TextElement(TextElementType.Error);
		}
		Quoting.TextElement result = new Quoting.TextElement(tag);
	
		if(result.mType == TextElementType.Quote)
			result.mContent = arg;
	
		// for tags without closing tag (if we ever want to support those)
		// just return result here
	
		if (currentText.length() > 0) {
			if (currentText.substring(0,1).equals("\n")) {
				// skip a starting \n (which is what the user expects to happen)
				result.mConsumedLength++;
			}
		}
	
		// [foo] or [/foo] or [foo="bar"] or [foo=bar] or (invalid!) [/foo="bar"]
		Pattern tagPattern = Pattern.compile("\\[(.+?)(?:=\"?(.+?)\"?)?\\]", Pattern.MULTILINE|Pattern.DOTALL);
		Matcher tagMatcher = tagPattern.matcher(currentText);
		// we detect a freenet key as a key type, an @ and then any characters that are valid to be in the key
		// TODO: we might want to allow linebreaks in the first (cryptographic key) part
		String keyRegex = "(CH|SS|US|KS)K@[%,~" + URLEncoder.getSafeURLCharacters() + "]+";
		Pattern keyPattern = Pattern.compile(keyRegex, Pattern.MULTILINE|Pattern.DOTALL);
		Matcher keyMatcher = keyPattern.matcher(currentText);
		// <name>@<key>.freetalk wrote:\n
		Pattern oldQuotePattern = Pattern.compile("^(?:On .*?)?(\\S+@\\S+?.freetalk) wrote:\n+", Pattern.MULTILINE|Pattern.DOTALL);
		Matcher oldQuoteMatcher = oldQuotePattern.matcher(currentText);
	
		while (result.mConsumedLength < currentText.length()) {
			// we look for a tag and for a key
			int tagPos = currentText.length();
			if (tagMatcher.find(result.mConsumedLength))
				tagPos = tagMatcher.start();
			int keyPos = currentText.length();
			if (keyMatcher.find(result.mConsumedLength))
				keyPos = keyMatcher.start();
			int oldQuotePos = currentText.length();
			if (oldQuoteMatcher.find(result.mConsumedLength))
				oldQuotePos = oldQuoteMatcher.start();
			int textEndPos = Math.min(Math.min(tagPos, keyPos),oldQuotePos);
	
			if (textEndPos > result.mConsumedLength)
			{
				Quoting.TextElement newElement = new Quoting.TextElement(TextElementType.PlainText);
				newElement.mContent = currentText.substring(result.mConsumedLength, textEndPos);
				newElement.mConsumedLength = textEndPos-result.mConsumedLength;
				result.mChildren.add(newElement);
				result.mConsumedLength += newElement.mConsumedLength;
			}
			if (textEndPos == currentText.length()) {
				break;
			}
			if (textEndPos == tagPos) {
				result.mConsumedLength += tagMatcher.group().length();
				String t = tagMatcher.group(1);
				String a = tagMatcher.group(2);
				if (t.equals("/"+tag))
					return result;
				if (t.substring(0,1).equals("/")) {
					// closing tag
					// ERROR
					Quoting.TextElement newElement = new Quoting.TextElement(TextElementType.Error);
					newElement.mContent = tagMatcher.group();
					newElement.mConsumedLength = newElement.mContent.length();
					result.mChildren.add(newElement);
				} else {
					// it's an opening tag
					String textToParse = currentText.substring(tagMatcher.end());
					Quoting.TextElement subElement = parseText(textToParse, t, a, maxRecursion-1);
					if (subElement.mType == TextElementType.Error) {
						// we show the entire piece of text that was parsed as an error
						subElement.mContent = currentText.substring(tagMatcher.start(), tagMatcher.end()+subElement.mConsumedLength);
					}
					result.mChildren.add(subElement);
					result.mConsumedLength += subElement.mConsumedLength;
				}
			} else if (textEndPos == keyPos) {
				Quoting.TextElement newElement = new Quoting.TextElement(TextElementType.Link);
				newElement.mContent = keyMatcher.group();
				newElement.mConsumedLength = newElement.mContent.length();
				result.mChildren.add(newElement);
				result.mConsumedLength += newElement.mConsumedLength;
			} else if (textEndPos == oldQuotePos) {
				String author = oldQuoteMatcher.group(1);
				result.mConsumedLength += oldQuoteMatcher.group().length();
				// now it gets nasty
				// we need to read all lines that have an > at the start
				// and stop when we reach one that doesn't
				// find the first line that doesn't have an >
				Pattern endOfOldQuotePattern = Pattern.compile("^[^>]", Pattern.MULTILINE|Pattern.DOTALL);
				Matcher endOfOldQuoteMatcher = endOfOldQuotePattern.matcher(currentText);
				int endOfOldQuotePos = currentText.length();
				if (endOfOldQuoteMatcher.find(result.mConsumedLength))
					endOfOldQuotePos = endOfOldQuoteMatcher.start();
				// cut it out
				String quoted = currentText.substring(result.mConsumedLength, endOfOldQuotePos);
				result.mConsumedLength += quoted.length();
				// we strip off all the >'s
				Pattern quotePartPattern = Pattern.compile("^>[ \t]*", Pattern.MULTILINE|Pattern.DOTALL);
				Matcher quotePartMatcher = quotePartPattern.matcher(quoted);
				String unquoted = quotePartMatcher.replaceAll("");
				// now process it like it was an ordinary [quote=<author>] tag
				Quoting.TextElement subElement = parseText(unquoted, "quote", author, maxRecursion-1);
				result.mChildren.add(subElement);
			}
		}
		return result;
	}

	public static final Quoting.TextElement parseMessageText(Message message) {
		return parseText(message.getText(), "", "", 20);
	}
}
