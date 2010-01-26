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
		assertEquals("messageNode.getFirstTag()", "div", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "<div>\n</div>\n", messageNode.generate());

		messageBody = "Single line test.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "div", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "<div>\nSingle line test.</div>\n", messageNode.generate());
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
		assertEquals("messageNode.getFirstTag()", "div", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "<div>\nFirst line.</div>\n<div>\nSecond line.</div>\n<div>\nThird line.</div>\n", messageNode.generate());
	}

	/**
	 * Tests the conversion of a single line that contains one (or more) links.
	 */
	public void testConversionSingleLineWithLink() {
		String messageBody;
		HTMLNode messageNode;

		messageBody = "The link CHK@foo/bar/baz is in this line.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "div", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "<div>\nThe link <a href=\"/CHK@foo/bar/baz\">CHK@foo/bar/baz</a> is in this line.</div>\n", messageNode.generate());

		messageBody = "The link CHK@foo/bar/baz is in this line, as is USK@foo/baz/bar";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "div", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "<div>\nThe link <a href=\"/CHK@foo/bar/baz\">CHK@foo/bar/baz</a> is in this line, as is <a href=\"/USK@foo/baz/bar\">USK@foo/baz/bar</a></div>\n", messageNode.generate());
	}

	/**
	 * Tests the conversion of multiple lines with multiple links.
	 */
	public void testConversionMultipleLinksWithLinks() {
		String messageBody;
		HTMLNode messageNode;

		messageBody = "The link CHK@foo/bar/baz is in this line.\nAnd here is a second line.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "div", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "<div>\nThe link <a href=\"/CHK@foo/bar/baz\">CHK@foo/bar/baz</a> is in this line.</div>\n<div>\nAnd here is a second line.</div>\n", messageNode.generate());

		messageBody = "The link CHK@foo/bar/baz is in this line.\nThe second line has the USK@foo/baz/bar link.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "div", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "<div>\nThe link <a href=\"/CHK@foo/bar/baz\">CHK@foo/bar/baz</a> is in this line.</div>\n<div>\nThe second line has the <a href=\"/USK@foo/baz/bar\">USK@foo/baz/bar</a> link.</div>\n", messageNode.generate());
	}

	/**
	 * Tests the conversion of a line with line breaks where the line breaks is
	 * embedded in a link, which can happen when a link is written in an NNTP
	 * message and is wrapped at a fixed column.
	 */
	public void testConversionLinksWithLineBreaks() {
		String messageBody;
		HTMLNode messageNode;

		messageBody = "This is a usenet message and it has a line break in the link USK@abc\ndef/foo/bar/baz which is not beautiful.";
		messageNode = ThreadPage.convertMessageBody(messageBody);
		assertNotNull("messageNode", messageNode);
		assertEquals("messageNode.getFirstTag()", "div", messageNode.getFirstTag());
		assertEquals("messageNode.generate()", "<div>\nThis is a usenet message and it has a line break in the link <a href=\"/USK@abcdef/foo/bar/baz\">USK@abcdef/foo/bar/baz</a> which is not beautiful.</div>\n", messageNode.generate());
	}

}
