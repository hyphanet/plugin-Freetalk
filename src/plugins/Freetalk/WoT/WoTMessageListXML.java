/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.xml.XMLConstants;
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
import org.w3c.dom.NodeList;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;

public final class WoTMessageListXML {
	
	private static final int XML_FORMAT_VERSION = 1;
	
	private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	
	private final DocumentBuilder mDocumentBuilder;
	
	private final DOMImplementation mDOM;
	
	private final Transformer mSerializer;
	
	
	public WoTMessageListXML() {
		try {
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			xmlFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			// DOM parser uses .setAttribute() to pass to underlying Xerces
			xmlFactory.setAttribute("http://apache.org/xml/features/disallow-doctype-decl", true);
			mDocumentBuilder = xmlFactory.newDocumentBuilder(); 
			mDOM = mDocumentBuilder.getDOMImplementation();

			mSerializer = TransformerFactory.newInstance().newTransformer();
			mSerializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			mSerializer.setOutputProperty(OutputKeys.INDENT, "no");
			mSerializer.setOutputProperty(OutputKeys.STANDALONE, "no");
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void encode(WoTMessageManager messageManager, WoTOwnMessageList list, OutputStream os) throws TransformerException, ParserConfigurationException, NoSuchMessageException  {
		synchronized(list) {
			Document xmlDoc;
			synchronized(mDocumentBuilder) {
				xmlDoc = mDOM.createDocument(null, Freetalk.PLUGIN_TITLE, null);
			}
			
			Element rootElement = xmlDoc.getDocumentElement();

			Element messageListTag = xmlDoc.createElement("MessageList");
			messageListTag.setAttribute("Version", Integer.toString(XML_FORMAT_VERSION)); /* Version of the XML format */
			
			/* Important: A OwnMessageList contains a single reference for each message. A MessageList however contains a message reference
			 * for each board a message is posted to. If this function is changed to be able to encode non-own MessageLists then you need
			 * to ensure that each message is only listed once, the for(each MessageReference) will return duplicates if a message is posted
			 * to multiple boards.*/
			for(MessageList.MessageReference ref : list) {
				OwnMessage message = messageManager.getOwnMessage(ref.getMessageID());
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
			StreamResult resultStream = new StreamResult(os);
			synchronized(mSerializer) {
				mSerializer.transform(domSource, resultStream);
			}
		}
	}
	
	public WoTMessageList decode(WoTMessageManager messageManager, WoTIdentity author, FreenetURI uri, InputStream inputStream) throws Exception {
		Document xml;
		synchronized(mDocumentBuilder) {
			xml = mDocumentBuilder.parse(inputStream);
		}
		
		Element listElement = (Element)xml.getElementsByTagName("MessageList").item(0);
		
		if(Integer.parseInt(listElement.getAttribute("Version")) > XML_FORMAT_VERSION)
			throw new Exception("Version " + listElement.getAttribute("Version") + " > " + XML_FORMAT_VERSION);
				
		NodeList messageElements = listElement.getElementsByTagName("Message");
		
		// The message list constructor does all validity checks for message lists, but we duplicate the easy size checks here to prevent memory DoS
		if(messageElements.getLength() > MessageList.MAX_MESSAGES_PER_MESSAGELIST)
			throw new IllegalArgumentException("Too many messages in MessageList: " + messageElements.getLength());
		
		/* The message count is multiplied by 2 because if a message is posted to multiple boards, a MessageReference has to be created for each */
		ArrayList<MessageList.MessageReference> messages = new ArrayList<MessageList.MessageReference>(messageElements.getLength() * 2);
		
		for(int messageIndex = 0; messageIndex < messageElements.getLength(); ++messageIndex) {
			Element messageElement = (Element)messageElements.item(messageIndex);
			
			String messageID = messageElement.getAttribute("ID");
			FreenetURI messageURI = new FreenetURI(messageElement.getAttribute("URI"));
			final Date messageDate;
			
			synchronized(mDateFormat) {
				messageDate = mDateFormat.parse(messageElement.getAttribute("Date"));
			}
		
			NodeList boardElements = messageElement.getElementsByTagName("Board");
			
			if(boardElements.getLength() > Message.MAX_BOARDS_PER_MESSAGE)
				throw new IllegalArgumentException("Too many boards for message " + messageID + ": " + boardElements.getLength());
			
			ArrayList<Board> messageBoards = new ArrayList<Board>(boardElements.getLength() + 1);
			
			for(int boardIndex = 0; boardIndex < boardElements.getLength(); ++boardIndex) {
				Element boardElement = (Element)boardElements.item(boardIndex);
				messageBoards.add(messageManager.getOrCreateBoard(boardElement.getAttribute("Name")));
			}
			
			for(Board board : messageBoards)
				messages.add(new MessageList.MessageReference(messageID, messageURI, board, messageDate));
		}
		
		return new WoTMessageList(author, uri, messages);
	}
}
