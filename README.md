# Distributed HornetQ Durable JTA Demo

A demo JMS implementation with a single publisher and subscriber, each enforcing transactionality between the JMS action and a corresponding JDBC action through JTA.
<br/>
<br/>

# User Registration Web

The demo publisher webapp. Registers a new end user to the system by saving the user's profile information to the database, and publish this event for other apps in the system to act upon if they need to.


# Customer Notifications Web

The demo subscriber webapp. Listens for events that customers should be notified about (such as a new registration to the system), sends the user a notification, and records an audit record that the notification was sent.
<br/>
<br/>


# Implementation Notes
 - This implementation does not rely on any HornetQ-specific packages; application code is written to the JMS interface.
 - Container-managed XA datasources are used for JDBC access to the applications' databases.
 - Container-managed XA JMS Connection Factories are used for JMS access to the broker.
<br/>
<br/>

# TODO
 1. Transactionality spanning JMS+JDBC on the subscriber is not implemented yet. Some issues using the client ID to establish the container-managed durable connection to be addressed.
 2. Verification that JMS batch size does not affect transactionality. For example, if a batch of 10 JMS messages is processed and an exception requiring rollback occurs processing the 4th message, the remaining 6 messages remain queued.
 3. Use jboss-deployment-structure.xml to make container-provided modules available to the webapps, and update scope of provided dependencies in POMs accordingly.
 4. Verify container-managed resources can be loaded when the webapps are distributed from the JMS broker.
 5. Trim standalone.xml down to necessary modules for this demo.

