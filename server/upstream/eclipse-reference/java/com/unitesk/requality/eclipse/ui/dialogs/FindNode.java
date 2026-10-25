package com.unitesk.requality.eclipse.ui.dialogs;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.NodeSearch;
import com.unitesk.requality.core.search.AttributeOperator;
import com.unitesk.requality.core.search.ISearchRequest;
import com.unitesk.requality.core.search.JSFormula;
import com.unitesk.requality.core.search.JSFormulaHolder;
import com.unitesk.requality.core.search.SearchMethod;
import com.unitesk.requality.core.search.SearchRequestMan;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.core.RepeatingJob;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;
//TODO rewrite 
/** Find dialog */
public class FindNode extends Dialog implements IFSIdProvider {
  private static final String DS_FILENAME = "findopssetts";
  public static final String PARAM_SELECTED_NODE = "selected";
  private static String TITLE = "Search Element";
  private static String CONST_SELECT_ATTRS = "~Select one~";
  private static String LBL_SB = "Scope:", LBL_PATH = "Path:", LBL_ANAME = "Attribute name:",
      LBL_FORMULA = "Formula:";
  // private IDialogSettings ds;
  private Text path;
  private RepeatingJob rj = null;
  private Text formula;
  private Text aname;
  private Text aval;
  private Text targ;
  private Label info;
  private Label info2;
  private TabItem tab1, tab2, tab3;
  private TabFolder folder;
  private Combo aop;
  private Combo anames;
  private ISearchRequest sr;
  private FSId fsid = null;

  RequalityCNF cnf;
  private TreeNode selection = null;
  private TreeNode[] found = null;
  int pos = 0;
  private int cpos = -1;
  private String focused = null;
  private Map<String, Control> namedMap = new HashMap<>();
  private static Map<String, Map<String, String>> saved = new HashMap<>();

  public boolean isDisposed() {
    return folder.isDisposed();
  }

  private class NamedFocusListener implements FocusListener {
    private String name;
    private Control c;

    public NamedFocusListener(String name, Control c) {
      this.name = name;
      namedMap.put(name, c);
      this.c = c;
    }

    @Override
    public void focusGained(FocusEvent e) {
      if (!name.equals(focused))
        cpos = -1;
      focused = name;
      if (c instanceof Text) {
        updateCpos((Text) e.widget, true);
      }
    }

    @Override
    public void focusLost(FocusEvent e) {
//      focused = null;
//      cpos = -1;
    }

  }

  private IUIStateChangeListener list = new IUIStateChangeListener() {
    @Override
    public void newActiveNodes(FSId source, SelectionRecord nodes) {
      SelectionManager.getOne().removeSourceListener(EventSource.EXPLORER, fsid, list);
      try {
        if (!source.isSameAs(fsid))
          if (targ.isDisposed()) {
            IUIStateChangeListener athis = this;
            // SelectionManager.getOne().removeGenericListener(fsid, athis);
          } else {
            if (selection == null) {
              found = null;
              Collection<Object> lnodes = new ConcurrentLinkedDeque<>();
              Stream.of(nodes.fullSelection).forEach(one -> lnodes.add(one));
              updateSelection(lnodes, true);
              loadDS(selection);
              finUpSel(true);
              targ.setText(selectionToLabel());
              clearNodes();
            }
          }
      } finally {
        SelectionManager.getOne().addSourceListener(EventSource.EXPLORER, fsid, list);
      }
    }

  };
  public static FindNode one;

  // private AttributeOperator ao = null;
  private AttributeOperator getAO() {
    int c = aop.getSelectionIndex();
    AttributeOperator ao = null;
    if (c > -1)
      ao = AttributeOperator.values()[aop.getSelectionIndex()];
    return ao;
  }

  private void clearNodes() {
    found = null;
    pos = 0;
  }

  public void loadDS(Object node) {
    if (isSuitableProject(node)) {
      IProject p = (IProject) node;
      TreeDB db;
      EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
      try {
        db = et.getTreeDB(p, null, et.getLocalName(p));
        if (db != null)
          loadDS(db.getRootNode());
        else
          loadDS((TreeNode) null);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
    } else if (node instanceof TreeNode)
      loadDS((TreeNode) node);
    else
      loadDS((TreeNode) null);
  }

  private boolean isSuitableProject(Object node) {
    try {
      EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
      return node instanceof IProject && ((IProject) node).hasNature(ReqCatalogue.ID)
          && et.getProjectLoadingState(((IProject) node).getLocationURI()) == ProjectLoadingState.LOADED
          && !((IProject) node).hasNature(Consts.CATALOG_BROKEN_NATURE);
    } catch (CoreException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return false;
  }

  public void loadDS(TreeNode node) {
    if (rj != null) {
      rj.cancel();
      rj.stop();
    }
    String adds = node != null ? node.getTreeDB().getProjectName() : "";
    clearNodes();

//    ds = new DialogSettings("FindOpsDialog-"+adds);//is it legal?
//    try {
//      ds.load(DS_FILENAME+adds);
//    } catch (FileNotFoundException fne){
//      //file not exist
//    } catch (IOException e) {
//      RLogger.logError(e);
//    }
    if (node != null)
      db = node.getTreeDB();
    sr = SearchRequestMan.restore(load(selection, node != null ? node.getTreeDB() : null));
  }

  public void loadDS(List<Object> list2) {
    String adds = "";
    boolean hasOne = false;
    if (list2 != null && !list2.isEmpty()) {
      Iterator<Object> it = list2.iterator();
      while (it.hasNext() && !hasOne) {
        Object next = it.next();
        if (isSuitableProject(next) || next instanceof TreeNode) {
          hasOne = true;
          loadDS(next);
        }
      }
    }
    if (!hasOne)
      loadDS((TreeNode) null);
  }

  public FindNode(Shell shell, RequalityCNF c) {
    super(shell);
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.DIALOG, this, false);
    one = this;
    setShellStyle(SWT.DIALOG_TRIM | SWT.CLOSE | SWT.MODELESS | SWT.BORDER | SWT.TITLE);
    shell.addShellListener(new ShellListener() {

      @Override
      public void shellIconified(ShellEvent e) {
        // TODO Auto-generated method stub

      }

      @Override
      public void shellDeiconified(ShellEvent e) {
        // TODO Auto-generated method stub

      }

      @Override
      public void shellDeactivated(ShellEvent e) {
      }

      @Override
      public void shellClosed(ShellEvent e) {
        close();
      }

      @Override
      public void shellActivated(ShellEvent e) {
        // TODO Auto-generated method stub

      }
    });
    loadDS(RequalityCNF.getSelectedObj());
    cnf = c;
  }

  @Override
  public boolean close() {
    try {
      return super.close();
    } finally {
      if (fsid != null) {
        SelectionManager.getOne().removeSourceListener(fsid.getEventSource(), fsid, list);
        SelectionManager.getOne().unregisterInstance(fsid);
      }
      fsid = null;
      one = null;
    }
  }

  boolean loaded = false;
  private TreeDB db;
  private SelectionListener folderL;
  private ModifyListener pathM;
  private SelectionListener anamesL;
  private SelectionListener aopL;
  private ModifyListener avalL;
  private ModifyListener targL;
  private ModifyListener anameL;
  private ModifyListener formulaM;

  @Override
  protected Control createDialogArea(Composite parent) {
    getShell().setText(TITLE);
    Composite comp1 = new Composite(parent, SWT.NONE);
    comp1.setLayout(new GridLayout(1, true));
    comp1.setLayoutData(new GridData(GridData.FILL_BOTH));

    PaintListener pl = new PaintListener() {

      @Override
      public void paintControl(PaintEvent e) {
        if (loaded && focused != null) {
          Control c = namedMap.get(focused);
          if (c == e.widget)
            updateCpos(((Text) e.widget), true);
        }
      }
    };

    Composite lc = new Composite(comp1, SWT.NONE);
    lc.setLayout(new GridLayout(3, false));
    lc.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Label l = new Label(lc, SWT.NONE);
    l.setText(LBL_SB);

    targ = new Text(lc, SWT.SINGLE);
    targL = new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        if (db != null) {
          info2.setText("");
          info.setText("");
          try {
            TreeNode node = db.getNode(targ.getText());
            if (node == null)
              info.setText("Element '" + targ.getText() + "' is not found!");
            else {
              setSelection(node, true);
              loadDS(node);
              sr.put("selectionpos", null);
              saveNewParamsAndUpdateOp(node.getTreeDB());
              finUpSel(false);
            }
            updateCpos((Text) e.widget, false);
            reinitSearch(true);
          } finally {
            finUpSel(true);
          }
        }
      }
    };
    targ.addFocusListener(new NamedFocusListener("target", targ));
    targ.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Button b = new Button(lc, SWT.NONE);
    b.setText("Select scope");
    b.addSelectionListener(new SelectionListener() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        SelectNodeFromWorkspace snfrt = new SelectNodeFromWorkspace(Display.getCurrent().getActiveShell(), true,
            getSelectedTreeNode()) {
        };
        if (snfrt.open() == Window.OK) {
          TreeNode node = snfrt.selectedNode;
          if (node != null) {
            // saveNewParamsAndUpdateOp();
            try {
              db = node.getTreeDB();
              targ.setText(node.getTreeDB().getProjectName() + "::" + node.getUserVisibleName());
              // selection = node;
            } catch (Exception e2) {
              RLogger.logError(e2);
            } finally {
              // finUpSel();
            }
          }
        }
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        widgetSelected(e);
      }
    });

    folder = new TabFolder(comp1, SWT.NONE);
    folder.setLayoutData(new GridData(GridData.FILL_BOTH));
    folder.setLayout(new GridLayout(1, true));

    // Tab 1
    tab1 = new TabItem(folder, SWT.NONE);
    tab1.setText("By path");

    Composite c = new Composite(folder, SWT.NONE);
    c.setLayout(new GridLayout(2, false));
    c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Label l2 = new Label(c, SWT.NONE);
    l2.setText(LBL_PATH);
    path = new Text(c, SWT.BORDER);
    path.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    // path.addPaintListener(pl);

    path.addFocusListener(new NamedFocusListener("path", path));

    pathM = new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updateCpos((Text) e.widget, false);
        reinitSearch(true);
      }
    };

    tab1.setControl(c);

    tab2 = new TabItem(folder, SWT.NONE);
    tab2.setText("By attributes");

    Composite c2 = new Composite(folder, SWT.NONE);
    c2.setLayout(new GridLayout(3, false));
    c2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Label l3 = new Label(c2, SWT.NONE);
    l3.setText(LBL_ANAME);

    aname = new Text(c2, SWT.BORDER);
    aname.addFocusListener(new NamedFocusListener("aname", aname));

    aname.addPaintListener(pl);
    aname.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    anames = new Combo(c2, SWT.READ_ONLY);
    initANames(selection != null ? selection.getTreeDB() : db, anames, cnf);

    anames.setText(CONST_SELECT_ATTRS);
    // anames.select(0);//init by names

    anamesL = (new SelectionListener() {

      @Override
      public void widgetSelected(SelectionEvent e) {
        if (!anames.getText().equals(CONST_SELECT_ATTRS))
          aname.setText(anames.getText());
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub

      }
    });

    anameL = new ModifyListener() {

      @Override
      public void modifyText(ModifyEvent e) {
        updateCpos((Text) e.widget, false);
        reinitSearch(true);
      }
    };
    // aname.setText(anames.getItems()[0]);
    Label l4 = new Label(c2, SWT.NONE);
    l4.setText("Method:");
    aop = new Combo(c2, SWT.READ_ONLY);
    aop.addFocusListener(new NamedFocusListener("method", aop));
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    aop.setLayoutData(gd);
    aopL = (new SelectionListener() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        clearNodes();
        int i = aop.getSelectionIndex();
        AttributeOperator ao = null;
        if (i > -1)
          ao = AttributeOperator.values()[aop.getSelectionIndex()];
        aval.setEnabled(true);
        if (ao == AttributeOperator.NOT_EXISTS || ao == AttributeOperator.EXISTS)
          aval.setEnabled(false);
        reinitSearch(true);
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub

      }
    });
    folderL = new SelectionListener() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        reinitSearch(true);
        setTFocus(getDefTextName(), "0");
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
      }
    };

    Label l5 = new Label(c2, SWT.NONE);
    l5.setText("Value:");

    aval = new Text(c2, SWT.BORDER);

    aval.addFocusListener(new NamedFocusListener("value", aval));

    avalL = (new ModifyListener() {

      @Override
      public void modifyText(ModifyEvent e) {
        clearNodes();
        updateCpos((Text) e.widget, false);
        reinitSearch(true);
      }
    });
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    aval.setLayoutData(gd);
    aval.addPaintListener(pl);
    tab2.setControl(c2);

    // Tab 3
    tab3 = new TabItem(folder, SWT.NONE);
    tab3.setText("By formula");

    c = new Composite(folder, SWT.NONE);
    c.setLayout(new GridLayout(2, false));
    c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Label l6 = new Label(c, SWT.NONE);
    gd = new GridData();
    gd.verticalAlignment = SWT.TOP;
    l6.setLayoutData(gd);
    l6.setText(LBL_FORMULA);
    formula = new Text(c, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
    formula.setLayoutData(new GridData(GridData.FILL_BOTH));
    // path.addPaintListener(pl);
    formula.addFocusListener(new NamedFocusListener("formula", formula));

    formulaM = new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updateCpos((Text) e.widget, false);
        TreeNode selection = getSelectedTreeNode();
        if (selection != null) {
          info.setText("Evaluated on " + selection.getUserVisibleName());
          info2.setText("Calculations...");
          JSFormulaHolder jfh = new JSFormulaHolder(selection.getTreeDB().getRootNode());
          JSFormula f = jfh.addRule("testJS", formula.getText());
          // Object res = null;
          Pair<Object, Boolean> res = f.runOnNode(selection);
          if (res.getFirst() == null)
            info2.setText("NULL");
          else
            info2.setText(res.getFirst().toString());
        } else
          info2.setText("Selection cannot be empty!");
        reinitSearch(true);
      }
    };

    tab3.setControl(c);

    info = new Label(comp1, SWT.BORDER);
    info.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    info2 = new Label(comp1, SWT.BORDER);
    info2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    
    List<Object> objs = RequalityCNF.getSelectedObj();
    updateSelection(objs, false);
    
    updateContent(objs);
    finUpSel(false);
    // SelectionManager.getOne().addGenericListener(fsid, list);
    return parent;
  }

  protected String getDefTextName() {
    switch (SearchRequestMan.getMethod(sr)) {
    case BY_PATH:
      return "path";
    case BY_ATTRIBUTE:
      return "value";
    case BY_REQUEST:
      return "formula";
    }
    return null;
  }

  protected void finUpSel(boolean save) {

    TreeNode sel = getSelectedTreeNode();
    selection = sel;
    List<Object> selList = new LinkedList<>();
    selList.add(selection);
    updateContent(selList);
    setSelection(selection, false);
    if (save)
      saveNewParamsAndUpdateOp(selection != null ? selection.getTreeDB() : db);
    updateContent(selList);
  }

  private void updateContent(List<Object> selAlt) {
    sr = SearchRequestMan.restore(load(selection, selection != null ? selection.getTreeDB() : db));

    info.setText("");
    info2.setText("");
    folder.removeSelectionListener(folderL);
    folder.setSelection(SearchRequestMan.getMethod(sr).ordinal());
    folder.addSelectionListener(folderL);
    AttributeOperator selop = null;

    aval.removeModifyListener(avalL);
    if (selop == AttributeOperator.NOT_EXISTS || selop == AttributeOperator.EXISTS)
      aval.setEnabled(false);
    String ab = sr.get("sample");
    aval.setText(ab != null ? ab : "");
    aval.addModifyListener(avalL);

    String sel = sr.get("selected");
    String selp = sr.get("selectionpos");
    boolean posIncorrect = false;
    if (selp != null && !selp.equals("0"))
      pos = Integer.parseInt(selp);
    if (sel == null) {
      pos = 0;
      posIncorrect = true;
      updateSelection(selAlt, true);
    } else {
      selection = null;
      String[] sela = sel.split("\\|");
      if (sela.length != 2)
        updateSelection(selAlt, false);
      else {
        for (int i = 0; i < sela.length; i += 2) {
          TreeDB db = ResManager.getManager().getTreeDB(null, null, sela[i], true,
              new ConsoleProgressMonitor());// TODO IDE monitor
          if (db != null) {
            FindNode.this.db = db;
            selection = db.getNode(UUID.fromString(sela[i + 1]));
          }
        }
      }
      if (selection == null) {
        pos = 0;
        updateSelection(selAlt, false);
      } else if (!posIncorrect) {
        initNodesSet(false);
        setSel(pos);
      }
    }

    aop.removeSelectionListener(aopL);
    aop.removeAll();
    for (AttributeOperator sm : AttributeOperator.values())
      aop.add(sm.name());
    String op = sr.get("op");
    for (int i = 0; i < AttributeOperator.values().length; i++) {
      AttributeOperator ao = AttributeOperator.values()[i];
      if (op == null)
        op = AttributeOperator.EQUALS.name();
      if (ao.name().equals(op)) {
        selop = ao;
        aop.select(i);
        break;
      }
    }
    aop.addSelectionListener(aopL);

    String at = sr.get("key"), alt = null;
    if (at != null) {
      alt = SystemNames.getOne(selection != null ? selection.getTreeDB() : db).getVisSysName(at);
      if (alt != null)
        at = alt;
    } else
      at = "";

    anames.removeSelectionListener(anamesL);
    anames.setText(CONST_SELECT_ATTRS);
    if (!at.isEmpty()) {
      String[] its = anames.getItems();
      for (int i = 0; i < its.length; i++)
        if (its[i].equals(at)) {
          anames.select(i);
          break;
        }
    } else
      at = anames.getText();
    anames.addSelectionListener(anamesL);

    aname.removeModifyListener(anameL);
    aname.setText(at);
    aname.addModifyListener(anameL);

    String pa = sr.get("path");
    if (pa == null)
      pa = "";

    targ.removeModifyListener(targL);
    targ.setText(selectionToLabel());
    targ.addModifyListener(targL);

    String fo = sr.get("formula");
    if (fo == null)
      fo = "";
    formula.removeModifyListener(formulaM);
    formula.setText(fo);
    formula.addModifyListener(formulaM);

    path.removeModifyListener(pathM);
    path.setText(pa);
    path.addModifyListener(pathM);
    String rf = sr.get("focus");
    if (rf == null || rf.isEmpty()) {
      rf = getDefTextName();
    }

    AttributeOperator ao = getAO();
    final String f = rf;
    final Text aval = this.aval;
    EclipseAPI.getDisplay().asyncExec(new Runnable() {

      @Override
      public void run() {
        if (aval.isDisposed())
          return;
        aval.setEnabled(true);
        if (ao == AttributeOperator.NOT_EXISTS || ao == AttributeOperator.EXISTS)
          aval.setEnabled(false);

        setTFocus(f, sr.get("focusPos"));
        loaded = true;
      }
    });
  }

  protected void setTFocus(String f, String pos) {
    Control ca = namedMap.get(f);
    if (ca != null)
      ca.setFocus();
    if (ca instanceof Text) {
      if (pos == null)
        pos = "0";
      cpos = Integer.parseInt(pos);
      if (cpos > -1) {
        ((Text) ca).setSelection(cpos);
      }
    }
  }

  protected void updateCpos(Text text, boolean save) {
    if (loaded) {
      int old = cpos;
      cpos = text.getSelection().x;
      if (old != cpos && save)
        saveNewParamsAndUpdateOp(selection != null ? selection.getTreeDB() : db);
    }
  }

  private String selectionToLabel() {
    return getSelectedTreeDB().getProjectName() + "::" + getSelectedTreeNode().getUserVisibleName();
  }

  private TreeNode getSelectedTreeNode() {
    return selection != null ? selection : db != null ? db.getNode(Requirement.getTypeRootQId()) : null;
  }

  private TreeDB getSelectedTreeDB() {
    return selection != null ? selection.getTreeDB() : db;
  }

  private static void initANames(TreeDB db, Combo c, RequalityCNF cnf2) {
    c.add(CONST_SELECT_ATTRS);
    for (String s : SystemNames.getOne(db).getVisSysNames())
      c.add(s);
  }

  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.CANCEL_ID, "Close", false);
    Button spaceButton = createButton(parent, 3, "", false);
    spaceButton.setVisible(false);
    createButton(parent, IDialogConstants.CLIENT_ID + 1, "<<", false);
    createButton(parent, IDialogConstants.CLIENT_ID + 2, ">>", true);
    // if (comboStrings.isEmpty()) {
//    if (combo.getText().isEmpty()) {
//      getButton(IDialogConstants.CLIENT_ID + 1).setEnabled(false);
//      getButton(IDialogConstants.CLIENT_ID + 2).setEnabled(false);
//    }
  }

  @Override
  protected void buttonPressed(int buttonId) {
    super.buttonPressed(buttonId);
    // if (combo.isDisposed()) return;
    // if (combo.getText().isEmpty()) return;
    saveTextToCombo();
    SelectionManager.getOne().removeSourceListener(EventSource.EXPLORER, fsid, list);
    if (IDialogConstants.CLIENT_ID + 1 == buttonId) {
      initNodesSet();
      pos--;
      if (pos < 0)
        pos = found != null ? found.length - 1 : 0;
      if (pos < 0)
        pos = 0;
      setSel(pos);
//      if (browser != null) {
//        browser.findText(combo.getText(), selectCaseSensitive, true, false);
//      }
      // selectWrapAround);
    }

    if (IDialogConstants.CLIENT_ID + 2 == buttonId) {
      initNodesSet();
      pos++;
      if (found == null || pos >= found.length)
        pos = 0;
      setSel(pos);
//      if (browser != null) {
//        browser.findText(combo.getText(), selectCaseSensitive, false, false);
//        //                    selectWrapAround);
//      }
    }
    if (folder.isDisposed())
      return;
    // TODO check
    SelectionManager.getOne().addSourceListener(EventSource.EXPLORER, fsid, list);
    updateInfoFound();
  }

  private void setSel(int pos2) {
    if (found == null || pos2 >= found.length || pos2 < 0)
      return;
    TreeNode nd = found[pos2] == null ? null : found[pos2];
    saveNewParamsAndUpdateOp(nd != null ? nd.getTreeDB() : db);
    if (nd != null) {
      SelectionManager sman = SelectionManager.getOne();
      sman.removeSourceListener(EventSource.EXPLORER, fsid, list);// TODO may be async
      try {
        sman.updateSelection(SourcePriority.PROPERTIES, fsid, nd);
        sman.changeActiveSource(SourcePriority.PROPERTIES, fsid.getEventSource(), fsid, true, true);
        FSId[] expId = sman.getActiveSources(EventSource.EXPLORER);
        if (expId != null && expId.length > 0) {
          sman.updateSelection(SourcePriority.PROPERTIES, fsid, nd);// first expand node
          sman.updateSelection(SourcePriority.PROPERTIES, expId[0], nd);// then set it as explorer-related
        }
      } finally {
        sman.addSourceListener(EventSource.EXPLORER, fsid, list);
      }
    }
  }

  private void reinitSearch(boolean save) {
    found = null;
    pos = 0;
    if (save)
      saveNewParamsAndUpdateOp(selection != null ? selection.getTreeDB() : db);
  }

  private void initNodesSet() {
    initNodesSet(true);
  }
  
  private WorkspaceJob lastUpdateJob = null;
  
  private void initNodesSet(boolean resetUp) {
    if (found != null)
      return;
    info2.setText("");
    info.setText("Searching...");
    char[] prog = new char[] { '⣀', '⣦', '⣇', '⣿' };
    if (rj != null) {
      rj.cancel();
      rj.stop();
    }
    rj = new RepeatingJob("update .", 100) {
      int j = 0;

      protected IStatus run(IProgressMonitor monitor) {
        j++;
        if (j > 3)
          j = 0;
        StringBuilder sb = new StringBuilder("Searching");
        for (int i = 0; i < j; i++)
          sb.append(".");
        if (found != null) {
          stop();
          return Status.OK_STATUS;
        } else {
          final RepeatingJob rjt = this;
          EclipseAPI.getDisplay().syncExec(new Runnable() {

            @Override
            public void run() {
              if (!info2.isDisposed()) {
                info2.setText(Character.toString(prog[j]));
                info.setText(sb.toString());
              } else {
                rjt.stop();
                rjt.cancel();
              }
            }
          });
          return super.run(monitor);
        }
      }
    };
    rj.schedule();
    if (resetUp) {
      pos = 0;
      saveNewParamsAndUpdateOp(selection != null ? selection.getTreeDB() : db);
      targ.setText(selectionToLabel());
    }
    final Text targ = FindNode.this.targ;
    final int pos = this.pos;
    final TreeNode selection = this.selection;
    final ISearchRequest sr = this.sr;
    final String targText = targ.getText();
    
    final WorkspaceJob wj = new WorkspaceJob("Start search "+targText) {

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        if(lastUpdateJob != this){
          this.cancel();
          return Status.CANCEL_STATUS;
        }
        // TODO Auto-generated method stub
        try{
          NodeSearch nsh = new NodeSearch(sr);
          TreeNode node = selection;
          
          nsh.execute(node);
          java.util.List<TreeNode> fnd = new LinkedList<>();
          List<Pair<? extends TreeNode, List<String>>> matches = nsh.getMatches();
          TreeNode [] onlyNodes = new TreeNode[matches.size()];
          int i = 0;
          for(Pair<? extends TreeNode, List<String>> pair: matches)
            onlyNodes[i++] = pair.getFirst();
          found = onlyNodes;
          Comparator<TreeNode> comp = TreeNodeComparator.getGComparator(SortingSettings.init(SortingMethod.BY_INDEX, false));
          if(onlyNodes!=null)
            Arrays.sort(onlyNodes,
              comp);
  
          EclipseAPI.getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
              if (targ.isDisposed())
                return;
              setSel(pos);
              updateInfoFound();
            }
  
          });
          return Status.OK_STATUS;
        }finally{
          try{
            synchronized (FindNode.this) {
              if(lastUpdateJob==this)
                lastUpdateJob = null;
            }
          }catch(Exception e){
            RLogger.logError(e);
          }
        }
      }
    };
    synchronized (this) {
      lastUpdateJob = wj;
      lastUpdateJob.schedule();
    }
  }

  private void updateInfoFound() {
    if (found == null || info2.isDisposed())
      return;
    info2.setText("");
    if (found.length == 0) {
      info.setText("0 results found");
      // info2.setText("scope: ["+selection.getUserVisibleName()+"]");
    } else {
      info.setText((pos + 1) + " of " + found.length + " matches");
      info2.setText("[" + found[pos].getUserVisibleName() + "]");
    }
  }

  private void updateSelection(Collection<Object> sel, boolean save) {
    selection = null;
    try {
      if (sel == null || sel.isEmpty()) {
        for (TreeDB db : ResManager.getManager().getTreeDBs()) {
          this.db = db;
          setSelection(db.getRootNode(), save);
          return;
        }
      } else {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        for (Object o : sel) {
          if (o instanceof IProject) {
            IProject p = (IProject) o;
            try {
              db = et.getTreeDB(p, null, et.getLocalName(p));
              setSelection(db.getRootNode(), save);
            } catch (IOException e) {
              RLogger.logError(e);
            } // TODO mv resMan
            return;
          } else if (o instanceof TreeNode) {
            setSelection((TreeNode) o, save);
            db = selection.getTreeDB();
            return;
          }
        }
      }
    } finally {
      if (save && db!=null)
        saveNewParamsAndUpdateOp(db);
    }
    setSelection(null, save);
  }

  private void setSelection(TreeNode rootNode, boolean save) {
    selection = rootNode;
    targ.removeModifyListener(targL);
    targ.setText(selectionToLabel());
    targ.addModifyListener(targL);
    reinitSearch(save);
  }

  private void saveNewParamsAndUpdateOp(TreeDB db) {
    folder.removeSelectionListener(folderL);
    try {
      SearchMethod sm = SearchMethod.BY_ATTRIBUTE;
      switch (folder.getSelectionIndex()) {
      case 0:
        sm = SearchMethod.BY_PATH;
        break;
      case 1:
        sm = SearchMethod.BY_ATTRIBUTE;
        break;
      case 2:
        sm = SearchMethod.BY_REQUEST;
        break;
      }
      Map<String, TypedValue> params = new HashMap<>();
      if (focused != null)
        params.put("focus", new TypedValue(AttributeType.STRING, focused));
      if (cpos > -1) {
        params.put("focusPos", new TypedValue(AttributeType.STRING, Integer.toString(cpos)));
      }
      switch (sm) {
      case BY_PATH:
        params.put("path", new TypedValue(AttributeType.STRING, path.getText()));
        break;
      case BY_ATTRIBUTE: {
        params.put("sample", new TypedValue(AttributeType.STRING, aval.getText()));
        params.put("op", new TypedValue(AttributeType.STRING, getAO().name()));
        params.put("key", new TypedValue(AttributeType.STRING, aname.getText()));
        String sn = SystemNames.getOne(db).getSysName(aname.getText());
        if (sn != null)
          params.put("key", new TypedValue(AttributeType.STRING, sn));
      }
        break;
      case BY_REQUEST: {
        params.put("formula", new TypedValue(AttributeType.STRING, formula.getText()));
      }
        break;
      }
      String adds = "";
      if (selection != null) {
        adds = selection.getTreeDB().getProjectName();
      }
      params.put(PARAM_SELECTED_NODE, new TypedValue(AttributeType.STRING, toSelectionString(selection)));

      params.put("selectionpos", new TypedValue(AttributeType.STRING, Integer.toString(pos)));

      params.put(SearchRequestMan.SMNAME, new TypedValue(AttributeType.STRING, sm.name()));
      sr = SearchRequestMan.restore(SearchRequestMan.toStored(SearchRequestMan.getMethod(sr), params));
      save(db, SearchRequestMan.toStored(SearchRequestMan.getMethod(sr), params));
//      try {
//        ds.save(DS_FILENAME+adds);
//      } catch (IOException e) {
//        RLogger.logError(e);
//      }
    } finally {
      folder.addSelectionListener(folderL);
    }
  }

  private static String toSelectionString(TreeNode selection) {
    if (selection == null)
      return "";
    StringBuilder sv = new StringBuilder();
    sv.append(selection.getTreeDB().getProjectName());
    sv.append("|");
    sv.append(selection.getSUUId());
    return sv.toString();
  }

  private static Map<String, String> load(TreeNode sel, TreeDB db) {
    if (db != null) {
      Map<String, String> stored = saved.get(db.getLocalName());
      if (stored == null)
        return new HashMap<>();
      if (sel != null) {
        String selS = stored.get(PARAM_SELECTED_NODE);
        String newStr = toSelectionString(sel);
        if (!sel.isRoot() && !selS.equals(newStr))// if we selected another search root
          return new HashMap<>();
      }
      return stored;
    }
    return new HashMap<>();
//    Map<String, String> ret = new HashMap<>();
//    String[] arr = ds.getArray("KEYS");
//    if(arr!=null){
//      for(String s:arr)
//        ret.put(s, ds.get(s));
//    }
//    return ret;
  }

  private static void save(TreeDB db, Map<String, String> stored) {
    saved.put(db.getLocalName(), new HashMap<>(stored));
    // ds.put("KEYS", stored.keySet().toArray(new String[0]));
//    for(String s:stored.keySet())
//      ds.put(s, stored.get(s));
  }

  private void saveTextToCombo() {
//    String text = combo.getText();
//    if (comboStrings.contains(text)) {
//      comboStrings.remove(text);
//    }
//    comboStrings.add(0, text);
//    combo.setItems(comboStrings.toArray(new String[0]));
//    combo.setText(text);
  }

  @Override
  public FSId getFSId() {
    return fsid;
  }

  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
