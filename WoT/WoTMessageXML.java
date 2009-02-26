package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.Message;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;

/**
 * Generator & parsers of message XML. Compatible to the FMS message XML format.
 */
public final class WoTMessageXML {
	private static final int XML_FORMAT_VERSION = 1;
	
	private static final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss");
	
	public static void encode(Message m, OutputStream os) throws TransformerException, ParserConfigurationException {
		synchronized(m) {
			StreamResult resultStream = new StreamResult(os);

			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
			DOMImplementation impl = xmlBuilder.getDOMImplementation();
			Document xmlDoc = impl.createDocument(null, Freetalk.PLUGIN_TITLE, null);
			Element rootElement = xmlDoc.getDocumentElement();

			Element messageTag = xmlDoc.createElement("Message");
			messageTag.setAttribute("version", Integer.toString(XML_FORMAT_VERSION)); /* Version of the XML format */
			
			Element idTag = xmlDoc.createElement("MessageID");
			idTag.appendChild(xmlDoc.createCDATASection(m.getID()));
			messageTag.appendChild(idTag);

			Element subjectTag = xmlDoc.createElement("Subject");
			subjectTag.appendChild(xmlDoc.createCDATASection(m.getTitle()));
			messageTag.appendChild(subjectTag);
			
			Element dateTag = xmlDoc.createElement("Date");
			synchronized(mDateFormat) {
				dateTag.appendChild(xmlDoc.createTextNode(mDateFormat.format(m.getDate())));
			}
			messageTag.appendChild(dateTag);
			
			Element timeTag = xmlDoc.createElement("Time");
			synchronized(mTimeFormat) {
				timeTag.appendChild(xmlDoc.createTextNode(mTimeFormat.format(m.getDate())));
			}
			messageTag.appendChild(timeTag);
			
			Element boardsTag = xmlDoc.createElement("Boards");
			for(Board b : m.getBoards()) {
				Element boardTag = xmlDoc.createElement("Board");
				boardTag.appendChild(xmlDoc.createCDATASection(b.getName()));
				boardsTag.appendChild(boardTag);
			}
			messageTag.appendChild(boardsTag);
			
			if(m.getReplyToBoard() != null) {
				Element replyBoardTag = xmlDoc.createElement("ReplyBoard");
				replyBoardTag.appendChild(xmlDoc.createCDATASection(m.getReplyToBoard().getName()));
				messageTag.appendChild(replyBoardTag);
			}

			if(!m.isThread()) {
				Element inReplyToTag = xmlDoc.createElement("InReplyTo");
				try {
					Element inReplyToMessage = xmlDoc.createElement("Message");
						Element inReplyToOrder = xmlDoc.createElement("Order"); inReplyToOrder.appendChild(xmlDoc.createTextNode("0"));	/* For FMS compatibility, not used by Freetalk */
						Element inReplyToID = xmlDoc.createElement("MessageID"); inReplyToID.appendChild(xmlDoc.createCDATASection(m.getParentID()));
						Element inReplyToURI = xmlDoc.createElement("MessageURI"); inReplyToURI.appendChild(xmlDoc.createCDATASection(m.getParentURI().toString()));
					inReplyToMessage.appendChild(inReplyToOrder);
					inReplyToMessage.appendChild(inReplyToID);
					inReplyToMessage.appendChild(inReplyToURI);
					inReplyToTag.appendChild(inReplyToMessage);
				}
				catch(NoSuchMessageException e) { }
					
				try {
					Element inReplyToThread = xmlDoc.createElement("Thread");
						Element inReplyToID = xmlDoc.createElement("MessageID"); inReplyToID.appendChild(xmlDoc.createCDATASection(m.getThreadID()));
						Element inReplyToURI = xmlDoc.createElement("MessageURI"); inReplyToURI.appendChild(xmlDoc.createCDATASection(m.getThreadURI().toString()));
					inReplyToThread.appendChild(inReplyToID);
					inReplyToThread.appendChild(inReplyToURI);
					inReplyToTag.appendChild(inReplyToThread);
				}
				catch(NoSuchMessageException e) { }
				
				messageTag.appendChild(inReplyToTag);
			}

			Element bodyTag = xmlDoc.createElement("Body");
			bodyTag.appendChild(xmlDoc.createCDATASection(m.getText()));
			messageTag.appendChild(bodyTag);
			
			Attachment[] attachments = m.getAttachments();
			if(attachments != null) {
				Element attachmentsTag = xmlDoc.createElement("Attachments");
				for(Attachment a : attachments) {
					Element fileTag = xmlDoc.createElement("File"); 
						Element keyTag = xmlDoc.createElement("Key"); keyTag.appendChild(xmlDoc.createCDATASection(a.getURI().toString()));
						Element sizeTag = xmlDoc.createElement("Size"); sizeTag.appendChild(xmlDoc.createCDATASection(Integer.toString(a.getSize())));
					fileTag.appendChild(keyTag);
					fileTag.appendChild(sizeTag);
					attachmentsTag.appendChild(fileTag);
				}
				messageTag.appendChild(attachmentsTag);
			}

			rootElement.appendChild(messageTag);

			DOMSource domSource = new DOMSource(xmlDoc);
			TransformerFactory transformFactory = TransformerFactory.newInstance();
			Transformer serializer = transformFactory.newTransformer();
			
			serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			serializer.setOutputProperty(OutputKeys.INDENT, "yes"); /* FIXME: Set to no before release. */
			serializer.setOutputProperty(OutputKeys.STANDALONE, "no");
			serializer.transform(domSource, resultStream);
		}
	}
	
	/**
	 * 
	 * @param db
	 * @param inputStream
	 * @param messageManager Needed for retrieving the Board object from the Strings of the board names.
	 * @param messageList
	 * @param uri
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	public static Message decode(MessageManager messageManager, InputStream inputStream, WoTMessageList messageList, FreenetURI uri) throws Exception {
		DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
		xmlFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		// XXX  DOM parser does not support this, only SAX do this.
		// xmlFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
		Document xml = xmlBuilder.parse(inputStream);
		
		Element messageElement = (Element)xml.getElementsByTagName("Message").item(0);
		
		if(Integer.parseInt(messageElement.getAttribute("version")) > XML_FORMAT_VERSION)
			throw new Exception("Version " + messageElement.getAttribute("version") + " > " + XML_FORMAT_VERSION);
		
		String messageID = messageElement.getElementsByTagName("MessageID").item(0).getTextContent();
		
		String messageTitle = messageElement.getElementsByTagName("Subject").item(0).getTextContent();

		Date messageDate = mDateFormat.parse(messageElement.getElementsByTagName("Date").item(0).getTextContent());
		Date messageTime = mTimeFormat.parse(messageElement.getElementsByTagName("Time").item(0).getTextContent());
		messageDate.setHours(messageTime.getHours()); messageDate.setMinutes(messageTime.getMinutes()); messageDate.setSeconds(messageTime.getSeconds());
		
		Set<Board> messageBoards = new HashSet<Board>();
		Element boardsElement = (Element)messageElement.getElementsByTagName("Boards").item(0);
		NodeList boardList = boardsElement.getElementsByTagName("Board");
		for(int i = 0; i < boardList.getLength(); ++i)
			messageBoards.add(messageManager.getOrCreateBoard(boardList.item(i).getTextContent()));
		
		Node replyToBoardElement = messageElement.getElementsByTagName("ReplyBoard").item(0);
		Board messageReplyToBoard =  replyToBoardElement != null ? messageManager.getOrCreateBoard(replyToBoardElement.getTextContent()) : null; 
		
		WoTMessageURI parentMessageURI = null;
		WoTMessageURI parentThreadURI = null;
		
		Element inReplyToElement = (Element)messageElement.getElementsByTagName("InReplyTo").item(0);
		if(inReplyToElement != null) {
			NodeList parentMessages = inReplyToElement.getElementsByTagName("Message");
			for(int i = 0; i < parentMessages.getLength(); ++i) {
				Element parentMessage = (Element)parentMessages.item(i);
				if(parentMessage.getElementsByTagName("Order").item(0).getTextContent().equals("0"))
					parentMessageURI = new WoTMessageURI(parentMessage.getElementsByTagName("MessageURI").item(0).getTextContent());
			}
		
			Element threadElement = (Element)inReplyToElement.getElementsByTagName("Thread").item(0);
			if(threadElement != null)
				parentThreadURI = new WoTMessageURI(threadElement.getElementsByTagName("MessageURI").item(0).getTextContent());
		}
		
		String messageBody = messageElement.getElementsByTagName("Body").item(0).getTextContent();
		
		ArrayList<Message.Attachment> messageAttachments = null;
		Element attachmentsElement = (Element)messageElement.getElementsByTagName("Attachments").item(0);
		if(attachmentsElement != null) {
			NodeList fileElements = attachmentsElement.getElementsByTagName("File");
			messageAttachments = new ArrayList<Message.Attachment>(fileElements.getLength());
			
			for(int i = 0; i < fileElements.getLength(); ++i) {
				Element fileElement = (Element)fileElements.item(i);
				Node keyElement = fileElement.getElementsByTagName("Key").item(0);
				Node sizeElement = fileElement.getElementsByTagName("Size").item(0);
				messageAttachments.add(new Message.Attachment(	new FreenetURI(keyElement.getTextContent()),
																sizeElement != null ? Integer.parseInt(sizeElement.getTextContent()) : -1));
			}
		}
		
		return WoTMessage.construct(messageList, uri, messageID, parentThreadURI, parentMessageURI, messageBoards, messageReplyToBoard,
									messageList.getAuthor(), messageTitle, messageDate, messageBody, messageAttachments);
	}
}
