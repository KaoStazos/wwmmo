package au.com.codeka.warworlds.server.handlers;

import java.io.IOException;

import javax.servlet.http.Cookie;

import au.com.codeka.common.Log;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.RequestHandler;
import au.com.codeka.warworlds.server.ctrl.LoginController;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Tokeninfo;

public class LoginHandler extends RequestHandler {
  private final Log log = new Log("LoginHandler");

  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
  private static final JsonFactory JSON_FACTORY = new GsonFactory();

  @Override
  protected void get() throws RequestException {
    boolean isLoadTest = false;
    if (isLoadTest) {
      loadTestAuthenticate();
      return;
    }

    String authToken = getRequest().getParameter("authToken");
    if (authToken == null || authToken.equals("null")) {
      throw new RequestException(400, "Bad login request.");
    }

    log.info("Building OAuth credentials");
    GoogleCredential credential = new GoogleCredential();
    Oauth2 oauth2 = new Oauth2.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName("wwmmo")
        .build();

    try {
      log.info("Executing OAuth request...");
      Tokeninfo tokenInfo = oauth2.tokeninfo()
          .setAccessToken(getRequest().getParameter("authToken")).execute();
      log.info("... done: %s", tokenInfo.getEmail());

      String emailAddr = tokenInfo.getEmail();
      String impersonateUser = getRequest().getParameter("impersonate");
      String cookie = new LoginController().generateCookie(emailAddr, false, impersonateUser);

      getResponse().setContentType("text/plain");
      getResponse().getWriter().write(cookie);
    } catch (GoogleJsonResponseException e) {
      throw new RequestException(e.getStatusCode(), e.getStatusMessage(), e);
    } catch (IOException e) {
      throw new RequestException(e);
    }
  }

  /**
   * Authentication for load tests is just based on trust. You pass in the email address you
   * want to use directly.
   */
  private void loadTestAuthenticate() throws RequestException {
    String emailAddr = getRequest().getParameter("email");
    String cookie = new LoginController().generateCookie(emailAddr, false, null);

    getResponse().addCookie(new Cookie("SESSION", cookie));
    getResponse().setStatus(200);
  }
}
