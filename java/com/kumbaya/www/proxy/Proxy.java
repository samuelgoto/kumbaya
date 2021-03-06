package com.kumbaya.www.proxy;

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.multibindings.MapBinder;
import com.kumbaya.common.Flags;
import com.kumbaya.common.Flags.Flag;
import com.kumbaya.common.InetSocketAddresses;
import com.kumbaya.common.Server;
import com.kumbaya.common.monitor.MonitoringModule;
import com.kumbaya.router.Kumbaya;
import com.kumbaya.router.Packets;
import com.kumbaya.router.Packets.Data;
import com.kumbaya.www.JettyServer;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.servlets.ProxyServlet;

public class Proxy implements Server {
  private static final Log logger = LogFactory.getLog(Proxy.class);

  private final JettyServer http;

  @Inject
  Proxy(JettyServer http) {
    this.http = http;
  }

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      install(new ServletModule());
      install(new MonitoringModule());

      install(new AbstractModule() {
        @Override
        protected void configure() {
          MapBinder<String, Servlet> mapbinder =
              MapBinder.newMapBinder(binder(), String.class, Servlet.class);
          mapbinder.addBinding("/*").to(MyProxyServlet.class);
        }
      });
    }
  }

  @Override
  public void start() throws IOException {
    logger.info("Binding into " + host + ":" + port);
    http.start();
  }

  @Override
  public void stop() {
    http.stop();
  }

  static class MyProxyServlet extends ProxyServlet.Transparent {
    private static final Log logger = LogFactory.getLog(MyProxyServlet.class);
    private @Inject(optional = true) @Flag("entrypoint") String entrypoint = "localhost:8081";

    private final Kumbaya client;

    @Inject
    MyProxyServlet(Kumbaya client) {
      this.client = client;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {}

    static String assemble(String url) throws MalformedURLException {
      URL result = new URL(url);
      String[] parts = result.getHost().split("\\.");
      if (parts.length > 2 && parts[parts.length - 2].equals("kumbaya")
          && parts[parts.length - 1].equals("io")) {

        String[] domain = parts[parts.length - 3].split("-");

        if (domain.length > 1) {
          // There is a port available.
          // Override last element with just the domain
          parts[parts.length - 3] = domain[0];
        }

        String host = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 2));

        if (domain.length > 1) {
          host += ":" + domain[1];
        }

        // Removes the old port and old host.
        return url.replace(":" + result.getPort(), "").replace(result.getHost(), host);
      }
      return url;
    }

    @Override
    public void service(ServletRequest req, ServletResponse res)
        throws ServletException, IOException {
      // The proxy will get two types of requests:
      // - through HTTP proxying, which sets the entire request with an absolute url.
      // - through DNS proxying, which requires the proxy to dissamble the url and reconstruct it.
      Packets.Interest interest = new Packets.Interest();
      HttpServletRequest request = (HttpServletRequest) req;
      HttpServletResponse response = (HttpServletResponse) res;

      String url = assemble(request.getRequestURL().toString());

      interest.getName().setName(url);
      try {
        logger.info("Got a request: " + url + " from " + req.getRemoteAddr());
        Optional<Data> result = client.send(InetSocketAddresses.parse(entrypoint), interest);

        if (result.isPresent()) {
          logger.info("Got a response. Returning as a 200.");
          response.getOutputStream().write(result.get().getContent());
          if (result.get().getMetadata().getContentType().isPresent()) {
            response.setContentType(result.get().getMetadata().getContentType().get());
          }
        } else {
          logger.info("Got an empty response. Interpreting as a 404.");
          response.sendError(404);
        }
      } finally {
        response.getOutputStream().close();
      }
    }
  }

  private @Inject(optional = true) @Flag("host") String host = "localhost";
  private @Inject(optional = true) @Flag("port") int port = 8080;

  public static void main(String[] args) throws Exception {
    logger.info("Running the Kumbaya Proxy");

    Proxy proxy = Guice.createInjector(new Module(), Flags.asModule(args)).getInstance(Proxy.class);
    proxy.start();
  }
}
