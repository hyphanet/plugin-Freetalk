package plugins.Freetalk;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.MessageXML.XMLTreeGenerator.XMLElement;
import plugins.Freetalk.WoT.WoTMessageList;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;
import freenet.support.Logger;
import freenet.support.MultiValueTable;

/**
 * Generator & parsers of message XML. Compatible to the FMS message XML format.
 */
public class MessageXML {
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
						Element inReplyToID = xmlDoc.createElement("MessageID"); inReplyToID.appendChild(xmlDoc.createCDATASection(m.getParentThreadID()));
						Element inReplyToURI = xmlDoc.createElement("MessageURI"); inReplyToURI.appendChild(xmlDoc.createCDATASection(m.getParentThreadURI().toString()));
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
			serializer.setOutputProperty(OutputKeys.INDENT,"yes");
			serializer.transform(domSource, resultStream);
		}
	}
	
	/** Valid element names for message XML version 1 */
	private static final HashSet<String> messageXMLElements1 = new HashSet<String>(Arrays.asList(
		new String[] { Freetalk.PLUGIN_TITLE, "Message", "MessageID", "Subject", "Date", "Time", "Boards", "Board", "ReplyBoard", "InReplyTo",
					  	"Order", "MessageURI", "Thread", "Body", "Attachments", "File", "Key", "Size"}));
	
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
	public static Message decode(MessageManager messageManager, InputStream inputStream, WoTMessageList messageList, FreenetURI uri) throws Exception {
		XMLTreeGenerator xmlTreeGenerator = new XMLTreeGenerator(messageXMLElements1);
		SAXParserFactory.newInstance().newSAXParser().parse(inputStream, xmlTreeGenerator);
		
		XMLElement rootElement = xmlTreeGenerator.getRoot();
		
		rootElement = rootElement.children.get("Message");
		
		/* FIXME FIXME FIXME
		 * getValue() returns null even though debugging shows that the value IS there !?!?
		 * Is this a bug in the parser?
		if(Integer.parseInt(rootElement.attrs.getValue("version")) > XML_FORMAT_VERSION)
			throw new Exception("Version > " + XML_FORMAT_VERSION);
		*/
		
		String messageID = rootElement.children.get("MessageID").cdata;
		
		String messageTitle = rootElement.children.get("Subject").cdata;
		Date messageDate = mDateFormat.parse(rootElement.children.get("Date").cdata);
		Date messageTime = mTimeFormat.parse(rootElement.children.get("Time").cdata);
		messageDate.setHours(messageTime.getHours()); messageDate.setMinutes(messageTime.getMinutes()); messageDate.setSeconds(messageTime.getSeconds());
		
		Set<Board> messageBoards = new HashSet<Board>();
		for(XMLElement board : rootElement.children.get("Boards").children.iterateAll("Board")) {
			messageBoards.add(messageManager.getOrCreateBoard(board.cdata));
		}
		
		XMLElement replyToBoardElement = rootElement.children.get("ReplyBoard");
		Board messageReplyToBoard =  replyToBoardElement != null ? messageManager.getOrCreateBoard(replyToBoardElement.cdata) : null; 
		
		FreenetURI parentMessageURI = null;
		FreenetURI parentThreadURI = null;
		
		XMLElement inReplyToElement = rootElement.children.get("InReplyTo");
		if(inReplyToElement != null) {
			if(inReplyToElement.children.containsKey("Message")) {
				for(XMLElement inReplyToMessageElement : inReplyToElement.children.iterateAll("Message")) {
					if(inReplyToMessageElement.children.get("Order").cdata.equals("0"))
						parentMessageURI = new FreenetURI(inReplyToMessageElement.children.get("MessageURI").cdata);
				}
			}
		
			XMLElement threadElement = inReplyToElement.children.get("Thread");
			if(threadElement != null)
				parentThreadURI = new FreenetURI(threadElement.children.get("MessageURI").cdata);
		}
		
		String messageBody = rootElement.children.get("Body").cdata;
		
		ArrayList<Message.Attachment> messageAttachments = new ArrayList<Message.Attachment>(10);
		
		XMLElement attachmentsElement = rootElement.children.get("Attachments");
		if(attachmentsElement != null) {
			for(XMLElement fileElement : attachmentsElement.children.iterateAll("File")) {
				XMLElement sizeElement = fileElement.children.get("Size");
				messageAttachments.add(new Message.Attachment(	new FreenetURI(fileElement.children.get("Key").cdata),
																sizeElement != null ? Integer.parseInt(sizeElement.cdata) : -1));
			}
		}
		
		return Message.construct(messageList, messageID, parentThreadURI, parentMessageURI, messageBoards, messageReplyToBoard,
									messageList.getAuthor(), messageTitle, messageDate, messageBody, messageAttachments);
	}
	
	/**
	 * TODO: This class is useful for general XML parsing. It should maybe be put into freenet.support.
	 * TODO: Also use this class in the WoT XML parsers.
	 */
	protected static class XMLTreeGenerator extends DefaultHandler2 {
		private final Set<String> mAllowedElementNames;

		public XMLTreeGenerator(Set<String> allowedElementNames) {
			mAllowedElementNames = allowedElementNames;
		}
		
		public XMLElement getRoot() {
			return rootElement;
		}
		
		public class XMLElement {
			public final String name;
			public final Attributes attrs;
			public String cdata = null;
			public MultiValueTable<String, XMLElement> children = new MultiValueTable<String, XMLElement>();
			
			public XMLElement(String newName, Attributes newAttributes) throws Exception {
				name = newName;
				attrs = newAttributes;
				
				if(!mAllowedElementNames.contains(name))
					throw new Exception("Unknown element in Message: " + name);
			}
		}
		
		private XMLElement rootElement = null;
		
		private final Stack<XMLElement> elements = new Stack<XMLElement>();
		
		@Override
		public void startElement(String nameSpaceURI, String localName, String qName, Attributes attrs) throws SAXException {
			String name = (qName == null ? localName : qName);

			try {
				XMLElement element = new XMLElement(name, attrs);
				
				if(rootElement == null)
					rootElement = element;
				
				try {
					elements.peek().children.put(name, element);
				}
				catch(EmptyStackException e) { }
				elements.push(element);
				
				/* FIXME: A speedup would be to only store CDATA for elements selected by the creator of the XMLTreeGenerator. */
				/* Alternatively the <Date> and <Time> elements could be changed to also contain a CDATA tag, then the following
				 * line could be removed because all CDATA in the message XML is also labeled as CDATA. We should ask SomeDude
				 * whether FMS can be modified to also do that, we want to stay compatible to FMS message XML. */
				cdata = new StringBuffer(10 * 1024);
			} catch (Exception e) {
				Logger.error(this, "Parsing error", e);
			}
		}
		
		StringBuffer cdata;
		
		public void beginCDATA() {
			 cdata = new StringBuffer(10 * 1024);
		}
		
		public void characters(char[] ch, int start, int length) {
			if(cdata != null)
				cdata.append(ch, start, length);
		}
		
		public void endCDATA() {
			elements.peek().cdata = cdata.toString();
			cdata = null;
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			try {
				XMLElement newElement = elements.pop(); /* This can fail because we do not push elements with invalid name */
				if(cdata != null)
					newElement.cdata = cdata.toString();
			}
			catch(EmptyStackException e) {}
			cdata = null;
		}
	}
}
