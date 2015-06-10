package publisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Singleton;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.jboss.logging.Logger;


// TODO: should @Stateless be used?
/**
 * a simple demo JMS publisher for demo of hornetQ publish-subscribe.
 * 
 * @author Mike O'Donnell  github.com/mikerodonnell
 */
@Singleton
public class UserRegistrationPublisher {

	private static final Logger LOGGER = Logger.getLogger(UserRegistrationPublisher.class);
	private static final String PROPERTIES_FILE_NAME = "user_registration_publisher.properties";
	private static final String DATASOURCE_JNDI_NAME = "java:jboss/datasources/user_database"; // see notes on @Resource lookup of dataSource

	private static ConnectionFactory connectionFactory;

	/* although @Resource is the easiest way to load the DataSource, it also seems to be the only way. attempts to load manually like:
	   dataSource = (DataSource) namingContext.lookup("java:jboss/datasources/user_database");
	   always throws NameNotFoundException, even when it's a local, not remote, resource. this is a problem since we want the resource name
	   to be loaded at runtime based from a properties file.
	 */
	// TODO: make this an XA datasource
	@Resource( lookup=DATASOURCE_JNDI_NAME )
	private static DataSource dataSource;

	private static boolean isSetupComplete = false;
	private static String userRegistrationTopicName;
	private static Topic userRegistrationTopic;
	private static Properties properties = new Properties();
	private static String jmsUsername;
	private static String jmsPassword;


	/**
	 * initialize this publisher by instantiating the Topic and other required Objects. Pre-requisite for publishing messages.
	 * 
	 * @throws NamingException
	 * @throws JMSException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws SQLException 
	 */
	// this doesn't need to be static since the class is annotated @Singleton; static declaration can be removed if needed in the future. same for static members set here.
	@PostConstruct // TODO: there doesn't seem to be a way to lazy-initialize without Spring. that would be preferable if possible.
	public static void setUp() throws NamingException, JMSException, FileNotFoundException, IOException, SQLException {
		LOGGER.info("setting up now");

		String fileName = System.getProperty("jboss.server.config.dir") + "/" + PROPERTIES_FILE_NAME;
		File propertiesFile = new File(fileName);
		properties.load(new FileInputStream(propertiesFile));

		jmsUsername = properties.getProperty("jms.username");
		jmsPassword = properties.getProperty("jms.password");

		Hashtable<String, String> environment = new Hashtable<String, String>();
		environment.put(Context.PROVIDER_URL, properties.getProperty("provider.url"));
		environment.put(Context.INITIAL_CONTEXT_FACTORY, properties.getProperty("initial.context.factory"));
		environment.put(Context.SECURITY_PRINCIPAL, jmsUsername);
		environment.put(Context.SECURITY_CREDENTIALS, jmsPassword);
		Context namingContext = new InitialContext(environment);

		String connectionFactoryString = properties.getProperty("default.connection.factory"); // ex: "jms/RemoteConnectionFactory"
		connectionFactory = (ConnectionFactory) namingContext.lookup(connectionFactoryString);

		userRegistrationTopicName = properties.getProperty("jms.topic.name");
		userRegistrationTopic = (Topic) namingContext.lookup(userRegistrationTopicName);

		if (namingContext != null) {
			try {
				namingContext.close();
			} catch (NamingException namingException) {
				LOGGER.error("Exception caught closing the publisher's Context: ", namingException);
			}
		}

		isSetupComplete = true;
		LOGGER.info("setup complete");
	}


	/**
	 * publishes a single message to the configured user registration Topic for the given newly-created user details.
	 * 
	 * @param userName
	 * @param userGuid
	 * @throws SQLException 
	 */
	public void publishForUserRegistrationEvent(final String userName, final String emailAddress, String password) {

		if(isSetupComplete) {

			LOGGER.info("Initialization of publishing requirements confirmed; adding user to DB now");

			PreparedStatement preparedStatement = null;
			Connection jdbcConnection = null;
			try {
				jdbcConnection = dataSource.getConnection();
				preparedStatement = jdbcConnection.prepareStatement("INSERT INTO users(user_name, email_address, password) VALUES (?, ?, ?);");
				preparedStatement.setString(1, userName);
				preparedStatement.setString(2, emailAddress);
				preparedStatement.setString(3, password);
				preparedStatement.execute();
				LOGGER.info("successfully executed insert, closing prepared statement now.");
			}
			catch(Exception exception) {
				LOGGER.error("caught exception inserting user, attempting to safely close connection now. exception was: " + exception);
			}
			finally {
				if( preparedStatement != null) {
					try {
						preparedStatement.close();
					} catch (SQLException sqlException) {
						LOGGER.error("caught SQLException attempting to close PreparedStatement, possible connection leak.", sqlException);
					}
				}

				if( jdbcConnection != null) {
					try {
						jdbcConnection.close();
					} catch (SQLException sqlException) {
						LOGGER.error("caught SQLException attempting to close JDBC Connection, possible connection leak.", sqlException);
					}
				}
			}

			LOGGER.info("Initialization of publishing requirements confirmed; publishing to topic " + userRegistrationTopicName + " now");
			JMSContext jmsContext = null;
			try {
				jmsContext = connectionFactory.createContext(jmsUsername, jmsPassword);
				JMSProducer producer = jmsContext.createProducer();
				producer.send(userRegistrationTopic, constructNewUserRegistrationMessage(userName, emailAddress));
				LOGGER.info("done publishing message");
			}
			finally {
				if(jmsContext != null )
					jmsContext.close();
			}

		}
		else
			LOGGER.warn("no setup, skipping save and publish");

	}


	/**
	 * construct a JMS message body of a USER_RESISTRATION message from the given user details.
	 * 
	 * @param userName
	 * @param userGuid
	 * @return
	 */
	private static Map<String, Object> constructNewUserRegistrationMessage(final String userName, final String emailAddress) {
		Map<String, Object> messageBody = new HashMap<String, Object>();
		messageBody.put("eventType", "USER_RESISTRATION");
		messageBody.put("userName", userName);
		messageBody.put("emailAddress", emailAddress);
		return messageBody;
	}

}
