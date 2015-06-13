
package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;

import org.jboss.logging.Logger;

import publisher.UserRegistrationPublisher;


@Singleton
public class UserRegistrationService {

	private static final Logger LOGGER = Logger.getLogger(UserRegistrationService.class);
	private static final String DATASOURCE_JNDI_NAME = "java:jboss/datasources/user_databaseXA"; // see notes on @Resource lookup of dataSource

	@Inject
	private UserRegistrationPublisher userRegistrationPublisher;
	
	/* although @Resource is the easiest way to load the DataSource, it also seems to be the only way. attempts to load manually like:
	   dataSource = (DataSource) namingContext.lookup("java:jboss/datasources/user_database");
	   always throws NameNotFoundException, even when it's a local, not remote, resource. this is a problem since we want the resource name
	   to be loaded at runtime based from a properties file.
	 */
	@Resource( lookup=DATASOURCE_JNDI_NAME )
	private static DataSource dataSource;

	/**
	 * create a unique account for the given user details, and publish a notification to any interested subscribers.
	 * 
	 * @param userName
	 */
	public void registerUser(final String userName, final String emailAddress, final String password) {
		
		TransactionManager transactionManager = null;
		try {
			transactionManager = (TransactionManager) new InitialContext().lookup("java:/jboss/TransactionManager");
		} catch (NamingException e) {
			LOGGER.error("caught exception getting configured TransactionManager. can't save user and publish message. aborting user registration.");
		}

		if( transactionManager != null ) {
			try {
				transactionManager.begin();
				
				// the ordering of the DB persist and JMS publish is arbitrary. doing the JMS publish first here just for testing convenience. it's easy to force
				// a DB exception by attempting to insert an extant user, then verify that the prior JMS publish beforehand got rolled back.
				userRegistrationPublisher.publishForUserRegistrationEvent(userName, emailAddress);
				persistNewUser(userName, emailAddress, password);
				
				transactionManager.commit();
				LOGGER.info("successfully registered user " + userName);
			}
			catch(Exception exception) {
				LOGGER.error("caught exception publishing user, attempting rollback: ", exception);
				try {
					transactionManager.rollback();
				}
				catch(Exception rollbackException) {
					LOGGER.fatal("caught exception in trying to roll back failed transaction!: ", rollbackException);
				}
			}
		}
	}
	
	
	/**
	 * Create and persist a new user record from the given profile parameters. No application validation is performed to validate that the user does not
	 * exist already; the underlying schema enforces uniqueness and the resulting SQLException will be thrown here.  
	 * 
	 * @param userName
	 * @param emailAddress
	 * @param password
	 * @throws SQLException for uniqueness constraint violation or any other exception triggered at the schema level.
	 */
	@Transactional(Transactional.TxType.MANDATORY)
	private void persistNewUser(final String userName, final String emailAddress, final String password) throws SQLException {
		
		LOGGER.info("saving new user \"" + userName + "\" to DB now");
		PreparedStatement preparedStatement = null;
		Connection jdbcConnection = null;
		try {
			jdbcConnection = dataSource.getConnection();
			preparedStatement = jdbcConnection.prepareStatement("INSERT INTO users(user_name, email_address, password) VALUES (?, ?, ?);");
			preparedStatement.setString(1, userName);
			preparedStatement.setString(2, emailAddress);
			preparedStatement.setString(3, password);
			preparedStatement.execute();
		}
		catch(SQLException exception) {
			LOGGER.error("caught exception inserting user, attempting to safely close connection now. exception was: " + exception);
			throw exception;
		}
		finally {
			if( preparedStatement != null)
				preparedStatement.close();

			if( jdbcConnection != null)
				jdbcConnection.close();
		}
		
	}

}
