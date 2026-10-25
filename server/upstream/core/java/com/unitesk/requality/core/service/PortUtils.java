package com.unitesk.requality.core.service;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;

public class PortUtils {
  /**
   * VSCODE-PATCH: the servers listen on 127.0.0.1. A wildcard bind with SO_REUSEADDR (the default of ServerSocket on
   * macOS) succeeds next to a loopback listener, so a port of a running server looked free and the next server could
   * not bind it. A port is free when 127.0.0.1 is free (without SO_REUSEADDR) and the wildcard test passes too.
   */
  public static boolean testPortIsFree(int port){
    try (ServerSocket loopback = new ServerSocket()) {
      loopback.setReuseAddress(false);
      loopback.bind(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port));
    } catch (IOException e) {
      return false;
    }
    return testWildcardPortIsFree(port);
  }

  private static boolean testWildcardPortIsFree(int port){
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      try {
        if(serverSocket==null || serverSocket.getLocalPort() != port)
          return false;
        (new ServerSocket(port)).close();
        return false;
      } catch(IOException ex2) {
        return true;
      }
    } catch (IOException e) {
      if(e instanceof BindException)
        return false;
    }
    return true;
  }
}
