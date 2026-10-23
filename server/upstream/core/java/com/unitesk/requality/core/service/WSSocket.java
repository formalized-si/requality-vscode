package com.unitesk.requality.core.service;
import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jetty.websocket.api.CloseException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.tool.CoreUtils;

@WebSocket
public class WSSocket {
  private WSServer serv;
  private Session sessions = null;
  protected List<ISocketMessageListener> handlers = new LinkedList<>();
  protected Map<String, ConcurrentLinkedQueue<JSONObject>> messages = new HashMap<>();
  protected Session started = null;
  protected Session inited = null;
  protected String source = null;
  private ISocketMessageListener persListener = null;
  private ISocketStateListener stateListener = null;
  private Set<Session> startedM = new HashSet<Session>();
  private ReentrantLock rl = null;
  private ConcurrentLinkedQueue<String> msgs = new ConcurrentLinkedQueue<String>();
  private Pattern specChar = Pattern.compile("&([a-z0-9A-Z#]+);");
  private boolean isDisposed = false;
  
  public WSSocket(){
  }
  
  public String getPortUserInfo() {
    return "Websocket instance "+serv.getName();
  }

  public long send(Session s, String method, Pair<String, String> ... data) {
    if(s == null)
      s = started;
    long code = getNewSendCode();
    initAnswer(code);
    if(s!=null && s.isOpen())//can be null when socket was already closed 
      handleSockSend(s, method, data);
    return code;
  }
  private long sendCode = 0;
  long getNewSendCode() {
    if(sendCode<Long.MAX_VALUE)
      return sendCode++;
    sendCode = Long.MIN_VALUE+1;
    return sendCode;
  }

  public void send(String method, JSONObject jarr, Session ... socks) {
    JSONObject ret = new JSONObject();
    ret.put("operation", method);
    ret.put("data", jarr);
    for(Session sock: socks) 
      sendString(sock, ret.toJSONString());
  }
  public void send(String method, JSONArray jarr,  Session ... socks) {
    JSONObject ret = new JSONObject();
    ret.put("operation", method);
    ret.put("data", jarr);
    for(Session sock: socks) 
      sendString(sock, ret.toJSONString());
  }
  public void send(String method, Pair<String, String>[] data, Session ... socks) {
    for(Session sock: socks)
      handleSockSend(sock, method, data);
  }
  public void send(Collection<Session> sess, String data) {
    for(Session sock: sess)
      sendString(sock, data);
  }
  private void handleSockSend(Session sock, String method, Pair<String, String>[] data) {
    JSONObject jo = new JSONObject();
    jo.put("operation", method);
    
    JSONObject data2 = new JSONObject();
    for(Pair<String, String> ss:data) {
      data2.put(ss.getFirst(), ss.getSecond());
    }
    jo.put("data", data2);
    sendString(sock, jo.toJSONString());
  }
  public String dumpMessages() {
    StringBuilder sb = new StringBuilder();
    ConcurrentLinkedQueue<String> s = msgs;
    {
      //System.out.println(s.getKey().toString());
      sb.append(":<\n");
      for(String ms:s)
        sb.append(ms+"\n");
      sb.append(">\n");
    }
    return sb.toString();
  }
  
  private void sendString(Session sock, String jsonString) {
    ReentrantLock rl2 = rl;
    if(rl2 == null) {
      rl2 = new ReentrantLock();
      rl = rl2;
    }
    if(sock == null)
    {
      RLogger.logError("Attempt to init null session");
      return;
    }
    rl2.lock();
    ConcurrentLinkedQueue<String> qu1 = msgs;
    if(qu1==null){
      qu1 = new ConcurrentLinkedQueue<String>();
      msgs = qu1;
    }
    final ConcurrentLinkedQueue<String> qu = qu1;
    qu.offer(jsonString);
    final ReentrantLock rrl2 = rl2;
    boolean started = false;
    synchronized (this) {
      started = startedM.contains(sock);
    }
    try {
      if(!started) {
        startedM.add(sock);
        JobManager.jm.startBaseJob("send message to socket "+serv.getPort(), new RunnableWithMonitor() {
          int iter = 0;
          @Override
          public void run(IProgressMonitor pm) {
            if(sock == null)
              return;//tends to be impossible here
            boolean tl = false;
            int att = 0;
            //ReentrantLock rl2 = null;
            try {
              //rl2 = rl.get(sock);
              tl = rrl2.tryLock();
              while(!qu.isEmpty() && att<3) {
                att++;
                boolean sockOpen = (sock!=null&&inited==sock)?sock.isOpen():false;
                while(!tl || !sockOpen && iter<100)
                  try {
                    Thread.currentThread().join(10);
                    if(!tl)
                      tl = rrl2.tryLock();
                    sockOpen = sock!=null?sock.isOpen():false;
                    if(!sockOpen)
                      iter++;
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                  }
                try {
                  Thread.currentThread().join(5);
                } catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                  e1.printStackTrace();
                }
                try {
                  iter = 0;
                  String msg = qu.peek();;
                  rrl2.unlock();
                  tl = false;
                  if(sock == null)
                    RLogger.logDebug("Socket now null, cannot be send",4);
                  else {
                    if(!sock.isOpen())
                      RLogger.logDebug("Lost because socket is closed  "+(sock.getRemote()!=null?sock.getRemote().toString():"null")+" "+msg,4);
                    else {
                      try {
                        if(msg== null)
                          RLogger.logError("attempt to send null message");
                        else
                          sock.getRemote().sendString(msg);
                        att--;
                        RLogger.logDebug("sent: "+jsonString.substring(0, Math.min(jsonString.length(), 200)),6);
                      }catch(WebSocketException cce) {
                        RLogger.logDebug("Lost because channel is closed ("+att+") "+(sock!=null&&sock.getRemote()!=null?sock.getRemote().toString():"null")+" "+msg,6);
                        Thread.currentThread().join(100);
                      }catch(ClosedChannelException cce) {
                        //lost this time, resheldule
                        RLogger.logDebug("Lost because channel is closed ("+att+") "+(sock!=null&&sock.getRemote()!=null?sock.getRemote().toString():"null")+" "+msg,6);
                        Thread.currentThread().join(100);
                      }
                      if(!tl){
                        rrl2.lock();
                        tl = true;
                      }
                      qu.poll();
                      if(tl){
                        rrl2.unlock();
                        tl = false;
                      }
                    }
                  }
                }catch(Exception e) {
                  RLogger.logError(e);
                }
                finally {
                  if(!tl){
                    rrl2.lock();
                    tl = true;
                  }
                }
                
              }
            //} catch (IOException e) {
             // e.printStackTrace();
            }finally {
              synchronized (WSSocket.this) {
                  msgs = qu;
                  startedM.remove(sock);
                  if(tl && rrl2!=null) {
                    rrl2.unlock();
                  }
              }
            }
          }
        }, false); 
      }
    }finally {
      rl2.unlock();
    }
  }
  private void sendBySocket(Session sock, String method, JSONObject data) {
    if(sock != null && sock.isOpen()) {
      JSONObject ret = new JSONObject();
      ret.put("operation", method);
      ret.put("data", data);
      sendString(sock, ret.toJSONString());
    }
  }
  private void sendBySocket(Session sock, String method, JSONArray data) {
    if(sock != null && sock.isOpen()) {
      JSONObject ret = new JSONObject();
      ret.put("operation", method);
      ret.put("data", data);
      sendString(sock, ret.toJSONString());
    }
  }
  public void setServer(WSServer s) {
    serv = s;
  }
  @OnWebSocketClose
  public void onClose(int statusCode, String reason) {
    //Test here what session is closed
  final ISocketStateListener ssl = stateListener;
    try {
      boolean dropConnection = statusCode!=1000;
      if(reason!=null&&reason.contains("TimeoutException")) {
        dropConnection = false;//adds posibility to reconnect
      }
      Session s = sessions;
      {
        started = null;
        unregisterSession(s, !dropConnection);
        WebSocketSession wss = (WebSocketSession) s;
        if(ssl!=null) {
          try {
            ssl.handleClosed(this, s, dropConnection);
          }catch(Exception ea) {
            RLogger.logError(ea);
          }
          RLogger.logDebug("Close: " + s + " "+reason,6);
        }
        unregisterSocket(s, !dropConnection);
        //TODO adds drop if no use?
        s.close(statusCode==1006?1000:statusCode, reason);//adds fix for 1006 on linux during page change
      }
    }finally {
      if(serv!=null)
        serv.removeSocketData(this);
      isDisposed = true;
    }
    //}
    RLogger.logDebug("Close socket "+serv.getName()+"-"+getPort()+"-"+this.toString()+": " + reason,6);
  }
  private void unregisterSession(Session s, boolean temp) {
    if(started == s)
      started = null;
    if(inited == s) {
      inited = null;
      source = null;
    }
  }
  
  public void addMessageHandler(ISocketMessageListener mg) {
    this.handlers.add(mg);
  }
  
  public void sessionDisposed(Session session, boolean temp) {
    unregisterSession(session, temp);
    //TODO add more?
  }
  @OnWebSocketError
  public void onError(Throwable t) {
    if(t instanceof TimeoutException) {
      //ignore socket drop on timeout
    }else
    if(t instanceof CloseException){
      CloseException ce = (CloseException) t;
      if(ce.getCause()!=null && ce.getCause() instanceof TimeoutException) {
        //ignore
        RLogger.logDebug("Timeout on socket "+serv.getPortUserInfo(),6);
      }
    }else
    {
      RLogger.logError("Socket("+serv.getPort()+"), error: " + t.getMessage()+",trace: "+CoreUtils.getTrace(t));
      RLogger.logDebug("error stack "+CoreUtils.getTrace(t),5);
    }
  }
  
  @OnWebSocketConnect
  public void onConnect(Session session) {
    registerSession(session);
    started = session;
  }
  private void registerSession(Session session2) {
    sessions = session2;
  }
  public JSONParser parser = new JSONParser();
  @OnWebSocketMessage
  public void onMessage(Session sess, String message) {
    Object o;
    try {
      o = parser.parse(message);
      if(o!=null){
        boolean isOp = false;
        JSONObject obj = (JSONObject) o;
        String op = (String) obj.get("operation");
        String win = CoreUtils.URLDecode((String) obj.get("location"));
        //TODO handle and filter by session
        String msess = (String) obj.get("session");
        if(isFiltered(op)) 
        {
          isOp = true;
          synchronized (messages) {
            String sessid = formKey(win, msess);
            ConcurrentLinkedQueue<JSONObject> map = 
            messages.get(sessid);
            if(map == null){
              map = new ConcurrentLinkedQueue<>();
              messages.put(sessid, map);
            }
            map.add(obj);
          }
        }
        boolean is = false, isInit = op.equals("init");
        if(isInit)
          for(Pair<ISocketMessageListener, ISocketStateListener> en:serv.messageAndStateStorage) 
            try {
              ISocketMessageListener mg = en.getFirst();
              if(mg == null)
                continue;
              ISocketStateListener sl = en.getSecond();
              boolean res = false;
              try {
                res = mg.handleMessage(this, sess, op, win, obj);
              }catch(Exception e) {
                RLogger.logDebug("Error happens during procession of "+op+" on "+win+" for session "+sess);
                RLogger.logDebug(e, 1);
              }
              if(res) {
                if(persListener!=null) {
                  RLogger.logError("not one pers");
                }
                persListener = mg;//TODO several?
                stateListener = sl;
                setInited(sess, mg.getId());
              }
              is = is || res;
            }catch(Exception ea) {
              RLogger.logError(ea);
            }
        else {
          if(persListener!=null)
            try {
              is = is || persListener.handleMessage(this, sess, op, win, obj);
            }catch(Exception ea) {
                RLogger.logError(ea);
            }
        }
        //was incorrect on current model
//        if(!is && isInit)
//          sess.close(1001, "wrong session call");
        if(!is && !isOp)
          RLogger.logError("Message lost! "+message);
      }
    } catch (ParseException e) {
      RLogger.logDebug(e,6);
    }
    RLogger.logDebug("Message: " + message,6);
    
  }
  
  public void setInited(Session s, String source) {
    inited = s;
    if(this.source!=null)
      RLogger.logDebug("Warning socket source reinit! "+s.toString()+" "+source);
    this.source = source;
    stateListener.handleConnected(this, s, source);
    serv.markInited(this);//change group of this socket to proper one
  }
  public String getRelatedSource() {
    return source;
  }
  private boolean isFiltered(String op) {
    return op.equals("init")||op.equals("sendInited");
  }

  public static String formKey(String win, String msess) {
    return msess == null ? win:win+"|"+msess;
  }
  ConcurrentHashMap<Long, AtomicReference<Object>> trashCan = new ConcurrentHashMap<>();
  void initAnswer(long code) {
    synchronized (trashCan) {
      trashCan.put(code, new AtomicReference<>());
    }
  }
  protected Object getArrayDecode(Object object) {
    if(object instanceof Object[]) {
      Object[] a = (Object[]) object;
      for(int i = 0;i<a.length;i++)
        a[i] = getArrayDecode(a[i]);
      return a;
    }
    if(object instanceof String) {
      String st = (String) object;
      Matcher m = specChar.matcher(st);
      int old = 0;
      StringBuilder sb = new StringBuilder();
      while(m.find()) {
        int s = m.start(), e = m.end();
        sb.append(st.substring(old,s));
        String cont = m.group(1);
        String repl = null;
        switch(cont) {
          case "lt":{
            repl = "<";
          }break;
          case "rt":{
            repl = ">";
          }break;
          case "amp":{
            repl = "&";
          }break;
          case "quot":{
            repl = "\"";
          }break;
          case "#039":{
            repl = "'";
          }break;
        }
        if(repl!=null)
          sb.append(repl);
        else
          sb.append("&"+cont+";");
        old = e;
      }
      if(old<st.length())
        sb.append(st.substring(old,st.length()));
      return sb.subSequence(1, sb.length()-1).toString();
    }
    return object;
  }
  public Integer getPort() {
    return serv.getPort();
  }


  private Map<String, Map<String, List<Session>>> documentToFrames = new HashMap<>();
  public void dropSession(String docId, String fullSession2, Session old) {
    old.close(1000, "session dropped");
  }
  public void unregisterSocket(Session session2, boolean temp) {
    if(session2==null)
      return;
    Map<String, List<String>> rem = Collections.emptyMap();
    for(Entry<String, Map<String, List<Session>>> en1:documentToFrames.entrySet()){
        for(Entry<String, List<Session>> en2:en1.getValue().entrySet()) {
            List<Session> list = en2.getValue();
            if(list.remove(session2)) {
                if(list.isEmpty()) {
                    if(rem.isEmpty())
                        rem = new HashMap<>();
                    if(!rem.containsKey(en1.getKey()))
                        rem.put(en1.getKey(), new LinkedList<>());
                    rem.get(en1.getKey()).add(en2.getKey());
                }
                //forgetSession(en2.getKey().substring(en2.getKey().lastIndexOf("|")+1) ,session2, temp);
                //will be called after
            }
        }
    }
    //forgetSession(session2);
    for(Entry<String, List<String>> en:rem.entrySet()){
        for(String s:en.getValue())
            unregisterSocket(en.getKey(), s, temp);
    }
  }
  public void unregisterSocket(String doc, String session, boolean temp) {
    Map<String, List<Session>> sess = documentToFrames.get(doc);
    if(sess!=null) {
      List<Session> sa = sess.get(session);
      if(sa!=null) {
        for(Session s:sa) {
          forgetSession(session.substring(session.lastIndexOf("|")+1), s, temp);
        }
        if(sess.isEmpty())
          documentToFrames.remove(doc);
      }
    }
  }
  private void forgetSession(String sess, Session s, boolean temp) {
    new Thread( new Runnable() {
      public void run() {
        if(msgs!=null){
          startedM.remove(s);
          rl = null;
          for(String s:msgs)
            RLogger.logDebug("Lost message "+s+" on session to be "+(temp?"resetted":"halted"), 5);
          msgs.clear();
        }

        if(s.isOpen())
          try {
            Thread.currentThread().join(500);
          } catch (InterruptedException e) {
                // TODO Auto-generated catch block
            e.printStackTrace();
          }
        try {
          s.close(temp?1000:1001, "session closing");
        }
        catch(Exception eof) {
        }
      }
    }).run();
  } 

  public Session registerSocket(String doc, String combinedSession, Session sess) {
    if(!documentToFrames.containsKey(doc))
      documentToFrames.put(doc, new HashMap<>());
    List<Session> slist = documentToFrames.get(doc).get(combinedSession);
    if(slist == null) {
        slist = new LinkedList<>();
        documentToFrames.get(doc).put(combinedSession, slist);
    }
    if(slist.contains(sess))
      return null;
    slist.add(sess);
    return null;
  }

  public boolean isDisposed() {
    return isDisposed;
  }

}