package com.unitesk.requality.vscode;

import java.io.File;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ClientApi;
import com.unitesk.requality.vscode.core.CoreBootstrap;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;
import com.unitesk.requality.vscode.rpc.RpcException;
import com.unitesk.requality.vscode.services.Services;

/**
 * Owns the RPC connection, the Requality core bootstrap and the workspace. Feature services register
 * their RPC methods in {@link Services}.
 */
public final class RequalityServer {
  public static final String VERSION = "1.5.0";

  private final JsonRpcConnection rpc;
  private final Main.ServerOptions options;
  private final ClientApi client;
  private final Workspace workspace;
  private volatile boolean initialized;

  public RequalityServer(JsonRpcConnection rpc, Main.ServerOptions options) {
    this.rpc = rpc;
    this.options = options;
    this.client = new ClientApi(rpc);
    this.workspace = new Workspace(this);
    com.unitesk.requality.vscode.core.ServerContext.set(this);
  }

  public JsonRpcConnection rpc() {
    return rpc;
  }

  public Main.ServerOptions options() {
    return options;
  }

  public ClientApi client() {
    return client;
  }

  public Workspace workspace() {
    return workspace;
  }

  public boolean isInitialized() {
    return initialized;
  }

  /** Throws unless {@code initialize} has completed. */
  public void requireInitialized() {
    if (!initialized)
      throw new RpcException(RpcException.NOT_READY, "Requality server is not initialized");
  }

  void registerServices() {
    rpc.onRequest("initialize", this::initialize);
    rpc.onRequest("shutdown", p -> {
      shutdownQuietly();
      return null;
    });
    rpc.onNotification("exit", p -> System.exit(0));
    Services.registerAll(this);
  }

  private synchronized Object initialize(JsonObject params) throws Exception {
    if (initialized)
      throw new RpcException(RpcException.REFUSED, "Already initialized");
    // the Requality Explorer as the Eclipse view (its SWT tree) or as the native tree of VS Code
    com.unitesk.requality.vscode.services.ExplorerService.setRendered(
        params.has("explorerView") && "eclipse".equals(params.get("explorerView").getAsString()));
    CoreBootstrap.start(this, params);
    List<String> folders = new ArrayList<>();
    if (params.has("workspaceFolders"))
      params.getAsJsonArray("workspaceFolders").forEach(e -> folders.add(e.getAsString()));
    initialized = true;
    File stateFile = workspaceStateFile(params, folders);
    workspace.setFolders(folders, stateFile);
    // The resource properties (Open With choices) are kept next to the workspace state; the editors of VS Code.
    com.unitesk.requality.vscode.ui.resources.ResourceProperties.setFile(stateFile);
    com.unitesk.requality.vscode.ui.workbench.EditorRegistry.registerVSCodeEditors(params.get("editors"));
    // Project > Build Automatically (a checked menu item)
    client().setContext("requality.autoBuilding", workspace.isAutoBuilding());
    com.unitesk.requality.vscode.services.ViewsService.createInitialLayout();
    JsonObject result = new JsonObject();
    result.addProperty("version", VERSION);
    result.addProperty("upstream", "unstable@e3f807bd4");
    return result;
  }

  /**
   * Where the workspace keeps its project metadata: in the VS Code storage of the workspace ({@code workspaceStorage}),
   * else in the server storage under a name derived from the workspace folders.
   */
  private File workspaceStateFile(JsonObject params, List<String> folders) {
    if (params.has("workspaceStorage") && !params.get("workspaceStorage").isJsonNull())
      return new File(params.get("workspaceStorage").getAsString(), "requality-workspace.json");
    if (options.storage == null)
      return null;
    List<String> sorted = new ArrayList<>(folders);
    java.util.Collections.sort(sorted);
    try {
      byte[] h = java.security.MessageDigest.getInstance("SHA-1")
          .digest(String.join("\n", sorted).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 8; i++)
        sb.append(String.format("%02x", h[i]));
      return new File(new File(options.storage, "workspaces"), sb + ".json");
    } catch (java.security.NoSuchAlgorithmException e) {
      return null;
    }
  }

  void shutdownQuietly() {
    if (!initialized)
      return;
    initialized = false;
    // No project load may run while the core shuts down.
    workspace.shutdown();
    try {
      CoreBootstrap.stop();
    } catch (Throwable t) {
      t.printStackTrace(System.err);
    }
  }
}
