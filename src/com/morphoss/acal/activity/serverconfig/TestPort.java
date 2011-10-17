package com.morphoss.acal.activity.serverconfig;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.providers.Servers;
import com.morphoss.acal.service.connector.AcalRequestor;
import com.morphoss.acal.service.connector.SendRequestFailedException;
import com.morphoss.acal.xml.DavNode;

public class TestPort {
	private static final String TAG = "aCal TestPort";
	private static final String pPathRequestData = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"+
			"<propfind xmlns=\"DAV:\">"+
				"<prop>"+
					"<principal-collection-set/>"+
					"<current-user-principal/>"+
					"<resourcetype/>"+
				"</prop>"+
			"</propfind>";

	private static final Header[] pPathHeaders = new Header[] {
		new BasicHeader("Depth","0"),
		new BasicHeader("Content-Type","text/xml; charset=UTF-8")
	};

	private final AcalRequestor requestor;
	int port;
	boolean useSSL;
	private String hostName;
	private String path;
	int connectTimeOut;
	int socketTimeOut;
	private Boolean isOpen;
	private Boolean authOK;
	private Boolean hasDAV;
	private Boolean hasCalDAV;
	private String principalUrl;
	
	TestPort(AcalRequestor requestor, int port, boolean useSSL) {
		this.requestor = requestor;
		this.path = requestor.getPath();
		this.hostName = requestor.getHostName();
		this.port = port;
		this.useSSL = useSSL;
		connectTimeOut = 200 + (useSSL ? 300 : 0);
		socketTimeOut = 3000;
		isOpen = null;
		authOK = null;
		hasDAV = null;
		hasCalDAV = null;
		principalUrl = null;
	}

	
	/**
	 * <p>
	 * Test whether the port is open.
	 * </p> 
	 * @return
	 */
	boolean isOpen() {
		if ( this.isOpen == null ) {
			requestor.setTimeOuts(connectTimeOut,socketTimeOut);
			requestor.setPath(path);
			requestor.setHostName(hostName);
			requestor.setPortProtocol( port, (useSSL?1:0) );
			Log.i(TAG, "Checking port open "+requestor.fullUrl());
			this.isOpen = false;
			try {
				requestor.doRequest("HEAD", null, null, null);
				Log.i(TAG, "Probe "+requestor.fullUrl()+" success: status " + requestor.getStatusCode());

				// No exception, so it worked!
				this.isOpen = true;
				if ( requestor.getStatusCode() == 401 ) this.authOK = false;
				checkCalendarAccess(requestor.getResponseHeaders());

				this.socketTimeOut = 15000;
				this.connectTimeOut = 15000;
				requestor.setTimeOuts(connectTimeOut,socketTimeOut);
			}
			catch (Exception e) {
				Log.d(TAG, "Probe "+requestor.fullUrl()+" failed: " + e.getMessage());
			}
		}
		return this.isOpen;
	}


	/**
	 * Increases the connection timeout and attempts another probe.
	 * @return
	 */
	boolean reProbe() {
		connectTimeOut += 1000;
		connectTimeOut *= 2;
		isOpen = null;
		return isOpen();
	}


	/**
	 * <p>
	 * Checks whether the calendar supports CalDAV by looking through the headers for a "DAV:" header which
	 * includes "calendar-access". Appends to the successMessage we will return to the user, as well as
	 * setting the hasCalendarAccess for later update to the DB.
	 * </p>
	 * 
	 * @param headers
	 * @return true if the calendar does support CalDAV.
	 */
	private boolean checkCalendarAccess(Header[] headers) {
		if ( headers != null ) {
			for (Header h : headers) {
				if (h.getName().equalsIgnoreCase("DAV")) {
					if (h.getValue().toLowerCase().contains("calendar-access")) {
						Log.i(TAG, "Discovered server supports CalDAV on URL "+requestor.fullUrl());
						hasCalDAV = true;
						hasDAV = true; // by implication
						return true;
					}
				}
			}
		}
		return false;
	}


	/**
	 * Does a PROPFIND request on the given path.
	 * @param requestPath
	 * @return
	 */
	private boolean doPropfindPrincipal( String requestPath ) {
		if ( requestPath != null ) requestor.setPath(requestPath);
		Log.i(TAG, "Doing PROPFIND for current-user-principal on " + requestor.fullUrl() );
		try {
			DavNode root = requestor.doXmlRequest("PROPFIND", null, pPathHeaders, pPathRequestData);
			
			int status = requestor.getStatusCode();
			Log.d(TAG, "PROPFIND request " + status + " on " + requestor.fullUrl() );

			checkCalendarAccess(requestor.getResponseHeaders());

			if ( status == 207 ) {
				if ( Constants.LOG_DEBUG ) Log.d(TAG, "Checking for principal path in response...");
				List<DavNode> unAuthenticated = root.getNodesFromPath("multistatus/response/propstat/prop/current-user-principal/unauthenticated");
				if ( ! unAuthenticated.isEmpty() ) {
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "Found unauthenticated principal");
					requestor.setAuthRequired();
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "We are unauthenticated, so try forcing authentication on");
					if ( requestor.getAuthType() == Servers.AUTH_NONE ) {
						requestor.setAuthType(Servers.AUTH_BASIC);
						if ( Constants.LOG_DEBUG ) Log.d(TAG, "Guessing Basic Authentication");
					}
					else if ( requestor.getAuthType() == Servers.AUTH_BASIC ) {
						requestor.setAuthType(Servers.AUTH_DIGEST);
						if ( Constants.LOG_DEBUG ) Log.d(TAG, "Guessing Digest Authentication");
					}
					return doPropfindPrincipal(requestPath);
				}
				for ( DavNode href : root.getNodesFromPath("multistatus/response/propstat/prop/resourcetype/principal") ) {
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "This is a principal URL :-)");
					requestor.interpretUriString(href.getText());
					principalUrl = requestor.getPath();
					return true;
				}
				for ( DavNode href : root.getNodesFromPath("multistatus/response/propstat/prop/current-user-principal/href") ) {
					if ( Constants.LOG_DEBUG ) Log.d(TAG, "Found principal URL :-)");
					requestor.interpretUriString(href.getText());
					principalUrl = requestor.getPath();
					return true;
				}
			}
			if ( status < 300 ) authOK = true;
		}
		catch (Exception e) {
			Log.e(TAG, "PROPFIND Error: " + e.getMessage());
			Log.d(TAG, Log.getStackTraceString(e));
		}
		return false;
	}

	
	/**
	 * Probes for whether the server has DAV support.  It seems odd to use the PROPFIND
	 * for this, rather than OPTIONS which was intended for the purpose, but every working
	 * DAV server will support PROPFIND on every URL which supports DAV, whereas OPTIONS
	 * may only be available on some specific URLs in weird cases.
	 */
	boolean hasDAV() {
		if ( !isOpen() ) return false;
		if ( hasDAV == null ) {
			hasDAV = false;
			if ( doPropfindPrincipal(this.path) ) 						hasDAV = true;
			else if ( !hasDAV && doPropfindPrincipal("/.well-known/caldav") )		hasDAV = true;
			else if ( !hasDAV && doPropfindPrincipal("/") )						hasDAV = true;
		}
		return hasDAV;
	}

	
	/**
	 * Probes for CalDAV support on the server using previous path used for DAV.
	 */
	boolean hasCalDAV() {
		if ( !isOpen() || !hasDAV() || !authOK() ) return false;
		if ( hasCalDAV == null ) {
			hasCalDAV = false;
			try {
				Log.i(TAG, "Starting OPTIONS on "+requestor.fullUrl());
				requestor.doRequest("OPTIONS", path, null, null);
				int status = requestor.getStatusCode();
				Log.d(TAG, "OPTIONS request " + status + " on " + requestor.fullUrl() );
				checkCalendarAccess(requestor.getResponseHeaders());
				if ( status == 200 ) return true;
			}
			catch (SendRequestFailedException e) {
				Log.d(TAG, "OPTIONS Error connecting to server: " + e.getMessage());
			}
			catch (Exception e) {
				Log.e(TAG,"OPTIONS Error: " + e.getMessage());
				Log.d(TAG,Log.getStackTraceString(e));
			}
		}
		return hasDAV;
	}


	/**
	 * Return whether the auth was OK.  If nothing's managed to tell us it failed
	 * then we give it the benefit of the doubt.
	 * @return
	 */
	public boolean authOK() {
		return (authOK == false ? false : true);
	}
	
	/**
	 * Returns a default ArrayList<TestPort> which can be used for probing a server to try
	 * and discover where the CalDAV / CardDAV server is hiding.  
	 * @param requestor The requestor which will be used for probing.
	 * @return The ArrayList of default ports.
	 */
	public static ArrayList<TestPort> defaultList(AcalRequestor requestor) {
		ArrayList<TestPort> r = new ArrayList<TestPort>(10);
		r.add( new TestPort(requestor,443,true) );
		r.add( new TestPort(requestor,8443,true) );
		r.add( new TestPort(requestor,80,false) );
		r.add( new TestPort(requestor,8008,false) );
		r.add( new TestPort(requestor,8843,true) );
		r.add( new TestPort(requestor,4443,true) );
		r.add( new TestPort(requestor,8043,true) );
		r.add( new TestPort(requestor,8800,false) );
		r.add( new TestPort(requestor,8888,false) );
		r.add( new TestPort(requestor,7777,false) );

		return r;
	}
}
