package plugins.FMSPlugin;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

/**
 * @author saces
 *
 */
public class XMLParser {
	SAXParser saxParser;
	
	XMLParser() throws ParserConfigurationException, SAXException {
		// TODO configure, paranoia settings?
		saxParser = SAXParserFactory.newInstance().newSAXParser();
	}
}
