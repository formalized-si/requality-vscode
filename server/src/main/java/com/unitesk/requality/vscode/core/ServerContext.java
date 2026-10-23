package com.unitesk.requality.vscode.core;

import com.unitesk.requality.vscode.RequalityServer;

/** Static access to the running server for code ported from Eclipse singletons (Activator, PlatformUI). */
public final class ServerContext {
  private static volatile RequalityServer server;

  private ServerContext() {
  }

  public static void set(RequalityServer s) {
    server = s;
  }

  public static RequalityServer server() {
    return server;
  }

  /** The extension client, or null when no server runs (unit tests of pure UI code). */
  public static ClientApi client() {
    RequalityServer s = server;
    return s == null ? null : s.client();
  }
}
