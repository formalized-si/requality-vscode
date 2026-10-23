package com.unitesk.requality.vscode.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.rpc.RpcException;

/**
 * Node references exchanged with the extension: {@code "<project local name>::<node uuid>"}, the same pair the
 * upstream resources servlet uses in editor URLs.
 */
public final class Nodes {
  public static final String SEP = "::";

  private Nodes() {
  }

  public static String id(TreeNode node) {
    return node.getTreeDB().getSemiLocalName() + SEP + node.getSUUId();
  }

  public static String projectOf(String nodeId) {
    int i = nodeId.lastIndexOf(SEP);
    if (i < 0)
      throw RpcException.invalidParams("Malformed node id " + nodeId);
    return nodeId.substring(0, i);
  }

  /** Finds a loaded project by its local name. */
  public static TreeDB findDB(String localName) {
    for (TreeDB db : ResManager.getManager().getTreeDBs()) {
      if (db == null || db.isDisposed())
        continue;
      if (localName.equals(db.getSemiLocalName()) || localName.equals(db.getLocalName()))
        return db;
    }
    return null;
  }

  public static TreeDB requireDB(String localName) {
    TreeDB db = findDB(localName);
    if (db == null)
      throw RpcException.notFound("Project " + localName + " is not loaded");
    return db;
  }

  /** True when the project is loaded and not being reloaded. */
  public static boolean isReady(TreeDB db) {
    TreeStateManager tsm = db.getTracker().getTreeStateManager();
    TreeState ts = tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db));
    return TreeState.readyState(ts) || ts == TreeState.MODIFYING;
  }

  public static TreeNode find(String nodeId) {
    int i = nodeId.lastIndexOf(SEP);
    if (i < 0)
      return null;
    TreeDB db = findDB(nodeId.substring(0, i));
    if (db == null)
      return null;
    UUID uuid;
    try {
      uuid = UUID.fromString(nodeId.substring(i + SEP.length()));
    } catch (IllegalArgumentException e) {
      return null;
    }
    TreeNode node = db.getNode(uuid);
    return node == null || node.isDisposed() ? null : node;
  }

  public static TreeNode require(String nodeId) {
    TreeNode node = find(nodeId);
    if (node == null)
      throw RpcException.notFound("Node " + nodeId + " does not exist");
    return node;
  }

  public static TreeNode require(JsonObject params, String key) {
    if (!params.has(key) || params.get(key).isJsonNull())
      throw RpcException.invalidParams("Missing parameter " + key);
    return require(params.get(key).getAsString());
  }

  public static List<TreeNode> requireAll(JsonObject params, String key) {
    List<TreeNode> nodes = new ArrayList<>();
    JsonElement e = params.get(key);
    if (e == null || !e.isJsonArray())
      throw RpcException.invalidParams("Missing array parameter " + key);
    for (JsonElement x : (JsonArray) e)
      nodes.add(require(x.getAsString()));
    return nodes;
  }

  public static String string(JsonObject params, String key) {
    JsonElement e = params.get(key);
    if (e == null || e.isJsonNull())
      throw RpcException.invalidParams("Missing parameter " + key);
    return e.getAsString();
  }

  public static String optString(JsonObject params, String key, String def) {
    JsonElement e = params.get(key);
    return e == null || e.isJsonNull() ? def : e.getAsString();
  }

  public static boolean optBool(JsonObject params, String key, boolean def) {
    JsonElement e = params.get(key);
    return e == null || e.isJsonNull() ? def : e.getAsBoolean();
  }

  public static int optInt(JsonObject params, String key, int def) {
    JsonElement e = params.get(key);
    return e == null || e.isJsonNull() ? def : e.getAsInt();
  }
}
