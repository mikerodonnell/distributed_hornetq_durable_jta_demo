package subscriber;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.logging.Logger;

import service.CustomerNotificationService;


/**
 * a simple demo JMS subscriber for demo of hornetQ publish-subscribe.
 * 
 * @author Mike O'Donnell  github.com/mikerodonnell
 */
@Singleton
public class UserRegistrationEventSubscriber {

	private static final Logger LOGGER = Logger.getLogger(UserRegistrationEventSubscriber.class);
	private static final String PROPERTIES_FILE_NAME = "customer_notifications_subscriber.properties";

	private static Topic userRegistrationTopic;
	private static boolean isSetupComplete = false;
	private static ConnectionFactory connectionFactory;
	private static String userRegistrationTopicName;
	private static Properties properties = new Properties();
	private static String jmsUsername;
	private static String jmsPassword;

	@Inject
	private CustomerNotificationService customerNotificationService;


	//this doesn't need to be static since the class is annotated @Singleton; static declaration can be removed if needed in the future. likewise for
	// the static members set here.
	@PostConstruct // TODO: there doesn't seem to be a way to lazy-initialize without Spring. that would be preferable if possible.
	public static void setUp() throws NamingException, JMSException, FileNotFoundException, IOException {
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

		String connectionFactoryString = properties.getProperty("default.connection.factory");
		connectionFactory = (ConnectionFactory) namingContext.lookup(connectionFactoryString);

		userRegistrationTopicName = properties.getProperty("jms.topic.name");
		userRegistrationTopic = (Topic) namingContext.lookup(userRegistrationTopicName);

		if (namingContext != null) {
			try {
				namingContext.close(); // TODO: verify that we can close this during setup, and still send messages
			} catch (NamingException namingException) {
				LOGGER.error("Exception caught closing the subscriber's Context: ", namingException);
			}
		}

		isSetupComplete = true;
		LOGGER.info("setup complete.");
	}


	public void pollSubscribedTopic() {

		if(isSetupComplete) {
			LOGGER.info("Initialization of publishing requirements confirmed; subscribing to topic " + userRegistrationTopicName + " now");

			JMSContext jmsContext = null;
			try {
				jmsContext = connectionFactory.createContext(jmsUsername, jmsPassword);
				jmsContext.setClientID("userRegistrationTopic"); // TODO: what should this value be?
				JMSConsumer consumer = jmsContext.createDurableConsumer(userRegistrationTopic, "userRegistrationTopic");
				Message message = consumer.receiveNoWait(); // TODO: decide if we want to use receive() or receiveNoWait(), may depending on triggering mechanism.

				if( message != null ) {
					try {
						LOGGER.info("polled queue and found a message.");
						Map<String, Object> userInfo = message.getBody(Map.class);
						customerNotificationService.welcomeNewUser((String) userInfo.get("userName"), (String) userInfo.get("emailAddress"));
					} catch (JMSException jmsException) {
						LOGGER.error("caught JMSException de-queuing message: ", jmsException);
					}
				}
				else
					LOGGER.info("polled queue and nothing was there.");

			}
			finally {
				if(jmsContext != null )
					jmsContext.close();
			}

		}
		else
			LOGGER.warn("no setup, skipping subscribe");

	}

}
