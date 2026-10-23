package com.unitesk.requality.core.service;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;

public class PortUtils {
  public static boolean testPortIsFree(int port){
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
