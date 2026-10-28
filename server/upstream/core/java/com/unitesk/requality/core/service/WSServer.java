package com.unitesk.requality.core.service;

import java.io.IOException;
import java.net.BindException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.tool.CoreUtils;

public class WSServer extends WebSocketHandler implements IPortStatusProvider{
  private final static int STOP_TIMEOUT = 2000;
  static final int IDLE_TIMEOUT = 200000;
  static final int ATTEMPTS = 50;
  static final int TEXT_BUFFER = 524288;
  private static ConcurrentHashMap<String, ConcurrentHashMap<WSServer, List<String>>> servers = new ConcurrentHashMap<>();
  private static AtomicBoolean stopped = new AtomicBoolean(false);
  private Thread serverThread = null;
  private List<WSSocket> getActiveSocketsGroup(String group) {
    List<WSSocket> list = activeSockets.get(group);
    if(list == null) {
      list = new CopyOnWriteArrayList<>();
      activeSockets.put(group, list);
    }
    return list;
  }
  private static Map<String, Integer> initPortValues;
  private static final long serialVersionUID = -2699302503236740294L;
  protected static final String NOT_INITED_GROUP = "<NOT_INITED_GROUP>";
  ConcurrentLinkedQueue<Pair<ISocketMessageListener,ISocketStateListener>> messageAndStateStorage 
          = new ConcurrentLinkedQueue<>();
  public Server server;
  private ServerConnector connector;
  private PortStatus portState = PortStatus.NOT_INITED;
  private String name;
  private Map<String,List<WSSocket>> activeSockets = new ConcurrentHashMap<>();
  //public Map<String,String> session = new HashMap<>();
 
  private int port = -1;//MAX_PORT++;

  @Override
  public int getPort() {
    return port;
  }
  @Override
  public String getPortUserInfo() {
    return "Websocket server "+name;
  }

  @Override
  public PortStatus getPortStatus() {
    return portState;
  }
  
  public static Map<String,ServiceStatus> getStatuses(){
    Map<String, ServiceStatus> ret = new HashMap<>();
    for(Entry<String, ConcurrentHashMap<WSServer, List<String>>> en:servers.entrySet()) {
      int i = 0;
      for(Entry<WSServer, List<String>> en2:en.getValue().entrySet()) {
        WSServer wss = en2.getKey();
        ret.put(en.getKey()+"["+(i++)+"]:"+Arrays.toString(en2.getValue().toArray(new String[en2.getValue().size()])), new ServiceStatus(Integer.toString(wss.getPort()),
            wss.getState(),wss.getPortState()));
      }
    }
    return ret;
  }
  public static Map<String,PortStatus> getPortStatuses(){
    Map<String, PortStatus> ret = new HashMap<>();
    for(Entry<String, ConcurrentHashMap<WSServer, List<String>>> en:servers.entrySet()) {
      int i = 0;
      for(Entry<WSServer, List<String>> en2:en.getValue().entrySet())
        ret.put(en.getKey()+"["+(i++)+"]:"+Arrays.toString(en2.getValue().toArray(new String[en2.getValue().size()])), en2.getKey().getPortState());
    }
    return ret;
  }
  public static void startSockets(Map<String, Integer> ports){
    initPortValues = ports;
//  for(Entry<String, Integer> s:ports.entrySet()) {
//      
//      int port = s.getValue();
//      WSServer ws = new WSServer(s.getKey(), port);
//      ws.init();
////        requests.set(reqs-1);        
//  }
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        stopSockets();
      }
    });
    Runtime.getRuntime().addShutdownHook(t);
  }

  public void sendCloseMessage(Collection<WSSocket> sess2, String message, boolean removeGroupData) {
    if(sess2 == null)
      return;
    for(WSSocket s: sess2)
      sendCloseMessage(s, message);
  }
  protected void removeSocketData(WSSocket s) {
    String g = s.getRelatedSource();
    if(g == null) 
      g = NOT_INITED_GROUP;
    List<WSSocket> socks = activeSockets.get(g);
    if(socks!=null){
      socks.remove(s);
      if(socks.size()==0)
        activeSockets.remove(g);
    }
  }
  void sendCloseMessage(WSSocket s, String message) {
    if(s.inited!=null)
      s.inited.close(1001, message);
    else
      if(s.started!=null)
        s.started.close(1001, message);
  }
  public void closeUsage(String source) {
    dispose(source);
  }
  public static WSServer getOne(String name, String requester) throws PortOccupiedException {
    ConcurrentHashMap<WSServer, List<String>> servs = servers.get(name);
    if(servs == null) {
      servs = new ConcurrentHashMap<WSServer, List<String>>();
      servers.put(name, servs);
    }
    WSServer init = null, last = null;
    for(Entry<WSServer, List<String>> en:servs.entrySet()) {
      WSServer wss = en.getKey();
      if(en.getValue().size()<4) {
        init = en.getKey();
        en.getValue().add(requester);
        break;
      }
      last = en.getKey();
    }
    
    if(init == null) {
      int lastPort = initPortValues.getOrDefault(name, initPortValues.get("custom"));//TODO add custom pull for non-default names
      if(last!=null)
      lastPort = last.getPort();
      int port = lastPort;
      if(!PortUtils.testPortIsFree(port)) {
        switch (PortInitStrategy.strategy) {
          case FIXED:{
            throw new PortOccupiedException("Cannot init socket server on port "+port+" because it is occupied");  
          }
          case DYNAMIC_ON_OCCUPIED:{
            port = detectPort(name, lastPort);
          }break;
        }
      }
      if(!PortUtils.testPortIsFree(port))
        throw new RuntimeException("Cannot init socket for "+name+" because all ports are ocupied");
      init = new WSServer(name, port);
      try {
        init.init(requester);
      } catch (PortOccupiedException e) {
        RLogger.logError(e);
      }
    }
    return init;
  }

  private static int detectPort(String name2, int port2) {
    int it = 0;
    while(!PortUtils.testPortIsFree(port2) && it<ATTEMPTS) {
      port2+=initPortValues.size();
      it++;
    }
    return port2;
  }
  private WSServer(String string, int port2) {
    this.name = string;
    port = port2;
  }

  public static void stopSockets() {
    stopped.set(true);
    for(ConcurrentHashMap<WSServer, List<String>> en :servers.values()) {
      for(Entry<WSServer, List<String>> sa:en.entrySet()) {
        WSServer s = sa.getKey();
          try {
            if(s.getPortState()!=PortStatus.STARTED)
              continue;
          } catch (Exception e) {
            e.printStackTrace();
          }
        try {
          for(Entry<String, List<WSSocket>> as:s.activeSockets.entrySet())
            s.sendCloseMessage(as.getValue(), "Server is closing", false);
            //send close message to all sockets
          Thread.currentThread().join(200);//wait it to be delivered for some time
          s.server.setStopTimeout(0);
          for(Connector cn:s.server.getConnectors()) {
            cn.stop();
            ((ServerConnector) cn).close();
          }
          //close connections
          s.connector.close();
          s.server.stop();
          if(s.serverThread!=null)
            s.serverThread.interrupt();
        } catch (Exception e) {
          e.printStackTrace();
        }
        RLogger.logInfo("Stopped "+s.name+" on "+s.getPort());
      }
    }
  }
  public void init(String requester) throws PortOccupiedException{
    PortStateRegister.getOne().registerProvider(this);
    initSocket(WSServer.this, requester);
  }
  protected static void initSocket(WSServer wss, String requester) {
    try {
      RLogger.logInfo("init "+wss.name+" sockets on port "+wss.port);
      wss.server = new Server();
      int att = 0;
      wss.portState = PortStatus.NOT_INITED;
      wss.connector = new ServerConnector(wss.server);
      QueuedThreadPool qtr = (QueuedThreadPool) wss.connector.getExecutor();
      qtr.setDaemon(true);
      if(!qtr.getState().equals("READY")) {
        qtr.setMinThreads(1);
        qtr.setReservedThreads(1);
      }
      wss.connector.setPort(wss.port);
      wss.connector.setHost("127.0.0.1"); // VSCODE-PATCH: this computer only
      wss.connector.setReuseAddress(true);
      wss.server.setConnectors(new Connector[] {wss.connector});
      wss.server.setHandler(wss);
      try {
        ConcurrentHashMap<WSServer, List<String>> cm = servers.get(wss.name);
        synchronized(servers) {
          if(cm == null) {
            cm = new ConcurrentHashMap<WSServer, List<String>>();
            servers.put(wss.name, cm);
          }
          List<String> active = new CopyOnWriteArrayList<>();
          active.add(requester);
          servers.get(wss.name).put(wss, active);
        }
        wss.server.start();
        wss.portState = PortStatus.STARTED;
        RLogger.logDebug("Socket "+wss.name+" INITIATED on "+wss.port);
      } catch(IOException ex) {
        boolean error = true;
        if(ex.getCause()!=null && ex.getCause() instanceof BindException) {
          BindException bo = (BindException) ex.getCause();
          error = false;
        }
        if(error) {
          RLogger.logInfo("Cannot start "+wss.name+" socket on port "+wss.port+", reinit "+att );
          RLogger.logError(ex);
        }else{
          RLogger.logDebug("Cannot start "+wss.name+" socket on port "+wss.port+", reinit "+att , 5);
          RLogger.logDebug(ex , 5);
        }
      } catch (Exception e) {
        RLogger.logInfo("Cannot start "+wss.name+" socket on port "+wss.port+", reinit "+att );
        RLogger.logError(e);
      }
      if(wss.portState!=PortStatus.STARTED)
        throw new PortOccupiedException("Cannot init socket server on port "+wss.port+"(and some more) because it is occupied");
       if(wss.server!=null) {
      wss.serverThread = Thread.currentThread();
    }
    }catch(PortOccupiedException e) {
      RLogger.logError(e);
    }
  }
  protected void markInited(WSSocket ws) {
    String rel = ws.getRelatedSource(); 
    if(rel == null)
      throw new RuntimeException("Mark not inited socket as inited");
    List<WSSocket> group = getActiveSocketsGroup(rel);
    group.add(ws);
    getActiveSocketsGroup(NOT_INITED_GROUP).remove(ws);
  }

  @Override
  public void configure(WebSocketServletFactory factory) {
    factory.getExtensionFactory().unregister("x-webkit-deflate-frame");
    factory.getExtensionFactory().unregister("deflate-frame"); 
    factory.getExtensionFactory().unregister("permessage-deflate");
    factory.getPolicy().setIdleTimeout(IDLE_TIMEOUT);
    factory.getPolicy().setMaxTextMessageSize(TEXT_BUFFER);
    factory.getPolicy().setMaxTextMessageBufferSize(TEXT_BUFFER);
    factory.getPolicy().setInputBufferSize(TEXT_BUFFER);
    factory.register(WSSocket.class);
    WebSocketCreator wsc = factory.getCreator();
    if(wsc!=null)
      factory.setCreator(new WebSocketCreator() {
        @Override
        public Object createWebSocket(ServletUpgradeRequest arg0, ServletUpgradeResponse arg1) {
          // VSCODE-PATCH: a web page of another site in a browser of this computer must not talk to the editors
          if (!localOrigin(arg0.getHeader("Origin"))) {
            try {
              arg1.sendForbidden("Origin not allowed");
            } catch (java.io.IOException e) {
              RLogger.logError(e);
            }
            return null;
          }
          Object ws = wsc.createWebSocket(arg0, arg1);
          if(ws!=null && (ws instanceof WSSocket)) {
            WSSocket wsa = (WSSocket) ws;
            wsa.source = NOT_INITED_GROUP;
            wsa.setServer(WSServer.this);
            List<WSSocket> group = getActiveSocketsGroup(NOT_INITED_GROUP);
            group.add(wsa);
          }
          return ws;
        }
      });
  }

  /**
   * VSCODE-PATCH: the origins of the web editors: their pages on this computer (http://127.0.0.1, localhost) and the
   * webviews of VS Code. A client that is no browser sends no origin.
   */
  static boolean localOrigin(String origin) {
    if (origin == null || origin.isEmpty())
      return true;
    if (origin.startsWith("vscode-webview://") || origin.startsWith("vscode-file://"))
      return true;
    try {
      java.net.URI u = java.net.URI.create(origin);
      String host = u.getHost();
      return ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && host != null
          && (host.equals("127.0.0.1") || host.equals("localhost") || host.equals("[::1]") || host.equals("::1"));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public String dumpConnections() {
    return "";
  }
  
  public void registerMessageAndStateListeners(ISocketMessageListener mesListener,
      ISocketStateListener genericSocketService) {
    messageAndStateStorage.add(new Pair<>(mesListener, genericSocketService));
  }

  public void unRegisterMessageAndStateListeners(ISocketMessageListener mesListener,
      ISocketStateListener genericSocketService) {
    Pair<ISocketMessageListener, ISocketStateListener> rem = null;
    Iterator<Pair<ISocketMessageListener, ISocketStateListener>> it = messageAndStateStorage.iterator();
    while(it.hasNext()) {
      Pair<ISocketMessageListener, ISocketStateListener> now = it.next();
      if(now.getFirst() == mesListener || now.getSecond() == genericSocketService)
        if(rem == null)
          rem = now;
        else
          throw new RuntimeException("Double listeners durnig remove");
    }
    if(rem!=null)
      messageAndStateStorage.remove(rem);
  }
  public void forget(Session session) {
  
  }
  public PortStatus getPortState() {
    return portState;
  }

  public void dispose(String source) {
    List<WSSocket> socks = activeSockets.remove(source);
    sendCloseMessage(socks, "Component is closing", false);
    ConcurrentHashMap<WSServer, List<String>> map = servers.get(name);
    if(map!=null) {
      synchronized (servers) {
        List<String> val = map.get(this);
        if(val!=null) 
        {
          if(val.remove(source) && val.isEmpty()){
            map.remove(this);
            val = null;
          }
        }
        if(val == null)//no active sess
          new Thread(new Runnable() {
            @Override
            public void run() {
              try {
                List<WSSocket> socks = activeSockets.remove(NOT_INITED_GROUP);
                sendCloseMessage(socks, "Component is closing", false);
                System.out.println("actual close "+name);
                map.remove(WSServer.this);
                server.setStopTimeout(0);
                for(Connector cn:server.getConnectors()) {
                  cn.stop();
                  ((ServerConnector) cn).close();
                }
                connector.stop();
                connector.close();
                try {
                  server.stop();
                }catch(Exception e) {
                  RLogger.logDebug(CoreUtils.getTrace(e));
                }
                try {
                  server.destroy();
                }catch(Exception e) {
                  RLogger.logDebug(CoreUtils.getTrace(e));
                }
              } catch (Exception e) {
                RLogger.logError(e);
              }
            }
          }).start();
      }
    }
  }
  public String getName() {
    return name;
  }
  public boolean isDisposed(String source) {
    boolean disposed = portState!=PortStatus.NOT_INITED&&portState!=PortStatus.STARTED;
    List<WSSocket> list = activeSockets.get(source);
    if(list!=null)
      for(WSSocket ws:list)
        if(!ws.isDisposed())
          disposed = false;
    return disposed;
  }
}