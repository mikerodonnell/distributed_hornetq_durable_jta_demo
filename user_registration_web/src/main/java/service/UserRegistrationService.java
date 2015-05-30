
package service;

import java.sql.SQLException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jboss.logging.Logger;

import publisher.UserRegistrationPublisher;

// TODO: see if @Stateless annotation can be used for transactionality

@Singleton
public class UserRegistrationService {

	private static final Logger LOGGER = Logger.getLogger(UserRegistrationService.class);

	@Inject
	private UserRegistrationPublisher userRegistrationPublisher;

	/**
	 * create a unique account for the given userName, and publish a notification to any interested subscribers.
	 * 
	 * @param userName
	 */
	public void registerUser(final String userName, final String emailAddress) {

		try {
			userRegistrationPublisher.publishForUserRegistrationEvent(userName, emailAddress);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		LOGGER.debug("registered user " + userName);
	}

}
