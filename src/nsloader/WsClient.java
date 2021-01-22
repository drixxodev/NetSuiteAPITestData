package nsloader;

import nsloader.wsproxy.platform.NetSuiteBindingStub;
import nsloader.wsproxy.platform.NetSuitePortType;
import nsloader.wsproxy.platform.NetSuiteServiceLocator;
import nsloader.wsproxy.platform.core.BaseRef;
import nsloader.wsproxy.platform.core.GetServerTimeResult;
import nsloader.wsproxy.platform.core.Passport;
import nsloader.wsproxy.platform.core.Record;
import nsloader.wsproxy.platform.core.RecordRef;
import nsloader.wsproxy.platform.messages.ApplicationInfo;
import nsloader.wsproxy.platform.messages.Preferences;
import nsloader.wsproxy.platform.messages.WriteResponseList;
import org.apache.axis.message.SOAPHeaderElement;

import javax.xml.rpc.ServiceException;
import javax.xml.soap.SOAPException;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Properties;

/**
 * Description: Responsible for connectivity to NetSuite Servers via Axis
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class WsClient
{
    public static final int MAX_CONCRRENCY_PER_EMAIL = 10;
    public static final int MIN_CONCRRENCY_PER_EMAIL = 1;
	private static final String APPLICATION_ID = "0F07E12D-55D6-4E78-B123-77C7E5CA4217";

	/**
	 * Proxy class that abstracts the communication with the NetSuite Web
	 * Services. All NetSuite operations are invoked as methods of this class.
	 */
	private NetSuitePortType _port;

	/**
	 * Abstraction of the external properties file that contains configuration
	 */
	private Properties _properties;

	/**
	 * Flag that indicates whether request level credentials are used.
	 * If request level credentials are used, then no session is kept
	 */
    private boolean _reqLvlCred = false;

	private String _email = null;
	private String _password = null;
	private String _roleId = null;
	private String _account = null;
	private String _hostUrl = null;
	private String _endpointVersion = null;
	private int _sessionsPerUser = 1;
    public final int instance;

	/**
	 * Requested page size for search
	 */
	private Boolean _ignoreReadOnlyFields = Boolean.TRUE;
	private final Boolean _warningsAsErrors;

	public static WsClient getInstance(int instanceCount) throws IOException, ServiceException, SOAPException
	{
		WsClient client = new WsClient(instanceCount);
		client.initNSPort();
		client.setPreferences();
		client.setApplicationId();
		return client;
	}

	private WsClient(int instanceCount) throws IOException
	{
		// In order to use SSL forwarding for SOAP messages.
		System.setProperty("axis.socketSecureFactory", "org.apache.axis.components.net.SunFakeTrustSocketFactory");
        this.instance = instanceCount;
		_properties = new Properties();
		_properties.load(new FileInputStream("config/nsloader.properties"));

		if ("true".equals(_properties.getProperty("useRequestLevelCredentials")))
		{
			_reqLvlCred = true;
		}

		_sessionsPerUser = Integer.parseInt(_properties.getProperty("client.sessionsPerUser"));
		_sessionsPerUser = _sessionsPerUser > MAX_CONCRRENCY_PER_EMAIL ? MAX_CONCRRENCY_PER_EMAIL : (_sessionsPerUser < MIN_CONCRRENCY_PER_EMAIL ? MIN_CONCRRENCY_PER_EMAIL : _sessionsPerUser);
        int nthUser = instanceCount / _sessionsPerUser + 1;
		_email = _properties.getProperty("login.email."+nthUser);
		_password = Controller.getPasswordForEmail(_email);
		_roleId = _properties.getProperty("login.roleInternalId."+nthUser);
		_account = _properties.getProperty("login.acct");
		String wae = _properties.getProperty("soap.warningsAsErrors");
		_warningsAsErrors = wae == null ? null : "T".equals(wae);

        _hostUrl = _properties.getProperty("host.url");
        _endpointVersion = _properties.getProperty("wsdl.version");
	}

	private void initNSPort() throws MalformedURLException, ServiceException
	{
		NetSuiteServiceLocator service = new NetSuiteServiceLocator();

		if (!_reqLvlCred) {
		  service.setMaintainSession(true);
		}
		_port = service.getNetSuitePort(new URL(_hostUrl + "/services/NetSuitePort_" + _endpointVersion));
		// Setting client timeout to 2 hours for long running operations
		((NetSuiteBindingStub) _port).setTimeout(1000 * 60 * 60 * 2);
	}

	/**
	 * Sets the preferences and search preferences for an operation
	 * by adding the preferences to the SOAP header
	 * @throws javax.xml.soap.SOAPException
	 */
	private void setPreferences() throws SOAPException
	{
		// Cast your login NetSuitePortType variable to a NetSuiteBindingStub
		NetSuiteBindingStub stub = (NetSuiteBindingStub) _port;

		// Clear the headers to make sure you know exactly what you are sending.
		// Headers do not overwrite when you are using Axis/Java
		stub.clearHeaders();

		Passport passport = new Passport();
		RecordRef role = new RecordRef();

		if (_reqLvlCred) {
			passport.setEmail(_email);
			passport.setPassword(_password);
			role.setInternalId(_roleId);
			passport.setRole(role);
			passport.setAccount(_account);
		}

		SOAPHeaderElement passportHeader = new SOAPHeaderElement(
			"urn:messages.platform.webservices.netsuite.com",
			"passport");
		passportHeader.setObjectValue(passport);

		// Create a new SOAPHeaderElement, this is what the NetSuiteBindingStub
		// will accept
		// This is the same command for all preference elements, ie you might
		// substitute "useDefaults" for "searchPreferences"
		SOAPHeaderElement prefHeader = new SOAPHeaderElement(
			"urn:messages.platform.webservices.netsuite.com",
			"preferences");
		Preferences prefs = new Preferences();
		prefs.setIgnoreReadOnlyFields(_ignoreReadOnlyFields);
		prefs.setWarningAsError(_warningsAsErrors);
		prefHeader.setObjectValue(prefs);

		// setHeader applies the Header Element to the stub
		// Again, note that if you reuse your NetSuitePort object (vs logging in
		// before every request)
		// that headers are sticky, so if in doubt, call clearHeaders() first.
		if (_reqLvlCred) {
		  stub.setHeader(passportHeader);
		}
		stub.setHeader(prefHeader);
	}

	private void setApplicationId() throws SOAPException
	{
		ApplicationInfo applicationInfo = new ApplicationInfo();
		applicationInfo.setApplicationId(APPLICATION_ID);
		SOAPHeaderElement applicationIdHeader = new SOAPHeaderElement("urn:messages.platform.webservices.netsuite.com", "applicationInfo");
		applicationIdHeader.setObjectValue(applicationInfo);
		((NetSuiteBindingStub) _port).setHeader(applicationIdHeader);
	}

    /** Test code, make sure everything is OK */
	public void getServerTime() throws RemoteException
	{
		GetServerTimeResult result = _port.getServerTime();

		if (result.getStatus().isIsSuccess())
		{
			System.out.println("Server Time: " + result.getServerTime().getTime());
		}
		else
		{
			System.out.println("[Error]: getServerTime Operation failed. " + result.getStatus());
		}
	}

    public WriteResponseList addRecords(Record[] toAdd) throws RemoteException
    {
        return _port.addList(toAdd);
    }
    public WriteResponseList updateRecords(Record[] toUpdate) throws RemoteException
    {
        return _port.updateList(toUpdate);
    }
	public WriteResponseList upsertRecords(Record[] toUpsert) throws RemoteException
	{
		return _port.upsertList(toUpsert);
	}
    public WriteResponseList deleteRecords(BaseRef[] toDelete) throws RemoteException
    {
        return _port.deleteList(toDelete);
    }

}
