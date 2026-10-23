package com.unitesk.requality.vscode.port.eclipse.views.compare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.IStreamContentAccessor;
import com.unitesk.requality.vscode.ui.compare.ResourceNode;
import com.unitesk.requality.vscode.ui.compare.contentmergeviewer.ContentMergeViewer;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.team.FileRevisionTypedElement;
import com.unitesk.requality.vscode.ui.team.LocalResourceTypedElement;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.requality.core.api.RequalityStoredVersionProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeModification;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.descriptors.NodeTypeDescriptor;
import com.unitesk.requality.core.events.DefaultNodeChangeListener;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.MemoryStorage;
import com.unitesk.requality.core.storage.NodeMapper;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.formatversion.LegalicyNodeMapper;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.core.ResourceStorage;
import com.unitesk.requality.vscode.port.eclipse.views.panels.ElementComparePanel;
import com.unitesk.requality.tool.CoreUtils;

public class TreeNodeCompareViewer extends ContentMergeViewer {
  private static TreeNodeCompareViewer last;
  public TreeNodeCompareViewer(Composite parent, int style, CompareConfiguration configuration) {
    super(style, ResourceBundle.getBundle("org.eclipse.compare.contentmergeviewer.TextMergeViewerResources"), configuration);
    buildControl(parent);
    String title= "Requality TreeNode Compare"; //$NON-NLS-1$
    getControl().setData(CompareUI.COMPARE_VIEWER_TITLE, title);
  }
  public TreeNodeCompareViewer(Composite parent, CompareConfiguration configuration) {
    this(parent, SWT.NULL, configuration);
    last = this;
  }
  //org.eclipse.compare.contentmergeviewer.TextMergeViewer;
  Composite canc, lc,rc;
  ElementComparePanel left, right;
  //Label leftv, rightv;
  boolean lefte, righte;
  TreeNode l, r;
  Pair<TreeNode, String> pl = null, pr = null;
  private DefaultNodeChangeListener leftc = null, rightc = null;
  IProject lp = null, rp = null;
  InputStream lcon = null, rcon = null;
  Object lefto, righto; 
  IStreamContentAccessor lte = null;
  IStreamContentAccessor rte = null;
  @Override
  protected void createControls(Composite composite) {
    canc = new Composite(composite, SWT.NO_FOCUS);

//    Label l = new Label(canc, SWT.NONE);
//    l.setText("Test");
    //canc.setLayout(new GridLayout(2,false));
    lc = new Composite(composite, SWT.NO_FOCUS);
    lc.setLayout(new GridLayout(1,false));
    rc = new Composite(composite, SWT.NO_FOCUS);
    rc.setLayout(new GridLayout(1,false));
    
  }

  @Override
  protected void handleResizeAncestor(int x, int y, int width, int height) {
    if (width > 0) {
      canc.setVisible(true);
      canc.setBounds(x, y, width, height);
    } else {
      canc.setVisible(false);
    }
  }

  @Override
  protected void handleResizeLeftRight(
      int x, int y, int leftWidth, int centerWidth, int rightWidth, int height) {
    lc.setBounds(x, y, leftWidth, height);
    rc.setBounds(x+leftWidth+centerWidth, y, rightWidth, height);
  }
  @Override
  protected void updateContent(Object ancestor, final Object lraw, final Object rraw) {
    lefto = lraw;
    righto = rraw;
    EclipseAPI.getDisplay().syncExec(new Runnable(){
      @Override
      public void run() {
        TreeNodeCompareViewer t = TreeNodeCompareViewer.this;
        if(t.left!=null)
          t.left.getControl().dispose();
        if(t.right!=null)
          t.right.getControl().dispose();
      }
    });
    
    l = null;
    pl = null;
    pr = null;
    r = null;
    leftc = rightc = null;
    lp = rp = null;
    lcon = rcon = null;
    if(lefto instanceof LocalResourceTypedElement){
      EclipseAPI.getDisplay().syncExec(new Runnable(){
        @Override
        public void run() {
          lp = ((LocalResourceTypedElement)lefto).getResource().getProject();
          try {
            lcon = ((LocalResourceTypedElement)lefto).getContents();
          } catch (CoreException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        }
      });
      if(lp!=null){
        TreeDB db = null;
		try {
		  EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
          db = et.getTreeDB(lp, null, et.getLocalName(lp));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
          e1.printStackTrace();
		}
        if(db!=null){
          try {
            UUID uid = NodeMapper.getUUId(ResourceStorage.readFile(lcon));
            if(uid!=null){
              TreeNode node = db.getNode(uid);
              if(node!=null)
                try {
                  lefto = new TreeNodeTypedElement(node);
                } catch (LockIsBusy e) {
                  RLogger.logDebug(CoreUtils.getTrace(e),7);
                }
            }
          } catch (JSONException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        }
      }
    }
    
    if(righto instanceof LocalResourceTypedElement){
      EclipseAPI.getDisplay().syncExec(new Runnable(){
        @Override
        public void run() {
          rp = ((LocalResourceTypedElement)righto).getResource().getProject();
          try {
            rcon = ((LocalResourceTypedElement)righto).getContents();
          } catch (CoreException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        }
      });
      if(rp!=null){
        TreeDB db = null;
		try {
		  EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
	      db = et.getTreeDB(rp, null, et.getLocalName(rp));
		} catch (IOException e1) {
          e1.printStackTrace();
		}
        if(db!=null){
          try {
            UUID uid = NodeMapper.getUUId(ResourceStorage.readFile(rcon));
            if(uid!=null){
              TreeNode node = db.getNode(uid);
              if(node!=null)
                try {
                  righto = new TreeNodeTypedElement(node);
                } catch (LockIsBusy e) {
                  RLogger.logError(e);
                }
            }
          } catch (JSONException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
          
        }
      }
    }
    String altPathL = null, altPathR = null;
    if(lefto instanceof TreeNodeTypedElement){
      l = null;
      final Object fright = righto;
      EclipseAPI.getDisplay().syncExec(new Runnable(){
      
        @Override
        public void run() {
          l = ((TreeNodeTypedElement)lefto).getTreeNode();
        }
      });
      if(l!=null){
        final TreeNode l = TreeNodeCompareViewer.this.l;
        if(l!=null) {
          leftc = new DefaultNodeChangeListener(){
            @Override
            public void attributeChange(TreeNode source, String what, Attribute old) {
              super.attributeChange(source,what,old);
              WorkspaceJob j = new WorkspaceJob("Update left compare panel"){
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if(source!=null && source == l)
                    return Status.CANCEL_STATUS;
                  try {
                    updateContent(ancestor, new TreeNodeTypedElement(l), fright);
                  } catch (LockIsBusy e) {
                    RLogger.logDebug(CoreUtils.getTrace(e),7);
                  }
                  return Status.OK_STATUS;
                }};
               j.schedule();
            }
            @Override
            public void moved(TreeNode node, TreeNode oldParent) {
              super.moved(node,oldParent);
              final DefaultNodeChangeListener leftc = TreeNodeCompareViewer.this.leftc;
              WorkspaceJob j = new WorkspaceJob("Update left compare panel"){
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if(node!=null && node == l)
                    return Status.CANCEL_STATUS;
                  try {
                    updateContent(ancestor, new TreeNodeTypedElement(l), fright);
                  } catch (LockIsBusy e) {
                    RLogger.logDebug(CoreUtils.getTrace(e),7);
                  }
                  return Status.OK_STATUS;
                }};
              j.schedule();
            }
          };
          l.addGuiChangeListener(leftc);
        }
      }
    }else
    {
      altPathL = getHackPath(lefto);
    }
    if(righto instanceof TreeNodeTypedElement){
      r = null;
      EclipseAPI.getDisplay().syncExec(new Runnable(){
      
        @Override
        public void run() {
          r = ((TreeNodeTypedElement)righto).getTreeNode();
        }
      });

      final Object fleft = lefto;
      final TreeNode r = TreeNodeCompareViewer.this.r;
      if(r!=null) {
        TreeNodeCompareViewer.this.rightc = new DefaultNodeChangeListener(){
          @Override
          public void attributeChange(TreeNode source, String what, Attribute old) {
            super.attributeChange(source,what,old);
            WorkspaceJob j = new WorkspaceJob("update right compare panel"){
              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(source!=null && source == r)
                  source.removeGuiChangeListener(rightc);
                try {
                  updateContent(ancestor, fleft, new TreeNodeTypedElement(r));
                } catch (LockIsBusy e) {
                  RLogger.logDebug(CoreUtils.getTrace(e),7);
                }
                return Status.OK_STATUS;
              }
            };
            j.schedule();
          }
          @Override
          public void moved(TreeNode node, TreeNode oldParent) {
            super.moved(node,oldParent);
            WorkspaceJob j = new WorkspaceJob("update right compare panel"){
              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(node!=null && node == r)
                  node.removeGuiChangeListener(rightc);
                try {
                  updateContent(ancestor, fleft, new TreeNodeTypedElement(r));
                } catch (LockIsBusy e) {
                  RLogger.logDebug(CoreUtils.getTrace(e),7);
                }
                return Status.OK_STATUS;
              }
            };
            j.schedule();
          }
        };
        r.addGuiChangeListener(rightc);
      }
    }else
      altPathR = getHackPath(righto);
    lefte = l!=null;//left instanceof IEditableContent;
    righte = r!=null;//right instanceof IEditableContent;
    
    lte = null;
    rte = null;
    EclipseAPI.getDisplay().syncExec(new Runnable(){
    
      @Override
      public void run() {
        if(lefto instanceof IStreamContentAccessor)
          lte = (IStreamContentAccessor)lefto;
        if(righto instanceof IStreamContentAccessor)
          rte = (IStreamContentAccessor)righto;
      }
    });
//    if(left instanceof IContentChangeNotifier){
//      ((IContentChangeNotifier)left).addContentChangeListener(new IContentChangeListener(){
//      
//        @Override
//        public void contentChanged(IContentChangeNotifier source) {
//          setLeftDirty(true);
//        }
//      });
//    }
    String name1 = gatherNameNoJson(l, lefto), name2 = gatherNameNoJson(r, righto);
    String rc = null, lc = null;
    if(r == null && righto instanceof FileRevisionTypedElement){
      rcon = null;
      EclipseAPI.getDisplay().syncExec(new Runnable(){
        @Override
        public void run() {
          // TODO Auto-generated method stub
          try {
            rcon = ((FileRevisionTypedElement)righto).getContents();
          } catch (CoreException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        }
      });
      if(rcon!=null){
        rc = ResourceStorage.readFile(rcon);
        if(rc!=null && rc.indexOf(CoreUtils.GIT_MERGE_START)>-1 )
          rc = CoreUtils.filterGitJson(rc,true);
      }
    }
    if(l == null && lefto instanceof FileRevisionTypedElement){
      lcon = null;
      EclipseAPI.getDisplay().syncExec(new Runnable(){
        @Override
        public void run() {
          try {
            lcon = ((FileRevisionTypedElement)lefto).getContents();
          } catch (CoreException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        }
      });
      if(lcon!=null){
        lc = ResourceStorage.readFile(lcon);
        if(lc!=null && lc.indexOf(CoreUtils.GIT_MERGE_START)>-1 )
          lc = CoreUtils.filterGitJson(lc,true);
       }
    }
    if(l == null && lte !=null && lc == null){
      try{
        lc = ResourceStorage.readFile(lte);
        if(lc!=null && lc.indexOf(CoreUtils.GIT_MERGE_START)>-1 )
          lc = CoreUtils.filterGitJson(lc,true);
      }catch(Exception e){
        RLogger.logError(e);
      }
    }
    if(r == null && rte!=null && rc == null){
      try{
        rc = ResourceStorage.readFile(rte);
        if(rc!=null && rc.indexOf(CoreUtils.GIT_MERGE_START)>-1 )
          rc = CoreUtils.filterGitJson(rc,false);
      }catch(Exception e){
        RLogger.logError(e);
      }
    }
    //this.left.setEditable(false);
    String leftver = null, rightver = null;
    String leftpar = "", rightpar = "";
    if(lc!=null || l!=null){
      boolean alt = false;
      if(l == null)
        try {
          pl = loadTreeNode(lc, name1); 
          if(pl!=null){
            l = pl.getFirst();
            leftver = pl.getSecond();
            //this.left.setFormatVersion(pl.getSecond());
            alt = true;
          }
        } catch (JSONException e) {
          RLogger.logError(e);
        } catch (IOException e) {
          RLogger.logError(e);
        }
      else
        leftver = RequalityStoredVersionProvider.getVersion();
      //this.left.setEditable(true);
    //  this.left.setTreeNode(l);
      if(l!=null)
        leftpar = alt?altPathL:l.getQualifiedId();
    }
    if(rc!=null || r!=null){
      boolean alt = false;
      if(r == null)
        try {
          pr = loadTreeNode(rc, name2); 
          if(pr!=null){
            r = pr.getFirst();
            rightver = pr.getSecond();
            alt = true;
          }
        } catch (JSONException | IOException e) {
          RLogger.logError(e);
        }
      else
        rightver = RequalityStoredVersionProvider.getVersion();
      if(r!=null)
        rightpar = alt?altPathR:r.getQualifiedId();
      //this.right.setEditable(true);
    }
    final String fleftver = leftver, frightver = rightver;
    final String fleftpar = leftpar, frightpar = rightpar;
    boolean pareq = Attribute.equalsValues(ElementComparePanel.filterPath(leftpar), ElementComparePanel.filterPath(rightpar)),
            verseq = Attribute.equalsValues(leftver, rightver);
    final Composite flc = TreeNodeCompareViewer.this.lc;
    final Composite frc = TreeNodeCompareViewer.this.rc;
    EclipseAPI.getDisplay().asyncExec(new Runnable(){
    
      @Override
      public void run() {
        if(flc.isDisposed() || frc.isDisposed())
          return;
        TreeNodeCompareViewer.this.left = new ElementComparePanel(flc, 1, !lefte, !pareq, !verseq);
        TreeNodeCompareViewer.this.right = new ElementComparePanel(frc, 1, !righte, !pareq, !verseq);
        left.getControl().addDisposeListener(new DisposeListener() {
			
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if(l!=null)
				  l.removeGuiChangeListener(leftc);
			}
		});
        right.getControl().addDisposeListener(new DisposeListener() {
			
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if(r!=null)
				  r.removeGuiChangeListener(rightc);
			}
		});
        ElementComparePanel fright = TreeNodeCompareViewer.this.right;
        ElementComparePanel fleft = TreeNodeCompareViewer.this.left;
        fleft.setVersion("NONE");
        fright.setVersion("NONE");
        fleft.resetColors();
        fright.resetColors();
        fleft.setVisible(false);
        fright.setVisible(false);
        fleft.setVisible(false);
        
        fleft.setTreeNode(l);
        if(l!=null){
          fleft.setFormatVersion(fleftver);
          //TreeNodeCompareViewer.this.left.setVersion(l.getno);
          //TreeNodeCompareViewer.this.left.setEditable(lefte);
          fleft.setVisible(true);
          fleft.setParent(fleftpar);
//          if(alt)
//              TreeNodeCompareViewer.this.left.setParent(altPathL);
        }
        fright.setTreeNode(r);
        if(r!=null){
          fright.setFormatVersion(frightver);
          //fright.setEditable(righte);
          fright.setVisible(true);
//          if(alt)
          fright.setParent(frightpar);
        }
        if(pl == null && l!=null)
          pl = new Pair<>(l, RequalityStoredVersionProvider.getVersion());
        if(pr == null && r!=null)
          pr = new Pair<>(r, RequalityStoredVersionProvider.getVersion());
        markDifference(pl, pr, !pareq);
        flc.layout();
        frc.layout();
      }
    });
   
  }
  Map<UUID, String> res = new HashMap<>();
  private String getHackPath(Object r) {
    if(r == null)
      return null;
    final UUID key = UUID.randomUUID();
    EclipseAPI.getDisplay().syncExec(new Runnable(){
    
      @Override
      public void run() {
        Method m = null;
        try {
          m = r.getClass().getMethod("getBufferedStorage", new Class[]{});
        } catch (NoSuchMethodException e2) {
          
        } catch (SecurityException e2) {
          // TODO Auto-generated catch block
          e2.printStackTrace();
        }
        try {
          if(m == null){
            if(r instanceof LocalResourceTypedElement){
              IResource resa = ((LocalResourceTypedElement)r).getResource();
              if(resa!=null) {
                IPath p = resa.getFullPath();
                if(p!=null){
                  String full = p.toPortableString();
                  res.put(key, full);
                }
              }
            }
            return;
          }
          Object o = m.invoke(r, new Object[0]);
          if(o!=null){
            Method ma = o.getClass().getMethod("getFullPath", new Class[0]);
            if(ma!=null) {
              Object op = ma.invoke(o, new Object[0]);
              IPath p = (IPath) op;
              if(p!=null){
                String full = p.toPortableString();
                res.put(key, full);
              }
            }
          }
        } catch (IllegalAccessException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        } catch (IllegalArgumentException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        } catch (InvocationTargetException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        } catch (NoSuchMethodException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        } catch (SecurityException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
      }
    });
    return res.get(key);
  }
  private void markDifference(Pair<TreeNode, String> pl, Pair<TreeNode, String> pr, boolean pd) {
    if(pl == null || pr == null)
      return;
    if(this.left!=null&&this.right!=null){
      this.left.setDiffColors(this.left.getDescPanel(),this.right.getDescPanel());
      //this.right.setDiffColors(this.left.getDescPanel().getText(),this.right.getDescPanel().getText(),false);
    }
    markIfChange(TreeNode.ATTR_ID, pl.getFirst().getId(), pr.getFirst().getId());
    Set<String> keys = new HashSet<>();
    keys.addAll(pl.getFirst().attrs().getAllAttributeKeys());
    keys.addAll(pr.getFirst().attrs().getAllAttributeKeys());
    for(String s:keys){
      Object o1 = pl.getFirst().attrs().getAttributeValue(s),
          o2 = pr.getFirst().attrs().getAttributeValue(s);
      markIfChange(s, o1, o2);
    }
    if(!pl.getSecond().equals(pr.getSecond())){
      left.markStoredVersionChange(AttributeModification.MODIFY);
      right.markStoredVersionChange(AttributeModification.MODIFY);
    }
    if(pd){
      left.markParentChange(AttributeModification.MODIFY);
      right.markParentChange(AttributeModification.MODIFY);
    }
  }
  private void markIfChange(String k, Object v1, Object v2) {
    if(v1 == null)
      if(v2 != null)
        markChange(k,null,AttributeModification.REMOVE);
      else
        return;
    else
      if(v2==null)
        markChange(k,AttributeModification.ADD,null);
      else
        if(!v1.equals(v2))
          markChange(k, AttributeModification.MODIFY, AttributeModification.MODIFY);
  }
  private void markChange(String k, AttributeModification add, AttributeModification add2) {
    if(add != null)
      left.markAttribute(k, add);
    if(add2 != null)
      right.markAttribute(k, add2);
  }
  
  private String gatherNameNoJson(TreeNode node, Object o) {
    if(node!=null)
      return node.getId();
    String n = gatherName(o);
    if(n.toLowerCase().endsWith(".json"))
      return n.substring(0,n.length()-5);
    return n;
  }
  private String gatherName(Object o) {
    if(o instanceof ResourceNode)
      return((ResourceNode)o).getName();
    if(o == null)
      return "";
    Method m;
    try {
      m = o.getClass().getMethod("getName");
      if(m!=null)
        return (String) m.invoke(o);
    } catch (NoSuchMethodException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (SecurityException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (IllegalAccessException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (IllegalArgumentException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (InvocationTargetException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return o.toString();
  }
  private Pair<TreeNode,String> loadTreeNode(String lc2s,String id) throws JSONException, IOException {
    //TODO mb change
	byte[] content = lc2s.getBytes(StandardCharsets.UTF_8);
    TreeDB db = TreeDB.loadTreeInstance(null,null, null,IResTracker.NULL, new MemoryStorage());//TODO check
    Map<String, NodeTypeDescriptor> nts = RequalityPlugin.getAdditionalTypes();
    for (String name : RequalityPlugin.getAdditionalTypes().keySet())
      db.addNodeType(name, nts.get(name).getClazz());
    UUID uid = null;
    try(ByteArrayInputStream bais = new ByteArrayInputStream(content)){
      uid = NodeMapper.getUUId(bais);
    }
    String version = RequalityStoredVersionProvider.getVersion();
    Map<String, AttributeDefinition> now = null;
    try(ByteArrayInputStream bais = new ByteArrayInputStream(content)){
      now = NodeMapper.getAttributes(uid, bais, TempFolderFilesProvider.EMPTY, false);
    }catch(Exception r){
      RLogger.logError(r);
    }
    if(now == null || now.isEmpty()){
      Pair<String, Map<String, AttributeDefinition>> ret =
        LegalicyNodeMapper.getAttributes(uid, content, TempFolderFilesProvider.getOne(null, false), null);
      now = ret.getSecond();
      version = ret.getFirst();
    }
    if(now == null||now.isEmpty())
      return null;
    now.put(TreeNode.ATTR_ID, TreeNode.formIdAttribute(uid, id));
    TreeNode tn = null;
    try {
      tn = db.getNodeType(now.get(TreeNode.ATTR_TYPE).getRawValue().getValue().toString()).getTypeClass().getConstructor(TreeDB.class, UUID.class)
      .newInstance(db,uid);
    } catch (InstantiationException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (IllegalAccessException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (IllegalArgumentException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (InvocationTargetException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (NoSuchMethodException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (SecurityException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    if(tn == null)
      return null;
    tn.attrs().setChanged(new ChangesSet<>(new HashSet<>(now.keySet()),
        null,null,null,now,null));
    ChangesSet<Attribute> change = TreeDB.addAttrsRec(tn.attrs(), true, false, OFlag.NONE);
    TreeDB.applyChanges(tn.attrs(), change);
    return new Pair<>(tn, version);
  }
  @Override
  protected void copy(boolean leftToRight) {
    // TODO Auto-generated method stub
    //System.out.println( ); 
  }
  @Override
  protected byte[] getContents(boolean left) {
    if(left && lefte){
      try {
        String c = NodeMapper.toJSON(l, true).toString(4);
        return c.getBytes("UTF-8");
      } catch (JSONException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      } catch (UnsupportedEncodingException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
    }
    if(!left&&righte){
      try {
        String c = NodeMapper.toJSON(r, true).toString(4);
        return c.getBytes("UTF-8");
      } catch (JSONException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      } catch (UnsupportedEncodingException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
    }
    return null;
  }
  @Override
  protected void handleDispose(DisposeEvent event) {
    super.handleDispose(event);
    if(l!=null && leftc!=null)
      l.removeGuiChangeListener(leftc);
    if(r!=null && rightc!=null)
      r.removeGuiChangeListener(rightc);
  }
  public void setLeftD(boolean b){
    setLeftDirty(b);
  }
  public void setRightD(boolean b){
    setRightDirty(b);
  }
  public static TreeNodeCompareViewer getLast() {
    return last;
  }
  public ElementComparePanel getLeft() {
    return left;
  }
}
