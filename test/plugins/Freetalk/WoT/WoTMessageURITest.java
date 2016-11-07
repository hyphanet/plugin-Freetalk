/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.WoT;

import java.net.MalformedURLException;
import java.util.UUID;

import plugins.Freetalk.DatabaseBasedTest;
import plugins.Freetalk.Message.MessageID;
import freenet.keys.FreenetURI;

/**
 * A test for class plugins.Freetalk.WoT.WoTMessagURI.
 * As of r25754 of WoTMessageURI.java, the test is or at least seems to be complete.
 * 
 * @author xor
 */
public class WoTMessageURITest extends DatabaseBasedTest {
	
	/* Attributes of first testing WoTMessageURI */
	private FreenetURI mSSK;
	private UUID mUUID;
	private MessageID mMessageID;
	
	/* Attributes of second testing WoTMessageURI */
	private FreenetURI mOtherSSK;
	private UUID mOtherUUID;
	private MessageID mMessageID_withOtherSSKbutSameUUID;
	private MessageID mMessageID_withOtherUUIDbutSameSSK;
	
	/* Invalid key types*/
	private FreenetURI mKSK, mCHK;
	
	private String mInvalidUUID;
	

	@Override public void setUp() throws Exception {
		super.setUp();
			
		mSSK = new FreenetURI("SSK@SZmdqGtog7v1wN3buILoMpucyD6V5krsYrVqFUfHosg,SLsRq9Q9ZlrmUS9KeyK2pmheJ4wbtW602UwX0o0E~w0,AQACAAE/Freetalk|MessageList-1");
		mUUID = UUID.fromString("d5b0dcc4-91cb-4870-8ab9-8588e895fa5d");
		mMessageID = MessageID.construct(mUUID, mSSK);
		
		mOtherSSK = new FreenetURI("SSK@tqaux3Nm4eH9wxTBVXx6vshv23LLpkwtBh7pE40st2o,NG5Ar~2yA6r5M16b-q~i3ODj~doLinvCuRYDNs1b9Ew,AQACAAE7/Freetalk|MessageList-1");
		mOtherUUID = UUID.fromString("6d01e2f0-dfdd-4be9-9e93-7a35c03f3f12");
		mMessageID_withOtherSSKbutSameUUID = MessageID.construct(mUUID, mOtherSSK);
		mMessageID_withOtherUUIDbutSameSSK = MessageID.construct(mOtherUUID, mSSK);
		
		mKSK = new FreenetURI("KSK@test");
		mCHK = new FreenetURI("CHK@7qMS7LklYIhbZ88i0~u97lxrLKS2uxNwZWQOjPdXnJw,IlA~FSjWW2mPWlzWx7FgpZbBErYdLkqie1uSrcN~LbM,AAIA--8");
		
		mInvalidUUID = "d5b0dcc491cb48708ab98588e895fa5d";
	}

	@Override public void tearDown() throws Exception {
	}

	public void testWoTMessageURIFreenetURIString() {
		/* Test construction of invalid URIs */
		
		try {
			new WoTMessageURI(null, mMessageID);
			fail("Construction of WoTMessageURI with null FreenetURI is possible.");
		}
		catch(IllegalArgumentException e) { }
		
		try {
			new WoTMessageURI(mSSK, null);
			fail("Construction of WoTMessageURI with null Message ID is possible.");
		}
		catch(IllegalArgumentException e) { }
		
		try {
			new WoTMessageURI(mSSK, mMessageID_withOtherSSKbutSameUUID);
			fail("Construction of WoTMessageURI with a Message ID not belonging to the author's SSK is possible.");
		}
		catch(IllegalArgumentException e) { }
		
		try {
			new WoTMessageURI(mCHK, mMessageID);
			fail("Construction of WoTMessageURI with invalid key type is possible.");
		}
		catch(IllegalArgumentException e) { }
		
		try {
			new WoTMessageURI(mKSK, mMessageID);
			fail("Construction of WoTMessageURI with invalid key type is possible.");
		}
		catch(IllegalArgumentException e) { }
		
		
		/* Test construction of valid URIs */
		
		WoTMessageURI uri = new WoTMessageURI(mSSK, mMessageID);
		uri.initializeTransient(mFreetalk);
		assertEquals(mSSK, uri.getFreenetURI());
		assertEquals(mMessageID, uri.getMessageID());
		
		/* The URI should always be stored as SSK even if constructed with USK */ 
		uri = new WoTMessageURI(mSSK.uskForSSK(), mMessageID);
		uri.initializeTransient(mFreetalk);
		assertTrue(uri.getFreenetURI().isSSK());
		assertEquals(mSSK, uri.getFreenetURI());
	}
	
	public void testWoTMessageURIString() throws MalformedURLException {
		/* Test construction of invalid URIs */
		
		try {
			new WoTMessageURI(null);
			fail("Construction of WoTMessageURI with null URI possible.");
		}
		catch (IllegalArgumentException e) { }
		catch (MalformedURLException e) {
			fail("Wrong exception thrown.");
		}
		
		try {
			new WoTMessageURI(mSSK.toASCIIString());
			fail("Construction of WoTMessageURI without Message UUID possible.");
		}
		catch (MalformedURLException e) { }
		
		try {
			new WoTMessageURI(mCHK.toASCIIString() + "#" + mUUID);
			fail("Construction of WoTMessageURI with invalid key type is possible.");
		}
		catch (MalformedURLException e) { }
		
		try {
			new WoTMessageURI(mKSK.toASCIIString() + "#" + mUUID);
			fail("Construction of WoTMessageURI with invalid key type is possible.");
		}
		catch (MalformedURLException e) { }
		
		try {
			new WoTMessageURI(mSSK.toASCIIString() + "#" + mInvalidUUID);
			fail("Construction of WoTMessageURI with invalid UUID is possible.");
		}
		catch (MalformedURLException e) { }
		
		
		/* Test construction of valid URIs */
		
		WoTMessageURI uri = new WoTMessageURI(mSSK.toASCIIString() + "#" + mUUID);
		uri.initializeTransient(mFreetalk);
		assertEquals(mSSK, uri.getFreenetURI());
		assertEquals(mMessageID, uri.getMessageID());
		
		/* The URI should always be stored as SSK even if constructed with USK */
		uri = new WoTMessageURI(mSSK.uskForSSK().toASCIIString() + "#" + mUUID);
		uri.initializeTransient(mFreetalk);
		assertTrue(uri.getFreenetURI().isSSK());
		assertEquals(mSSK, uri.getFreenetURI());
	}
	
	public void testGetFreenetURI() throws MalformedURLException {
		WoTMessageURI uri = new WoTMessageURI(mSSK.toASCIIString() + "#" + mUUID);
		uri.initializeTransient(mFreetalk);
		assertEquals(mSSK, uri.getFreenetURI());
	}
	
	public void testGetMessageID() throws MalformedURLException {
		WoTMessageURI uri = new WoTMessageURI(mSSK.toASCIIString() + "#" + mUUID);
		uri.initializeTransient(mFreetalk);
		assertEquals(mMessageID, uri.getMessageID());
	}

	public void testEquals() {
		WoTMessageURI uri1a = new WoTMessageURI(mSSK, mMessageID); uri1a.initializeTransient(mFreetalk);
		WoTMessageURI uri1b = new WoTMessageURI(mSSK, mMessageID); uri1b.initializeTransient(mFreetalk);
		WoTMessageURI uri2 = new WoTMessageURI(mSSK, mMessageID_withOtherUUIDbutSameSSK); uri2.initializeTransient(mFreetalk);
		WoTMessageURI uri3 = new WoTMessageURI(mOtherSSK, mMessageID_withOtherSSKbutSameUUID); uri3.initializeTransient(mFreetalk);
		
		assertTrue(uri1a.equals(uri1a));
		assertTrue(uri1a.equals(uri1b)); assertTrue(uri1b.equals(uri1a));
		
		assertFalse(uri1a.equals(uri2)); assertFalse(uri2.equals(uri1a));
		assertFalse(uri1a.equals(uri3)); assertFalse(uri3.equals(uri1a));
	}

	public void testToString() {
		WoTMessageURI uri = new WoTMessageURI(mSSK, mMessageID);
		uri.initializeTransient(mFreetalk);
		assertEquals(mSSK.toString() + "#" + mUUID, uri.toString());
	}
}
