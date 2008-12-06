/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

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

import plugins.Freetalk.Board;
import plugins.Freetalk.Freetalk;
import plugins.Freetalk.IdentityManager;
import plugins.Freetalk.MessageInserter;
import plugins.Freetalk.MessageManager;
import plugins.Freetalk.OwnMessage;
import plugins.Freetalk.Message.Attachment;
import plugins.Freetalk.exceptions.NoSuchMessageException;
import freenet.client.ClientMetadata;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientPutter;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.io.NativeThread;

public class WoTMessageInserter extends MessageInserter {

	private Random mRandom;
	
	public WoTMessageInserter(Node myNode, HighLevelSimpleClient myClient, String myName, IdentityManager myIdentityManager,
			MessageManager myMessageManager) {
		super(myNode, myClient, myName, myIdentityManager, myMessageManager);
	}

	@Override
	protected Collection<ClientGetter> createFetchStorage() {
		return null;
	}

	@Override
	protected Collection<BaseClientPutter> createInsertStorage() {
		return new ArrayList<BaseClientPutter>(10);
	}

	public int getPriority() {
		return NativeThread.NORM_PRIORITY;
	}
	
	@Override
	protected long getStartupDelay() {
		return STARTUP_DELAY/2 + mRandom.nextInt(STARTUP_DELAY);
	}
	
	@Override
	protected long getSleepTime() {
		return THREAD_PERIOD/2 + mRandom.nextInt(THREAD_PERIOD);
	}

	@Override
	protected void iterate() {
		abortAllTransfers();
		
		Iterator<OwnMessage> messages = mMessageManager.notInsertedMessageIterator();
		while(messages.hasNext()) {
			try {
				/* FIXME: Delay the messages!!!!! And set their date to reflect the delay */
				insertMessage(messages.next());
			}
			catch(Exception e) {
				Logger.error(this, "Insert of message failed", e);
			}
		}
	}
	
	protected void insertMessage(OwnMessage m) throws InsertException, IOException, TransformerException, ParserConfigurationException {
		Bucket tempB = mTBF.makeBucket(2048 + m.getText().length()); /* TODO: set to a reasonable value */
		OutputStream os = tempB.getOutputStream();
		
		try {
			MessageEncoder.encode(m, os);
			os.close(); os = null;
			tempB.setReadOnly();

			ClientMetadata cmd = new ClientMetadata("text/xml");
			InsertBlock ib = new InsertBlock(tempB, cmd, m.getInsertURI());
			InsertContext ictx = mClient.getInsertContext(true);

			/* FIXME: are these parameters correct? */
			ClientPutter pu = mClient.insert(ib, false, null, false, ictx, this);
			// pu.setPriorityClass(RequestStarter.UPDATE_PRIORITY_CLASS); /* pluginmanager defaults to interactive priority */
			addInsert(pu);
			tempB = null;

			Logger.debug(this, "Started insert of message from " + m.getAuthor().getNickname());
		}
		finally {
			if(tempB != null)
				tempB.free();
			if(os != null)
				os.close();
		}
	}

	@Override
	public void onSuccess(BaseClientPutter state) {
		try {
			OwnMessage m = (OwnMessage)mMessageManager.get(state.getURI());
			m.markAsInserted();
		}
		catch(NoSuchMessageException e) {
			Logger.error(this, "Message insert finished but message was deleted: " + state.getURI());
		}
		
		removeInsert(state);
	}
	
	@Override
	public void onFailure(InsertException e, BaseClientPutter state) {
		Logger.error(this, "Message insert failed", e);
		removeInsert(state);
	}

	
	/* Not needed functions*/
	
	@Override
	public void onSuccess(FetchResult result, ClientGetter state) { }
	
	@Override
	public void onFailure(FetchException e, ClientGetter state) { }
	
	@Override
	public void onGeneratedURI(FreenetURI uri, BaseClientPutter state) { }
	
	@Override
	public void onFetchable(BaseClientPutter state) { }

	@Override
	public void onMajorProgress() { }
	
	/**
	 * Encodes a Freetalk message to a format which is compatible with the FMS message format. This was done to allow developers to add 
	 * code to Freetalk or FMS to retrieve each others messages.
	 */
	private static class MessageEncoder {
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
		
		
	}

}
