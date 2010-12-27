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
import plugins.Freetalk.Message.MessageID;
import plugins.Freetalk.MessageList;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.Version;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;

/**
 * Generators & parsers of {@link WoTMessageList} XML.
 */
public final class WoTMessageListXML {
	
	public static final int MAX_XML_SIZE = 128 * 1024;
	
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

	public void encode(final WoTMessageManager messageManager, final WoTOwnMessageList list, final OutputStream os) throws TransformerException, ParserConfigurationException, NoSuchMessageException  {
		synchronized(list) {
			final Document xmlDoc;
			synchronized(mDocumentBuilder) {
				xmlDoc = mDOM.createDocument(null, Freetalk.PLUGIN_TITLE, null);
			}
			
			final Element rootElement = xmlDoc.getDocumentElement();
			final Element messageListElement = xmlDoc.createElement("MessageList");
			
			// Versions
			
			// We include the Freetalk version to have an easy way of handling bogus XML which might be created by bugged versions.
			rootElement.setAttribute("Version", Long.toString(Version.getRealVersion()));
			messageListElement.setAttribute("Version", Integer.toString(XML_FORMAT_VERSION));
		
			// Messages
			
			/* Important: A OwnMessageList contains a single reference for each message. A MessageList however contains a message reference
			 * for each board a message is posted to. If this function is changed to be able to encode non-own MessageLists then you need
			 * to ensure that each message is only listed once, the for(each MessageReference) will return duplicates if a message is posted
			 * to multiple boards.*/
			for(final MessageList.MessageReference ref : list) {
				final OwnMessage message = messageManager.getOwnMessage(ref.getMessageID());
				
				// Duplicate checks to prevent severe breakage, also done in message list constructor.
				if(message.getAuthor() != list.getAuthor())
					throw new RuntimeException("Message author does not match message list author");
				
				if(message.wasInserted() == false)
					throw new RuntimeException("Trying to convert a MessageList to XML which contains a not inserted message.");
				
				final Element messageElement = xmlDoc.createElement("Message");
				
				// ID
				
				messageElement.setAttribute("ID", message.getID());
				
				// URI

				messageElement.setAttribute("FreenetURI", message.getFreenetURI().toString());
				
				// Date
				
				synchronized(mDateFormat) {
					messageElement.setAttribute("Date", mDateFormat.format(message.getDate()));
				}
				
				// Boards
				
				for(final Board board : message.getBoards()) {
					final Element boardTag = xmlDoc.createElement("Board");
					boardTag.setAttribute("Name", board.getName());
					messageElement.appendChild(boardTag);
				}
	
				messageListElement.appendChild(messageElement);
			}
			
			rootElement.appendChild(messageListElement);

			final DOMSource domSource = new DOMSource(xmlDoc);
			final StreamResult resultStream = new StreamResult(os);
			synchronized(mSerializer) {
				mSerializer.transform(domSource, resultStream);
			}
		}
	}
	
	public WoTMessageList decode(WoTMessageManager messageManager, WoTIdentity author, FreenetURI uri, InputStream inputStream) throws Exception {
		if(inputStream.available() > MAX_XML_SIZE)
			throw new IllegalArgumentException("XML contains too many bytes: " + inputStream.available());
		
		final Document xml;
		synchronized(mDocumentBuilder) {
			xml = mDocumentBuilder.parse(inputStream);
		}
		
		final Element listElement = (Element)xml.getDocumentElement().getElementsByTagName("MessageList").item(0);
		
		// Version check
		
		if(Integer.parseInt(listElement.getAttribute("Version")) > XML_FORMAT_VERSION)
			throw new Exception("Version " + listElement.getAttribute("Version") + " > " + XML_FORMAT_VERSION);
	
		// Messages
		
		final NodeList messageElements = listElement.getElementsByTagName("Message");
		
		// Prevent memory DoS as early as possible - the MessageList constructor also does it but we don't even want to construct the list here.
		if(messageElements.getLength() > MessageList.MAX_MESSAGES_PER_MESSAGELIST)
			throw new IllegalArgumentException("Too many messages in MessageList: " + messageElements.getLength());
		
		// The message count is multiplied by 2 because if a message is posted to multiple boards, a MessageReference has to be created for each
		final ArrayList<MessageList.MessageReference> messages = new ArrayList<MessageList.MessageReference>(messageElements.getLength() * 2);
		
		for(int messageIndex = 0; messageIndex < messageElements.getLength(); ++messageIndex) {
			final Element messageElement = (Element)messageElements.item(messageIndex);
			
			// ID
			
			final MessageID messageID = MessageID.construct(messageElement.getAttribute("ID"));
			messageID.throwIfAuthorDoesNotMatch(author); // Duplicate check to prevent severe breakage, also done in message list constructor.
			
			// URI
			
			final FreenetURI messageURI = new FreenetURI(messageElement.getAttribute("FreenetURI")); // TODO: FreenetURI won't throw if too long
			
			// Date
			
			final Date messageDate;
			synchronized(mDateFormat) {
				messageDate = mDateFormat.parse(messageElement.getAttribute("Date"));
			}
		
			// Boards
			
			final NodeList boardElements = messageElement.getElementsByTagName("Board");
		
			// Prevent memory DoS as early as possible - the MessageList constructor also does it but we don't even want to construct the list here.
			if(boardElements.getLength() > Message.MAX_BOARDS_PER_MESSAGE)
				throw new IllegalArgumentException("Too many boards for message " + messageID + ": " + boardElements.getLength());
			
			final ArrayList<Board> messageBoards = new ArrayList<Board>(boardElements.getLength() + 1);
			
			for(int boardIndex = 0; boardIndex < boardElements.getLength(); ++boardIndex) {
				final Element boardElement = (Element)boardElements.item(boardIndex);
				messageBoards.add(messageManager.getOrCreateBoard(boardElement.getAttribute("Name")));
			}
			
			for(final Board board : messageBoards)
				messages.add(new MessageList.MessageReference(messageID, messageURI, board, messageDate));
		}
		
		return new WoTMessageList(author, uri, messages);
	}
}
