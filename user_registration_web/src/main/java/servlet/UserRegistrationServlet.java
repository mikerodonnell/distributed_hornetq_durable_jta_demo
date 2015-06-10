
package servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import service.UserRegistrationService;


/**
 * A simple test stub servlet to invoke a publish to the subscribed Topic.
 * 
 * @author Mike O'Donnell  github.com/mikerodonnell
 */
@SuppressWarnings("serial")
@WebServlet("/RegisterUser")
public class UserRegistrationServlet extends HttpServlet {

	@Inject
	private UserRegistrationService userRegistrationService;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/plain");

		String userName = request.getParameter("userName");
		String emailAddress = request.getParameter("emailAddress");
		String password = request.getParameter("password");
		userRegistrationService.registerUser(userName, emailAddress, password);
		PrintWriter writer = response.getWriter();
		writer.println("request submitted to register user " + userName + ".");
		writer.close();
	}

}
