package plugins.Freetalk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import freenet.keys.FreenetURI;
import freenet.support.URLEncoder;


public class Quoting {
	
	public static enum TextElementType {
		Error,
		PlainText,
		Quote,
		Bold,
		Italic,
		Code,
		Link,
		Image;
	
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
			else if (tag.equalsIgnoreCase("img") || tag.equalsIgnoreCase("image"))
				return Image;
	
			return Error;
		}
		
		private static final String regexpForAllTypes = "(quote|b|i|code|link|url|uri|img|image)";
	}

	public final static class TextElement {
		public final TextElementType mType;
		public final HashMap<String, String> mAttributes;
		public String mContent;
		
		public final List<TextElement> mChildren;
		
		private int mConsumedLength;
	
		public TextElement(TextElementType myType, HashMap<String, String> attributes) {
			mType = myType;
			mAttributes = attributes;
			mContent = "";
			mChildren = new ArrayList<TextElement>();
			mConsumedLength = 0;
		}
		
		public TextElement(TextElementType myType) {
			this(myType, new HashMap<String,String>(1));
		}
	
		public TextElement(String tag, HashMap<String,String> attributes) {
			this(TextElementType.fromString(tag), attributes);
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
	
	/**
	 * Used for finding a single starting/ending bbcode tag in the style of:
	 * - [name]
	 * - [name key=value]
	 * - [name key1=value1 key2=value2 ...]
	 * - [/name]
	 */
	private static final Pattern tagPattern = Pattern.compile(
			"\\[" +
				"((/?)" + TextElementType.regexpForAllTypes + ")" +	// tag name, eventually prefixed with / if it is a closing tag
				"(" +				// group which contains all "key=value" attribute pairs... capturing groups only capture the last occurrence so we need this
					"(" +			// in addition to this
						"( (\\w+)=(\\S+))" +	// non-quoted attributes
						"|( (\\w+)=\"([^\"]*)\")" +	// quoted attributes 
					")*" +
				")" +
			"\\]",
			Pattern.MULTILINE|Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
	
	/**
	 * After we have matched a tag using {@link tagPattern}, we parse its key=value pairs using the capturing groups of this {@link Pattern}
	 */
	private static final Pattern attributePattern = Pattern.compile(
			"(" +
				" (\\w+)=((\\S+)|(\"([^\"]*)\"))" +	// quoted or unquoted attributes 
			")"
			, Pattern.MULTILINE|Pattern.DOTALL|Pattern.CASE_INSENSITIVE);


	/**
	 * Used for finding a single {@link FreenetURI}
	 * TODO: Allow lin ebreaks, spaces, etc.
	 */
	private static final Pattern keyPattern = Pattern.compile("(CH|SS|US|KS)K@[%,~" + URLEncoder.getSafeURLCharacters() + "]+", Pattern.MULTILINE|Pattern.DOTALL);
	

	private static final TextElement parseText(String currentText, String tag, HashMap<String,String> args, int maxRecursion) {
		if (maxRecursion < 0) {
			return new TextElement(TextElementType.Error);
		}
		final TextElement result = new TextElement(tag, args);
	
		// for tags without closing tag (if we ever want to support those)
		// just return result here
	
		if (currentText.length() > 0) {
			if (currentText.substring(0,1).equals("\n")) {
				// skip a starting \n (which is what the user expects to happen)
				result.mConsumedLength++;
			}
		}

		// The source code of .matcher() shows that it is synchronized so we can mess around withoud synchronization
		final Matcher tagMatcher = tagPattern.matcher(currentText);
		final Matcher keyMatcher = keyPattern.matcher(currentText);
	
		while (result.mConsumedLength < currentText.length()) {
			// we look for a tag and for a key
			final int tagPos = tagMatcher.find(result.mConsumedLength) ? tagMatcher.start() : currentText.length();
			final int keyPos = keyMatcher.find(result.mConsumedLength) ? keyMatcher.start() : currentText.length();
			final int textEndPos = Math.min(tagPos, keyPos);
	
			if (textEndPos > result.mConsumedLength)
			{
				final TextElement newElement = new TextElement(TextElementType.PlainText);
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
				final String nextTag = tagMatcher.group(1);
				
				// We found a closing tag for result, return it.
				if (nextTag.equals("/"+tag))
					return result;

				// closing tag which does not match the opening tag type => ERROR.
				if (nextTag.substring(0,1).equals("/")) {

					TextElement newElement = new TextElement(TextElementType.Error);
					newElement.mContent = tagMatcher.group();
					newElement.mConsumedLength = newElement.mContent.length();
					result.mChildren.add(newElement);
				} else { // it's an opening tag
					final HashMap<String, String> attributes = new HashMap<String,String>();
					
					// check whether it has attributes
					if(tagMatcher.groupCount() > 1) {
						final String attributesString = tagMatcher.group(4);
						final Matcher attributeMatcher = attributePattern.matcher(attributesString);
						
						while(attributeMatcher.find()) {
							final String key = attributeMatcher.group(2).trim().toLowerCase();
							String value = attributeMatcher.group(3).trim();
							if(value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
								value = value.substring(1, value.length()-1);
							attributes.put(key, value);
						}
					}

					String textToParse = currentText.substring(tagMatcher.end());
					TextElement subElement = parseText(textToParse, nextTag, attributes, maxRecursion-1);
					if (subElement.mType == TextElementType.Error) {
						// we show the entire piece of text that was parsed as an error
						subElement.mContent = currentText.substring(tagMatcher.start(), tagMatcher.end()+subElement.mConsumedLength);
					}
					result.mChildren.add(subElement);
					result.mConsumedLength += subElement.mConsumedLength;
				}
			} else if (textEndPos == keyPos) {
				TextElement newElement = new TextElement(TextElementType.Link);
				newElement.mContent = keyMatcher.group();
				newElement.mConsumedLength = newElement.mContent.length();
				result.mChildren.add(newElement);
				result.mConsumedLength += newElement.mConsumedLength;
			}
			
		}
		return result;
	}

	public static final TextElement parseText(String text) {
		return parseText(text, "", new HashMap<String,String>(1), 20);
	}
	
	public static final TextElement parseText(Message message) {
		return parseText(message.getText());
	}
}
