package plugins.Freetalk;

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

import com.db4o.ObjectContainer;

import freenet.keys.FreenetURI;

import plugins.Freetalk.Message.Attachment;


public class MessageXML {
	private static final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss");
	
	public static void encode(OwnMessage m, OutputStream os) throws TransformerException, ParserConfigurationException {
		synchronized(m) {
			StreamResult resultStream = new StreamResult(os);

			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
			DOMImplementation impl = xmlBuilder.getDOMImplementation();
			Document xmlDoc = impl.createDocument(null, Freetalk.PLUGIN_TITLE, null);
			Element rootElement = xmlDoc.getDocumentElement();

			Element messageTag = xmlDoc.createElement("Message");
			messageTag.setAttribute("version", "1"); /* Version of the XML format */
			
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
					Element inReplyToMessage = xmlDoc.createElement("Message");
						Element inReplyToOrder = xmlDoc.createElement("Order"); inReplyToOrder.appendChild(xmlDoc.createTextNode("0"));	/* For FMS compatibility, not used by Freetalk */
						Element inReplyToID = xmlDoc.createElement("MessageID"); inReplyToID.appendChild(xmlDoc.createCDATASection(m.getParentID()));
						Element inReplyToURI = xmlDoc.createElement("MessageURI"); inReplyToURI.appendChild(xmlDoc.createCDATASection(m.getParentURI().toString()));
					inReplyToMessage.appendChild(inReplyToOrder);
					inReplyToMessage.appendChild(inReplyToID);
					inReplyToMessage.appendChild(inReplyToURI);
					
					Element inReplyToThread = xmlDoc.createElement("Thread");
						inReplyToID = xmlDoc.createElement("MessageID"); inReplyToID.appendChild(xmlDoc.createCDATASection(m.getParentThreadID()));
						inReplyToURI = xmlDoc.createElement("MessageURI"); inReplyToURI.appendChild(xmlDoc.createCDATASection(m.getParentThreadURI().toString()));
					inReplyToThread.appendChild(inReplyToID);
					inReplyToThread.appendChild(inReplyToURI);
				inReplyToTag.appendChild(inReplyToMessage);
				inReplyToTag.appendChild(inReplyToThread);
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
	
	public static Message decode(ObjectContainer db, InputStream inputStream, FreenetURI uri) {
		return null;
	}
}
