package com.unitesk.requality.core.service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.IContextStringProvider;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.Version;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.CopyHandler;
import com.unitesk.requality.core.operation.DropNodeOperation;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.PasteHandler;
import com.unitesk.requality.core.operation.delete.DeleteNodeOperation;
import com.unitesk.requality.core.operation.undoredo.UndoRedoMech;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.PastingIntoLeafException;
import com.unitesk.requality.core.tree.ITreeLogic.ProjectNotLoadedException;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.core.tree.ITreeLogic.TreeLogicException;
import com.unitesk.requality.core.tree.ITreeLogic.WrongProjectException;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.document.ILinkedStateProvider;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.tool.message.RMessages;

public class EditorsSocketService extends GenericSocketService {
  private LocationState state = LocationState.NOT_INITED;
  private int editMode;
  private SortingSettings storedSortingSettings = null;
  private ISelectionChangedHandler selChangeHandler;
  public Set<String> additionalAttrs = new HashSet<>();
  public Set<String> systemAttrs = new HashSet<>();

  private String editorString;
  public String fullSession = null;

  ITreeChangeListener treeChangeListener = new DefaultTreeChangeListener() {
    @Override
    public void deleted(TreeNode node, byte flag, Moment m) {
      super.deleted(node, flag, m);
    }

    public void deleted(TreeNode node) {
    }

    private void reactMoved(TreeNode node, TreeNode oldParent, TreeNode newParent) {

    }

    @SuppressWarnings("unchecked")
    @Override
    public void changed(List<TreeNodeChangeEvent> changes) {
      List<UUID> toup = new LinkedList<>();
      for (TreeNodeChangeEvent ce : changes)
        switch (ce.getType()) {
        case DELETE: {
          if (ce.getMoment() == Moment.BEFORE) {
            TreeNode node = ce.getTreeNodeTarget();
            continue;
          }
          break;
        }
        case MOVE:
          if (ce.getMoment() == Moment.BEFORE)
            continue;
          TreeNode node = ce.getTreeNodeTarget();
          TreeNode par = (TreeNode) ce.getArguments()[0];
          TreeNode npar = (TreeNode) ce.getArguments()[1];
          reactMoved(node, par, npar);
          break;
        default:
          break;
        }
    }

  };

  public EditorsSocketService(TreeNode startNode, String editor, FSId fsid, int editMode, ILinkedStateProvider prov, ISelectionChangedHandler selChangeHandler) {
    super(startNode, editor, fsid, prov);
    editorString = editor;
    this.editMode = editMode;
    storedSortingSettings = db.getSortingSettings();
    this.selChangeHandler = selChangeHandler;
    updateListeners();
  }

  private ISocketMessageListener hl = null;

  @Override
  protected ITreeChangeListener getTreeChangeListener() {
    return treeChangeListener;
  }

  protected void sendTemplates(WSSocket ws, Session s, String loc, JSONArray obj) {
    if (ws == null)
      return;
    JSONObject ret = new JSONObject();
    ret.put("templates", obj);
    ws.send("updateNodeTemplates", ret, s);
  }

  protected void sendRedLinkSelectionReturn(WSSocket ws, Session session, String loc, String node, String[] nds) {
    if (ws == null)
      return;
    JSONObject ret = new JSONObject();
    ret.put("node", node);
    JSONArray ja = new JSONArray();
    for (String s : nds)
      ja.add(s);
    ret.put("nodes", ja);
    ws.send("sendRedLinkSelectionReturn", ret, ws.inited);
  }

  protected void sendNameCheckState(WSSocket ws, Session session, String loc, String node, boolean state,
      String newName) {
    if (ws == null)
      return;
    JSONObject ret = new JSONObject();
    ret.put("node", node);
    ret.put("state", state);
    ret.put("newName", newName);
    ws.send("sendNameCheckState", ret, session);
  }

  private static int MAXUUIDS = 65536 / 55;

  protected void sendCheckChildsResult(WSSocket ws, Session session, String loc, String node, String newName,
      List<String> corr) {
    if (ws == null)
      return;
    JSONObject ret = new JSONObject();
    ret.put("node", node);
    ret.put("newName", newName);
    JSONArray ja = new JSONArray();
    int i = 0;
    for (String s : corr) {
      ja.add(s);
      i++;
      if (i > MAXUUIDS) {
        i = 0;
        ret.put("nodes", ja);
        ws.send("sendCheckChildsResult", ret, session);
        ja = new JSONArray();
      }
    }
    if (ja.size() > 0) {
      ret.put("nodes", ja);
      ws.send("sendCheckChildsResult", ret, session);
    }
    ret.remove("nodes");
    ws.send("sendCheckChildsResultOver", ret, session);
  }

  protected void sendCheckChildsResult(WSSocket ws, Session session, String loc, String node, String[] nodes) {
    if (ws == null)
      return;
    JSONObject ret = new JSONObject();
    ret.put("node", node);
    JSONArray ja = new JSONArray();
    int i = 0;
    for (String s : nodes) {
      ja.add(s);
      i++;
      if (i > MAXUUIDS) {
        i = 0;
        ret.put("nodes", ja);
        ws.send("sendCheckChildsResult", ret, session);
        ja = new JSONArray();
      }
    }
    if (ja.size() > 0) {
      ret.put("nodes", ja);
      ws.send("sendCheckChildsResult", ret, session);
    }
    ret.remove("nodes");
    ws.send("sendCheckChildsResultOver", ret, session);
  }

  protected void wrongLinkUpdateResult(WSSocket ws, Session s, String euuid, String sel, String node, String uvid) {
    if (ws == null)
      return;
    ws.send(s, "wrongLinkUpdateResult", new Pair<>("euuid", euuid), new Pair<>("sel", sel),
        new Pair<>("node", node), new Pair<>("uvid", uvid));
  }

  public void sendNewSelectedNode(String node) {
    for (WSSocket s : currentInitedSockets)
      s.send(s.inited, "sendNewSelectedNode", new Pair<>("node", node));

  }

  protected void sendLoadNodeByUUIDResult(WSSocket ws, Session session, String loc, String node, String res) {
    if (ws == null)
      return;
    JSONObject jo = new JSONObject();
    jo.put("node", node);
    JSONParser parser = new JSONParser();
    JSONObject json;
    try {
      json = (JSONObject) parser.parse(res);
      jo.put("data", json);
    } catch (ParseException e) {
      jo.put("data", res);
    }
    ws.send("sendLoadNodeByUUIDResult", jo, session);
  }

  protected void sendLoadNodeTreeByUUIDResult(WSSocket ws, Session session, String loc, String node, String[] ares) {
    // TODO parallel?
    if (ws == null)
      return;
    for (String res : ares) {
      JSONObject jo = new JSONObject();
      jo.put("node", node);
      JSONParser parser = new JSONParser();
      JSONArray json;
      try {
        Object j = parser.parse(res);
        json = (JSONArray) j;
        jo.put("data", json);
      } catch (ParseException e) {
        jo.put("data", res);
      }
      ws.send("sendLoadNodeTreeByUUIDResult", jo, session);
    }
    try {
      Thread.currentThread().join(100);
    } catch (InterruptedException e) {
    }
    ws.send("sendLoadNodeTreeByUUIDResultFinish", new JSONObject(), session);
  }

  public void sendEvents(String location, JSONArray events, Collection<WSSocket> wss) {
    WSServer wso = getWSProvider();
    if (wso == null)
      return;
    // Look into frame concept of socket
    for (WSSocket ws : wss)
      ws.send("sendEvents", events, ws.inited);
//    JobManager.jm.startJob("Send events for "+db.getSemiLocalName(), new RunnableWithMonitor() {
//      
//      @Override
//      public void run(IProgressMonitor pm) {
//        getSocket().send("{'operation':'onEvents', 'data':\"" + escape(val) + "\"}");
//      }
//    });
  }

  public void oldRefresh(TreeNode node) {
    WSServer wss = getWSProvider();
    if (wss == null)
      return;
    // TODO implement
    JSONObject ret = new JSONObject();
    ret.put("node", node.getSUUId());
    for (WSSocket ws : currentInitedSockets)
      ws.send("oldRefresh", ret, ws.inited);
  }

  public void sendEvents(JSONObject events) {
    WSServer wss = getWSProvider();
    if (wss == null)
      return;
    // Look into frame concept of socket
    for (WSSocket ws : currentInitedSockets)
      ws.send("sendEvents", events, ws.inited);
//    JobManager.jm.startJob("Send events for "+db.getSemiLocalName(), new RunnableWithMonitor() {
//      
//      @Override
//      public void run(IProgressMonitor pm) {
//        getSocket().send("{'operation':'onEvents', 'data':\"" + escape(val) + "\"}");
//      }
//    });
  }

  public void sendEvents(String events, WSSocket... sess) {
    // Look into frame concept of socket
    WSServer wss = getWSProvider();
    if (wss == null)
      return;
    if (sess == null || sess.length == 0)
      sess = currentInitedSockets.toArray(new WSSocket[currentActiveSockets.size()]);
    Pair<String, String> p = new Pair<>("data", events);
    for (WSSocket s : sess)
      s.send(s.inited, "sendEvents", p);
  }

  public void sendChangeUpdatingState(boolean b) {
    WSServer wss = getWSProvider();
    if (wss == null)
      return;
    for (WSSocket ws : currentInitedSockets)
      ws.send(ws.inited, "sendEvents", new Pair<>("sendChangeUpdatingState", Boolean.toString(b)));
  }

  @Override
  protected ISocketMessageListener getMessageListener() {
    if (hl == null)
      hl = new ISocketMessageListener() {

        @Override
        public boolean handleMessage(WSSocket ws, Session session, String op, String rwin, JSONObject message) {
          if (isDisposed)
            return false;
          String window = CoreUtils.URLDecode((String) message.get("location"));
          String transf = filterPath(window, startNode);
          transf = CoreUtils.URLDecode(transf);
          String fs = getComplexSession(transf);
          Object o = message.get("session");
          if (!window.startsWith(transf) || o != null && !sess.equals(o))
            return false;
          switch (op) {
          case "init":
            WSServer wss = getWSProvider();
            if (wss == null) {
              // disconnect(session);
              return false;
            }
            // sess = ws.session.get(transf);
            if (o != null && ((sess != null || !o.equals("null")) && !o.equals(sess))) {
              // TODO add getSocket().dropSession(doc, ssess, session);
              // session.close(1001, "wrong session call");
              // disconnect(session);
              return false;
            }
            fullSession = fs;
            setSessionInited(ws, session);
            // registerSocket(startNode.getSUUId(), fullSession, session);//TODO mb move
            // init socket and then locations
            String fo = (String) o;
            Runnable ir = new Runnable() {
              @Override
              public void run() {
                sendMessage(ws, session, op, Pair.createSS("session", fo),
                    Pair.createSS("editMode", Integer.toString(editMode)),
                    Pair.createSS("editorName", editorString),
                    Pair.createSS("readOnly", Boolean.toString(testIsReadOnly(db))));
              }
            };
            ir.run();
            registerResourceRelatedSocket(ws, startNode, null);
            return true;
          case "printToConsole": // message
            System.out.println(message.get("message"));
            return true;
          case "callOpenTemplates": // node
            String type = Requirement.TYPE_NAME;

            if (message.containsKey("node")) {
              String node = (String) message.get("node");
              if (node == null || node.length() > 4) {
                UUID nodeUUID = UUID.fromString(node);
                type = db.getNodeType(nodeUUID);
              }
            }
            RMessages.openView(db, Consts.TEMPLATES_SELECTION_DIALOG_NAME, type);
            // TODO return?
            return true;
          case "sendInited":
            state = LocationState.INITED;
            return true;
          case "changeNodeId": {// node, newId
            String node = (String) message.get("node");
            final TreeNode n = db.getNode(UUID.fromString(node));
            if (n == null) {
              RLogger.logDebug("Cannot change an id for a node that we cannot find " + node);
              return true;
            }
            String newId = (String) message.get("newId");
            if (newId.equals("")) {
              sendEvents(n.attrs(), new Object[] { "changeAttributes", node,
                  new Object[] { new Object[] { "_nameOrId", n.getNameOrId(), n.getNameOrId() } } },
                  ws);
            } else {
              try {
                newId = newId.replace((char) 160, ' ').replace("&nbsp;", " ").trim();
                if (db.getTreeLogic().isIdValidExc(n, newId)) {
                  if (db.startTransaction("Update " + n.getTypeLabel() + " Id")) {
                    n.setId(newId);
                    n.commitAttributes();
                    db.commit();
                  } else
                    return true;
                } else {
                  RMessages.openError("Update " + n.getTypeLabel() + " Id",
                      "Id of element cannot be changed to " + newId, null, false);
                  return true;
                }
              } catch (IllegalStateException ise) {
                RMessages.openError("Update " + n.getTypeLabel() + " Id",
                    "Some actions are already performing.", CoreUtils.getTrace(ise.getStackTrace()),
                    false);
                return true;
              } catch (SameIdExistsInChildren e) {
                RMessages.openError("Update " + n.getTypeLabel() + " Id",
                    "Element with same id exists in " + n.getWorkParent().getUserVisibleName(false),
                    CoreUtils.getTrace(e.getStackTrace()), false);
                return true;
              } catch (IdEmpty e) {
                RMessages.openError("Update " + n.getTypeLabel() + " Id", "Element id cannot be empty",
                    CoreUtils.getTrace(e.getStackTrace()), false);
                return true;
              } catch (IdContainsWrongChars e) {
                RMessages.openError("Update " + n.getTypeLabel() + " Id",
                    "Element id cannot contains wrong character - " + e.getWrongCharacter(),
                    CoreUtils.getTrace(e.getStackTrace()), false);
                return true;
              }
            }
          }
            break;
          case "callEditNode":// node
            String node = (String) message.get("node");
            final TreeNode par = db.getNode(UUID.fromString(node));
            if (par == null) {
              RLogger.logError("Cannot load treeNode " + node
                  + " during editor callEditNode because it cannot be found");
              return true;
            }

            boolean inTool = false;
            if (message.containsKey("inTool")) {
              Object ints = message.get("inTool");
              if (ints != null && (ints instanceof Boolean && (Boolean) ints == true
                  || ints.toString().equals("true")))
                inTool = true;
            }
            if (!inTool)
              return true;// ignore calls on non-tool mode
            final String title = "Edit '" + par.getUserVisibleName(false) + "'";
            String jobname = "Update '" + par.getUserVisibleName(false) + "' element";
            JobManager.jm.startDisplayJob(jobname, new RunnableWithMonitor() {
              @Override
              public void run(IProgressMonitor mon) {
                if (!db.startTransaction(jobname))
                  return;
                Object o = RMessages.openView(db, Consts.EDIT_ELEMENT_DIALOG_NAME, false, node, title);
                if (o == null || (int) o == 1) {// cancel
                  db.rollback();
                  return;
                }
                db.commit();
              }
            }, false);
            return true;
          // fixed
          case "createNode":// node, type and possibly title, addid, attributes[]{"key"->value}
            node = (String) message.get("node");
            TreeNode par5 = db.getNode(UUID.fromString(node));
            if (par5 == null) {
              RLogger.logDebug("Cannot create a node on parent that we cannot find " + node);
              return true;
            }
            type = (String) message.get("type");
            // final TreeNode nde = db.getNode(UUID.fromString((String) node));
            String id = null;// RequalityCLI.getNextId(nde, nde.getType(), null);;
            String title2 = "Edit " + CoreUtils.splitWords(type);
            String addid = null;
            Map<String, Object> attrs = new HashMap<>();
            if (message.containsKey("title"))
              title2 = (String) message.get("title");
            if (message.containsKey("addid"))
              addid = (String) message.get("addid");
            inTool = false;
            if (message.containsKey("inTool")) {
              Object ints = message.get("inTool");
              if (ints != null && (ints instanceof Boolean && (Boolean) ints == true
                  || ints.toString().equals("true")))
                inTool = true;
            }
            if (message.containsKey("attributes")) {
              JSONArray ja = (JSONArray) message.get("attributes");
              for (int i = 0; i < ja.size(); i++) {
                JSONObject jo = (JSONObject) ja.get(i);
                for (Object ok : jo.keySet()) {
                  Object val = jo.get(ok);
                  attrs.put((String) ok, val);
                }
              }
            }
            TreeNode template = par5.getTreeDB().getActiveTemplate(type);
            TreeNode after = (addid == null || addid.equals("")) ? null
                : db.getNode(UUID.fromString(addid));
            title2 = title2.replace("[template]", template == null ? "" : (" - " + template.getId()));
            if (id == null || id.length() == 0) {
              if (db.getTreeLogic().isAutoRenamable(type)) {
                id = RequalityCLI.getNextId(par5, type, type + " ");
              } else {
                if (type.equals(TestPurpose.TYPE_NAME)) {
                  String parid = par5.getId();
                  if (par5 instanceof Requirement)
                    parid = ((Requirement) par5).getNameOrId();
                  id = RequalityCLI.getNextId(par5, type, parid + "_T");
                } else {
                  id = RequalityCLI.getNextId(par5, type, null);
                }
              }
            }
            id = id.replace((char) 160, ' ').replace("&nbsp;", " ").trim();
            TreeLogic tl = db.getTreeLogic();
            try {
              if (!tl.canBeCreatedInExc(type, id, par5)) {
                RMessages.openError("Add Element", "Element of type '" + type
                    + "' cannot be created in " + par5.getUserVisibleName(true), null, false);

              }
            } catch (PastingIntoLeafException e) {
              RMessages.openError("Add Element",
                  "Can't add " + type + " to element with "
                      + (type.equals(TestPurpose.TYPE_NAME) ? "non-" : "") + "TestPurpose child",
                  null, false);
              return true;
            } catch (SameIdExistsInChildren e) {
              RMessages.openError("Add Node",
                  "Element with same id exists in " + par5.getUserVisibleName(true), null, false);
              return true;
            } catch (IdEmpty e) {
              RMessages.openError("Add Node", "Element id is empty", null, false);
              return true;
            } catch (IdContainsWrongChars e) {
              RMessages.openError("Add Node",
                  "Element id contains wrong character - " + e.getWrongCharacter(), null, false);
              return true;
            } catch (ProjectNotLoadedException e1) {
              RMessages.openError("Add Node",
                  "Project " + par5.getTreeDB().getVisibleName() + " is not loaded", null, false);
              return true;
            }
            if (type == null || type.isEmpty())
              type = par5.getType();
            final String ftype = type, fid = id, ftitle = title2;
            boolean finTool = inTool;
            String jobName = "Create " + CoreUtils.splitWords(ftype);
//            JobManager.jm.startDisplayJob(jobName, new RunnableWithMonitor() {
//
//              @Override
//              public void run(IProgressMonitor pm) {
            // TODO Auto-generated method stub
            if (db.startTransaction(jobName)) {
              try {
                TreeNode newChi = par5.createChild(fid, ftype);
                boolean save = false;
                if (db.getTreeLogic().isAutoRenamable(ftype) || after != null) {
                  Index ind = Index.createOne();
                  ind = (Index.getNextIndex(ftype, par5, after, false));
                  newChi.setIndex(ind, false);
                  save = true;
                }
                if (ftype.equals(TestPurpose.TYPE_NAME)) {
//                            node.attrs()
//                                .setAttribute(TestPurpose.ATTR_AUTHOR, System.getProperty("user.name", ""));
                  newChi.attrs().setConstAttribute(TestPurpose.ATTR_AUTHOR,
                      System.getProperty("user.name", ""), Flag.HIDDEN);
                  newChi.attrs().setConstAttribute(TestPurpose.ATTR_STATUS, "in process",
                      Flag.HIDDEN);
                  save = true;
                }
                // for comments we adds default author
                if (ftype.equals(Comment.TYPE_NAME)) {
                  newChi.attrs().setConstAttribute(Comment.ATTR_AUTHOR,
                      System.getProperty("user.name", ""), Flag.HIDDEN);
                  // node.setIndex(ind);
                  save = true;
                }
                if (!attrs.isEmpty()) {
                  TempFolderFilesProvider prov = TempFolderFilesProvider.getOne(newChi.getNodeResourcesFile(), true);
                  for (String s : attrs.keySet())
                    newChi.attrs()
                        .setAttribute(new AttributeDefinition(newChi.getUUId(), s,
                            AttributeDefinition.detectValueProvider(s, AttributeType.STRING,
                                AttributeDefinition.detectFlags(s), attrs.get(s), prov)));
                  save = true;
                }
                if (save)
                  newChi.commitAttributes();
//                        if (finTool) {
//                          Object ret = RMessages.openView(db, Consts.EDIT_ELEMENT_DIALOG_NAME, false,
//                              newChi.getSUUId(), ftitle);
//                          if (ret == null || (int) ret == 1) {
//                            db.rollback();
//                            return;
//                          }
//                        }
                db.commit();

                // TODO send message "focus"? "created" already tends to be sended
                return true;// newChi.getSUUId();
              } catch (Exception e) {
                RLogger.logError(e);
                db.rollback();
              }
            }
//              }
//            }, false);

            return true;
          case "addTest":// node
            node = (String) message.get("node");

            final TreeNode nd = db.getNode(UUID.fromString(node));
            if (nd == null) {
              RLogger.logDebug("Cannot add a test for a node that we cannot find " + node);
              return true;
            }
            inTool = false;
            if (message.containsKey("inTool")) {
              Object ints = message.get("inTool");
              if (ints != null && (ints instanceof Boolean && (Boolean) ints == true
                  || ints.toString().equals("true")))
                inTool = true;
            }
            if (!inTool)
              return true;// ignore calls on non-tool mode
            if (nd.getType().equals(Requirement.TYPE_NAME) || nd.getType().equals(TestPurpose.TYPE_NAME) || nd.getType().equals(TextNode.TYPE_NAME))
              RMessages.openView(db, Consts.TEST_DIALOG_PROVIDER_CREATE_NAME, true, nd.getQualifiedId());
            return true;
          case "openIdChange":// node, title, message, lastValue [idCheck]
            node = (String) message.get("node");
            final TreeNode nde = db.getNode(UUID.fromString(node));
            if (nde == null) {
              RLogger.logDebug("Cannot open id change for a node that we cannot find " + node);
              return true;
            }
            String title3 = (String) message.get("title");
            String msg = (String) message.get("message"), lastValue = (String) message.get("lastValue");
            boolean idcheck = true;
            if (message.containsKey("idCheck"))
              idcheck = (Boolean) message.get("idCheck");
            final boolean checkId = idcheck;
            RMessages.openView(db, RMessages.ID_CHANGE_DIALOG, node, title3, msg, lastValue,
                Boolean.toString(checkId));
//              browser.forceFocus();
//              javaDialogOpened = false;
//              if (id.getReturnCode() == InputDialog.CANCEL) return null;
//              return id.getValue();
            // TODO check messages to be delivered
            return true;
          case "changeNodeName":// node, newName
            node = (String) message.get("node");
            TreeNode n = db.getNode(UUID.fromString(node));
            if (n == null) {
              RLogger.logDebug("Cannot change a name for a node that we cannot find " + node);
              return true;
            }
            if (!n.getType().equals(Requirement.TYPE_NAME)) {
              return true;
            }
            String newName = (String) message.get("newName");
            if (newName != null)
              newName = newName.replace((char) 160, ' ').replace("&nbsp;", " ").trim();
            boolean started = db.startTransaction("Change name of " + n.getUserVisibleName() + "("
                + n.getTypeLabel() + ") to " + (newName != null ? newName : "empty"), true);
            if (started) {
              try {
                ((Requirement) n).setName(newName);
                n.commitAttributes();
                if (started)
                  db.commit();
              } finally {
                if (started)
                  db.rollback();
                ;
              }
            }
            return true;
          case "changeName":// node, newName, [parent]
            newName = (String) message.get("newName");
            node = (String) message.get("node");
            n = null;
            if (newName != null && newName != "")
              newName = newName.replace((char) 160, ' ').replace("&nbsp;", " ").trim();
            boolean wasName = false;
            if (!(node).equals("")) {
              n = db.getNode(UUID.fromString((String) node));
              if (n == null) {
                RLogger.logDebug("Cannot change a name for a node that we cannot find " + node);
                return true;
              }
              if (n != null && n.getType().equals(Requirement.TYPE_NAME)) {
                Attribute a = n.attrs().getAttribute(TreeNode.ATTR_NAME);
                wasName = a != null && a.getValue() != null && ((String) a.getValue()).length() > 0;
                if (a == null && (newName == null || newName.isEmpty()) || a != null
                    && a.getValue() != null && a.getValue() != "" && a.getValue().equals(newName))
                  return true;
              }
              // if (newNamenewName.equals("")) newName = n.getId();
            }
            started = db.startTransaction(
                "Change " + (n == null ? node : (n.getUserVisibleName() + "(" + n.getTypeLabel() + ")"))
                    + " name to " + (newName == null ? "empty" : newName),
                true);
            try {
              TreeNode parent = null;
              if (n != null)
                parent = n.getWorkParent();
              if (message.containsKey("parent")) {
                String ps = (String) message.get("parent");
                if (ps != null && !ps.isEmpty())
                  parent = db.getNode(UUID.fromString((String) message.get("parent")));
                if (parent == null) {
                  RLogger.logDebug(
                      "Cannot change a name for node which parent we cannot find " + node);
                  return true;
                }
              }
              // TODO maybe change to string error message
              if ((n == null) || (parent == null) || newName != null && newName.equals("")) {
                RLogger.logError("Error during name check - nodes are null " + node);
                return true;
              }
              if (n != null && db.getTreeLogic().isAutoRenamable(n.getType()))
                return true;
              if (newName == null || newName.equals("")) {
                n.setName(null);
                n.attrs().commitAttributes();
                started = false;
                db.commit();
                sendNameCheckState(ws, session, transf, node, true, newName);
              } else if (reqIdValid(n, parent, newName)) {
                n.setName(newName);
                n.attrs().commitAttributes();
                started = false;
                db.commit();
              } else
                sendNameCheckState(ws, session, transf, node, false, newName);

            } finally {
              if (started)
                db.rollback();
            }
            return true;
          case "changeNodeAttr":// node, key, value
            try {
              node = (String) message.get("node");
              UUID uuid = UUID.fromString(node);
              n = db.getNode(uuid);
              if (n == null) {
                RLogger.logDebug("Cannot change an attribute for a node that we cannot find " + node);
                return true;
              }
              String key = (String) message.get("key");
              String value = (String) message.get("value");
              if (key.equals(TreeNode.ATTR_NAME)) {
                if (n.getName() != null) {
                  if (value != null)
                    value = value.replace((char) 160, ' ').trim();
                  if (value != null && value.length() > 0 && value.charAt(0) == ((char) 255)) {

                  } else {
                    started = db
                        .startTransaction(
                            "Change " + n.getUserVisibleName() + "(" + n.getTypeLabel()
                                + ") name to " + (value == null ? "empty" : value),
                            true);
                    if (started) {
                      try {
                        n.setName(value);
                        n.commitAttributes();
                        if (started) {
                          db.commit();
                          started = false;
                        }
                      } finally {
                        if (started)
                          db.rollback();
                      }
                    } else
                      return true;
                  }
                } else if (n.getName() == null) {
                  if (value != null) {// TODO null val?
                    value = value.replace((char) 160, ' ').replace("&nbsp;", " ").trim();
                    if (value.length() > 0 && value.charAt(0) == ((char) 255)) {
                      sendEvents(n.attrs(),
                          new Object[] { "changeAttributes", node,
                              new Object[] { new Object[] { "_nameOrId", n.getNameOrId(),
                                  n.getNameOrId() } } },
                          ws);
                    } else {
                      try {
                        if (db.getTreeLogic().isIdValidExc(n, value)) {
                          started = db.startTransaction("Update " + n.getTypeLabel() + " Id",
                              true);
                          if (started) {
                            try {
                              n.setId(value);
                              n.commitAttributes();
                              if (started) {
                                started = false;
                                db.commit();
                              }
                            } finally {
                              if (started) {
                                started = false;
                                db.rollback();
                              }
                            }
                            // Activator.getDefault().setActiveNode(n, n.getType());
                          }
                        } else {
                          RMessages.openError("Update " + n.getTypeLabel() + " Id",
                              "Id of element cannot be changed to " + value, null, false);
                          return true;
                        }
                      } catch (SameIdExistsInChildren e) {
                        RMessages.openError("Update " + n.getTypeLabel() + " Id",
                            "Element with same id exists in "
                                + n.getWorkParent().getUserVisibleName(false),
                            null, false);
                        return true;
                      } catch (IdEmpty e) {
                        RMessages.openError("Update " + n.getTypeLabel() + " Id",
                            "Element id cannot be empty", null, false);
                        return true;
                      } catch (IdContainsWrongChars e) {
                        RMessages.openError("Update " + n.getTypeLabel() + " Id",
                            "Element id cannot contains wrong character - "
                                + e.getWrongCharacter(),
                            null, false);
                        return true;
                      }
                    }
                  }
                }
              } else {
                started = db.startTransaction("Update " + n.getTypeLabel(), true);
                if (started) {
                  try {
                    AttributeType atype = AttributeType.STRING;
                    if(Attribute.getHTMLNames().contains(key)) {
                      atype = AttributeType.HTML;
                      // RequalityCLI.deleteOldImages(n.attrs().getAttribute(key), value, n);
                    }
                    Attribute attr = null;
                    if (!value.equals("")) {
                      n.attrs()
                          .setAttribute(new AttributeDefinition(n.getUUId(), key,
                              AttributeDefinition.detectValueProvider(key,
                                atype, value, 
                                TempFolderFilesProvider.getOne(n.getNodeResourcesFile(), true)),
                                AttributeDefinition.detectFlags(key)));
                      attr = n.attrs().getAttribute(key);
                    } else {
                      n.attrs().removeAttribute(key);
                    }
                    n.commitAttributes();
                    if (started) {
                      started = false;
                      db.commit();
                    }
                  } finally {
                    if (started)
                      db.rollback();
                  }
                }
              }
            } catch (Exception e) {
              RLogger.logError(e);
            } catch (Throwable e) {
              e.printStackTrace();
            }
            return true;
          case "changeNodeParent":// node, newParent, altPressed
            node = (String) message.get("node");
            n = db.getNode(UUID.fromString((String) node));
            if (n == null) {
              RLogger.logDebug("Cannot change a parent for a node that we cannot find " + node);
              return true;
            }
            TreeNode parent = db.getNode(UUID.fromString((String) message.get("newParent")));
            if (parent == null) {
              RLogger.logDebug("Cannot change a parent to a parent that we cannot find "
                  + message.get("newParent"));
              return true;
            }
            boolean shiftPressed = (Boolean) message.get("shiftPressed");
            List<TreeNode> src = new ArrayList<>();
            src.add(n);
            DropNodeOperation.execute(db, src, parent, false, shiftPressed);
            return true;
          case "removeNode": // node
            node = (String) message.get("node");
            n = db.getNode(UUID.fromString(node));
            if (n == null) {
              RLogger.logDebug("Cannot remove node that we cannot find " + node);
              return true;// cannot delete but message handled
            }
            TreeNode par2 = null;
            par2 = n.getShownParent();
            DeleteNodeOperation.deleteNodes(Arrays.asList(new TreeNode[] { n }), true);
            if (par2 == null || par2.isDisposed())
              par2 = n.getTreeDB().getRootNode();
            // setSelectedNode(par); //TODO needed?
            return true;
          case "setRequalityFocus":// node
            node = (String) message.get("node");
            TreeNode nd2 = db.getNode(UUID.fromString(node));
            if (nd2 == null) {
              RLogger.logDebug("Cannot remove node that we cannot find " + node);
              return true;
            }
            // SelectionManager.getOne().changeActiveSource(fsid.getEventSource(),fsid,
            // false);
            setSelectionOnManager(nd2);
            // TODO send to eclipse editor?
            return true;

          case "copyNode":// node, cut
            try {
              String snode = (String) message.get("node");
              try {
                TreeNode nde2 = db.getNode(UUID.fromString(snode));
                if (nde2 == null) {
                  RLogger.logDebug("Cannot remove node that we cannot find " + snode);
                  return true;
                }
                if (!db.getTreeLogic().isCopyable(nde2))
                  return true;
                boolean cutNode = false;
                if (message.containsKey("cut"))
                  cutNode = (Boolean) message.get("cut");
                (new CopyHandler()).copyNodes(Arrays.asList(new TreeNode[] { nde2 }), cutNode);
              } catch (Exception e) {
                RLogger.logError("Cannot copy node " + snode);
                RLogger.logError(e);
              }
            } catch (Exception e) {
              RLogger.logError(e);
            }
            return true;
          case "pasteNode":// node, [pasteTree], [nodeAfter]
            boolean trStarted = false;
            try {
              String parentS = (String) message.get("node");
              TreeNode parentN = db.getNode(UUID.fromString(parentS)), lastnode = null;
              if (parentN == null) {
                RLogger.logDebug("Cannot paste to node that we cannot find " + parentN);
                return true;
              }

              inTool = false;
              if (message.containsKey("inTool")) {
                Object ints = message.get("inTool");
                if (ints != null && (ints instanceof Boolean && (Boolean) ints == true
                    || ints.toString().equals("true")))
                  inTool = true;
              }
              if (!inTool)
                return true;// ignore calls on non-tool mode
              boolean pasteTree = false;
              if (message.containsKey("pasteTree"))
                pasteTree = (Boolean) message.get("pasteTree");
              if (message.containsKey("nodeAfter")) {
                String na = (String) message.get("nodeAfter");
                if (!na.isEmpty()) {
                  lastnode = db.getNode(UUID.fromString((String) message.get("nodeAfter")));
                  if (lastnode == null) {
                    RLogger.logDebug("Cannot paste node after on we cannot find "
                        + message.get("nodeAfter"));
                    return true;
                  }
                }
              }

              Object ret = (new PasteHandler()).execute(parentN, lastnode, pasteTree, false, false, true,
                  true);
              jobname = "Edit elements call";
//              JobManager.jm.startDisplayJob(jobname, new RunnableWithMonitor() {
//                @Override
//                public void run(IProgressMonitor mon) {
              if (ret != null && ret instanceof TreeNode[]) {
                TreeNode[] nodes = (TreeNode[]) ret;
                TreeDB cancelTrans = null;
                if (nodes.length > 0)
                /* if (nodes.length == 1) */ {// TODO mb change
                  TreeNode nde2 = nodes[0];
//                      Object r = RMessages.openView(db, Consts.EDIT_ELEMENT_DIALOG_NAME, false,
//                          nde2.getSUUId(), "Paste element '" + nde2.getNameOrId() + "'");
//                      if (r == null)// cancel
//                      {
//                        cancelTrans = nde2.getTreeDB();
//                      }
                }
                if (cancelTrans != null)
                  UndoRedoMech.callUndo();
                return true;// changes to true because message is still handled here
              } else {
                if (ret != null)
                  RLogger.logDebug(ret.toString());
                return true;
              }
//                }
//              }, false);
              // test step, temporary here
            } catch (RuntimeException e) {
              RLogger.logError(e);
              return true;
            }
          case "fireRedLinkSelection":// node, link
            node = (String) message.get("node");
            String link = (String) message.get("link");
            if (link == null)
              link = "";
            List<String> filter = db.getLinkToTypeFilter();
            String[] nds = link.isEmpty() ? null
                : db.getNodesByUFName(link.replace((char) 160, ' ').replace("&nbsp;", " ").trim(),
                    filter);
            if (nds == null)
              nds = new String[0];
            sendRedLinkSelectionReturn(ws, session, transf, node, nds);
            return true;
          // [0] - test procedure UUID
          // [1] - previous test step UUID
          case "createTestStep": // node, [nodeAfter]
            String name = (String) message.get("node");
            TreeNode par4 = db.getNode(UUID.fromString(name));
            if (par4 == null) {
              RLogger.logDebug("Cannot create test step on node that we cannot find " + name);
              return true;
            }
            try {
              if (!db.getTreeLogic().canBeCreatedInExc("TestStep", par4)) {
                RMessages.openError("Add Element", "Element of type Test Step"
                    + " cannot be created in " + par4.getUserVisibleName(true), null, false);
                return true;
              }
            } catch (PastingIntoLeafException e) {
              return true;
            } catch (ProjectNotLoadedException e) {
              RMessages.openError("Add Element", "Project " + db.getVisibleName() + " is not loaded",
                  null, false);
              return true;
            }
            String nodeAfter = null;
            if (message.containsKey("nodeAfter"))
              nodeAfter = (String) message.get("nodeAfter");
            if (nodeAfter == null || nodeAfter.length() == 0) {
              createTestStep(par4, Index.createOne());
              return true;
            }
            TreeNode node2 = db.getNode(UUID.fromString(nodeAfter));
            if (node2 == null) {
              RLogger.logDebug("Cannot add test step after a node that we cannot find " + nodeAfter);
              return true;
            }
            TreeNode ts = createTestStep(par4, Index.getNextIndex("TestStep", par4, node2));
            // TODO event? mb focus
            // return ts.getSUUId();
            return true;
          case "removeTestPurposeLink":// node
            node = (String) message.get("node");
            TreeNode nde3 = db.getNode(UUID.fromString(node));
            if (nde3 == null) {
              RLogger.logDebug("Cannot remove test purpose link that we cannot find " + node);
              return true;
            }
            if (nde3 == null)
              return true;
            // TreeNode anode = db.getNode(s);
            if (nde3.attrs().removeAttribute(TreeNode.ATTR_LINKS) != null)
              nde3.commitAttributes();
            // add event to be fired after?
            return true;

          case "addSingleTestPurposeToTestStep":// node
            node = (String) message.get("node");
            TreeNode nde4 = db.getNode(UUID.fromString(node));
            if (nde4 == null) {
              RLogger.logDebug("Cannot add test purpose for node we cannot find " + node);
              return true;
            }
            Object ret = RMessages.openView(db, Consts.SELECT_TEST_PURPOSE_DIALOG_NAME, false);
            if (ret != null) {
              TreeNode res = (TreeNode) ret;
              started = db.startTransaction(
                  "Add Test Purpose " + res.getUserVisibleName() + " to " + nde4.getUserVisibleName(),
                  true);
              if (started) {
                try {
                  nde4.attrs().removeAttribute(TreeNode.ATTR_LINKS);
                  nde4.commitAttributes();
                  RelManager.addReferenceToList(nde4, res.getUserVisibleName(), res,
                      TreeNode.ATTR_LINKS);
                  nde4.commitAttributes();
                  if (started) {
                    db.commit();
                    started = false;
                  }
                  // refresh(node);
                  // refresh(res);
                } catch (Exception r) {
                  RLogger.logError(r);
                } finally {
                  if (started)
                    db.rollback();
                }
              }
            }
            return true;
          case "openEditorForNode":// node
            node = (String) message.get("node");
            TreeNode nde5 = db.getNode(node);
            if (nde5 == null) {
              RLogger.logDebug("Cannot open an editor on node that we cannot find " + node);
              return true;
            }
            inTool = false;
            if (message.containsKey("inTool")) {
              Object ints = message.get("inTool");
              if (ints != null && (ints instanceof Boolean && (Boolean) ints == true
                  || ints.toString().equals("true")))
                inTool = true;
            }
            if (!inTool)
              return true;// ignore calls on non-tool mode

            final TreeNode sel = nde5;
            String tp = nde5.getType();
            if (!nde5.getQualifiedId().equals(Requirement.getTypeRootQId()))
              nde5 = nde5.getShownParent();
            String editorid = Consts.UniEditorID;
            if (tp.equals(Document.TYPE_NAME))
              editorid = Consts.ReqMarkerID;
            RMessages.openEditor(editorid, nde5, sel);
            return true;
          case "loadNodeByUUID":
            node = (String) message.get("node");
            if (node == null || node.equals("null")) {
              RLogger.logError("Tries to load null node on " + startNode.getUserVisibleName() + " editor["
                  + EditorsSocketService.this.sess + "]");
              return true;
            }
            UUID nodeUUID = nodeReferenceUUID((String) node); // VSCODE-PATCH: a location addressed by its path
            String result = "";
            if (nodeUUID == null || db == null)
              return true;
            try {
              n = db.getNode(nodeUUID);
              result = node2jsonString(n);
              if (result == null) {
                RLogger.logDebug("node not found during loadNode from editor :" + nodeUUID);
                return true;
              }
            } catch (Exception e) {
              RLogger.logError(e);
            }
            sendLoadNodeByUUIDResult(ws, session, transf, node, result);
            return true;
          case "loadNodeTreeByUUID":
            node = (String) message.get("node");
            nodeUUID = nodeReferenceUUID((String) node); // VSCODE-PATCH: a location addressed by its path
            String[] arrresult = new String[0];
            if (nodeUUID == null || db == null)
              return true;
            try {
              n = db.getNode(nodeUUID);
              if (n == null)
                return true;
              arrresult = nodeTree2jsonString(n);

            } catch (Exception e) {
              RLogger.logError(e);
            }
            sendLoadNodeTreeByUUIDResult(ws, session, transf, node, arrresult);
            return true;
          case "checkChilds":// node, newName, childs
            node = (String) message.get("node");
            n = db.getNode(UUID.fromString(node));
            if (n == null) {
              RLogger.logDebug("Cannot check a child on node that we cannot find " + node);
              return true;
            }
            boolean all = n != null && db.getTreeLogic().isAutoRenamable(n.getType());
            newName = ((String) message.get("newName")).replace("&nbsp;", " ").replace((char) 160, ' ')
                .trim();
            JSONArray ja = (JSONArray) message.get("childs");
            List<String> corr = new LinkedList<>();
            TreeLogic tlo = db.getTreeLogic();
            for (int i = 0; i < ja.size(); i++) {
              String chiId = (String) ja.get(i);
              if (chiId == null)
                continue;
              TreeNode chi = db.getNode(UUID.fromString((String) chiId));
              if (chi == null) {
                RLogger.logDebug("Cannot check child when some of childs we cannot find " + node);
                return true;
              }
              DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(chi, false, true, false);
              Iterator<TreeNode> it = dftw.iterator();
              while (it.hasNext()) {
                TreeNode chi2 = it.next();
                if (all || reqChildValid(n, chi2, newName) && tlo.canBeChildOf(n.getType(), chi2))
                  corr.add(chi2.getSUUId());
              }
            }
            sendCheckChildsResult(ws, session, transf, node, newName, corr);
            return true;
          case "loadTemplates":
            sendTemplates(ws, session, transf);
            return true;
          case "selectFileDialog":
            String exts = (String) message.get("exts");
            if (CoreUtils.DEBUG_LEVEL > 0) {
              RLogger.logDebug(System.currentTimeMillis() + " :" + exts.toString());
            }
            ret = RMessages.openView(db, RMessages.ID_FILE_DIALOG, false, exts);
            if (ret == null) {// send null
              return true;
            }
            // send
            sendSelectFileDialogResult(ws, transf, ret == null ? "" : (String) ret, session);
            return true;
          case "addNewTestPurposeToEditor":// euuid, sel, node, tolink
            String euuid = (String) message.get("euuid"), tolink = (String) message.get("tolink"),
                ssel = (String) message.get("sel");
            node = (String) message.get("node");
            TreeNode nde7 = db.getNode(UUID.fromString((String) node));
            if (nde7 == null) {
              RLogger.logDebug("Cannot add a test purpose to editor for node we cannot find " + node);
              return true;
            }
            ret = RMessages.openView(db, Consts.SELECT_TEST_PURPOSE_DIALOG_NAME, false);
            if (ret != null) {
              // TODO rewrite to old model
              TreeNode res = (TreeNode) ret;
              started = db.startTransaction(
                  "Add Test Purpose " + res.getUserVisibleName() + " to " + nde7.getUserVisibleName(),
                  true);
              if (started) {
                try {
                  nde7.attrs().removeAttribute(TreeNode.ATTR_LINKS);
                  nde7.commitAttributes();
                  RelManager.addReferenceToList(nde7, res.getUserVisibleName(), res,
                      TreeNode.ATTR_LINKS);
                  nde7.commitAttributes();
                  // nde7.getTreeDB().commit();
                  /*
                   * node = addNewTestPurposeToEditor(); if(node != null){ wrongLinkUpdate(euuid,
                   * sel, node, getUFName(node)); }
                   */
                  // refresh(node);
                  // refresh(res);
                  if (started) {
                    started = false;
                    db.commit();
                  }
                  wrongLinkUpdateResult(ws, session, euuid, ssel, res.getSUUId(),
                      res.getUserVisibleName());

                  return true;
                } catch (Exception r) {
                  RLogger.logError(r);
                  if (started)
                    db.rollback();
                }
              }
              wrongLinkUpdateResult(ws, session, euuid, ssel, res.getSUUId(), res.getUserVisibleName());
              return true;// TODO true?
            }
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

  protected void setSelectionOnManager(TreeNode nd2) {
    if (fsid != null) {
      if(selChangeHandler!=null)
        selChangeHandler.run(nd2);
    }
  }

  protected void disconnect(Session session) {
    try {
      session.disconnect();
    } catch (IOException e) {
      RLogger.logError(e);
    }
  }

  private void sendTemplatesChanges() {
    // TODO rewrite
    // TODO fire on templates id changes or when selected template is changed too
    // wsserv = getSocket();
    String str = ResourcesProvider.getOne().formEditorURL(editorString, startNode, sess, getPort()).toString();
    for (WSSocket s : currentActiveSockets)
      sendTemplates(s, s.inited, str);
  }

  protected void sendSelectFileDialogResult(WSSocket ws, String transf, String result, Session session) {
    JSONArray ja = new JSONArray();
    if (ws == null)
      return;
    JSONObject ret = new JSONObject();
    ret.put("result", result == null ? "" : result);
    ws.send("sendSelectFileDialogResult", ret, session);
  }

  private void sendTemplates(WSSocket ws, Session session, String transf) {
    if (ws == null || session == null)
      return;
    JSONArray ja = new JSONArray();
    Map<String, String> templ = db.getActiveTemplates();
    for (Entry<String, String> en : templ.entrySet()) {
      JSONObject jo = new JSONObject();
      jo.put(en.getKey(), en.getValue());
    }
    sendTemplates(ws, session, transf, ja);
  }

  protected void sendMessage(WSSocket ws, Session s, String op, Pair<String, String>... createSS) {
    if (ws == null)
      return;
    ws.send(op, createSS, s);
  }

  @Deprecated
  protected void sendMessage(WSSocket ws, String loc, String suuId, String session, String op, boolean b,
      Pair<String, String>... createSS) {
    if (ws == null)
      return;
    ws.send(op, createSS, ws.inited);
  }

  public void sendEvents(AttributeStorage as, final Object deleted, WSSocket... sess) {
    if (sess.length == 0)
      return;
    JSONArray jar = new JSONArray();
    dataToString(as, deleted, jar);
    String dis = escape(jar.toString());
    sendEvents(dis, sess);
  }

  private static String escape(String a) {
    a = (a + " ").replaceAll("\\%([^0-9%]+)", "%25$1");
    a = a.substring(0, a.length());
    return a.replace("\\\"", "\\\\\"").replace("\"", "\\\"").replace("\\n", "\\\\n").replace("\\r", "\\\\r")
        .replace("\\t", "\\\\t");
    // if(a.startsWith("\\\""))
    // a = a.substring(1,a.length()-1);
    // return a.replace("\"","\\\\\"");
  }

  private TreeNode createTestStep(TreeNode parent, Index index) {
    String type = "TestStep";
    String id = RequalityCLI.getNextId(parent, type, null);
    if (parent.findWorkChild(id) == null) {
      try {
        if (!db.getTreeLogic().canBeCreatedInExc(type, id, parent)) {
          RMessages.openError("Add Element",
              "Element of type 'TestStep" + "' cannot be created in " + parent.getUserVisibleName(true),
              null, false);
          return null;
        }
      } catch (PastingIntoLeafException e) {
        return null;
      } catch (SameIdExistsInChildren e) {
        RMessages.openError("Add Element", "Element with same id exists in " + parent.getUserVisibleName(true),
            null, false);
        return null;
      } catch (ProjectNotLoadedException e) {
        RMessages.openError("Add Element", "Project " + parent.getTreeDB().getVisibleName() + " is not loaded",
            null, false);
        return null;
      } catch (IdEmpty e) {
        RMessages.openError("Add Element", "Element id is empty", null, false);
        return null;
      } catch (IdContainsWrongChars e) {
        RMessages.openError("Add Element", "Element id contains wrong character - " + e.getWrongCharacter(),
            null, false);
        return null;
      }
      if (db.startTransaction("Add Test Step", true)) {
        try {
          TreeNode newNode = parent.createChild(id, type);
          newNode.setIndex(index, false);
          newNode.commitAttributes();
          return newNode;
        } finally {
          db.commit();
        }
      }
    }
    return null;
  }

  private static void dataToString(AttributeStorage attrs, Object obj, JSONArray ja) {
    if (obj instanceof JSONObject)
      ja.add(obj);
    else if (obj instanceof Object[]) {
      JSONArray jar = new JSONArray();
      for (Object o : ((Object[]) obj)) {
        dataToString(attrs, o, jar);
      }
      ja.add(jar);
    } else if (obj instanceof EnumValue) {
      ja.add(((EnumValue) obj).getValue());
      // sb.append();
    } else if (obj instanceof ListValue) {
      ListValue lv = (ListValue) obj;
      JSONArray jar = new JSONArray();
      for (Object o : lv.getValues()) {
        dataToString(attrs, o, jar);
      }
      ja.add(jar);
    } else {
      String rt = "";
      if (obj != null) {
        if (obj instanceof IContextStringProvider)
          rt = CoreUtils.URLEncode(((IContextStringProvider) obj).toString(attrs));
        else
          rt = CoreUtils.URLEncode(obj.toString());
      }
      ja.add(rt);
    }
  }

  public static String toString(TreeNode node, Object o) {
    JSONArray jar = new JSONArray();
    dataToString(node.attrs(), o, jar);
    return jar.toString();
  }

  @Override
  protected ITreeChangeListener getGUITreeChangeListener() {
    return getGuiListener();
  }

  private ITreeChangeListener getGuiListener() {
    return guiTreeChangeListener;
  }

  protected boolean isDeffered(TreeNode node, List<TreeNode> addedRoots) {
    TreeNode par = node;
    boolean con = false;
    while (par != null && !con) {
      con = addedRoots.contains(par);
      par = par.getWorkParent();
    }
    return con;
  }

  @Override
  protected URI getUrl(TreeNode startNode, String session) {
    return ResourcesProvider.getOne().formEditorURL(editorString, startNode, session, getPort());
  }

  protected String node2jsonString(TreeNode n) {
    if (n == null)
      return null;
    String result;
    LinkedList<Attribute> list = new LinkedList<>();
    Set<String> handled = new HashSet<>();

    for (Entry<String, Attribute> en : n.attrs().getLocalAttributes().entrySet()) {
      // TODO change for set of attrs
      Attribute a = en.getValue();
      handled.add(en.getKey());
      if (a != null) {
        if (/*
           * Attribute.affectsDescription(n, k) || Attribute.affectsId(n, k) ||
           * Attribute.affectsStatus(n, k)
           */
        // (a.getDefinition().getFlags()&Flag.HIDDEN)==Flag.HIDDEN||
        systemAttrs.contains(en.getKey()) || additionalAttrs.contains(en.getKey())) {
          list.add(a);
        }
      }
    }
    for (String s : systemAttrs)
      if (!handled.contains(s)) { // possible virtual keys
        handled.add(s);
        Attribute a = n.attrs().getAttribute(s);
        if (a != null)
          list.add(a);
      }
    for (String s : additionalAttrs)
      if (!handled.contains(s)) { // possible virtual keys
        handled.add(s);
        Attribute a = n.attrs().getAttribute(s);
        if (a != null)
          list.add(a);
      }
    JSONObject json = toJSJSON(n.getUUId(), list, n);
    ;
    json.put("id", n.getId());
    JSONArray jsonChildren = new JSONArray();
    List<TreeNode> children = n.getSortedShownChildren();
    for (TreeNode chi : children) {
      jsonChildren.add(chi.getSUUId());
    }
    UUID par = n.getWorkParentUUID();
    if (par != null)
      json.put("parent", n.getWorkParentUUID().toString());
    else
      RLogger.logError("node without parent in node json packaging - " + n.getSUUId());

    json.put("children", jsonChildren);

    JSONObject attr = (JSONObject) json.get("attributes");
    String type = n.getType();
    if (type.equals(Requirement.TYPE_NAME) || type.equals(TextNode.TYPE_NAME) || type.equals(Location.TYPE_NAME)) {
      JSONObject attrObj = new JSONObject();
      attrObj.put("key", "_hidden");
      attrObj.put("type", AttributeType.STRING.name());
      Object origin = n.getUUId();
      if (origin == null)
        origin = "";
      else
        origin = origin.toString();
      attrObj.put("origin", origin);
      attrObj.put("version", Version.getZero());
      attrObj.put("value", n.isHidden());
      attrObj.put("newvalue", n.isHidden());
      attrObj.put("availability", AttributeAvailability.LOCAL.name());
      attr.put("_lhidden", attrObj);
    }
    @SuppressWarnings("rawtypes")
    Set<String> keys = attr.keySet();
    for (String key : keys) {
      JSONObject a = (JSONObject) attr.get(key);
    }
    return json.toString();
  }

  private static int MESSAGE_NODES_LIMIT = 100;

  protected String[] nodeTree2jsonString(TreeNode nr) {
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(nr, false, true, false);
    Iterator<TreeNode> it = dftw.iterator();
    List<String> slist = new LinkedList<>();
    int i = 0;
    JSONArray ar = new JSONArray();
    while (it.hasNext()) {
      i++;
      TreeNode n = it.next();
      String result;
      LinkedList<Attribute> list = new LinkedList<>();
      // local attrs are inappropriate for virtual nodes
      Set<String> handled = new HashSet<>();
      for (Entry<String, Attribute> en : n.attrs().getActiveAttributes().entrySet()) {
        // TODO change for set of attrs
        Attribute a = en.getValue();
        if (a != null) {
          if (/*
             * Attribute.affectsDescription(n, k) || Attribute.affectsId(n, k) ||
             * Attribute.affectsStatus(n, k)
             */
          // (a.getDefinition().getFlags()&Flag.HIDDEN)==Flag.HIDDEN||
          systemAttrs.contains(en.getKey()) || additionalAttrs.contains(en.getKey())) {
            list.add(a);
            handled.add(en.getKey());
          }
        }
      }

      for (String s : systemAttrs)
        if (!handled.contains(s)) { // possible virtual keys
          handled.add(s);
          Attribute a = n.attrs().getAttribute(s);// BUG numeric id formed wrong way
          if (a != null)
            list.add(a);
        }
      for (String s : additionalAttrs)
        if (!handled.contains(s)) { // possible virtual keys
          handled.add(s);
          Attribute a = n.attrs().getAttribute(s);
          if (a != null)
            list.add(a);
        }
      JSONObject json = toJSJSON(n.getUUId(), list, n);
      ;
      json.put("id", n.getId());
      JSONArray jsonChildren = new JSONArray();
      List<TreeNode> children = n.getSortedShownChildren();
      for (TreeNode chi : children) {
        jsonChildren.add(chi.getSUUId());
      }
      UUID par = n.getWorkParentUUID();
      if (par != null)
        json.put("parent", n.getWorkParentUUID().toString());
      else
        RLogger.logError("node without parent in node json packaging - " + n.getSUUId());

      json.put("children", jsonChildren);

      JSONObject attr = (JSONObject) json.get("attributes");
      String type = n.getType();
      if (type.equals(Requirement.TYPE_NAME) || type.equals(TextNode.TYPE_NAME)
          || type.equals(Location.TYPE_NAME)) {
        JSONObject attrObj = new JSONObject();
        attrObj.put("key", "_hidden");
        attrObj.put("type", AttributeType.STRING.name());
        Object origin = n.getUUId();
        if (origin == null)
          origin = "";
        else
          origin = origin.toString();
        attrObj.put("origin", origin);
        attrObj.put("version", Version.getZero());
        attrObj.put("value", n.isHidden());
        attrObj.put("newvalue", n.isHidden());
        attrObj.put("availability", AttributeAvailability.LOCAL.name());
        attr.put("_lhidden", attrObj);
      }
      @SuppressWarnings("rawtypes")
      Set<String> keys = attr.keySet();
      for (String key : keys) {
        JSONObject a = (JSONObject) attr.get(key);
      }
      ar.add(json);
      // result = json.toString(4);
      // cnt += System.currentTimeMillis() - t;
      if (i > MESSAGE_NODES_LIMIT && ar.toString().length() > 60000) {
        i = 0;
        slist.add(ar.toString());
        ar = new JSONArray();
      }
    }
    if (i > 0)
      slist.add(ar.toJSONString());
    return slist.toArray(new String[slist.size()]);
  }

  private static boolean reqIdValid(TreeNode req, TreeNode parent, String text) {
    try {
      return reqIdValidExc(req, parent, text);
    } catch (TreeLogicException exc) {
      return false;
    }
  }

  // Check if id is valid
  private static boolean reqIdValidExc(TreeNode req, TreeNode parent, String text)
      throws SameIdExistsInChildren, IdEmpty, IdContainsWrongChars {
    if (text == null)
      return false;
    if (req == null && parent == null)
      return false;
    TreeLogic tlogic;
    if (req == null) {
      tlogic = parent.getTreeDB().getTreeLogic();
    } else {
      tlogic = req.getTreeDB().getTreeLogic();
    }
    return tlogic.isChildIdValidExc(parent, req, text);
  }

  // Check if child can be added to parent
  private static boolean reqChildValidExc(TreeNode req, TreeNode parent, String name) throws SameIdExistsInChildren,
      IdEmpty, IdContainsWrongChars, WrongProjectException, PastingIntoLeafException, ProjectNotLoadedException {
    if (req == null && parent == null)
      return false;
    TreeLogic tlogic;
    if (req == null)
      return false;
    tlogic = req.getTreeDB().getTreeLogic();
    return (name == null || name.equals("") || tlogic.isChildIdValidExc(parent, req, name))
        && tlogic.canBeParentOf(parent.getType(), req) && tlogic.canBeChildOfExc(req.getType(), parent);
  }

  private static boolean reqChildValid(TreeNode req, TreeNode parent, String name) {
    try {
      return reqChildValidExc(req, parent, name);
    } catch (TreeLogicException exc) {
      return false;
    }
  }

  protected void appendOrAdd(JSONArray ret, Object o) {
    if (o != null) {
      if (o instanceof JSONArray)
        ((JSONArray) o).forEach(one -> ret.add(one));
      else
        ret.add(o);
    }
  }

  public static JSONObject toJSJSON(UUID uuid, LinkedList<Attribute> collection, TreeNode node) {
    // for editors js
    JSONObject result = null;
    result = new JSONObject();
    result.put("uuid", uuid.toString());
    UUID par = node.getWorkParentUUID();
    if (par != null)
      result.put("parent", par.toString());
    JSONObject attrs = new JSONObject();
    for (Attribute a : collection) {
      // if (!a.getOrigin().equals(uuid)) continue;
      JSONObject attrObj = new JSONObject();
      // attrObj.put("key", a.getKey());
      attrObj.put("type", (String) a.getType().name());
      if (!a.getOrigin().equals(uuid))
        attrObj.put("origin", a.getOrigin().toString());
      // attrObj.put("version", a.getVersion().toString());
      // newval?
      TypedValue rw = a.getDefinition().getRawValue();
      TypedValue nw = a.getTValue();
      String s = a.getKey();
      Object ov = null, nwa = a.getValue();
      if (nwa instanceof String)
        nwa = CoreUtils.URLEncode((String) nwa);
      if (rw != null) {
        ov = rw.getValue();
        if (ov instanceof String)
          ov = CoreUtils.URLEncode((String) ov);
        attrObj.put("value",
            Attribute.rawValueToJSON(node.attrs(), rw.getType(), ov,
                CoreUtils.URLEncode(node.getExternalNodeResourcesPath().toString()),
                CoreUtils.URLEncode(node.getExternalNodeResourcesPath(s).toString())));// TODO
      }
      attrObj.put("newvalue",
          Attribute.rawValueToJSON(node.attrs(), a.getType(), nwa,
              CoreUtils.URLEncode(node.getExternalNodeResourcesPath().toString()),
              CoreUtils.URLEncode(node.getExternalNodeResourcesPath(s).toString())));

//      attrObj.put("newvalue", Attribute.resultValueToJSON(
//              nw.getType(), nw.getValue(),node.getNodeResourcesPath(),node.getNodeResourcesPath(a.getKey())));
//      if(rw!=null)
//        attrObj.put("value", Attribute.rawValueToJSON(rw.getType(), rw.getValue(),CoreUtils.URLEncode(node.getNodeResourcesPath().toString()),CoreUtils.URLEncode(node.getNodeResourcesPath(a.getKey()).toString())));
      if (!a.getAvailability().equals(AttributeAvailability.LOCAL))
        attrObj.put("availability", a.getAvailability().name());
      attrs.put(a.getKey(), attrObj);
    }
    result.put("attributes", attrs);
    return result;
  }

  public boolean relates(TreeNode nde) {
    String vroot = nde.getVRoot();
    if (vroot != null) {
      TreeNode root = null;
      try {
        root = nde.getTreeDB().getNodeExc(UUID.fromString(vroot));
        if (root != null)
          if (root.getId().startsWith("com.unitesk.requality.report"))
            return false;// TODO flag in report generator
      } catch (Exception e) {
        return false;
      }
    }
    try {
      String qid = nde.getQualifiedId(true), rootqid = startNode.getQualifiedId(true);
      return (qid + "/").startsWith(rootqid + "/");
    } catch (NodeNotFoundException nnfe) {
      return false;// unloaded subtree
    }
  }

  ITreeChangeListener guiTreeChangeListener = new DefaultTreeChangeListener() {
    private JSONObject getMoved(TreeNode node2, TreeNode oldParent) {
      TreeNode[] nodes = new TreeNode[2];
      if (!relates(node2) && (!relates(node2.getShownParent()) && !relates(oldParent)))
        return null;
      if (relates(node2.getShownParent())) {
        nodes[0] = node2.getShownParent();
      }
      if (!node2.getShownParent().getUUId().equals(oldParent.getUUId()))
        if (relates(oldParent))
          nodes[1] = oldParent;
      // refresh(oldParent);
      // refresh(node2);
      JSONObject jo = new JSONObject();
      try {
        jo.put("op", "moved");
        String suuid = node2.getSUUId();
        jo.put("node", suuid);
        String old = nodes[0] == null ? null : nodes[0].getSUUId();
        if (old != null)
          jo.put("parent", old);
        String opar = nodes[1] == null ? null : nodes[1].getSUUId();
        if (opar != null)
          jo.put("oldparent", opar);
        else {
          // preload node-related data for nodes out of seen tree
          String[] arrresult = new String[0];
          try {
            arrresult = nodeTree2jsonString(node2);
          } catch (Exception e) {
            RLogger.logError(e);
          }
          for (WSSocket wss : currentInitedSockets)// TODO limit?
            sendLoadNodeTreeByUUIDResult(wss, wss.inited, null, suuid, arrresult);// TODO add transf?
        }
        JSONArray ja = new JSONArray();
        for (TreeNode node : oldParent.getSortedShownChildren())
          ja.add(node.getSUUId());
        jo.put("oldParentChildren", ja);
        ja = new JSONArray();
        for (TreeNode node : node2.getShownParent().getSortedShownChildren())
          ja.add(node.getSUUId());
        jo.put("parentChildren", ja);
        return jo;
      } catch (Exception e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
      return null;
      // return new
      // Object[]{"moved",node2.getUUId(),,nodes[1]==null?null:nodes[1].getUUId()};
    }

    public JSONObject getChildrenOrderChanged(TreeNode node) {
      if (!relates(node))
        return null;
      JSONObject jo1 = new JSONObject();
      try {
        jo1.put("op", "updateChildrenOrder");
        jo1.put("children", jsonChildren(node));
        jo1.put("node", node.getSUUId());
      } catch (Exception e) {
        RLogger.logError(e);
      }
      return jo1;
    }

    @Override
    public void childrenOrderChanged(TreeNode node, byte flag) {
      if (state == LocationState.NOT_INITED)
        return;
      if (!relates(node))
        return;
      JSONObject jo = getChildrenOrderChanged(node);
      if (jo != null)
        sendEvents(node.attrs(), jo, getInitedSocketsArray());
      // super.childrenOrderChanged(node);
    }

    @Override
    public void moved(TreeNode node2, TreeNode oldParent, TreeNode newParent) {
      if (relates(node2)) { // TODO collapsed conflict
        JSONObject jo = new JSONObject();
        try {
          jo.put("op", "moved");
          jo.put("node", node2.getSUUId());
          jo.put("parent", node2.getShownParentUUID().toString());
          jo.put("oldparent", oldParent.getSUUId());
          sendEvents(node2.attrs(), jo, getInitedSocketsArray());
        } catch (Exception e) {
          RLogger.logError(e);
        }
      }
    }

    private Object getDeleted(TreeNode nodeDesc) {
      if (!relates(nodeDesc))
        return null;
      if (nodeDesc == null)
        RLogger.logInfo("null description of deleting node");
      if (nodeDesc != null) {
        JSONObject jo = new JSONObject();
        try {
          jo.put("op", "deleted");
          jo.put("node", nodeDesc.getSUUId());
          try {
            UUID par = nodeDesc.getShownParentUUIDExc();
            if (par != null)
              jo.put("parent", par.toString());
          } catch (NodeNotFoundException e) {

          }
          return jo;
        } catch (Exception e) {
          RLogger.logError(e);
        }
      }
      return null;
    }

    private Object getDeleted(NodeDesc nodeDesc) {
      if (nodeDesc != null) {
        JSONObject jo = new JSONObject();
        try {
          jo.put("op", "deleted");
          jo.put("node", nodeDesc.getSUUId());
          UUID par = nodeDesc.getShownParentUUID();
          if (par != null)
            jo.put("parent", par.toString());
          return jo;
        } catch (Exception e) {
          RLogger.logError(e);
        }
      }
      return null;
    }

    @Override
    public void deleted(TreeNode node) {
      if (state == LocationState.NOT_INITED)
        return;
      sendEvents(node.attrs(), getDeleted(node), getInitedSocketsArray());
    }

    private Object getChanged(TreeNode nde, ChangesSet<Attribute> what) {
      // TreeNode[] ret = new TreeNode[2];
      if (!relates(nde))
        return null;
      if (nde.isRoot()) {
        // TODO reduction
        // refresh root
        if (!db.getSortingSettings().equals(storedSortingSettings)) {
          storedSortingSettings = db.getSortingSettings();
          // TODO disposed case
          // if(!browser.isDisposed())
          // browser.refresh();
          // just update and do nothing else
          return nde;
        }
      }
      TreeNode n = nde;
      // if(n == null || qid.equals(rootqid) ||
      // nde.getQualifiedId().equals(db.getRootNode().getQualifiedId()))
      // n = nde;
      boolean changeIt = false, changeParent = false;
      Set<String> requiredchange = new HashSet<>(), changesA = what.getAll();
      for (String w : changesA) {
        if (additionalAttrs.contains(w) || systemAttrs.contains(w)) {
          requiredchange.add(w);
          changeIt = true;
        }
      }
      // Temporary solution. tends to become new virtual attr
      changeParent = changesA.contains(TreeDB.INDEX_ATTRIBUTE) || changesA.contains(TreeNode.ATTR_LABEL)
          || changesA.contains(Requirement.ATTR_LOCATIONS);
      if (n.getQualifiedId(true).equals(startNode.getQualifiedId(true)))
        changeParent = false;
      JSONArray changes = new JSONArray();
      // Object[] attrchanges = null;
      if (changeIt) {
        for (String s : requiredchange) {
          try {
            Attribute a = n.attrs().getAttribute(s);
            JSONObject ach = new JSONObject();
            ach.put("key", s);
            if (a != null) {
              // Attribute oldA = what.getOld(s);
              TypedValue rw = null;
              if (a.getDefinition() != null)
                rw = a.getDefinition().getRawValue();

              if (rw != null)
                ach.put("value",
                    Attribute.rawValueToJSON(n.attrs(), rw.getType(), rw.getValue(),
                        CoreUtils.URLEncode(n.getExternalNodeResourcesPath().toString()),
                        CoreUtils.URLEncode(n.getExternalNodeResourcesPath(s).toString())));// TODO

              ach.put("newvalue",
                  Attribute.rawValueToJSON(n.attrs(), a.getType(), a.getValue(),
                      CoreUtils.URLEncode(n.getExternalNodeResourcesPath().toString()),
                      CoreUtils.URLEncode(n.getExternalNodeResourcesPath(s).toString())));
              // attrchanges[i++] = new
              // Object[]{s,escape(Attribute.rawValueToJSONString(a.getRawType(),
              // a.getRawValue())),
              // escape(Attribute.resultValueToJSONString(a.getResultType(), a.getValue()))};
            }
            changes.add(ach);
          } catch (Exception e) {
            RLogger.logError(e);
          }
        }
      }
      try {
        if (changeParent && changeIt) {
          // JSONArray joa = new JSONArray();
          TreeNode shownPar = n.getShownParent();
          JSONObject jo1 = null;
          if (shownPar != null) {
            jo1 = new JSONObject();
            jo1.put("op", "updateChildrenOrder");
            jo1.put("children", jsonChildren(shownPar));
            jo1.put("node", shownPar.getSUUId());
          }
          // joa.put(jo1);
          JSONObject jo2 = new JSONObject();
          jo2.put("op", "changeAttributes");
          jo2.put("node", n.getSUUId());
          jo2.put("values", changes);
          // joa.put(jo2);
          JSONArray ret = new JSONArray();
          if (jo1 != null)
            ret.add(jo1);
          ret.add(jo2);
          return ret;
          // return new Object[]{new
          // Object[]{"updateChildrenOrder",n.getShownParent().getUUId()},
          // new Object[]{"changeAttributes", n.getSUUId(), attrchanges}};
        }
        if (changeParent && n.getShownParent() != null) {
          TreeNode par = n.getShownParent();
          JSONObject jo1 = new JSONObject();
          jo1.put("op", "updateChildrenOrder");
          jo1.put("children", jsonChildren(par));
          jo1.put("node", par.getSUUId());
          return jo1;
        }
        if (changeIt && !changeParent) {
          JSONObject jo2 = new JSONObject();
          jo2.put("op", "changeAttributes");
          jo2.put("node", n.getSUUId());
          jo2.put("values", changes);
          return jo2;
          // return new Object[]{"changeAttributes", n.getSUUId(), attrchanges};
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
      return null;
    }

    private JSONArray jsonChildren(TreeNode n) {
      JSONArray jsonChildren = new JSONArray();
      List<TreeNode> children = n.getSortedShownChildren();
      for (TreeNode chi : children) {
        jsonChildren.add(chi.getSUUId());
      }
      return jsonChildren;
    }

    @Override
    public void changed(final TreeNode nde, final ChangesSet<Attribute> what) {
      if (state == LocationState.NOT_INITED)
        return;
      Object o = getChanged(nde, what);
      if (o != null) {
        if (o instanceof TreeNode)
          oldRefresh((TreeNode) o);
        else
          sendEvents(nde.attrs(), o, getInitedSocketsArray());
      }
      // if(nde2!=null)
      // if(nde2.isRoot()){
      // oldRefresh(rootNode);
      // if(!browser.isDisposed())
      // browser.refresh();
      // }else{
      // updateData(new HashMap<UUID,Map<String,Object>>(){{
      // put(nde.getUUId(),new HashMap<String, Object>());
      // for(String key:what){
      // Attribute a = nde.getAttribute(key);
      // get(nde.getUUId()).put(key, a==null?null:a.getValue());
      // }
      // }});
      // //targeted update
      // //refresh(nde2);
      // }
    }

    private void oldRefresh(TreeNode o) {
      // TODO Auto-generated method stub

    }

    private Object getCreated(TreeNode nde) {
      if (!relates(nde))
        return null;
      JSONObject jo = new JSONObject();
      try {
        jo.put("op", "created");
        jo.put("node", nde.getSUUId());
        if (nde.getShownParentUUID() != null) {
          jo.put("parent", nde.getShownParentUUID().toString());
          jo.put("parentChildren", jsonChildren(nde.getShownParent()));
        }
      } catch (NodeNotFoundException e) {
      }
      return jo;
    }

    @Override
    public void created(TreeNode nde) {
      if (state == LocationState.NOT_INITED)
        return;
      Object o = getCreated(nde);
      if (o != null) {
        sendEvents(nde.attrs(), o, getInitedSocketsArray());
      }
    }

    @Override
    public void changed(List<TreeNodeChangeEvent> changes) {
      if (state == LocationState.NOT_INITED)
        return;
      // List<Object> ret = new LinkedList<Object>();
      List<TreeNode> addedRoots = new LinkedList<>();
      JSONArray ret = new JSONArray();
      for (TreeNodeChangeEvent event : changes) {
        switch (event.getType()) {
        case CHANGE: {
          if (event.getMoment() != Moment.AFTER)
            continue;
          Object add = getChanged(event.getTreeNodeTarget(), (ChangesSet<Attribute>) event.getArguments()[0]);
          if (add instanceof TreeNode)
            oldRefresh((TreeNode) add);
          else
            appendOrAdd(ret, add);
        }
          break;
        case CREATE: {
          if (event.getMoment() != Moment.AFTER)
            continue;
          TreeNode node = event.getTreeNodeTarget();
          //preinit seme virtual attributes
          Object add = getCreated(node);
          if (add != null) {
            if (!isDeffered(node, addedRoots)) {
              addedRoots.add(node);
              appendOrAdd(ret, add);
            }
          }

        }
          break;
        case MOVE: {

          if (event.getMoment() != Moment.AFTER)
            continue;
          Object o = getMoved(event.getTreeNodeTarget(), (TreeNode) event.getArguments()[0]);
          if (o != null) {
            appendOrAdd(ret, o);
          }
        }
          break;

        case CHILDREN_ORDER_CHANGE: {
          TreeNode node = event.getTreeNodeTarget();
          Object o = getChildrenOrderChanged(node);
          if (o != null) {
            appendOrAdd(ret, o);
          }
        }
          break;
        case DELETE: {
          if (event.getMoment() == Moment.BEFORE)
            continue;
          TreeNode nd = event.getTreeNodeTarget();
          Object o = null;
          if (nd != null)
            o = getDeleted(nd);
          else
            o = getDeleted(event.getTreeNodeTarget());
          if (o != null) {
            appendOrAdd(ret, o);
          }
        }
          break;
        default:
          break;
        }
      }
      sendEvents(fullSession, ret, currentInitedSockets);
    }

    private JSONObject getVChanges(TreeNode nde, boolean internal) {
      if (!relates(nde))
        return null;
      JSONObject jo = new JSONObject();
      try {
        jo.put("op", "versionChanged");
        jo.put("node", nde.getUUId());
      } catch (Exception e) {
        RLogger.logError(e);
      }
      return jo;
    }

  };

  private static Set<String> readOnlyOps;
  static {
    readOnlyOps = new HashSet<>();
    readOnlyOps.add("init");
    readOnlyOps.add("printToConsole");
    readOnlyOps.add("sendInited");
    readOnlyOps.add("sendInited");
    readOnlyOps.add("addTest");
    readOnlyOps.add("setRequalityFocus");
    readOnlyOps.add("openEditorForNode");
    readOnlyOps.add("loadNodeByUUID");
    readOnlyOps.add("loadNodeTreeByUUID");
    readOnlyOps.add("checkChilds");
    readOnlyOps.add("loadTemplates");
    readOnlyOps.add("selectFileDialog");
  }
  @Override
  public boolean isReadOnlyOperation(String op) {
    return readOnlyOps.contains(op);
  }

//  @Override
//  protected void onDispose(WSSocket ws) {
//    if(ws == null) 
//      return;
//  }


  /**
   * VSCODE-PATCH: the UUID of a node the page of an editor asks for. The locations of the catalogs converted from an
   * older format address their document element by its path ending with the UUID ("/Documents/a.xhtml/&lt;uuid&gt;"),
   * which UUID.fromString rejected, so the page never got the node and did not initialize.
   */
  static UUID nodeReferenceUUID(String node) {
    try {
      return UUID.fromString(node);
    } catch (IllegalArgumentException e) {
      int slash = node.lastIndexOf('/');
      if (slash >= 0) {
        try {
          return UUID.fromString(node.substring(slash + 1));
        } catch (IllegalArgumentException e2) {
          // not a node reference
        }
      }
      RLogger.logDebug("not a node reference: " + node, 5);
      return null;
    }
  }
}
