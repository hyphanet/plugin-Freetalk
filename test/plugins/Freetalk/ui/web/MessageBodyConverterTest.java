/*
 * plugin-Freetalk-staging - MessageBodyConverterTest.java - Copyright © 2010 David Roden
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugins.Freetalk.ui.web;

import junit.framework.TestCase;
import freenet.support.HTMLNode;

/**
 * Test case for the {@link ThreadPage#convertMessageBody(String)} method that
 * converts a single string containing line breaks and Freenet URIs into an
 * {@link HTMLNode} that can be displayed in a web interface.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class MessageBodyConverterTest extends TestCase {

	/**
	 * Tests the conversion of single lines without line breaks.
	 */
	public void testConversionSingleLine() {
		String messageBody;
		HTMLNode messageNode;

		messageBody = "";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.generate()", "", messageNode.generate());

		messageBody = "\n\n";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.generate()", "\n\n", messageNode.generate());

		messageBody = "   \n  \n   ";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.generate()", "   \n  \n   ", messageNode.generate());

		messageBody = "Single line test.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.generate()", "Single line test.", messageNode.generate());
	}

	/**
	 * Tests the conversion of multiple lines, i.e. a line with line breaks.
	 */
	public void testConversionMulitpleLines() {
		String messageBody;
		HTMLNode messageNode;

		messageBody = "First line.\nSecond line.\nThird line.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.generate()", "First line.\nSecond line.\nThird line.", messageNode.generate());
	}

	/**
	 * Tests the conversion of a single line that contains one (or more) links.
	 */
	public void testConversionSingleLineWithLink() {
		String messageBody;
		HTMLNode messageNode;

		/* too short to be a valid CHK@ link. */
		messageBody = "The link CHK@foo/bar/baz is in this line.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertNull("messageNode.getFirstTag()", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link CHK@foo/bar/baz is in this line.", messageNode.generate());

		/* a valid CHK@ link. */
		messageBody = "The link CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3 is in this line.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link <a href=\"/CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3\">CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3</a> is in this line.", messageNode.generate());

		/* two valid CHK@ links. */
		messageBody = "The link CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3 is in this line, as is CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link <a href=\"/CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3\">CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3</a> is in this line, as is <a href=\"/CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3\">CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3</a>", messageNode.generate());

		/* one valid and one invalid CHK@ link. */
		messageBody = "The link CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3 is in this line, as is CHK@foo/bar/baz";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link <a href=\"/CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3\">CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3</a> is in this line, as is CHK@foo/bar/baz", messageNode.generate());

		/* one valid SSK@ link. */
		messageBody = "The link SSK@pEPG1JsVWyW1fUfc~BX9O55xrYtqPJ~45XXFXSOEeZQ,ACoXFENcgMzxXVfMwPbRXgFY~FcgJmHwX-ixuXWo0EE,AQACAAE/test-0/test.dat is in this line, as is SSK@pEPG1JsVWy/test.dat.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link <a href=\"/SSK@pEPG1JsVWyW1fUfc~BX9O55xrYtqPJ~45XXFXSOEeZQ,ACoXFENcgMzxXVfMwPbRXgFY~FcgJmHwX-ixuXWo0EE,AQACAAE/test-0/test.dat\">SSK@pEPG1JsVWyW1fUfc~BX9O55xrYtqPJ~45XXFXSOEeZQ,ACoXFENcgMzxXVfMwPbRXgFY~FcgJmHwX-ixuXWo0EE,AQACAAE/test-0/test.dat</a> is in this line, as is SSK@pEPG1JsVWy/test.dat.", messageNode.generate());

		/* one KSK link. unfortunately there’s almost no formal invalidity for those. */
		messageBody = "The link KSK@GPL-3 is in this line.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link <a href=\"/KSK@GPL-3\">KSK@GPL-3</a> is in this line.", messageNode.generate());
	}

	/**
	 * Tests the conversion of multiple lines with multiple links.
	 */
	public void testConversionMultipleLinksWithLinks() {
		String messageBody;
		HTMLNode messageNode;

		messageBody = "The link KSK@foo/bar/baz is in this line.\nAnd here is a second line.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link <a href=\"/KSK@foo/bar/baz\">KSK@foo/bar/baz</a> is in this line.\nAnd here is a second line.", messageNode.generate());

		messageBody = "The link KSK@foo/bar/baz is in this line.\nThe second line has the KSK@foo/baz/bar link.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link <a href=\"/KSK@foo/bar/baz\">KSK@foo/bar/baz</a> is in this line.\nThe second line has the <a href=\"/KSK@foo/baz/bar\">KSK@foo/baz/bar</a> link.", messageNode.generate());

		messageBody = "The link KSK@foo/bar/baz is in this line.\nLink at the end: KSK@foo/goo/hoo\nThe second line has the KSK@foo/baz/bar link.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "The link <a href=\"/KSK@foo/bar/baz\">KSK@foo/bar/baz</a> is in this line.\nLink at the end: <a href=\"/KSK@foo/goo/hoo\">KSK@foo/goo/hoo</a>\nThe second line has the <a href=\"/KSK@foo/baz/bar\">KSK@foo/baz/bar</a> link.", messageNode.generate());
	}

	/**
	 * Tests the conversion of a line with line breaks where the line breaks is
	 * embedded in a link, which can happen when a link is written in an NNTP
	 * message and is wrapped at a fixed column.
	 */
	public void testConversionLinksWithLineBreaks() {
		String messageBody;
		HTMLNode messageNode;

		/* KSK links end at whitespace. */
		messageBody = "This is a usenet message and it has a line break in the link KSK@abc\ndef/foo/bar/baz which is not beautiful.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "This is a usenet message and it has a line break in the link <a href=\"/KSK@abc\">KSK@abc</a>\ndef/foo/bar/baz which is not beautiful.", messageNode.generate());

		messageBody = "This is a usenet message and it has a line break in the link CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJE\nYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3 which is not beautiful.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "a", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "This is a usenet message and it has a line break in the link <a href=\"/CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3\">CHK@9F3g6E3VtQ3113zeU2AiG3GxBm1-XDnVfyVQS2kWWr4,bNZl5ibJEYE7a0UTsWcLhbsVwgvSvHdk-KImh8D5hs0,AAIC--8/GPL-3</a> which is not beautiful.", messageNode.generate());
	}

}
