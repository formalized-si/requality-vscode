package com.unitesk.requality.vscode.ui.runtime;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Icons;

/** {@code org.osgi.framework.Bundle} of the Requality plug-in: its entries are the server's resource files. */
public final class Bundle {
  public static final Bundle REQUALITY = new Bundle();

  private Bundle() {
  }

  public String getSymbolicName() {
    return "com.unitesk.requality";
  }

  /** The URL of a file of the plug-in, or null when there is none. */
  /** {@code Bundle.getResource(String)}: the resources and the plug-in entries are the same folder here. */
  public URL getResource(String path) {
    return getEntry(path);
  }

  public URL getEntry(String path) {
    File f = Icons.resource(path.startsWith("/") ? path.substring(1) : path);
    if (!f.exists())
      return null;
    try {
      return f.toURI().toURL();
    } catch (MalformedURLException e) {
      return null;
    }
  }

  /** {@code Bundle.findEntries(path, filePattern, recurse)}: the single entry at the path, or null when there is none. */
  public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
    URL url = getEntry(path);
    return url == null ? null : Collections.enumeration(Collections.singletonList(url));
  }

  /** The manifest headers (Bundle-Version is the server version). */
  public Dictionary<String, String> getHeaders() {
    Hashtable<String, String> h = new Hashtable<>();
    h.put("Bundle-SymbolicName", getSymbolicName());
    h.put("Bundle-Version", RequalityServer.VERSION);
    return h;
  }
}
