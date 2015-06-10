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
	private static final String DATASOURCE_JNDI_NAME = "java:jboss/datasources/user_databaseXA"; // see notes on @Resource lookup of dataSource

	private static ConnectionFactory connectionFactory;

	/* although @Resource is the easiest way to load the DataSource, it also seems to be the only way. attempts to load manually like:
	   dataSource = (DataSource) namingContext.lookup("java:jboss/datasources/user_database");
	   always throws NameNotFoundException, even when it's a local, not remote, resource. this is a problem since we want the resource name
	   to be loaded at runtime based from a properties file.
	 */
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
	public static void setUp() {
		LOGGER.info("setting up now");
		
		if( loadProperties() ) {
			jmsUsername = properties.getProperty("jms.username");
			jmsPassword = properties.getProperty("jms.password");
	
			if( setUpJms() ) {
				isSetupComplete = true;
				LOGGER.info("setup completed successfully.");
			}
			else
				LOGGER.error("Failed to get JMS resources, aborting setup.");
		}
		else
			LOGGER.error("required properties were not loaded successfully, aborting setup.");
		
	}
	
	private static boolean loadProperties() {
		boolean isPropertyLoadingComplete = false;
		
		String fileName = System.getProperty("jboss.server.config.dir") + "/" + PROPERTIES_FILE_NAME;
		File propertiesFile = new File(fileName);
		try {
			properties.load(new FileInputStream(propertiesFile));
			isPropertyLoadingComplete = true;
		}
		catch (IOException ioException) {
			LOGGER.error("caught exception loading properties from file: " + propertiesFile.getAbsolutePath() + ", returning success=false.", ioException);
		}
		
		return isPropertyLoadingComplete;
	}
	
	private static boolean setUpJms() {
		boolean isJmsSetupComplete = false;
		
		Hashtable<String, String> environment = new Hashtable<String, String>();
		environment.put(Context.PROVIDER_URL, properties.getProperty("provider.url"));
		environment.put(Context.INITIAL_CONTEXT_FACTORY, properties.getProperty("initial.context.factory"));
		environment.put(Context.SECURITY_PRINCIPAL, jmsUsername);
		environment.put(Context.SECURITY_CREDENTIALS, jmsPassword);
		Context namingContext = null;
		try {
			//namingContext = new InitialContext(environment); // revisit if there's a reason to do this ... using a "http-remoting" provider URL when instantiating the InitialContext seems problematic
			namingContext = new InitialContext();
			
			String connectionFactoryString = properties.getProperty("default.connection.factory");
			connectionFactory = (ConnectionFactory) namingContext.lookup(connectionFactoryString);
			
			userRegistrationTopicName = properties.getProperty("jms.topic.name");
			userRegistrationTopic = (Topic) namingContext.lookup(userRegistrationTopicName);
			
			isJmsSetupComplete = true;
		}
		catch (NamingException namingException) {
			LOGGER.error("caught NamingException attempting to find JMS topic in server's configuration: ", namingException);
		}
		finally {
			if (namingContext != null) {
				try {
					namingContext.close();
				} catch (NamingException namingException) {
					LOGGER.error("NamingException caught closing the publisher's Context: ", namingException);
				}
			}
		}
		
		return isJmsSetupComplete;
	}


	/**
	 * publishes a single message to the configured user registration Topic for the given newly-created user details.
	 * 
	 * @param userName
	 * @param userGuid
	 * @throws SQLException 
	 */
	// @Transactional(Transactional.TxType.MANDATORY) // TODO: we'll probably want this in place once UserRegistrationService#registerUser is refactored to contain the
	// start point of the transaction.
	public void publishForUserRegistrationEvent(final String userName, final String emailAddress, String password) {

		if(isSetupComplete) {

			LOGGER.info("Initialization of publishing requirements confirmed; adding user to DB now");
			
			// TODO: to demonstrate transactionality across multiple DB actions (even without JMS), break up insert into insert+update.
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
