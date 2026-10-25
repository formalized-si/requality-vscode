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
    }
  }
}
