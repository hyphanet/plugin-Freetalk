/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;

/**
 * @author saces
 *
 */
public class Backup {

	private static SAXParser saxParser;

	public final static void exportConfigDb(ObjectContainer config_db, Writer ow) throws IOException {

		Writer w = new BufferedWriter(ow);

		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		w.write("<fms-kidding>\n");
		w.write("\t<OwnIdentities>\n");
		ObjectSet<FMSOwnIdentity> ownIdentities = config_db.queryByExample(FMSOwnIdentity.class);

		while (ownIdentities.hasNext()) {
			FMSOwnIdentity id = ownIdentities.next();
			w.write("\t\t<OwnIdentity>\n");
			w.write("\t\t\t<Nick>");
			XMLUtils.writeEsc(w, id.getNickName());
			w.write("</Nick>\n");
			w.write("\t\t\t<RequestURI>");
			w.write(id.getRequestURI());
			w.write("</RequestURI>\n");
			w.write("\t\t\t<InsertURI>");
			w.write(id.getInsertURI());
			w.write("</InsertURI>\n");
			w.write("\t\t\t<PublishTrustList>");
			w.write(id.doesPublishTrustList() ? "true" : "false");
			w.write("</PublishTrustList>\n");
			w.write("\t\t</OwnIdentity>\n");
		}

		w.write("\t</OwnIdentities>\n");
		w.write("\t<Identities>\n");

		ObjectSet<FMSIdentity> identities = config_db.queryByExample(FMSIdentity.class);

		while (identities.hasNext()) {
			FMSIdentity id = identities.next();
			if (id instanceof FMSOwnIdentity)
				continue;
			w.write("\t\t<Identity>\n");
			w.write("\t\t\t<Nick>");
			XMLUtils.writeEsc(w, id.getNickName());
			w.write("</Nick>\n");
			w.write("\t\t\t<RequestURI>");
			w.write(id.getRequestURI());
			w.write("</RequestURI>\n");
			w.write("\t\t</Identity>\n");
		}

		w.write("\t</Identities>\n");
		w.write("</fms-kidding>\n");
		w.flush();
		w.close();
	}

	public final static void importConfigDb(ObjectContainer config_db, InputStream is) throws IOException, ParserConfigurationException, SAXException {
		InputStream i = new BufferedInputStream(is);
		SAXParser parser = getSaxParser();
		parser.parse(i, new ImportHandler(config_db));
	}

	private static SAXParser getSaxParser() throws ParserConfigurationException, SAXException {
		if (saxParser == null) {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
		}
		return saxParser;
	}

	public static class ImportHandler extends DefaultHandler {

		boolean shouldRecord = false;
		StringBuilder currentItem = new StringBuilder();
		private String requestUri;
		private String nick;
		private String insertUri;
		private boolean publishTL;
		ObjectContainer config_db;

		ImportHandler(ObjectContainer configdb) {
			config_db = configdb;
		}
		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
		 * java.lang.String, java.lang.String, org.xml.sax.Attributes)
		 */
		@Override
		public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {

			if ("Nick".equals(name)) {
				shouldRecord = true;
				return;
			}
			if ("RequestURI".equals(name)) {
				shouldRecord = true;
				return;
			}
			if ("InsertURI".equals(name)) {
				shouldRecord = true;
				return;
			}
			if ("PublishTrustList".equals(name)) {
				shouldRecord = true;
				return;
			}

			shouldRecord = false;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
		 */
		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			if (shouldRecord)
				currentItem.append(ch, start, length);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String,
		 * java.lang.String, java.lang.String)
		 */
		@Override
		public void endElement(String uri, String localName, String name) throws SAXException {

			if ("Nick".equals(name)) {
				nick = currentItem.toString();
				resetRecord();
				return;
			}
			if ("RequestURI".equals(name)) {
				requestUri = currentItem.toString();
				resetRecord();
				return;
			}
			if ("InsertURI".equals(name)) {
				insertUri = currentItem.toString();
				resetRecord();
				return;
			}
			if ("PublishTrustList".equals(name)) {
				String val = currentItem.toString();
				publishTL = "true".equals(val);
				resetRecord();
				return;
			}

			if ("OwnIdentity".equals(name)) {
				FMSOwnIdentity oid = new FMSOwnIdentity(nick, requestUri, insertUri, publishTL);
				config_db.store(oid);
				config_db.commit();
				return;
			}
			
			if ("Identity".equals(name)) {
				FMSIdentity id = new FMSIdentity(nick, requestUri);
				config_db.store(id);
				config_db.commit();
				return;
			}
			shouldRecord = false;
			currentItem.delete(0, currentItem.length());
		}

		private void resetRecord() {
			shouldRecord = false;
			currentItem.delete(0, currentItem.length());
		}

	}
}
