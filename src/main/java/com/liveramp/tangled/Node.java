package com.liveramp.tangled;

class Node {

  private final String artifactId;
  private final String groupId;
  private final String prettyName;

  private int size = 0;
  private int inheritSize = 0;
  private int totalDeps = 0;

  Node(String artifactId, String groupId, String prettyName) {
    this.artifactId = artifactId;
    this.groupId = groupId;
    this.prettyName = prettyName;
  }

  public String getFullName() {
    return groupId + "." + artifactId;
  }

  public void setSize(int size){
    this.size = size;
  }

  public void setInheritSize(int inheritSize){
    this.inheritSize = inheritSize;
  }

  public void setTotalDeps(int totalDeps){
    this.totalDeps = totalDeps;
  }

  public int getInheritSize() {
    return inheritSize;
  }

  public int getTotalDeps() {
    return totalDeps;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getPrettyName() {
    return prettyName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Node node = (Node)o;

    if (artifactId != null ? !artifactId.equals(node.artifactId) : node.artifactId != null) {
      return false;
    }
    if (groupId != null ? !groupId.equals(node.groupId) : node.groupId != null) {
      return false;
    }
    return prettyName != null ? prettyName.equals(node.prettyName) : node.prettyName == null;

  }

  @Override
  public int hashCode() {
    int result = artifactId != null ? artifactId.hashCode() : 0;
    result = 31 * result + (groupId != null ? groupId.hashCode() : 0);
    result = 31 * result + (prettyName != null ? prettyName.hashCode() : 0);
    return result;
  }

  public int getSize() {
    return size;
  }
}
