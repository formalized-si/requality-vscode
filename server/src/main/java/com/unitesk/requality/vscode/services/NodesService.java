package com.unitesk.requality.vscode.services;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;

/** Generic node queries: node lists for selection dialogs and the target of the Open command. */
public final class NodesService implements Services.Service {
  @Override
  public void register(RequalityServer server) {
    server.rpc().onRequest("nodes/list", p -> {
      server.requireInitialized();
      TreeDB db = Nodes.requireDB(Nodes.string(p, "project"));
      Set<String> types = null;
      if (p.has("types") && p.get("types").isJsonArray()) {
        types = new HashSet<>();
        for (com.google.gson.JsonElement e : p.getAsJsonArray("types"))
          types.add(e.getAsString());
      }
      JsonArray out = new JsonArray();
      collect(db.getRootNode(), types, out);
      return out;
    });
    server.rpc().onRequest("nodes/item", p -> {
      server.requireInitialized();
      TreeNode n = Nodes.find(Nodes.string(p, "node"));
      return n == null ? null : ExplorerService.item(n);
    });
  }

  private static void collect(TreeNode node, Set<String> types, JsonArray out) {
    for (TreeNode c : node.getSortedShownChildren()) {
      if (c == null)
        continue;
      if (types == null || types.contains(c.getType())) {
        JsonObject o = new JsonObject();
        o.addProperty("id", Nodes.id(c));
        o.addProperty("label", ExplorerService.label(c));
        o.addProperty("qid", c.getQualifiedId());
        o.addProperty("type", c.getType());
        out.add(o);
      }
      collect(c, types, out);
    }
  }
}
