package com.liveramp.tangled;

import javax.servlet.DispatcherType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.xml.DOMConfigurator;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServer implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);

  private final Semaphore shutdownLock = new Semaphore(0);

  private final JSONObject configuration;

  public WebServer(JSONObject configuration) {
    this.configuration = configuration;
  }

  public final void shutdown() {
    shutdownLock.release();
  }

  @Override
  public void run() {
    try {


      Server uiServer = new Server(new ExecutorThreadPool(50, 50, Integer.MAX_VALUE, TimeUnit.MINUTES));

      ServerConnector http = new ServerConnector(uiServer, new HttpConnectionFactory());
      http.setPort(47171);
      http.setIdleTimeout(30000);
      uiServer.addConnector(http);

      final URL warUrl = uiServer.getClass().getClassLoader().getResource("com/liveramp/tangled/www");
      final String warUrlString = warUrl.toExternalForm();

      WebAppContext context = new WebAppContext(warUrlString, "/");

      context.addServlet(new ServletHolder(new JSONServlet(new DefaultsServlet(
          configuration.getString("defaultGroup"),
          configuration.getString("defaultArtifact")
      ))), "/defaults");

      context.addServlet(new ServletHolder(new JSONServlet(new GraphServlet(
          getRemoteRepositories(configuration),
          configuration.getString("localRepository"),
          configuration.getString("truncatePrefix")
      ))), "/graph");

      context.addFilter(GzipFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));

      uiServer.setHandler(context);

      uiServer.start();

      shutdownLock.acquire();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public List<RemoteRepository> getRemoteRepositories(JSONObject configuration) {

    List<RemoteRepository> repositories = Lists.newArrayList();

    JSONArray snapshotRepos = configuration.getJSONArray("snapshotRepositories");

    for (int i = 0; i < snapshotRepos.length(); i++) {
      JSONObject repo = snapshotRepos.getJSONObject(i);

      String id = repo.getString("id");
      String type = repo.getString("type");
      String url = repo.getString("url");
      String updatePolicy = repo.getString("updatePolicy");

      repositories.add(new RemoteRepository.Builder(id, type, url)
          .setSnapshotPolicy(new RepositoryPolicy(true, updatePolicy, null))
          .setReleasePolicy(new RepositoryPolicy(false, null, null))
          .build()
      );
    }

    JSONArray releaseRepos = configuration.getJSONArray("releaseRepositories");

    for (int i = 0; i < releaseRepos.length(); i++) {
      JSONObject repo = releaseRepos.getJSONObject(i);

      String id = repo.getString("id");
      String type = repo.getString("type");
      String url = repo.getString("url");
      String updatePolicy = repo.getString("updatePolicy");

      repositories.add(new RemoteRepository.Builder(id, type, url)
          .setSnapshotPolicy(new RepositoryPolicy(false, null, null))
          .setReleasePolicy(new RepositoryPolicy(true, updatePolicy, null))
          .build()
      );
    }

    return repositories;

  }


  public static void main(String[] args) throws InterruptedException, IOException {
    DOMConfigurator.configure(WebServer.class.getResource("/com/liveramp/tangled/tangled.log4j.xml"));

    JSONObject config = new JSONObject(FileUtils.readFileToString(new File(args[0]), "utf-8"));

    WebServer server = new WebServer(config);
    Thread thread1 = new Thread(server);

    thread1.start();
    thread1.join();
  }
}