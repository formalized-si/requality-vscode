package com.unitesk.requality.vscode;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.unitesk.requality.vscode.rpc.JsonRpcConnection;

/**
 * Requality server entry point. Standard input/output carry JSON-RPC; everything else the Requality core
 * prints goes to standard error, which the extension shows in its output channel.
 *
 * <p>Arguments: {@code --resources <dir>} (jstools/templates), {@code --storage <dir>} (server state, logs,
 * global caches).
 */
public final class Main {
  private Main() {
  }

  public static void main(String[] args) throws Exception {
    // Must happen before any Requality class is loaded: RLogger captures System.out in its static initializer.
    InputStream rpcIn = System.in;
    OutputStream rpcOut = new BufferedOutputStream(new FileOutputStream(java.io.FileDescriptor.out), 1 << 16);
    PrintStream stderr = new PrintStream(new FileOutputStream(java.io.FileDescriptor.err), true, StandardCharsets.UTF_8);
    System.setOut(stderr);
    System.setErr(stderr);
    // Headless AWT, except on Windows: there the registry fix of the plug-in (RegistryFixMessageDialog, shown on
    // Windows only) opens its .reg file with java.awt.Desktop, as in Eclipse.
    if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"))
      System.setProperty("java.awt.headless", "true");

    ServerOptions options = ServerOptions.parse(args);
    AtomicInteger threadId = new AtomicInteger();
    ExecutorService workers = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "requality-rpc-" + threadId.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
    JsonRpcConnection connection = new JsonRpcConnection(rpcIn, rpcOut, workers);
    RequalityServer server = new RequalityServer(connection, options);
    server.registerServices();
    try {
      connection.listen();
    } finally {
      server.shutdownQuietly();
      System.exit(0);
    }
  }

  /** Command-line options. */
  public static final class ServerOptions {
    public File resources;
    public File storage;

    static ServerOptions parse(String[] args) {
      ServerOptions o = new ServerOptions();
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "--resources":
            o.resources = new File(args[++i]);
            break;
          case "--storage":
            o.storage = new File(args[++i]);
            break;
          default:
            throw new IllegalArgumentException("Unknown argument " + args[i]);
        }
      }
      if (o.resources == null)
        o.resources = new File(System.getProperty("requality.resources", "resources"));
      if (o.storage == null)
        o.storage = new File(System.getProperty("user.dir"));
      return o;
    }
  }
}
