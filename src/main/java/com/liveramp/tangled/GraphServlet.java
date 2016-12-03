package com.liveramp.tangled;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.takari.aether.localrepo.TakariLocalRepositoryManagerFactory;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.liveramp.tangled.aether.ConsoleRepositoryListener;
import com.liveramp.tangled.aether.ConsoleTransferListener;

public class GraphServlet implements JSONServlet.Processor {
  private static final Logger LOG = LoggerFactory.getLogger(GraphServlet.class);

  private final RepositorySystem system;
  private final DefaultRepositorySystemSession session;
  private final List<RemoteRepository> repositories;

  private final String prefix;

  public GraphServlet(List<RemoteRepository> remoteRepositories,
                      String localRepository,
                      String truncatePrefix) throws NoLocalRepositoryManagerException {
    system = newRepositorySystem();
    session = newRepositorySystemSession(localRepository, system);
    repositories = remoteRepositories;
    prefix = truncatePrefix;

    session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
    session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
  }

  @Override
  public JSONObject getData(Map<String, String> parameters) throws Exception {

    try {

      String group = parameters.get("groupId");
      String artifactId = parameters.get("artifactId");
      String version = parameters.get("version");
      String scope = parameters.get("scope");

      LOG.info("Getting request: " + parameters);

      Artifact artifact = new DefaultArtifact(group + ":" + artifactId + ":" + version);

      CollectRequest collectRequest = new CollectRequest();
      collectRequest.setRoot(new Dependency(artifact, scope));
      collectRequest.setRepositories(repositories);

      CollectResult collectResult = system.collectDependencies(session, collectRequest);

      DependencyRequest dependencyRequest = new DependencyRequest();
      dependencyRequest.setCollectRequest(collectRequest);


      final Set<Artifact> toCollect = Sets.newHashSet();

      collectResult.getRoot().accept(new DependencyVisitor() {
        @Override
        public boolean visitEnter(DependencyNode node) {
          toCollect.add(node.getArtifact());
          return true;
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
          return true;
        }
      });

      Map<String, Node> allDeps = Maps.newHashMap();
      DirectedGraph<String, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);

      collectResult.getRoot().accept(new DependencyEdgeGenerator(
          allDeps,
          graph
      ));


      List<ArtifactRequest> requests = Lists.newArrayList();
      for (Artifact artifact1 : toCollect) {
        requests.add(new ArtifactRequest(artifact1, repositories, scope));
      }

      List<ArtifactResult> artifactResult = system.resolveArtifacts(session, requests);

      for (ArtifactResult result : artifactResult) {
        Artifact resArt = result.getArtifact();
        allDeps.get(getFullName(resArt)).setSize((int)resArt.getFile().length());
      }

      LOG.info("Generated graph with " + graph.vertexSet().size() + " nodes and " + graph.edgeSet().size() + " edges");

      removeRedundantEdges(graph);
      setInheritedValues(allDeps, graph);

      LOG.info("Pruned graph to " + graph.vertexSet().size() + " nodes and " + graph.edgeSet().size() + " edges");

      JSONArray edges = new JSONArray();
      for (DefaultEdge edge : graph.edgeSet()) {
        edges.put(new JSONObject()
            .put("source", graph.getEdgeTarget(edge))
            .put("target", graph.getEdgeSource(edge)));
      }

      JSONArray array = new JSONArray();
      for (String name : getNodesInTraversalOrder(graph)) {
        Node node = allDeps.get(name);

        array.put(new JSONObject()
            .put("artifactId", node.getArtifactId())
            .put("groupId", node.getGroupId())
            .put("prettyName", node.getPrettyName())
            .put("fullName", node.getFullName())
            .put("inheritSize", node.getInheritSize())
            .put("totalDeps", node.getTotalDeps())
            .put("size", node.getSize())
        );
      }

      return new JSONObject()
          .put("artifacts", array)
          .put("edges", edges);


    }catch (ArtifactResolutionException e){
      LOG.info("Error resolving artifact ", e);

      return new JSONObject()
          .put("error" , true);
    }

  }


  private List<String> getNodesInTraversalOrder(DirectedGraph<String, DefaultEdge> graph) {
    TopologicalOrderIterator<String, DefaultEdge> iterator = new TopologicalOrderIterator<>(new EdgeReversedGraph<>(graph));

    List<String> nodes = Lists.newArrayList();
    while (iterator.hasNext()) {
      String node = iterator.next();
      nodes.add(node);
    }
    return nodes;
  }

  private void removeRedundantEdges(DirectedGraph<String, DefaultEdge> graph) {
    for (String project : graph.vertexSet()) {
      Set<String> firstDegDeps = new HashSet<>();
      Set<String> secondPlusDegDeps = new HashSet<>();
      for (DefaultEdge edge : graph.outgoingEdgesOf(project)) {
        String depStep = graph.getEdgeTarget(edge);
        firstDegDeps.add(depStep);
        getDepsRecursive(depStep, secondPlusDegDeps, graph);
      }

      for (String firstDegDep : firstDegDeps) {
        if (secondPlusDegDeps.contains(firstDegDep)) {
          graph.removeAllEdges(project, firstDegDep);
        }
      }
    }
  }

  private void getDepsRecursive(String step, Set<String> deps, DirectedGraph<String, DefaultEdge> graph) {
    for (DefaultEdge edge : graph.outgoingEdgesOf(step)) {
      String s = graph.getEdgeTarget(edge);
      boolean isNew = deps.add(s);
      if (isNew) {
        getDepsRecursive(s, deps, graph);
      }
    }
  }

  private void setInheritedValues(Map<String, Node> nodes, DirectedGraph<String, DefaultEdge> graph) {

    for (Map.Entry<String, Node> entry : nodes.entrySet()) {
      String name = entry.getKey();
      Node root = entry.getValue();

      //  get all dependencies
      Queue<String> queue = Lists.newLinkedList(Arrays.asList(name));

      Map<String, Node> deps = Maps.newHashMap();

      while (!queue.isEmpty()) {
        String explore = queue.poll();
        if (!deps.containsKey(explore)) {
          deps.put(explore, nodes.get(explore));
          queue.addAll(incomingSources(graph, explore));
        }
      }

      int sumSize = 0;
      for (Node node : deps.values()) {
        sumSize += node.getSize();
      }

      root.setInheritSize(sumSize);
      root.setTotalDeps(deps.size());

    }

  }

  private static Set<String> incomingSources(DirectedGraph<String, DefaultEdge> graph, String node) {

    Set<DefaultEdge> deps = graph.outgoingEdgesOf(node);
    Set<String> incoming = Sets.newHashSet();

    for (DefaultEdge dep : deps) {
      incoming.add(graph.getEdgeTarget(dep));
    }

    return incoming;
  }

  private String getPrettyName(Artifact artifact) {

    String group = artifact.getGroupId();
    String artifactId = artifact.getArtifactId();

    if (group.startsWith(prefix)) {
      return artifactId;
    }

    //  TODO this is garbage
    String[] packages = group.split("\\.");
    return packages[packages.length - 1] + "." + artifactId;
  }


  private Node getNode(Artifact artifact) {
    return new Node(artifact.getArtifactId(), artifact.getGroupId(), getPrettyName(artifact));
  }

  private static String getFullName(Artifact artifact) {
    return artifact.getGroupId() + "." + artifact.getArtifactId();
  }


  private class DependencyEdgeGenerator implements DependencyVisitor {

    private final Map<String, Node> nodes;
    private final DirectedGraph<String, DefaultEdge> graph;

    private DependencyEdgeGenerator(Map<String, Node> nodesByFullName, DirectedGraph<String, DefaultEdge> graph) {
      this.nodes = nodesByFullName;
      this.graph = graph;
    }

    @Override
    public boolean visitEnter(DependencyNode node) {

      Artifact artifact = node.getArtifact();

      String fullName = getFullName(artifact);
      addNode(fullName, getNode(artifact));

      for (DependencyNode dependency : node.getChildren()) {
        String childName = getFullName(dependency.getArtifact());
        addNode(childName, getNode(dependency.getArtifact()));

        graph.addEdge(fullName, childName);
      }

      return true;
    }

    private void addNode(String name, Node node) {
      if (!nodes.containsKey(name)) {
        graph.addVertex(name);
        nodes.put(name, node);
      }
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
      return true;
    }
  }

  public static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

    locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
      @Override
      public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
        exception.printStackTrace();
      }
    });

    return locator.getService(RepositorySystem.class);
  }

  public static DefaultRepositorySystemSession newRepositorySystemSession(String localRepository, org.eclipse.aether.RepositorySystem system) throws NoLocalRepositoryManagerException {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

    LocalRepository localRepo = new LocalRepository(localRepository);
    session.setTransferListener(new ConsoleTransferListener());
    session.setRepositoryListener(new ConsoleRepositoryListener());
    session.setLocalRepositoryManager(new TakariLocalRepositoryManagerFactory().newInstance(session, localRepo));

    return session;
  }

}
