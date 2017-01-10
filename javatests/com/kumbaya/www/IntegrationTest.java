package com.kumbaya.www;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.multibindings.MapBinder;
import com.kumbaya.common.Server;
import com.kumbaya.router.Router;
import com.kumbaya.www.gateway.Gateway;
import com.kumbaya.www.proxy.JettyServer;
import com.kumbaya.www.proxy.Proxy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;

public class IntegrationTest extends TestCase {

  private static class TestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      response.getWriter().println("hello world");
    }
  }

  private String get(String url) throws MalformedURLException, IOException {
    URLConnection connection = new URL(url).openConnection();
    Scanner scanner = new Scanner(connection.getInputStream());
    scanner.useDelimiter("\\Z");
    String result = scanner.next();
    scanner.close();
    return result;
  }

  Server server() {
    return Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        MapBinder<String, HttpServlet> mapbinder
        = MapBinder.newMapBinder(binder(), String.class, HttpServlet.class);

        mapbinder.addBinding("/helloworld").to(TestServlet.class);
      }
    }).getInstance(JettyServer.class);
  }

  public void testHttpServer() throws IOException {
    Server server = server();
    server.bind(new InetSocketAddress("localhost", 6060));
    String result = get("http://localhost:6060/helloworld");
    assertEquals("hello world", result);
    server.close();
  }

  public void foo() throws IOException {
    Proxy proxy = Guice.createInjector(new Proxy.Module()).getInstance(Proxy.class);
    proxy.bind(new InetSocketAddress("localhost", 8080));

    Router router = Guice.createInjector().getInstance(Router.class);
    router.bind(new InetSocketAddress("localhost", 9090));

    Gateway gateway = Guice.createInjector().getInstance(Gateway.class);
    gateway.bind(new InetSocketAddress("localhost", 7070));
    
    Server www = server();

    proxy.close();
    router.close();
    gateway.close();
    www.close();
  }


}
