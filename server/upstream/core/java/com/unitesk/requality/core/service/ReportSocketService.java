package com.unitesk.requality.core.service;

import java.net.URI;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.document.ILinkedStateProvider;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.message.RMessages;

public class ReportSocketService extends GenericSocketService {
  private static final String REPORT_CONST = "reportview";
  
  public ReportSocketService(TreeNode startNode, FSId fsid, ILinkedStateProvider prov) {
    super(startNode, REPORT_CONST, fsid, prov);
    updateListeners();
  }
  private ISocketMessageListener hl = null;
  private String fullSession = null;
   
  @Override
  protected ISocketMessageListener getMessageListener() {
    if(hl == null)
      hl = new ISocketMessageListener() {
        
        @Override
        public boolean handleMessage(WSSocket ws, Session session, String op, String win, JSONObject message) {
          RLogger.logDebug("message "+op+" "+win+" isd "+isDisposed+" "+message.get("session"),6);
          if(isDisposed)
            return false;
          String window = (String) message.get("location");
          window = CoreUtils.URLDecode(window);
          Object o = message.get("session");
          String transf = CoreUtils.URLDecode(filterPath(window, startNode));
          String fs = getComplexSession(transf);
          if(o == null || op == null)
            return false;//TODO check
          if(!window.startsWith(transf) || !op.equals("init")&o!=null&&!sess.equals(o))
            return false;
          switch(op){
            case "printToConsole": //message
              System.out.println(message.get("message"));
              return true;
            case "openEditorForNode"://node
              String node = (String) message.get("node");
              TreeNode nde5 = db.getNode(node);
              final TreeNode sel = nde5;
              String tp = nde5.getType();
              if (!nde5.getQualifiedId().equals(Requirement.getTypeRootQId()))
                nde5 = nde5.getShownParent();
              String editorid = Consts.UniEditorID;
              if (tp.equals(Document.TYPE_NAME)||tp.equals(Location.TYPE_NAME)) editorid = Consts.ReqMarkerID;
              RMessages.openEditor(editorid, nde5, sel);
              return true;
            case "downloadLink":
              String link = (String) message.get("link");
              if(link.equals("null"))
                return true;
              RMessages.openView(db, RMessages.DOWNLOAD_LINK_DIALOG,true, link);
              return true;
            case "callAddTest"://node
              if(db == null)
                return false;
              String nodeS = (String) message.get("node");
              RMessages.openView(db, Consts.TEST_DIALOG_PROVIDER_CREATE_NAME, true, nodeS);
              return true;
            case "init":
               String sh = win.substring(0,win.lastIndexOf("/"));
               WSServer wss = getWSProvider();
               if(wss == null) {
                 return false;
               } 
              
              if(o!=null && ((sess!=null||!o.equals("null"))&&!o.equals(sess))) {
                return false;
              }
              fullSession = fs;
              setSessionInited(ws, session);
              Runnable ir = new Runnable() {
				
				@Override
				public void run() {
	              sendMessage(ws, session, op, 
                    Pair.createSS("session", (String)o));
                }
			  };
			  registerResourceRelatedSocket(ws, startNode, null);
			  ir.run();
              return true;

          }
		  return false;
        }

		@Override
		public String getId() {
          return getFSIDFull();
		}
      };
    return hl;
  }
//  @Override
//  protected void onDispose(WSSocket ws) {
//    if(ws == null) 
//      return;
//  }
  
  protected void sendMessage(WSSocket ws, Session s, String op,
      Pair<String, String> ... createSS) {
    if(ws == null)
      return;
    ws.send(s, op,  createSS);
  }
  @Override
  protected ITreeChangeListener getTreeChangeListener() {
    return null;
  }

  @Override
  protected ITreeChangeListener getGUITreeChangeListener() {
    return null;
  }

  
  @Override
  protected URI getUrl(TreeNode startNode, String session) {
    return ResourcesProvider.getOne().formReportsURL((ReportData) startNode, session, getPort());
  }
  @Override
  public boolean isReadOnlyOperation(String op) {
    return true;//TODO test
  }

}
