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
 * <p>Arguments: see {@link #USAGE}. The folders contain the Requality projects (default: the current directory); the
 * resources default to {@code ../resources} next to the server jar, the storage (caches, logs, journals, backups) to
 * {@code ~/.requality-mcp}.
 */
public final class McpMain {
  private McpMain() {
  }

  static final String INSTRUCTIONS = "Requality is a requirements management tool. A project (catalog) has a tree of "
      + "requirements (/Requirements), specification documents (/Documents) whose marked text are the locations of the "
      + "requirements, test purposes under the requirements, and reports. Jobs and their tools: requirements from a "
      + "specification: import_document, read_document (filter uncovered), apply_changes (create, then locate with the exact "
      + "sentence as quote); a new version of a specification: update_document_version first (its result is the list of what "
      + "to fix), then apply_changes; traceability: trace_coverage; review of your changes: review_changes, undo (this "
      + "session), revert_change (any change). Make related changes in one apply_changes call with a summary: it is one step "
      + "for the review and for undo. Address a node by its path (/Requirements/A/B) or UUID and give the project name "
      + "(list_projects) with each call. A project must be ready; a project in the format of an older Requality needs "
      + "convert_project first. Files to import and report folders must be in the folders of the server.";

  static final String USAGE = "Usage: requality-mcp [options] [<folder with Requality catalogs>...]\n"
      + "The default folder is the current one. Options:\n"
      + "  --allow <folder>    the agent may also import files from this folder and write reports into it\n"
      + "  --convert           convert catalogs in an old format when they open, without a backup (else the tool\n"
      + "                      convert_project converts one after a backup)\n"
      + "  --storage <folder>  caches, logs, change histories and backups (default ~/.requality-mcp)\n"
      + "  --version           print the version\n"
      + "  --help              print this text\n";

  public static void main(String[] args) {
    // Before any Requality class is loaded: RLogger captures System.out in its static initializer.
    InputStream mcpIn = System.in;
    OutputStream mcpOut = new BufferedOutputStream(new FileOutputStream(java.io.FileDescriptor.out), 1 << 16);
    PrintStream stderr = new PrintStream(new FileOutputStream(java.io.FileDescriptor.err), true, StandardCharsets.UTF_8);
    System.setOut(stderr);
    System.setErr(stderr);
    if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"))
      System.setProperty("java.awt.headless", "true");
    // no HTTP or WebSocket server for the web editors: nothing shows them (DefaultAPISource, VSCODE-PATCH)
    System.setProperty("requality.noServers", "true");

    File resources = null;
    File storage = new File(System.getProperty("user.home"), ".requality-mcp");
    List<File> folders = new ArrayList<>();
    List<File> allowed = new ArrayList<>();
    boolean convert = false;
    try {
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "--version":
            // no MCP session: the answer goes to standard output
            stdout(mcpOut, "requality-mcp " + RequalityServer.VERSION + "\n");
            System.exit(0);
            break;
          case "--help":
          case "-h":
            stdout(mcpOut, USAGE);
            System.exit(0);
            break;
          case "--resources":
            resources = new File(value(args, ++i, "--resources"));
            break;
          case "--storage":
            storage = new File(value(args, ++i, "--storage"));
            break;
          case "--allow":
            allowed.add(new File(value(args, ++i, "--allow")).getAbsoluteFile());
            break;
          case "--convert":
            convert = true;
            break;
          default:
            if (args[i].startsWith("--"))
              throw new IllegalArgumentException("Unknown option " + args[i]);
            File f = new File(args[i]).getAbsoluteFile();
            if (!f.isDirectory())
              throw new IllegalArgumentException("No folder " + f);
            folders.add(f);
        }
      }
    } catch (IllegalArgumentException e) {
      stderr.println("requality-mcp: " + e.getMessage());
      stderr.print(USAGE);
      System.exit(2);
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
    List<File> roots = new ArrayList<>(folders);
    roots.addAll(allowed);

    AtomicInteger threadId = new AtomicInteger();
    ExecutorService workers = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "requality-mcp-" + threadId.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
    int code = 0;
    HeadlessClient headless = null;
    try {
      headless = new HeadlessClient(resources, storage, convert);
      headless.initialize(folders);
      McpServer mcp = new McpServer(mcpIn, mcpOut, workers, RequalityServer.VERSION, INSTRUCTIONS);
      RequalityTools.register(mcp, headless, new Policy(roots));
      mcp.listen();
      // the input ended: the calls that run end first (at most a minute), their results are sent
      if (!mcp.awaitIdle(60_000))
        stderr.println("requality-mcp: tool calls still run after the input ended; the server stops");
    } catch (Throwable t) {
      stderr.println("requality-mcp: the server failed");
      t.printStackTrace(stderr);
      code = 1;
    } finally {
      if (headless != null)
        headless.close();
      System.exit(code);
    }
  }

  private static void stdout(OutputStream out, String text) {
    try {
      out.write(text.getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (java.io.IOException e) {
      // no standard output
    }
  }

  private static String value(String[] args, int i, String option) {
    if (i >= args.length)
      throw new IllegalArgumentException(option + " needs a value");
    return args[i];
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
