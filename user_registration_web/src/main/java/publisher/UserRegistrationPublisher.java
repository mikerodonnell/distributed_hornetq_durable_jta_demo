package publisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Topic;
import javax.jms.XAConnectionFactory;
import javax.jms.XAJMSContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.Transactional;

import org.jboss.logging.Logger;


/**
 * a simple demo JMS publisher for demo of hornetQ publish-subscribe.
 * 
 * @author Mike O'Donnell  github.com/mikerodonnell
 */
@Singleton
public class UserRegistrationPublisher {

	private static final Logger LOGGER = Logger.getLogger(UserRegistrationPublisher.class);
	private static final String PROPERTIES_FILE_NAME = "user_registration_publisher.properties";

	private static XAConnectionFactory connectionFactory;
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
	@PostConstruct // there doesn't seem to be a way to lazy-initialize without Spring. that would be preferable if possible.
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
		
		/*
		Hashtable<String, String> environment = new Hashtable<String, String>();
		environment.put(Context.PROVIDER_URL, properties.getProperty("provider.url"));
		environment.put(Context.INITIAL_CONTEXT_FACTORY, properties.getProperty("initial.context.factory"));
		environment.put(Context.SECURITY_PRINCIPAL, jmsUsername);
		environment.put(Context.SECURITY_CREDENTIALS, jmsPassword);
		Context namingContext = new InitialContext(environment); // revisit if there's a reason to do this ... using a "http-remoting" provider URL when instantiating the InitialContext seems problematic
		*/
		Context namingContext = null;
		try { 
			namingContext = new InitialContext();
			
			String connectionFactoryString = properties.getProperty("default.connection.factory");
			connectionFactory = (XAConnectionFactory) namingContext.lookup(connectionFactoryString);
			
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
	@Transactional(Transactional.TxType.MANDATORY)
	public void publishForUserRegistrationEvent(final String userName, final String emailAddress) throws SQLException {
		
		if(isSetupComplete) {

			LOGGER.info("Initialization of publishing requirements confirmed; publishing to topic " + userRegistrationTopicName + " now");
			XAJMSContext jmsContext = null;
			try {
				jmsContext = connectionFactory.createXAContext(jmsUsername, jmsPassword);
				
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
