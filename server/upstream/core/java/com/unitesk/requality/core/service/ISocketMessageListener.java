package com.unitesk.requality.core.service;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;

public interface ISocketMessageListener {
	public boolean handleMessage(WSSocket ws, Session session, String op, String win, JSONObject message);
	public String getId();
}
