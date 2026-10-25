package com.unitesk.requality.core.service;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.create.CreateRequirementOperation;
import com.unitesk.requality.core.operation.delete.DeleteLocationOperation;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.document.BrowserState;
import com.unitesk.requality.document.IDocumentAddedHandler;
import com.unitesk.requality.document.ILinkedStateProvider;
import com.unitesk.requality.document.ILocationChangedHandler;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class DocumentsSocketService extends GenericSocketService {
  private String lastIdSended = null;
  private String lastSendedReqId = null;
  
  public Location selLocation = null;
  public Requirement selRequirement = null;
  private int selLocInd = 0; 
  
  private final Document startDoc;
  public boolean locChangeEnabled = true;
  private long actionTime = -1;
  private Location initLocation = null;
  
  protected HashMap<String, LocationState> locationState = new HashMap<>();
    
  public ILocationChangedHandler locationChangedHandler;
  public static final List<IDocumentAddedHandler> docAddHandlers = new LinkedList<>();

  protected final ITreeChangeListener treeChangeListener = new DefaultTreeChangeListener() {
    @Override
    public void deleted(TreeNode node, byte flag, Moment m) {
      WSServer server = getWSProvider();
      if(server == null)
        return;
      boolean hasDocs = false;
      synchronized (resourceNodeToSocks) {
        hasDocs = resourceNodeToSocks.getOrDefault(node, Collections.emptyList()).size()>0;
      }
      if(m == Moment.BEFORE && node.getType().equals(Document.TYPE_NAME) &&
          hasDocs)
        beforeDeleted((Document) node, server);
      if(m == Moment.BEFORE && node.getSUUId().equals(startDoc.getSUUId()))
        beforeDeleted((Document) node, server);
      super.deleted(node, flag, m);
    }
    public void deleted(TreeNode node) {
      List<WSSocket> stopSockets = null;
      synchronized (resourceNodeToSocks) {
        for(Entry<TreeNode, List<WSSocket>> en:resourceNodeToSocks.entrySet()) {
          TreeNode doc = en.getKey();
          if(en.getValue()!=null && en.getValue().size()>0)
            if (doc.getUUId().equals(node.getUUId())) {
              stopSockets = new LinkedList<>(en.getValue());
              break;
            }
        }
      }
      if(stopSockets!=null)
         getWSProvider().sendCloseMessage(stopSockets, "Node "+node.getSUUId()+" was removed", true);
    }
    @Override
    public void changed(TreeNode node, ChangesSet<Attribute> what) {
      if(node == selRequirement) {
        if(what.contains(TreeNode.ATTR_CHILDREN)) {
          String id = getNewId(selRequirement);
          if(!id.equals(lastIdSended)) {
            updateRequirement(null, selRequirement, getInitedSocketsArray());
          }
        }
        if(what.contains(TreeNode.ATTR_LABEL))
          updateRequirement(null, selRequirement, getInitedSocketsArray());
      }
    }
  
  
    private void reactMoved(TreeNode node, TreeNode oldParent, TreeNode newParent, WSServer server) {
      boolean beforeDel = false;
      synchronized (resourceNodeToSocks) {
        for(Entry<TreeNode, List<WSSocket>> en:resourceNodeToSocks.entrySet()) {
          TreeNode doc = en.getKey();
          if(en.getValue()!=null && en.getValue().size()>0)
            if (doc.getUUId().equals(node.getUUId()))
              {
                beforeDel = true;
                break;
              }
        }
      }
      if(beforeDel)
        beforeDeleted((Document) node, server);
    }
  
  
    @SuppressWarnings("unchecked")
    @Override
    public void changed(List<TreeNodeChangeEvent> changes) {
      WSServer server = getWSProvider();
      if(server == null)
        return;
      List<UUID> toup = new LinkedList<>();
      for (TreeNodeChangeEvent ce : changes)
        switch (ce.getType()) {
          case DELETE:
            {
              if (ce.getMoment() == Moment.BEFORE) {
                TreeNode node = ce.getTreeNodeTarget();
                if(node.getType().equals(Document.TYPE_NAME)) {
                  boolean wasDoc = false;
                  synchronized (resourceNodeToSocks) {
                    wasDoc = resourceNodeToSocks.getOrDefault(node, Collections.emptyList()).size()>0;
                  }
                  if(wasDoc)
                    beforeDeleted((Document)node, server);
                }
                continue;
              }
              TreeNode nd = ce.getTreeNodeTarget();
              if (nd != null) { // needed?
                List<WSSocket> allSocks = null;
                if(nd.getType().equals(Document.TYPE_NAME)){
                  synchronized(resourceNodeToSocks){
                    for(Entry<TreeNode, List<WSSocket>> en:resourceNodeToSocks.entrySet()) {
                      TreeNode doc = en.getKey();
                      if(en.getValue()!=null && en.getValue().size()>0)
                        if (doc.getUUId().equals(nd.getUUId())) {
                          if(allSocks== null)
                            allSocks = new LinkedList<>();
                          allSocks.addAll(en.getValue());
                        }
                    }
                  }
                }
                if(allSocks!=null)
                  getWSProvider().sendCloseMessage(allSocks, "Node "+nd.getSUUId()+" was removed", true);
              }
              break;
            }
          case MOVE:
            if (ce.getMoment() == Moment.BEFORE) continue;
            TreeNode node = ce.getTreeNodeTarget();
            TreeNode par = (TreeNode) ce.getArguments()[0];
            TreeNode npar = (TreeNode) ce.getArguments()[1];
            reactMoved(node, par, npar, server);
            break;
          case CHANGE:
            if(ce.getMoment() == Moment.BEFORE) continue;
            node = ce.getTreeNodeTarget();
            changed(node, (ChangesSet<Attribute>) ce.getArguments()[0]);
          default:
            break;
        }
    }
  };
  protected final ITreeChangeListener treeGUIChangeListener = new DefaultTreeChangeListener() {
      @Override
      public void deleted(TreeNode node, byte flag, Moment m) {
        WSServer server = getWSProvider();
        if(server == null)
          return;
        if(m == Moment.BEFORE && node.getType().equals(Document.TYPE_NAME)) {
          boolean wasDoc = false;
          synchronized (resourceNodeToSocks) {
            wasDoc = resourceNodeToSocks.containsKey(node);
          }
          if(wasDoc)
            beforeDeleted((Document) node, server);
        }
        super.deleted(node, flag, m);
      }
      private Pair<TreeNode, Collection<WSSocket>> reactDeleted(TreeNode node) {
        if(node.getType().equals(Location.TYPE_NAME)) {
          if(hasRelatedSessions(node.getWorkParent())) {
            Collection<WSSocket> sess = getRelatedSessions(node.getWorkParent());
            return new Pair<>(node, sess);
          }
        
        }else if(node.getType().equals(Requirement.TYPE_NAME)) {
          if(selRequirement!=null && selRequirement.getUUId().equals(node.getUUId()))
            updateRequirement(null, null, getInitedSocketsArray());//reset selection
        }
        return null;
        //TODO UPDATE OUTLINE
      }
  
      public void deleted(TreeNode node) {
         List<WSSocket>sess = null;
         synchronized(resourceNodeToSocks) {
           sess = resourceNodeToSocks.get(node);
           if(sess!=null)
             sess = new LinkedList<>(sess);
         }
         if(sess!=null && sess.size()>0)
         {
           getWSProvider().sendCloseMessage(sess, "Node "+node.getSUUId()+" was removed", true);
           return;        
         }
         Pair<TreeNode, Collection<WSSocket>> nde = reactDeleted(node);
         if(nde!=null)
            locationRemoved(nde.getFirst(), nde.getSecond());
      }
  
      @Override
      public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent) {
        reactMoved(node, oldParent, newParent);
      }
  
      private void reactMoved(TreeNode node, TreeNode oldParent, TreeNode newParent) {
        synchronized (resourceNodeToSocks) {
          for(Entry<TreeNode, List<WSSocket>> en:resourceNodeToSocks.entrySet()) {
            TreeNode doc = en.getKey();
            if(en.getValue()!=null && en.getValue().size()>0)
              if (doc.getUUId().equals(node.getUUId())) return;//TODO actions on doc move?
          }
        }
      }
  
      private void reactChanged(TreeNode node, ChangesSet<Attribute> what) {
  //          for(Document doc:allDues())
  //            if (doc.getUUId().equals(node.getUUId())) return true;
  //          if (node.getWorkParentUUID() != null
  //              && node.getType().equals(Location.TYPE_NAME))
  //            for(Document doc:allDues())
  //              if(node.getWorkParentUUID().equals(doc.getUUId()))
  //                return what.contains(TreeNode.ATTR_LABEL);
  //          //          if (node.getType().equals(Requirement.TYPE_NAME)
  //          //              && what.contains(Requirement.ATTR_LOCATIONS)) {
  //          //            Requirement r = (Requirement) node;
  //          //            for (TreeNode l : r.getLocations())
  //          //              if (l.getWorkParentUUID().equals(doc.getUUId())) return true;
  //          //          }
  //          return false;
        //TODO UPDATE OUTLINE
      }
  
      @Override
      public void changed(TreeNode node, ChangesSet<Attribute> what) {
        reactChanged(node, what);
      }
  
      private Pair<TreeNode, Collection<WSSocket>> reactCreated(TreeNode node) {
        if (node.getWorkParentUUID() != null
            && node.getType().equals(Location.TYPE_NAME))
          if(hasRelatedSessions(node.getWorkParent())) {
            Collection<WSSocket> sess = getRelatedSessions(node.getWorkParent());
            return new Pair<>(node, sess);
          }
          return null;
      }
  
      @Override
      public void created(TreeNode node) {
        Pair<TreeNode, Collection<WSSocket>> nd = reactCreated(node);
        if(nd!=null)
          locationCreated(nd.getFirst(), nd.getSecond());
      }
  
      @SuppressWarnings("unchecked")
      @Override
      public void changed(List<TreeNodeChangeEvent> changes) {
        List<Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean>> toup = new LinkedList<>();
        for (TreeNodeChangeEvent ce : changes)
          switch (ce.getType()) {
            case CREATE:
              if (ce.getMoment() == Moment.BEFORE) continue;
              TreeNode node = ce.getTreeNodeTarget();
              Pair<TreeNode, Collection<WSSocket>> cnd = reactCreated(node);
              if(cnd!=null)
                toup.add(new Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean>(cnd, true));
              break;
            case DELETE:
              {
                if (ce.getMoment() == Moment.BEFORE) continue;
                TreeNode nd = ce.getTreeNodeTarget();
                if (nd != null) { // needed?
                  Pair<TreeNode, Collection<WSSocket>> nde = reactDeleted(nd);
                  if(nde!=null)
                    toup.add(new Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean>(nde, false));
                }
                break;
              }
            case CHANGE:
              if (ce.getMoment() == Moment.BEFORE) continue;
              node = ce.getTreeNodeTarget();
              reactChanged(node, (ChangesSet<Attribute>) ce.getArguments()[0]);
              break;
  
            case MOVE:
              if (ce.getMoment() == Moment.BEFORE) continue;
              node = ce.getTreeNodeTarget();
              TreeNode par = (TreeNode) ce.getArguments()[0];
              TreeNode npar = (TreeNode) ce.getArguments()[1];
              reactMoved(node, par, npar);
              break;
            default:
              break;
          }
          
        LinkedList<Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean>> nds = new LinkedList<>(toup);
        nds.sort(new Comparator<Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean>>() {
          @Override
          public int compare(Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean> o1, Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean> o2) {
            String a1 = o1.getFirst().getFirst().attrs().getStringAttributeValue(Location.ATTR_MARKUP, "");
            String a2 = o2.getFirst().getFirst().attrs().getStringAttributeValue(Location.ATTR_MARKUP, "");
            return getStartPoint(a2).compareTo(getStartPoint(a1));
          }
        });
        ListIterator<Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean>> it = nds.listIterator(nds.size());
        while(it.hasPrevious()) {
          Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean> nd = it.previous();
          if(!nd.getSecond())
              locationRemoved(nd.getFirst().getFirst(),nd.getFirst().getSecond());
        }
        it = nds.listIterator();
        while(it.hasNext()) {
          Pair<Pair<TreeNode, Collection<WSSocket>>, Boolean> nd = it.next();
          if(nd.getSecond())
            locationCreated(nd.getFirst().getFirst(),nd.getFirst().getSecond());
        }
      }
    };

  private ISocketMessageListener mh = null;

  private JSONParser parser = new JSONParser();

  private boolean isDisposed = false;
  public DocumentsSocketService(TreeNode startNode, FSId fsid, ILinkedStateProvider prov,
      Location loc) {
    super(startNode, "markup", fsid, prov);
    this.initLocation = selLocation = loc;
    if(startNode instanceof Document)
      startDoc = (Document) startNode;
    else
      startDoc = null;
    updateListeners();
  }

  private LocationState getInState(String loc) {
   return locationState.getOrDefault(loc, LocationState.NOT_INITED); 
  }
  @Override
  protected ITreeChangeListener getGUITreeChangeListener() {
    return treeGUIChangeListener;
  }

  private String getNewId(TreeNode selRequirement) {
    return RequalityCLI.getNextId(selRequirement, Requirement.TYPE_NAME,null);
  }
  private Integer getStartPoint(String stored) {
    int st = 0;
    if(stored.startsWith("["))
      st = 1;
    int en = stored.indexOf("|",st);
    try {
      if(st>-1&&en>-1)
        return Integer.parseInt(stored.substring(st, en));
    }catch(NumberFormatException nfe) {
      RLogger.logError("Incorrect markup numbers on "+startDoc.getQualifiedId()+" doc locations ("+stored+")");
    }
    return 0;
  }

  @Override
  protected ITreeChangeListener getTreeChangeListener() {
    return treeChangeListener;
  }
  

  private void beforeDeleted(Document node, WSServer server) {
    if(startDoc == node && server!=null)
    {
      List<WSSocket> socks = null;
      synchronized (resourceNodeToSocks) {
        socks = resourceNodeToSocks.get(node);
        if(socks!=null)
          socks = new LinkedList<>(socks);
      }
      server.sendCloseMessage(socks, "Node "+node.getSUUId()+" was removed", true);
    }
    else{
        //TODO frames deleted?
    }
  }
  
  @Override
  protected ISocketMessageListener getMessageListener() {
    if(mh == null)
      mh = new ISocketMessageListener() {
        @Override
        public boolean handleMessage(WSSocket ws, Session session, String op, String win, JSONObject obj) {
          if(isDisposed)
            return false;
          Object o = obj.get("session");
          String window = CoreUtils.URLDecode((String) obj.get("location"));
          boolean inTool = extractTool(window);
          String doc = (String) obj.get("document");
          if(doc == null || doc.isEmpty())
            doc = extractDoc(win);
          if(doc == null || doc.isEmpty()) {
            doc = extractDocPathComponent(window, db);
          }
          String transf = WSSocket.formKey(win,(String)o);
          transf = CoreUtils.URLDecode(transf);
          TreeNode da = null;
          //TODO rewrite to one more array
          synchronized (resourceNodeToSocks) {
            for(Entry<TreeNode, List<WSSocket>> s:resourceNodeToSocks.entrySet())
              for(WSSocket sa:s.getValue())
                if(sa == ws)
                  da = s.getKey();
          }
          if(da == null)
            da = db.getNode(doc);
          if(da == null)
            return false;
          if(o == null || sess == null)
            return false;//TODO check
          if(!transf.startsWith(window) || o!=null&&!sess.equals(o))
            return false;
        //  String sess = ws!=null?ws.session.get(filterPath(win, da)):null;
          String ssess = WSSocket.formKey(win, sess);
          if(o!=null && (/*not inited*/sess!=null && !o.equals(sess))) {
            RLogger.logError("wrong session call");
            //TODO add  getSocket().dropSession(doc, ssess, session);
            //session.close(1001, "wrong session call");
            //forgetDestination(win);
            return false;
          }
          WSServer wss = getWSProvider();//TODO check wssserver is proper one?
          switch (op){
          case "sendInited":
            locationState.put(transf, LocationState.INITED);
            return true;
            case "printToConsole":
              RLogger.logDebug("ReqMarker: "+(String)obj.get("message"));
            return true;
            case "logInfo":
              RLogger.logInfo("ReqMarkerInfo: "+(String)obj.get("message"));
            return true;
            case "logError":
              RLogger.logError("ReqMarkerError: "+(String)obj.get("message"));
            return true;
            
            case "init":
            
              if(wss == null)
                return false;
              if(da == null)
                RLogger.logError("Null db");
              registerResourceRelatedSocket(ws, da, startDoc);
              setSessionInited(ws, session);
              //selLocation = initLocation;
              //, Pair.createSS("locationId", locationId), Pair.createSS("requirementId", requirementId), Pair.createSS("nextId", next), Pair.createSS("requirementUUID", reqUUID)
              //init socket and then locations
            case "initLocations":

              //registerSocket(da == null?doc:da.getSUUId(), ssess, session);//TODO mb move
              TreeNode[]reqs = SelectionManager.getOne().getSelection(Requirement.TYPE_NAME, new TreeNode[] {da.getTreeDB().getNode(Requirement.getTypeRootQId())});
              if(reqs.length>0 && reqs[0] == null || reqs.length == 0)
                reqs = new TreeNode[] {da.getTreeDB().getNode(Requirement.getTypeRootQId())};
              
              //da = startDoc;//TODO frames
              TreeNode fda = da;
              TreeNode[] freqs = reqs;
              final String ftransf = transf;
              Runnable r = new Runnable() {

                @Override
                public void run() {
                  sendLocations(ws, session, op, fda, sess, getSelLocString());
                  updateRequirement(ftransf, (Requirement) freqs[0], ws);
                }
              };
              r.run(); 
              return true;
              //updateSelection(selLocation!=null?new TreeNode[] {selLocation}:new TreeNode[0], reqs );
      //      return true;
                
            case "reportDocumentLoaded":
              setState(ssess, BrowserState.READY);
              ws.send(session, "setInitialLocation", Pair.createSS("locationId",selLocation!=null?selLocation.getSUUId():"*"));
            return true;
            case "initDocument":
              String asess = (String) obj.get("session");
              String loc = (String) obj.get("location");
              if(loc!=null && asess!=null)
                loc = WSSocket.formKey(loc, asess);
              if(doc!=null && !doc.toString().isEmpty()) {
                registerResourceRelatedSocket(ws, (Document)db.getNode(doc), startDoc);
              }else
                if(loc!=null && !loc.toString().isEmpty()){
                  loadHandlingDoc(loc, ws); 
                }
            return true;  
//                case "setInitialLocation":
//                  try{
//                      String source = (String) obj.get("location");
//                      setTargetLocation(getTime(), source, initialLocation, true);
//                    }catch(Exception e){
//                      e.printStackTrace();
//                    }
//                return true;  
            case "selectLocation":
              long time = getTime();
              String locId = (String) obj.get("locationId");
              Location newTarget = null;
              selLocation = null;
              if(locId!=null && !locId.toString().isEmpty()){
                TreeNode nd = startDoc.getTreeDB().getNode(locId);
                if(nd == null) {
                  RLogger.logError("select location is null for node "+locId);
                }else {
                  if(!(nd instanceof Location))
                    RLogger.logError("on reqmarker location is not loc in selectLocation "+(nd == null?"null":nd.getUserVisibleName()));
                  else
                    newTarget = selLocation = (Location)nd;
                }
                //selectedSource = window; 
              }
              onSelectedLocationChangeByClick(time, newTarget, true);
              
            return true;
//                case "onSelectedLocationChangeByClick":
//                  locId = (String) obj.get("locationId");
//                  selLocation = null;
//                  if(locId!=null && !locId.toString().isEmpty()){
//                    selLocation = (Location) startDoc.getTreeDB().getNode(locId);
//                  }
//                onSelectedLocationChangeByClick(getTime(), location, false);
//                return true;
            case "removeLocation":
              locId = (String) obj.get("locationId");
              Document docu = null;
              if(locId!=null && !locId.toString().isEmpty()){
                Location location = (Location) startDoc.getTreeDB().getNode(locId);
                //selectedSource = window;
                if(location!=null) {
                  docu = (Document) location.getWorkParent();
                  TreeDB db =location.getTreeDB();
                  DeleteLocationOperation dlo = DeleteLocationOperation.getOne();
                  dlo.setIgnoreGUI(!inTool);
                  if(dlo.execute(false, location)==null)
                    docu = null;
                }
              } //UNDOREDO?
//              if(docu!=null)
//                sendLocations("initLocations",win, docu, null,getSelLocString());
//              Map<String, TreeNode[]> sel = SelectionManager.getOne().getSelection();
              //TODO update selection?
//              TreeNode[]rsel = sel.getOrDefault(Requirement.TYPE_NAME, new TreeNode[0]);
//              TreeNode[]lsel = sel.getOrDefault(Location.TYPE_NAME, new TreeNode[0]);
//              updateSelection(lsel,rsel);
            return true;
            case "removeFragment"://OUTDATED
              locId = (String) obj.get("locationId");
              String range = (String) obj.get("range");
              docu = null;
              if(locId!=null && !locId.toString().isEmpty()){
                Location location = (Location) startDoc.getTreeDB().getNode(locId);
                //selectedSource = window;
                if(location!=null) {
                  docu = (Document) location.getWorkParent();
                  TreeDB db =location.getTreeDB();
                  if(DeleteLocationOperation.getOne().execute(false, location)==null)
                    docu = null;
                }
              } //UNDOREDO?
              if(docu!=null)
                sendLocations(ws, session, "initLocations",docu, transf, getSelLocString());//TODO or sess?
//              SelectionRecord sel = SelectionManager.getOne().getSelection();
              //TODO update selection?
//              TreeNode[]rsel = sel.getOrDefault(Requirement.TYPE_NAME, new TreeNode[0]);
//              TreeNode[]lsel = sel.getOrDefault(Location.TYPE_NAME, new TreeNode[0]);
//              updateSelection(lsel,rsel);
            return true;
            case "createLocationLinkRequirement":{
              TreeNode df = da;
              TreeDB db = df.getTreeDB();
              boolean started = false;
              try {
                String desc = (String)obj.get("description");
                String msg = desc.length()>10?desc.substring(0,10):desc;
                String requuid = (String)obj.get("requuid");
                started = db.startTransaction("Create location '"+msg+"' for existing requirement");
                try {
                  //JSONArray locationIds = (JSONArray) obj.get("locationIds");
                  JSONArray locationIds = (JSONArray) parser.parse((String)obj.get("locationIds"));
                  JSONArray locationRegions = (JSONArray) parser.parse((String)obj.get("locationRegions"));
                  obj.put("locationIds", locationIds);
                  obj.put("locationRegions", locationRegions);
                  List<Object> frags = new LinkedList<>();
                  for(int i = 0;i<locationRegions.size();i++){
                    JSONObject oj = (JSONObject)locationRegions.get(i);
                    frags.add(oj.get("start")+"|"+oj.get("end"));
                  };
                  
                  
                  JSONObject jo = new JSONObject();
                  //TODO replace by old-new AddSelectionToRequirement
                  Location l = createLocation(df, frags, desc);
                  obj.put("uuid", l.getSUUId());
                  obj.put("lid", locationIds);
                  linkLocation(l, requuid);
                  if(started) {
                    db.commit();
                    started = false;
                  }
              //    getSocket().send(df.getSUUId(),ssess,"createLocation", false, obj);
                }catch(Exception e) {
                  RLogger.logError(e);
                }
              }finally {
                if(started)
                  db.rollback();
              }
            }return true;
            case "createLocationCreateRequirement":{
              Document df = (Document) da;
              TreeDB db = df.getTreeDB();
              boolean started = false;
              try {
                String reqid = (String)obj.get("requrementId");
                String reqname = (String)obj.get("requrementName");
                
                String desc = (String)obj.get("description");
                String msg = desc.length()>10?desc.substring(0,10):desc;
                String reqnid = reqid!=null?reqid:reqname;
                started = db.startTransaction("Create location '"+msg+"' and create new requirement '"+reqnid+"'");
                JSONArray locationIds = (JSONArray) parser.parse((String)obj.get("locationIds"));
                JSONArray locationRegions = (JSONArray) parser.parse((String)obj.get("locationRegions"));
                obj.put("locationIds", locationIds);
                obj.put("locationRegions", locationRegions);
                List<Object> frags = new LinkedList<>();
                for(int i = 0;i<locationRegions.size();i++){
                  JSONObject oj = (JSONObject)locationRegions.get(i);
                  frags.add(oj.get("start")+"|"+oj.get("end"));
                };
                
                String reqparent = (String)obj.get("requrementParent");
                
                JSONObject jo = new JSONObject();
                //TODO replace by old-new AddSelectionToRequirement
                Location l = createLocation(df, frags, desc);
                obj.put("uuid", l.getSUUId());
                
                TreeNode node = createRequirement(reqparent, reqid, reqname);
                if(node == null)
                {
                  RLogger.logError("Requirement was not created during creation of location "+desc);
                }else
                  linkLocation(l, node.getSUUId());
                if(started) {
                  db.commit();
                  started = false;
                }
                obj.put("lid", locationIds);
              //  getSocket().send(df.getSUUId(),ssess,"createLocation", false, obj);
              }catch(Exception e) {
                RLogger.logError(e);
                if(started)
                  db.rollback();
              }
            }return true;

            case "linkLocationLinkRequirement":{
              Document df = (Document) da;
              TreeDB db = df.getTreeDB();
              boolean started = false;
              try {
                String lid = (String) obj.get("locationId");
                String requuid = (String) obj.get("requuid");
                if(requuid == null)
                  requuid = df.getTreeDB().getNode(Requirement.getTypeRootQId()).getSUUId();
                Location l = (Location) df.getTreeDB().getNode(lid);
                started = db.startTransaction("Link location '"+l.getUserVisibleName()+"' to existing requirement '"+requuid+"'");
                
                linkLocation(l, requuid);
                if(started) {
                  db.commit();
                  started = false;
                }
              }catch(Exception e) {
                RLogger.logError(e);
                if(started)
                  db.rollback();
              }          
            }return true;
            case "linkLocationCreateRequirement":{
              Document df = (Document) da;
              TreeDB db = df.getTreeDB();
              boolean started = false;
              try {
                String requrementParent = (String) obj.get("requrementParent");
                if(requrementParent == null)
                  requrementParent = df.getTreeDB().getNode(Requirement.getTypeRootQId()).getSUUId();
                Location l = (Location) df.getTreeDB().getNode((String) obj.get("locationId"));
                String reqid = (String)obj.get("requrementId");
                String reqname = (String)obj.get("requrementName");
                String reqnid = reqid!=null?reqid:reqname;
                JSONArray ja = new JSONArray();
      //          obj.put("uuid", l.getSUUId());
                started = db.startTransaction("Link location '"+l.getUserVisibleName()+"' and create requirement '"+reqnid+"'");
                
                
                JSONObject jo = new JSONObject();
                //TODO replace by old-new AddSelectionToRequirement
                
                linkLocation(l, createRequirement(requrementParent, reqid, reqname).getSUUId());
                if(started) {
                  db.commit();
                  started = false;
                }
      //          for(Requirement r:l.getRequirements()) {
      //            JSONObject jor = new JSONObject();
      //            jor.put("uuid", r.getSUUId());
      //            jor.put("name", r.getNameOrId());
      //            jor.put("qid", r.getQualifiedId());
      //            ja.add(jor);
      //          }
      //          obj.put("requirements", ja);
      //          socket.send(df.getSUUId(),window,"createLocation", false, obj);
              }catch(Exception e) {
                RLogger.logError(e);
                if(started)
                  db.rollback();
              }
            }return true;
          }
          return false;
        }

        private boolean extractTool(String window) {
          String inT = extractParam(window, "inTool");
          return inT != null && inT.equals("1");
        }
        private String extractDocPathComponent (String location, TreeDB db) {
          String[]components = location.split("/");
          if(components.length>4 && components[4].equalsIgnoreCase("documents")) {
            String dbNode = components[5];
            int ind = dbNode.indexOf("::");
            String node = null, dbStr = null;
            if(ind>-1)
            {
              node = dbNode.substring(ind+2);
              dbStr = dbNode.substring(0, ind);
            }else
              node = dbNode;
            if(dbStr!=null) {
              TreeDB dba = db.getTracker().getTreeDB(db, dbStr, dbStr, false, ConsoleProgressMonitor.ONE);
              if(dba!=db)
                return null;
            }
            return node;
          }
          return null;
        }

        private String extractDoc(String win) {
          return extractParam(win, "document");
        }

        private String extractParam(String win, String param) {
          int doc = win.indexOf(param+"=");
          if(doc>0)
          {
              int r = win.indexOf("&", doc+1);
              int k = win.indexOf("/", doc+1);
              int l = win.length();
              if(r>-1 && r<l)
                  l = r;
              if(k>-1 && k<l)
                  l = k;
              return win.substring(doc+param.length()+1, l);
          }
          return null;
        }

        private void forgetDestination(String win) {
          // TODO Auto-generated method stub
          
        }

        @Override
        public String getId() {
          return getFSIDFull();
        }
      };
    return mh;
  }

  private TreeNode createRequirement(String parent, String reqid, String reqname) {
    TreeNode parentNode = null;
    if(parent != null && !parent.equalsIgnoreCase("null") && !parent.isEmpty())
      parentNode = startDoc.getTreeDB().getNode(parent);
    if(parentNode == null)
      parentNode = startDoc.getTreeDB().getNode(Requirement.getTypeRootQId());
    if(reqid == null || reqname!=null && !reqname.isEmpty())
      reqid = getNewId(parentNode);
    TreeNode node = CreateRequirementOperation.getOne().execute(true, parentNode, reqid, false, null);
    if(node!=null && reqname!=null) {
      node.setName(reqname);
      node.commitAttributes();
    }
    return node;
  }
    //    private Location createLocation(Document df, String reqname, List<Object> frags, String desc) {
//      Location l = (Location)df.createChild(RequalityCLI.getNextId(df,Location.TYPE_NAME,""),Location.TYPE_NAME);
//      l.attrs().setAttribute(Location.ATTR_MARKUP, );
//      l.commitAttributes();
//    }
  protected void linkLocation(Location l, String requuid) {
    Requirement req = (Requirement) startDoc.getTreeDB().getNode(requuid);
    
    if(req == null) {
      RLogger.logError("No requirement provided for link loc");
      req = (Requirement) startDoc.getTreeDB().getNode(Requirement.getTypeRootQId());
    }
      
    req.addLocation(l.getUUId());
    req.commitAttributes();
  }
  protected Location createLocation(TreeNode d, List<Object> frags, String desc) {
    if(!(d instanceof Document))
      throw new RuntimeException("Cannot create location on non-document node "+d.getUserVisibleName());
    Location loca = ((Document)d).createLocation(null);
    frags.sort(Location.FRAGMENT_SORTER);
    loca.attrs().setConstAttribute(Location.ATTR_MARKUP, new ListValue(AttributeType.STRING, frags), Flag.HIDDEN);
        //undo = (String) jsData[1];
        //final String undof = undo;
        //TODO add undo
        //saveDocument(d, doca,selectedSource,undof);
      
    if (desc == null){
      //d.getLocationIndexMap();//loadDescs //tends not working for now, needs rework
      //desc = d.getLocationsDescriptions().get(loca.getUUId());
    }
    d.commitAttributes();
    if(desc!=null){
      File resFolder = new File(loca.getNodeResourcesPath(TreeNode.ATTR_DESCRIPTION));
      TempFolderFilesProvider added = TempFolderFilesProvider.getOne(resFolder, false);
      String val = RequalityCLI.updateImages(d.getNodeResourcesPath(), desc, added,true);
      HtmlValueProvider hvp = new HtmlValueProvider(AttributeType.HTML, val, null, added);
      loca.setDescription(hvp);
      loca.commitAttributes();
    }
      
    return loca;
  }

  private void locationCreated(TreeNode l, Collection<WSSocket> sessa) {
    if(!l.getType().equals(Location.TYPE_NAME))
      return;
    JSONObject jo = new JSONObject();
    List<Object> lo = l.attrs().getListAttributeValue(Location.ATTR_MARKUP);
    if(lo == null)
      return;
    JSONArray ranges = new JSONArray();
    for(Object la:lo) {
      String ls = (String) la;
      if(ls == null)
        continue;
      int pos = ls.indexOf(Location.FRAGMENT_SEPARATOR);
      if(pos == -1)
        continue;
      String start = ls.substring(0,pos), end = ls.substring(pos+1);
      JSONObject loa = new JSONObject();
      loa.put("start", start);
      loa.put("end", end);
      ranges.add(loa);
    }
    jo.put("locationRegions", ranges);

    jo.put("uuid", l.getSUUId());
    
    //jo.put("lid", obj.get("lid"));
    
    TreeNode doc = l.getWorkParent();
    for(WSSocket wss: currentInitedSockets)
      wss.send("createLocation", jo, wss.inited);
  }
  private void locationRemoved(TreeNode l, Collection<WSSocket>sessa) {
    if(!l.getType().equals(Location.TYPE_NAME))
      return;
    JSONObject jo = new JSONObject();

    jo.put("uuid", l.getSUUId());

    TreeNode doc = l.getWorkParent();
    Location location = (Location) l;
    for(WSSocket wss: currentInitedSockets)
      wss.send("removeLocation", jo, wss.inited);
    
  }
  
  public void onSelectedLocationChangeByClick(long time, Location loc, boolean upreq) {
    SelectionManager sman = SelectionManager.getOne();
    SelectionRecord sr = sman.getSelection(fsid);//actual selection
    TreeNode selNow = sr.fullSelection.isEmpty()?null:sr.fullSelection.iterator().next();
    TreeNode newSelRec = null;
    if(selNow != loc){
      selLocation = loc;
      if(loc!=null) {
        Requirement[]reqs = loc.getRequirements();
        if(loc!=null && reqs!=null && reqs.length>0)
          newSelRec = reqs[0];
      }
    }else
      if(loc!=null)
      {
        Requirement[] r = loc.getRequirements();
        for(int i = 0 ;i<r.length;i++)
            if(r[i] == selRequirement)
            {
                int j = i+1;
                if(j == r.length)
                  j = 0;
                newSelRec = r[j];
                break;
            }
      }
    if(locChangeEnabled && locationChangedHandler!=null)
      locationChangedHandler.onLocationChanged(time, loc, upreq, true);
    //TODO selRequirement update by newSelRec
  }
  protected String getSelLocString() {
    Location l = selLocation;
    if(l==null)
      l = initLocation;
    return l!=null?l.getSUUId():null;
  }

  public void setTargetLocation(long time, Location loc, boolean first) {
    setTargetLocation(time, null, loc, first);
  }
  
  public void setTargetLocation(long time, String source, Location loc, boolean first) {
    //source tends to be related to frames
    if(actionTime>time)
      return;
    if(source == null)
      source = "";//TODO remove this arg
    Pair<Document, Collection<WSSocket>> det = detectWhere(startDoc, loc);
    //TODO mb return source check?
//    if(det!=null) {
//      Collection<WSSocket> sockets = det.getSecond();
//      
//    }
//    if(det!=null&&det.getSecond()!=null&&!det.getSecond().equals("")&&!source.equals("") && !det.getSecond().equals(source))
//      return;//ignore frames calls
    //TODO mb change to null checks ||
    //ignoreSetLocation = true;
    try {
      if (first) {
        Location l = loc;
        Requirement r = null;
        if (l != null) {
          this.selLocation = l;
          //locId = l.getSUUId();
          //selectedSource = det == null? null : det.getSecond();
          Requirement[] rqs = l.getRequirements();
          if (isLinked() && rqs.length > 0) r = rqs[0];
        }else
        {
          this.selLocation = null;
        //  selectedSource = "";
        }
      }

      setTargetLocation(det!=null?det.getSecond():null,loc);//TODO test det
    } finally {
//      ignoreSetLocation = false;
    }
  }
  private Pair<Document,Collection<WSSocket>> detectWhere(Document doc, Location loc) {
    if(loc!=null)
      doc = loc.getDocument();
    if(doc!=null){
      if(hasRelatedSessions(doc))
      {
        Collection<WSSocket> sess = getRelatedSessions(doc);
        return new Pair<>(doc, sess); 
      }
    }
    return null;
  }

  protected void sendLocations(WSSocket ws, Session s, String op, TreeNode d, String fullsess, String selLoc) {
  if(ws == null)
      return;
    StringBuilder rangy = new StringBuilder();
    JSONObject ret = new JSONObject();
    JSONArray locs = new JSONArray();
    int cnt = 1;
    if(d!=null) {
      List<TreeNode> locsr = d.getSortedShownChildren();
      boolean floc = true;
      for(TreeNode node:locsr)
      {
        JSONObject nodeO = new JSONObject();
        JSONArray fragsf = new JSONArray();
        List<Object> frags = node.attrs().getListAttributeValue("_markup");
        if(frags!=null) {
          for(Object o:frags)
          {
            String os = o.toString();
            fragsf.add(os);
            rangy.append("|");
            int pos = os.indexOf("|");
            
            rangy.append((Integer.parseInt(os.substring(0,pos)))+"");
            rangy.append("$");
            rangy.append((Integer.parseInt(os.substring(pos+1)))+"");
            rangy.append("$");
            rangy.append(cnt++);
            rangy.append("$fragment$html_container");
          }
        }
        nodeO.put("uuid", node.getSUUId());
        nodeO.put("fragments", fragsf);
        nodeO.put("id", Integer.toString(cnt-1));//TODO several fragments?
        locs.add(nodeO);
      }
      if(fullsess!=null)
        ret.put("session", fullsess);
      ret.put("locations", locs);
      ret.put("rangy", "type:textContent"+rangy);
      ret.put("document", d.getSUUId());
      ret.put("locationId", selLoc!=null?selLoc:"");
      ret.put("readOnly", Boolean.toString(testIsReadOnly(db)));
    }
    ws.send(op, ret, s);//TODO separate send on null d
  }

  protected long sendMessage(WSSocket ws, Session sess, String method, Pair<String,String> ... data) {
    if(ws == null)
      return -1;
    return ws.send(sess, method, data);
  }
  @Override
  public boolean registerResourceRelatedSocket(WSSocket location, TreeNode doc2, TreeNode docPar) {
    boolean b = super.registerResourceRelatedSocket(location, doc2, docPar);
    if(b)
      for(IDocumentAddedHandler doh: docAddHandlers)
        doh.onDocAdded(doc2);
    return b;
  }
  public void loadHandlingDoc(String location, WSSocket sess) {
//    if(allDocs.containsKey(location))
//      return;
     Document doc2 = findDocument(location);
     if(doc2!=null){
       registerResourceRelatedSocket(sess, (Document)doc2, startDoc);
     }
  }
  public Document findDocument(String location) {
    int last = location.lastIndexOf("#");
    if(last>-1)
      location = location.substring(0,last);
    File p1 = new File(URI.create(location));
    File p2 = startDoc.getNodeResourcesFile().toPath().resolve(startDoc.getResourceName()).toFile();
    if (p1.exists() && p2.exists()) {
      FileSystem fs = null;
      try {
        fs = p1.toPath().getFileSystem();
        String rp =
          CoreUtils.getRelativePath(
              fs, p2.toURI().toString(), p1.toURI().toString());
        TreeNode doc2 = tryFind(p2, startDoc, p1, rp.split("/"));
        return (Document) doc2;
      } finally {
        if(fs!=null) {
          try {fs.close();}catch(Exception e) {}
        }
      }
    }
    return null;
  }
  public TreeNode tryFind(File f, TreeNode doc2, File filefrom, String[] raw) {
    int st = 0, max = raw.length;
    if (filefrom.getName().toLowerCase().endsWith(".xhtml")
        && filefrom.getParentFile().getName().toLowerCase().endsWith(ResManager.RESOURCES_SUFFIX)) {
      if (raw[st].equals(".")) st++;
      if (raw[st].equals("..")) st++;
      String name = filefrom.getName();
      raw[raw.length - 2] = name;
      max = raw.length - 1;
    }
    return tryFind(doc2, raw, st, max);
  }

  protected TreeNode tryFind(TreeNode doc2, String[] raw, int ind, int max) {
    if (ind >= max) return doc2;
    if (raw[ind].equals(".")) ind++;
    if (raw[ind].equals("..")) {
      TreeNode node = doc2.getWorkParent();
      if(node!=null)
        return tryFind(node, raw, ind + 1, max);
      return null;
    }
    TreeNode node = doc2.findWorkChild(raw[ind]);
    if (node != null) return tryFind(node, raw, ind + 1, max);
    return null;
  }
  public long getTime() {
    return CoreUtils.getTime();
  }

  private INodeChangeListener selectionncl = new INodeChangeListener() {
    
    @Override
    public void moved(TreeNode node, TreeNode oldParent) {
      // TODO Auto-generated method stub
      
    }
    
    @Override
    public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
      // TODO Auto-generated method stub
      
    }
    
    @Override
    public void deleted(TreeNode source) {
      // TODO Auto-generated method stub
      
    }
    
    @Override
    public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
      if(what.contains(TreeNode.ATTR_CHILDREN)) {
        updateAfterAttrChange(what.getNew(TreeNode.ATTR_CHILDREN));
        //updateSelection(selLocation!=null?new TreeNode[] {selLocation}:null, new TreeNode[] {lastsel});
      }
    }
    
    private void updateAfterAttrChange(Attribute a) {
      if(selLocation == null)
        return;
      boolean changed = a == null;
      if(a!=null && a.getType() == AttributeType.LIST) {
        ListValue lv = (ListValue) a.getValue();
        boolean was = false;
        for(Object o:lv.getValues())
          if(o == selLocation.getUUId())
            was = true;
        if(!was)
          changed = true;
      }else
        changed = true;
      if(changed){
        updateLocation(startDoc, null);
      }
    }

    @Override
    public void attributeChange(TreeNode source, String what, Attribute old) {
      if(what.equals(TreeNode.ATTR_CHILDREN)) {
        updateAfterAttrChange(source.attrs().getAttribute(what));
      }
        //updateSelection(selLocation!=null?new TreeNode[] {selLocation}:null, new TreeNode[] {lastsel});
    }
  };


  
  public void updateTargetRequirement(long time, Requirement req) {
    //TODO adds to impl
//    if(selRequirement == null || req == null || !req.getSUUId().equals(selRequirement.getSUUId())){
//      targind = 0;
//    }else
//      targind++;
//    if(req == null)
//      return;
//    List<Location> locationList = req.getLocations();
//    if(locationList.size() == 0){
//      RLogger.logInfo("Call for target update on non-location-related requirement("+req.getQualifiedId()+")");
//      return;
//    }
//    if(targind>=locationList.size())
//      targind = 0;
//    if (locationList.size() == 0) return;
//    Pair<Document, String> p = null;
//    while(targind<locationList.size() && p == null) {
//      Location location = locationList.get(targind);
//      p = setTargetLocation(time, location);
//    }
//    if(targind>=locationList.size()){
//      targind = 0;
//      Location location = locationList.get(targind);
//      setTargetLocation(time, location);
//    }
  }

  private Pair<Document, Collection<WSSocket>> setTargetLocation(long time, Location location) {
      Pair<Document, Collection<WSSocket>> p = detectWhere(startDoc,  location);
      Collection<WSSocket> ps = p == null? null:p.getSecond();
      if(ps!=null)
        setTargetLocation(time, null, location, true);
      return p;
  }
  
  public void updateRequirement(String win, Requirement breq, WSSocket ... wss) {
    if(wss == null || wss.length == 0)
    return;
    if(startDoc == null)
      return;
    if(breq == null)
      breq = (Requirement) startDoc.getTreeDB().getNode(Requirement.getTypeRootQId());
    boolean changes = false;
    final Requirement req = breq;  
    if(selRequirement == req) {
      String reqNewId = req.getUserVisibleName();
      String newnext = RequalityCLI.getNextId(req, Requirement.TYPE_NAME, null);
      if(lastIdSended == null || !newnext.equals(lastIdSended)) 
        changes = true;
      if(lastSendedReqId == null || !lastSendedReqId.equals(reqNewId))
        changes = true;
      //lastIdSended = newnext;
    }else
      changes = true;
    if(changes)
      JobManager.jm.startJob("update selected requirement in markup "+startDoc, new RunnableWithMonitor() {
        
        @Override
        public void run(IProgressMonitor pm) {
          //Requirement req = null;
          try {
            if(selRequirement!=null)
              selRequirement.removeChangeListener(selectionncl);
            //TODO non 1 length array with null value on 1st
            String requirementId = null;
            
            selRequirement = req;
            if(selRequirement.isDisposed())
              selRequirement = null;
            if(req!=null)
              req.addChangeListener(selectionncl);
            String next = "001";
            if(req == null || req.isDisposed())
              requirementId = "";
            else {
              requirementId = req.getUserVisibleName();
              next = getNewId(req);
              lastIdSended = next;
            }
            String reqUUID = (req == null || req.isDisposed()) ? "" : req.getSUUId();
            for(WSSocket ws:wss) {
              Session s=ws.inited;
              ws.send(s, "updateRequirement", Pair.createSS("requirementId", requirementId), Pair.createSS("nextId", next), Pair.createSS("requirementUUID", reqUUID));
            }
          }finally {
           // updateSelection(location, req, false);
          }
        }
      }, false);
  }
  public void updateLocation(Document doc, Location bloc) {
    if(startDoc == null)
      return;
    boolean changes = false;
    final Location loc = bloc;  
    if(selLocation != loc) 
      changes = true;
    Pair<Document, Collection<WSSocket>> wh = detectWhere(doc, loc);
    if((loc == null || wh!=null) && changes)
      JobManager.jm.startJob("update selection ", new RunnableWithMonitor() {
        
        @Override
        public void run(IProgressMonitor pm) {
          try {
            if(wh == null)
              return;
            //TODO non 1 length array with null value on 1st
            String locationId = loc == null?"":loc.getSUUId();
            DocumentsSocketService.this.selLocation = loc;
            for(WSSocket s: wh.getSecond()) {
              try {
                s.send(s.inited, "updateLocation",Pair.createSS("locationId", locationId));
              }catch(Exception ea) {
                RLogger.logError(ea);
              }
            }
          }catch(Exception e) {
            RLogger.logError(e);
          }finally {
            //(location, req, false);
          }
        }
      }, false);
  }

  public void setTargetLocation(Location location) {
    activateLocation(location);
  }
  public void setTargetLocation(Collection<WSSocket> where,Location location) {
    if (location != null) {
      setTargetLocation(where,location.getId());
    } else activateLocation(where,"");
  }
  /** @param targetRequirement - Target requirement or null */
  public void setTargetLocation(Collection<WSSocket>  where, String locationId) {
    if (locationId != null) {
      activateLocation(where,locationId);
    } else {
      activateLocation(where,"");
      RLogger.logInfo("ReqDocBrowser.setTargetLocation(Location location): argument is null");
      // TODO: why location is null?
    }
  }
  public void activateLocation(Collection<WSSocket> where, String locationId) {
    if(where == null)
      where = currentInitedSockets;//all
    if(locationId == null)
      locationId = "";
    boolean change = false;
    if(locationId!=null) {
      Location nsel = (Location) db.getNode(locationId);
      if(selLocation!=nsel)//TODO change compare
        change = true;
    }
    if(change)
//      for(Entry<String, Document> en:allDocs.entrySet())
//      {
//        if(en.getKey().equals(where) && getInState(en.getKey())==LocationState.INITED)//TODO test
      for(WSSocket s: where)
        s.send(s.inited,"activateLocation", Pair.createSS("locationId", locationId));
      //}
  }
  public void activateLocation(Location location) {
    Pair<Document, Collection<WSSocket> > loc = detectWhere(startDoc, location);
    if(loc!=null) {
      updateLocation(loc.getFirst(), location);
    }
  }
  public boolean containsDoc(TreeNode workParent) {
    synchronized (resourceNodeToSocks) {
      return resourceNodeToSocks.getOrDefault(workParent, Collections.emptyList()).size()>0;
    }
  }
/**
   * <code>addSelectionTo</code> must be called in transaction
   *
   * @param req
   * @param oid
   * @return array of objects. [0] - selection description, [1] - location node
   * @throws UnsupportedEncodingException
   */
 
  public void deleteLocation(Location loc) {
    loc.delete();
    // TODO implement call on socket
  }
  public void disableLocationChangeListener() {
    locChangeEnabled = false;
  }

  public void enableLocationChangeListener() {
    locChangeEnabled = true;
  }
  public void deleteLocations(Collection<Location> hashSet) {
    for(Location l:hashSet)
      l.delete();
    // TODO implement call on socket
  }

  public Document getStartDocument() {
    return startDoc;
  }

  @Override
  protected URI getUrl(TreeNode startNode, String session) {
    return ResourcesProvider.getOne().formDocumentURL(startDoc, session, getPort());
  }

  public void newRequirements(Document doc, Collection<TreeNode> reqs) {
    Requirement nreq = null;
    if(reqs!=null && reqs.size()>0)
      nreq = (Requirement)reqs.iterator().next();
    if(nreq!=null) {
      if(nreq != selRequirement)
        updateRequirement(null, nreq, getInitedSocketsArray());
    }
    else
      updateRequirement(null, null, getInitedSocketsArray());
  }
  public void newLocations(Document doc, Collection<TreeNode> loc) {
    Location nloc = null;
    if(loc!=null) {
      if(loc.size()>0)
        nloc = (Location) loc.iterator().next();

      for(TreeNode sela:loc) {
        Location l = (Location)sela;
        if(hasRelatedSessions(l.getWorkParent())){//TODO check fo
          nloc = l;
          break;
        }
      }
    }
    if(loc!=null)
      updateLocation(doc, nloc);
    else
      RLogger.logDebug("incedent");
  }

  private static Set<String> readOnlyOps;
  static {
    readOnlyOps = new HashSet<>();
    readOnlyOps.add("init");
    readOnlyOps.add("sendInited");
    readOnlyOps.add("printToConsole");
    readOnlyOps.add("logInfo");
    readOnlyOps.add("logError");
    readOnlyOps.add("initLocations");
    readOnlyOps.add("reportDocumentLoaded");
    readOnlyOps.add("initDocument");
    readOnlyOps.add("selectLocation");
  }
  @Override
  public boolean isReadOnlyOperation(String op) {
    return readOnlyOps.contains(op);
  }

}
