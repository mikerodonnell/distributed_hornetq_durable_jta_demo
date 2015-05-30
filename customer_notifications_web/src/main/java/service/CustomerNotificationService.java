
package service;

import javax.inject.Singleton;

import org.jboss.logging.Logger;


@Singleton
public class CustomerNotificationService {
	
	private static final Logger LOGGER = Logger.getLogger(CustomerNotificationService.class);

	
	/**
	 * perform any business tasks for welcoming a newly registered user, such as sending an email.
	 * 
	 * @param userName
	 * @param emailAddress
	 */
    public void welcomeNewUser(final String userName, final String emailAddress) {
    	LOGGER.info("welcoming new user \"" + userName + "\" now!");
    	
    	// end of the line for the hornetQ demo. here you'd send the user a welcome email or any other actions.
    }

}
