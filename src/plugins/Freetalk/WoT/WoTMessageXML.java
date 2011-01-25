/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.activation.MimeType;
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
import plugins.Freetalk.Version;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.Message.MessageID;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.exceptions.NoSuchBoardException;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.keys.FreenetURI;

/**
 * Generator & parsers of {@link WoTMessage} XML.
 * 
 * @author xor (xor@freenetproject.org)
 */
public final class WoTMessageXML {
	
	public static final int MAX_XML_SIZE = 128 * 1024;
	
	private static final int XML_FORMAT_VERSION = 1;
	
	
	private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");	
	
	private final DocumentBuilder mDocumentBuilder;
	
	private final DOMImplementation mDOM;
	
	private final Transformer mSerializer;
	
	public WoTMessageXML() {
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
	
	
	public void encode(final Message m, final OutputStream os) throws TransformerException, ParserConfigurationException {
		synchronized(m) {
			final Document xmlDoc;
			synchronized(mDocumentBuilder) {
				xmlDoc = mDOM.createDocument(null, Freetalk.PLUGIN_TITLE, null);
			}	
			
			// 1.0 does not support all Unicode characters which the String class supports. To prevent us from having to filter all Strings, we use 1.1
			xmlDoc.setXmlVersion("1.1");
			
			final Element rootElement = xmlDoc.getDocumentElement();
			final Element messageElement = xmlDoc.createElement("Message");
			
			// Versions

			// We include the Freetalk version to have an easy way of handling bogus XML which might be created by bugged versions.
			rootElement.setAttribute("Version", Long.toString(Version.getRealVersion()));			
			messageElement.setAttribute("Version", Integer.toString(XML_FORMAT_VERSION));
			
			// ID
			
			messageElement.setAttribute("ID", m.getID());
			
			// Date
			
			synchronized(mDateFormat) {
				messageElement.setAttribute("Date", mDateFormat.format(m.getDate()));
			}
			
			// Boards
			
			final Element boardsTag = xmlDoc.createElement("Boards");
			for(final Board b : m.getBoards()) {
				final Element boardTag = xmlDoc.createElement("Board");
				boardTag.setAttribute("Name", b.getName());
				boardsTag.appendChild(boardTag);
			}
			
			// Reply-to board
			
			try {
				final Board replyToBoard = m.getReplyToBoard();
				final Element replyBoardTag = xmlDoc.createElement("ReplyToBoard");
				replyBoardTag.setAttribute("Name", replyToBoard.getName());
				boardsTag.appendChild(replyBoardTag);
			} catch(NoSuchBoardException e) {}
			
			messageElement.appendChild(boardsTag);

			// Parent thread / message
			
			if(!m.isThread()) {
				final Element inReplyToTag = xmlDoc.createElement("InReplyTo");
				
				try {
					final Element inReplyToThread = xmlDoc.createElement("Thread");
					inReplyToThread.setAttribute("URI", m.getThreadURI().toString());
					inReplyToTag.appendChild(inReplyToThread);
				}
				catch(NoSuchMessageException e) { }
				
				try {
					final Element inReplyToMessage = xmlDoc.createElement("Message");
					inReplyToMessage.setAttribute("URI", m.getParentURI().toString());
					inReplyToTag.appendChild(inReplyToMessage);
				}
				catch(NoSuchMessageException e) { }
				
				messageElement.appendChild(inReplyToTag);
			}
			
			// Subject/Body - they are the "core" components of a message, therefore they violate the convention and are Elements, not attributes.
			
			final Element subjectTag = xmlDoc.createElement("Subject");
			subjectTag.appendChild(xmlDoc.createCDATASection(m.getTitle()));
			messageElement.appendChild(subjectTag);

			final Element bodyTag = xmlDoc.createElement("Body");
			bodyTag.appendChild(xmlDoc.createCDATASection(m.getText()));
			messageElement.appendChild(bodyTag);
			
			// Attachments
			
			final Attachment[] attachments = m.getAttachments();
			if(attachments != null) {
				final Element attachmentsTag = xmlDoc.createElement("Attachments");
				for(final Attachment a : attachments) {
					final Element fileTag = xmlDoc.createElement("File"); 
					fileTag.setAttribute("URI", a.getURI().toString());
					fileTag.setAttribute("MIMEType", a.getMIMEType().toString());
					fileTag.setAttribute("Size", Long.toString(a.getSize()));
					attachmentsTag.appendChild(fileTag);
				}
				messageElement.appendChild(attachmentsTag);
			}

			rootElement.appendChild(messageElement);

			final DOMSource domSource = new DOMSource(xmlDoc);
			final StreamResult resultStream = new StreamResult(os);
			synchronized(mSerializer) {
				mSerializer.transform(domSource, resultStream);
			}
		}
	}
	
	public Message decode(MessageManager messageManager, InputStream inputStream, WoTMessageList messageList, FreenetURI uri) throws Exception {
		if(inputStream.available() > MAX_XML_SIZE)
			throw new IllegalArgumentException("XML contains too many bytes: " + inputStream.available());
		
		final Document xml;
		synchronized(mDocumentBuilder) {
			xml = mDocumentBuilder.parse(inputStream);
		}

		final Element messageElement = (Element)xml.getDocumentElement().getElementsByTagName("Message").item(0);
		
		// Format version
		
		if(Integer.parseInt(messageElement.getAttribute("Version")) > XML_FORMAT_VERSION)
			throw new Exception("Version " + messageElement.getAttribute("Version") + " > " + XML_FORMAT_VERSION);
		
		// ID
		
		final MessageID messageID = MessageID.construct(messageElement.getAttribute("ID"));
		messageID.throwIfAuthorDoesNotMatch(messageList.getAuthor()); // Double check, the message constructor should also do this.
		
		// Date
		
		final Date messageDate;
		synchronized(mDateFormat) {
			messageDate = mDateFormat.parse(messageElement.getAttribute("Date"));
		}
		
		// Board list
		
		final Element boardsElement = (Element)messageElement.getElementsByTagName("Boards").item(0);
		final NodeList boardList = boardsElement.getElementsByTagName("Board");
		
		if(boardList.getLength() > Message.MAX_BOARDS_PER_MESSAGE)
			throw new IllegalArgumentException("Too many boards: " + boardList.getLength());
		
		final Set<Board> messageBoards = new HashSet<Board>(boardList.getLength() * 2);
		
		for(int i = 0; i < boardList.getLength(); ++i) {
			final Element boardElement = (Element)boardList.item(i);
			messageBoards.add(messageManager.getOrCreateBoard(boardElement.getAttribute("Name")));
		}
		
		// ReplyTo board
		
		final Element replyToBoardElement = (Element)boardsElement.getElementsByTagName("ReplyToBoard").item(0);
		final Board messageReplyToBoard =  replyToBoardElement != null ? messageManager.getOrCreateBoard(replyToBoardElement.getAttribute("Name")) : null; 
		
		// Parent / thread URI
		
		WoTMessageURI parentMessageURI = null;
		WoTMessageURI parentThreadURI = null;
		
		final Element inReplyToElement = (Element)messageElement.getElementsByTagName("InReplyTo").item(0);
		if(inReplyToElement != null) {
			final Element parentElement = (Element)inReplyToElement.getElementsByTagName("Message").item(0);
			if(parentElement != null)
				parentMessageURI = new WoTMessageURI(parentElement.getAttribute("URI"));
		
			Element threadElement = (Element)inReplyToElement.getElementsByTagName("Thread").item(0);
			if(threadElement != null)
				parentThreadURI = new WoTMessageURI(threadElement.getAttribute("URI"));
		}
		
		// Title / body
		
		final String messageTitle = messageElement.getElementsByTagName("Subject").item(0).getTextContent();
		final String messageBody = messageElement.getElementsByTagName("Body").item(0).getTextContent();
		
		// Attachments
		
		final Element attachmentsElement = (Element)messageElement.getElementsByTagName("Attachments").item(0);
		ArrayList<Message.Attachment> messageAttachments = null;
		if(attachmentsElement != null) {
			final NodeList fileElements = attachmentsElement.getElementsByTagName("File");
			
			if(fileElements.getLength() > Message.MAX_ATTACHMENTS_PER_MESSAGE)
				throw new IllegalArgumentException("Too many attachments listed in message: " + fileElements.getLength());
						
			messageAttachments = new ArrayList<Message.Attachment>(fileElements.getLength() + 1);
			
			for(int i = 0; i < fileElements.getLength(); ++i) {
				final Element fileElement = (Element)fileElements.item(i);
				final String fileURI = fileElement.getAttribute("URI");
				final String fileMIMEType = fileElement.hasAttribute("MIMEType") ? fileElement.getAttribute("MIMEType") : null;
				final String fileSize = fileElement.hasAttribute("Size") ? fileElement.getAttribute("Size") : null;
				messageAttachments.add(new Message.Attachment(	new FreenetURI(fileURI),
																fileMIMEType != null ? new MimeType(fileMIMEType) : null,
																fileSize != null ? Long.parseLong(fileSize) : -1));
			}
		}
		
		return WoTMessage.construct(messageList, uri, messageID, parentThreadURI, parentMessageURI, messageBoards, messageReplyToBoard,
									messageList.getAuthor(), messageTitle, messageDate, messageBody, messageAttachments);
	}
}
