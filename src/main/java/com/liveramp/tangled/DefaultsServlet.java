package com.liveramp.tangled;

import java.util.Map;

import org.jgrapht.graph.DefaultEdge;
import org.json.JSONObject;

public class DefaultsServlet implements JSONServlet.Processor {

  private final String defaultGroup;
  private final String defaultArtifact;

  public DefaultsServlet(String defaultGroup, String defaultArtifact){
    this.defaultGroup = defaultGroup;
    this.defaultArtifact = defaultArtifact;
  }

  @Override
  public JSONObject getData(Map<String, String> parameters) throws Exception {
    return new JSONObject()
        .put("groupId", defaultGroup)
        .put("artifactId", defaultArtifact);
  }
}
