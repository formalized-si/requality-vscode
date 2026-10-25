package com.unitesk.requality.vscode.mcp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.unitesk.requality.vscode.RequalityServer;

/**
 * Headless Requality with a Model Context Protocol server on standard input/output: an AI agent works with the
 * Requality projects of the given folders without VS Code. Everything else the core prints goes to standard error.
 *
 * <p>Arguments: {@code [--resources <dir>] [--storage <dir>] [<folder>...]}. The folders contain the Requality
 * projects (default: the current directory); the resources default to {@code ../resources} next to the server jar,
 * the storage (caches, logs, workspace state) to {@code ~/.requality-mcp}.
 */
public final class McpMain {
  private McpMain() {
  }

  static final String INSTRUCTIONS = "Requality is a requirements management tool. A project (catalog) is a tree of nodes: "
      + "requirements, documents with requirement locations, test purposes, folders. Address a node by its UUID or by "
      + "its path in a project (\"/Requirements/A/B\", the qualified id) together with the project name. "
      + "Call list_projects first; a project must be ready before its nodes can be read or changed.";

  public static void main(String[] args) throws Exception {
    // Before any Requality class is loaded: RLogger captures System.out in its static initializer.
    InputStream mcpIn = System.in;
    OutputStream mcpOut = new BufferedOutputStream(new FileOutputStream(java.io.FileDescriptor.out), 1 << 16);
    PrintStream stderr = new PrintStream(new FileOutputStream(java.io.FileDescriptor.err), true, StandardCharsets.UTF_8);
    System.setOut(stderr);
    System.setErr(stderr);
    if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"))
      System.setProperty("java.awt.headless", "true");

    File resources = null;
    File storage = new File(System.getProperty("user.home"), ".requality-mcp");
    List<File> folders = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--resources":
          resources = new File(args[++i]);
          break;
        case "--storage":
          storage = new File(args[++i]);
          break;
        default:
          if (args[i].startsWith("--"))
            throw new IllegalArgumentException("Unknown argument " + args[i]);
          folders.add(new File(args[i]).getAbsoluteFile());
      }
    }
    if (resources == null)
      resources = defaultResources();
    if (folders.isEmpty())
      folders.add(new File(System.getProperty("user.dir")).getAbsoluteFile());
    storage = storage.getAbsoluteFile();
    storage.mkdirs();
    // The core writes its logs to <user.dir>/logs (RLogger reads the property when it is loaded): in the storage, as the
    // extension starts the server in its storage folder. The folders and paths above are absolute already.
    System.setProperty("user.dir", storage.getPath());

    AtomicInteger threadId = new AtomicInteger();
    ExecutorService workers = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "requality-mcp-" + threadId.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
    HeadlessClient headless = new HeadlessClient(resources, storage);
    try {
      headless.initialize(folders);
      McpServer mcp = new McpServer(mcpIn, mcpOut, workers, RequalityServer.VERSION, INSTRUCTIONS);
      RequalityTools.register(mcp, headless);
      mcp.listen();
      // the input ended: the calls that run end first, their results are sent
      workers.shutdown();
      workers.awaitTermination(5, java.util.concurrent.TimeUnit.MINUTES);
    } finally {
      headless.close();
      System.exit(0);
    }
  }

  /** {@code resources} next to the {@code lib} folder of the server jar (dist/server/lib/requality-server.jar). */
  static File defaultResources() {
    try {
      File jar = new File(McpMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return new File(jar.getParentFile().getParentFile(), "resources");
    } catch (Exception e) {
      return new File(System.getProperty("requality.resources", "resources"));
    }
  }
}
