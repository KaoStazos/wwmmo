package au.com.codeka.warworlds.server.handlers.admin;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import au.com.codeka.carrot.CarrotEngine;
import au.com.codeka.carrot.CarrotException;
import au.com.codeka.carrot.bindings.MapBindings;
import au.com.codeka.carrot.resource.FileResourceLocator;
import au.com.codeka.carrot.util.SafeString;
import au.com.codeka.common.Log;
import au.com.codeka.warworlds.server.Configuration;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.RequestHandler;
import au.com.codeka.warworlds.server.Session;
import au.com.codeka.warworlds.server.ctrl.AdminController;
import au.com.codeka.warworlds.server.model.BackendUser;

import com.google.common.base.Throwables;
import com.google.gson.JsonElement;

import org.joda.time.DateTime;

public class AdminHandler extends RequestHandler {
  private final Log log = new Log("AdminHandler");

  private static final CarrotEngine TEMPLATE_ENGINE;
  static {
    TEMPLATE_ENGINE = new CarrotEngine(
        new au.com.codeka.carrot.Configuration.Builder()
            .setResourceLocator(new FileResourceLocator.Builder()
                .setBasePath(new File(Configuration.i.getDataDirectory(), "tmpl").getAbsolutePath()))
            .setEncoding("utf-8")
            .build(),
        new MapBindings.Builder()
            .set("Users", new UsersHelper())
            .set("Format", new FormatHelper()));
  }

  @Override
  public void onBeforeHandle() {
    if (!(this instanceof AdminLoginHandler)) {
      // if we're not the Login handler and we're not yet authed, auth now
      if (getSessionNoError() == null || !getSessionNoError().isAdmin()) {
        // if they're not authenticated yet, we'll have to redirect them to the
        // authentication
        // page first.
        authenticate();
        return;
      }
    }
  }

  @Override
  protected void handleException(RequestException e) {
    try {
      TreeMap<String, Object> data = new TreeMap<>();
      data.put("exception", e);
      data.put("stack_trace", Throwables.getStackTraceAsString(e));
      render("exception.html", data);
    } catch (Exception e2) {
      setResponseBody(e.getGenericError());
    }
  }

  protected void render(String path, Map<String, Object> data) throws RequestException {
    if (data == null) {
      data = new TreeMap<>();
    }

    data.put("realm", getRealm());
    Session session = getSessionNoError();
    if (session != null) {
      data.put("logged_in_user", session.getActualEmail());
      data.put("backend_user", new AdminController().getBackendUser(session.getActualEmail()));

      // If there's no admins, then everyone is an admin, so we'll want to warn about that.
      data.put("num_backend_users", new AdminController().getNumBackendUsers());
    }

    getResponse().setContentType("text/html");
    getResponse().setHeader("Content-Type", "text/html; charset=utf-8");
    try {
      getResponse().getWriter().write(TEMPLATE_ENGINE.process(path, new MapBindings(data)));
    } catch (CarrotException | IOException e) {
      log.error("Error rendering template!", e);
    }
  }

  protected void write(String text) {
    getResponse().setContentType("text/plain");
    getResponse().setHeader("Content-Type", "text/plain; charset=utf-8");
    try {
      getResponse().getWriter().write(text);
    } catch (IOException e) {
      log.error("Error writing output!", e);
    }
  }

  protected void writeJson(JsonElement json) {
    getResponse().setContentType("text/json");
    getResponse().setHeader("Content-Type", "text/json; charset=utf-8");
    try {
      getResponse().getWriter().write(json.toString());
    } catch (IOException e) {
      log.error("Error writing output!", e);
    }
  }

  protected void authenticate() {
    URI requestUrl;
    try {
      requestUrl = new URI(getRequestUrl());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    String finalUrl = requestUrl.getPath();
    String redirectUrl = requestUrl.resolve("/realms/" + getRealm() + "/admin/login").toString();
    try {
      redirectUrl += "?continue=" + URLEncoder.encode(finalUrl, "utf-8");
    } catch (UnsupportedEncodingException e) {
      // should never happen
    }

    redirect(redirectUrl);
  }

  /*

  private static class NumberFilter implements Filter {
    private static DecimalFormat sFormat = new DecimalFormat("#,##0");

    @Override
    public String getName() {
      return "number";
    }

    @Override
    public Object filter(Object object, CarrotInterpreter interpreter, String... args)
        throws InterpretException {
      if (object == null) {
        return object;
      }

      if (object instanceof Integer) {
        int n = (int) object;
        return sFormat.format(n);
      }
      if (object instanceof Long) {
        long n = (long) object;
        return sFormat.format(n);
      }
      if (object instanceof Float) {
        float n = (float) object;
        return sFormat.format(n);
      }
      if (object instanceof Double) {
        double n = (double) object;
        return sFormat.format(n);
      }

      throw new InterpretException("Expected a number.");
    }
  }

  private static class AttrEscapeFilter implements Filter {
    @Override
    public String getName() {
      return "attr-escape";
    }

    @Override
    public Object filter(Object object, CarrotInterpreter interpreter, String... args)
        throws InterpretException {
      return object.toString().replace("\"", "&quot;").replace("'", "&squot;");
    }
  }

   */
  private static class FormatHelper {
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0");

    public SafeString date(DateTime dt) {
      return new SafeString(String.format(Locale.ENGLISH, "<script>(function() {"
          + " var dt = new Date(\"%s\");" + " +document.write(dt.toLocaleString());"
          + "})();</script>", dt));
    }

    public String number(long n) {
      return NUMBER_FORMAT.format(n);
    }

    public String number(double d) {
      return NUMBER_FORMAT.format(d);
    }
  }


  private static class UsersHelper {
    public boolean isInRole(BackendUser user, String roleName) {
      BackendUser.Role role = BackendUser.Role.valueOf(roleName);
      return user.isInRole(role);
    }
  }
}
