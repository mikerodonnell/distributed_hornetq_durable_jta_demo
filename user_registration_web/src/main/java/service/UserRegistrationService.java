
package service;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.TransactionManager;

import org.jboss.logging.Logger;

import publisher.UserRegistrationPublisher;


@Singleton
public class UserRegistrationService {

	private static final Logger LOGGER = Logger.getLogger(UserRegistrationService.class);

	@Inject
	private UserRegistrationPublisher userRegistrationPublisher;

	/**
	 * create a unique account for the given user details, and publish a notification to any interested subscribers.
	 * 
	 * @param userName
	 */
	public void registerUser(final String userName, final String emailAddress, final String password) {

		// TODO: once JTA transactionality is working, come back and do the DB save here...no reason to pass details like password to the publisher

		TransactionManager transactionManager = null;
		try {
			transactionManager = (TransactionManager) new InitialContext().lookup("java:/jboss/TransactionManager");
		} catch (NamingException e) {
			LOGGER.error("caught exception getting configured TransactionManager. can't save user and publish message. aborting user registration.");
		}

		if( transactionManager != null ) {
			try {
				transactionManager.begin();

				userRegistrationPublisher.publishForUserRegistrationEvent(userName, emailAddress, password);
				
				transactionManager.commit();
				LOGGER.info("successfully registered user " + userName);
			}
			catch(Exception exception) {
				LOGGER.error("caught exception publishing user, attempting rollback: ", exception);
				try {
					transactionManager.rollback();
					LOGGER.info("rollback complete!");
				}
				catch(Exception rollbackException) {
					LOGGER.fatal("caught exception in trying to roll back failed transaction!: ", rollbackException);
				}
			}
		}
	}

}
