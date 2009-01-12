package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import freenet.keys.FreenetURI;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.exceptions.NoSuchMessageException;

public final class WoTMessageListXML {
	private static final int XML_FORMAT_VERSION = 1;
	
	private static final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	public static void encode(WoTMessageManager messageManager, WoTOwnMessageList list, OutputStream os) throws TransformerException, ParserConfigurationException, NoSuchMessageException  {
		synchronized(list) {
			StreamResult resultStream = new StreamResult(os);

			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
			DOMImplementation impl = xmlBuilder.getDOMImplementation();
			Document xmlDoc = impl.createDocument(null, Freetalk.PLUGIN_TITLE, null);
			Element rootElement = xmlDoc.getDocumentElement();

			Element messageListTag = xmlDoc.createElement("MessageList");
			messageListTag.setAttribute("Version", Integer.toString(XML_FORMAT_VERSION)); /* Version of the XML format */
			
			for(MessageList.MessageReference ref : list) {
				OwnMessage message = messageManager.getOwnMessage(ref.getID());
				if(message.wasInserted() == false)
					throw new RuntimeException("Trying to convert a MessageList to XML which contains a not inserted message.");
				
				Element messageTag = xmlDoc.createElement("Message");
				messageTag.setAttribute("ID", message.getID());
				messageTag.setAttribute("URI", message.getRealURI().toString());
				synchronized(mDateFormat) {
					messageTag.setAttribute("Date", mDateFormat.format(message.getDate()));
				}
				
				for(Board board : message.getBoards()) {
					Element boardTag = xmlDoc.createElement("Board");
					boardTag.setAttribute("Name", board.getName());
					messageTag.appendChild(boardTag);
				}
	
				messageListTag.appendChild(messageTag);
			}
			
			rootElement.appendChild(messageListTag);

			DOMSource domSource = new DOMSource(xmlDoc);
			TransformerFactory transformFactory = TransformerFactory.newInstance();
			Transformer serializer = transformFactory.newTransformer();
			
			serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			serializer.setOutputProperty(OutputKeys.INDENT, "yes"); /* FIXME: Set to no before release. */
			serializer.transform(domSource, resultStream);
		}
	}
	
	public static WoTMessageList decode(InputStream inputStream) { 
		return null;
	}
}
