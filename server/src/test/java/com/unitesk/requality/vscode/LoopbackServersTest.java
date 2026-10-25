package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.unitesk.requality.core.service.PortStatus;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.service.WSServer;

/**
 * The HTTP and WebSocket servers of the web editors accept connections from this computer only (VSCODE-PATCH): the
 * path token was their only access check, and they listened on every network interface.
 */
class LoopbackServersTest {
  static boolean accepts(InetAddress address, int port) {
    try (Socket s = new Socket()) {
      s.connect(new InetSocketAddress(address, port), 1000);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** The status line of a WebSocket handshake with the origin (null: none). */
  static String handshake(int port, String origin) throws Exception {
    try (Socket s = new Socket("127.0.0.1", port)) {
      s.setSoTimeout(10_000);
      String req = "GET / HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
          + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n"
          + (origin != null ? "Origin: " + origin + "\r\n" : "") + "\r\n";
      s.getOutputStream().write(req.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      s.getOutputStream().flush();
      return new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII))
          .readLine();
    }
  }

  @Test
  void theEditorServersListenOnLoopbackOnly() throws Exception {
    try (ServerHarness h = new ServerHarness()) {
      h.initialize(h.workspace);
      ResourcesProvider rp = ResourcesProvider.getOne();
      h.await("the document server", 60_000, v -> rp.getPortStatus() == PortStatus.STARTED);
      // a WebSocket server starts when an editor asks for one
      WSServer ws = WSServer.getOne("loopback-test", "test");
      h.await("the WebSocket server", 60_000, v -> ws.getPortStatus() == PortStatus.STARTED);
      List<Integer> ports = List.of(rp.getPort(), ws.getPort());
      // the IPv4 addresses of this computer that are not loopback
      List<InetAddress> others = new ArrayList<>();
      // a port no server listens on: an address that accepts it is a proxy tunnel (it accepts any port) and tells nothing
      int free;
      try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
        free = ss.getLocalPort();
      }
      for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
        if (ni.isUp() && !ni.isLoopback())
          for (InetAddress a : Collections.list(ni.getInetAddresses()))
            if (a instanceof Inet4Address && !accepts(a, free))
              others.add(a);
      for (int port : ports) {
        assertTrue(accepts(InetAddress.getByName("127.0.0.1"), port), "loopback " + port);
        for (InetAddress a : others)
          assertFalse(accepts(a, port), a + ":" + port);
      }
      assertEquals(PortStatus.STARTED, rp.getPortStatus());
      // a WebSocket server serves four editors: the fifth one gets a second server on another free port
      List<WSServer> servers = new ArrayList<>();
      for (int i = 0; i < 5; i++)
        servers.add(WSServer.getOne("loopback-five", "editor" + i));
      WSServer fifth = servers.get(4);
      h.await("the second WebSocket server", 60_000, v -> fifth.getPortStatus() == PortStatus.STARTED);
      assertTrue(fifth != servers.get(0) && fifth.getPort() != servers.get(0).getPort(), servers.toString());
      assertTrue(accepts(InetAddress.getByName("127.0.0.1"), fifth.getPort()));
      // the editor pages of this computer may connect, a page of another site may not
      assertTrue(handshake(ws.getPort(), "http://127.0.0.1:" + rp.getPort()).contains(" 101 "));
      assertTrue(handshake(ws.getPort(), null).contains(" 101 "));
      String foreign = handshake(ws.getPort(), "http://evil.example");
      assertTrue(foreign.contains(" 403 "), foreign);
    }
  }
}
