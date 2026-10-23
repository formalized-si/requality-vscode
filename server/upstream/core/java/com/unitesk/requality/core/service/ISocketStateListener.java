package com.unitesk.requality.core.service;

import org.eclipse.jetty.websocket.api.Session;

public interface ISocketStateListener {
  public void handleConnected(WSSocket ws, Session session, String connectedId);
  public void handleClosed(WSSocket ws, Session session, boolean stopAfter);
}
