
package servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import subscriber.UserRegistrationEventSubscriber;


/**
 * A simple test stub servlet to invoke polling the subscribed Topic.
 * 
 * @author Mike O'Donnell  github.com/mikerodonnell
 */
@SuppressWarnings("serial")
@WebServlet("/PollQueue")
public class PollQueueServlet extends HttpServlet {

    @Inject
    UserRegistrationEventSubscriber userRegistrationEventSubscriber;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");
        
        userRegistrationEventSubscriber.pollSubscribedTopic();
        PrintWriter writer = resp.getWriter();
        writer.println("Triggered poll of subscribed topic from test stub servlet.");
        writer.close();
    }

}
