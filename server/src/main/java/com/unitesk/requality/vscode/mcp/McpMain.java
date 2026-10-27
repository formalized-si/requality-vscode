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
      + "session), revert_change (any change), compare_with_revision (all changes since a Git commit); ReqIF files: "
      + "import_reqif; project settings (attributes, enumerations, checker rules): get_project_settings, "
      + "change_project_settings. Make related changes in one apply_changes call with a summary: it is one step for the "
      + "review and for undo; check a large call with dry_run first. Address a node by its path (/Requirements/A/B), UUID "
      + "or unique id, and give the project name (list_projects) with each call. A project must be ready; a project in the "
      + "format of an older Requality needs convert_project first; a locked project is open in another process (a call "
      + "waits for it). Files to import and report folders must be in the folders of the server (the user may allow "
      + "another folder when the client asks). Read all result pages: pass next_from as from for read_document and "
      + "next_offset as offset for search_nodes, coverage lists, review_changes and compare_with_revision. Keep the "
      + "same filters and finish reading before changing the catalog or input file.";

  static final String USAGE = "Usage: requality-mcp [options] [<folder with Requality catalogs>...]\n"
      + "The default folder is the current one. Options:\n"
      + "  --allow <folder>    the agent may also import files from this folder and write reports into it\n"
      + "  --convert           convert catalogs in an old format when they open, without a backup (else the tool\n"
      + "                      convert_project converts one after a backup)\n"
      + "  --locale <language> the language of the messages and reports of Requality (default en; ru for Russian)\n"
      + "  --locks <folder>    the folder of the catalog locks (default ~/.requality/locks)\n"
      + "  --release-after <minutes>\n"
      + "                      release a catalog that no tool used for this time (the next call loads it again);\n"
      + "                      default: only when another session or VS Code waits for it (after 20 idle seconds)\n"
      + "  --storage <folder>  caches, logs, change histories and backups (default ~/.requality-mcp)\n"
      + "  --version           print the version\n"
      + "  --help              print this text\n";

  /** How long a catalog another process waits for stays unused before the server gives it up. */
  static final long HANDOFF_MS = Long.getLong("requality.handoff", 20_000);

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
    // the checkers run when a tool asks for them (trace_coverage refresh), not in the background
    System.setProperty("requality.noBackgroundChecks", "true");
    // the JavaScript of the catalogs (formulas, checker rules) uses no Java class that reaches files or programs
    ScriptSandbox.install();

    File resources = null;
    File storage = new File(System.getProperty("user.home"), ".requality-mcp");
    List<File> folders = new ArrayList<>();
    List<File> allowed = new ArrayList<>();
    boolean convert = false;
    long releaseAfter = 0;
    String locale = "en";
    // the folder where relative arguments start: the launcher of Windows runs Java in its own folder and gives the
    // current folder of the caller in REQUALITY_CWD
    String cwd = System.getenv("REQUALITY_CWD");
    File base = cwd != null && !cwd.isEmpty() ? new File(cwd) : new File(System.getProperty("user.dir"));
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
            // the folder of the package, relative to the folder of Java (the launcher gives it)
            resources = new File(value(args, ++i, "--resources")).getAbsoluteFile();
            break;
          case "--storage":
            storage = resolve(base, value(args, ++i, "--storage"));
            break;
          case "--allow":
            allowed.add(folder(resolve(base, value(args, ++i, "--allow"))));
            break;
          case "--locale":
            // the language of the messages and reports of the core (default English: the agent reads them)
            locale = value(args, ++i, "--locale");
            break;
          case "--locks":
            // the folder of the catalog locks (default ~/.requality/locks), read when the workspace starts
            File locks = resolve(base, value(args, ++i, "--locks"));
            locks.mkdirs();
            System.setProperty("requality.locks", folder(locks).getPath());
            break;
          case "--convert":
            convert = true;
            break;
          case "--release-after":
            try {
              releaseAfter = Long.parseLong(value(args, ++i, "--release-after")) * 60_000L;
            } catch (NumberFormatException e) {
              throw new IllegalArgumentException("--release-after needs a number of minutes");
            }
            if (releaseAfter <= 0)
              throw new IllegalArgumentException("--release-after needs a number of minutes above 0");
            break;
          default:
            if (args[i].startsWith("--"))
              throw new IllegalArgumentException("Unknown option " + args[i]);
            folders.add(folder(resolve(base, args[i])));
        }
      }
    } catch (IllegalArgumentException e) {
      stderr.println("requality-mcp: " + e.getMessage());
      stderr.print(USAGE);
      System.exit(2);
    }
    // before a class of the core reads it (Localization)
    java.util.Locale.setDefault(java.util.Locale.forLanguageTag(locale));
    if (resources == null)
      resources = defaultResources();
    if (folders.isEmpty()) {
      // the agent may import and write in the folders: not in the whole file system or home folder by accident
      File home = new File(System.getProperty("user.home"));
      if (same(base, home) || base.getAbsoluteFile().toPath().getParent() == null) {
        stderr.println("requality-mcp: give the folder that contains your Requality catalogs (the current folder is " + base
            + ")");
        stderr.print(USAGE);
        System.exit(2);
      }
      folders.add(base.getAbsoluteFile());
    }
    storage = storage.getAbsoluteFile();
    storage.mkdirs();
    // the histories of the changes and the backups keep the content of documents: only the user reads them (also a
    // storage folder that an earlier version or the user made)
    History.ownerOnly(storage);
    for (String sub : new String[] { "journals", "backups" })
      if (new File(storage, sub).isDirectory())
        History.ownerOnly(new File(storage, sub));
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
      Core core = RequalityTools.register(mcp, headless, new Policy(roots));
      // Idle catalogs are released: the ones no tool used for --release-after, and the ones another process (VS Code,
      // another session) waits for that no tool used for HANDOFF_MS. A catalog is released only between calls.
      long after = releaseAfter;
      java.util.concurrent.ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "requality-mcp-release");
        t.setDaemon(true);
        return t;
      });
      timer.scheduleWithFixedDelay(() -> {
        try {
          if (!core.idle(after, HANDOFF_MS).isEmpty())
            mcp.exclusive(() -> core.releaseIdle(after, HANDOFF_MS));
        } catch (RuntimeException e) {
          e.printStackTrace(stderr);
        }
      }, 2, 2, java.util.concurrent.TimeUnit.SECONDS);
      // a client that ends the server (SIGTERM) lets the running calls finish and the catalogs close
      HeadlessClient h = headless;
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        mcp.stop();
        try {
          if (!mcp.awaitIdle(25_000))
            stderr.println("requality-mcp: tool calls still run; the server stops (a change in progress is not saved)");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        h.close();
      }, "requality-mcp-shutdown"));
      mcp.listen();
      // the input ended: the calls that run end first (a large import or update takes minutes), their results are sent
      mcp.stop();
      if (!mcp.awaitIdle(10 * 60_000))
        stderr.println("requality-mcp: tool calls still run 10 minutes after the input ended; the server stops");
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

  /** An absolute file: a relative path starts in the folder {@code base}. */
  static File resolve(File base, String path) {
    File f = new File(path);
    return (f.isAbsolute() ? f : new File(base, path)).getAbsoluteFile();
  }

  private static File folder(File f) {
    if (!f.isDirectory())
      throw new IllegalArgumentException("No folder " + f);
    return f;
  }

  private static boolean same(File a, File b) {
    try {
      return a.getCanonicalFile().equals(b.getCanonicalFile());
    } catch (java.io.IOException e) {
      return a.getAbsoluteFile().equals(b.getAbsoluteFile());
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
