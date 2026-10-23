package com.unitesk.requality.vscode.port.eclipse.views.projectSettings;

import java.io.IOException;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPropertyPage;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.checker.CheckerRulesHolder;
import com.unitesk.requality.core.checker.DefTermProjectChecker;
import com.unitesk.requality.core.checker.UVIdProjectChecker;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.search.JSFormula;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.message.RMessages;

public class CheckerRulesProps extends RequalityPropertyPage implements IWorkbenchPropertyPage {
  private List list;
  private TreeDB db;
  private TreeNode snode;
  private IProject p;  
  private Composite c;
  private Button add;
  private Button remove;
  private Button enableUVIdChecker;
  private Button enableDefTermChecker;
  private Text aname;
  private Text acp;
  private Text nodesel;
  private Label tn;
  private Group rs;
  private CheckerRulesHolder crh;
  private boolean uvIDCheckerEnabledState = true; 
  private boolean defTermCheckerEnabledState = true;

  public CheckerRulesProps() {}
 
  private void recEnabled(Control c, boolean b){
    c.setEnabled(b);
    if(c instanceof Composite){
      Composite cm = (Composite)c;
      for(Control c2:cm.getChildren())
        recEnabled(c2, b);
    }
  }
  @Override
  protected Control createContents(Composite parent) {

    c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(2, false));
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    Composite c2 = new Composite(c, SWT.NONE);
    c2.setLayoutData(new GridData(GridData.FILL_BOTH));
    c2.setLayout(new GridLayout(1,true));

    final Group checkersGeneral = new Group(c2, SWT.NONE);
    checkersGeneral.setText("Checkers settings:");
    checkersGeneral.setLayout(new GridLayout(2, false));
    checkersGeneral.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    Label l = new Label(checkersGeneral, SWT.NONE);
    l.setText("UserVisible Id Checker Enabled");
    enableUVIdChecker = new Button(checkersGeneral, SWT.CHECK);

    l = new Label(checkersGeneral, SWT.NONE);
    l.setText("Terms Checker Enabled");
    enableDefTermChecker = new Button(checkersGeneral, SWT.CHECK);
    if(db!=null) {
      enableUVIdChecker.setSelection(UVIdProjectChecker.isEnabled(db));
      enableDefTermChecker.setSelection(DefTermProjectChecker.isEnabled(db));
    }
    enableUVIdChecker.addSelectionListener(new SelectionListener() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        uvIDCheckerEnabledState = enableUVIdChecker.getSelection();		
      }
      @Override
        public void widgetDefaultSelected(SelectionEvent e) {
      }
	});
    enableDefTermChecker.addSelectionListener(new SelectionListener() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          defTermCheckerEnabledState = enableDefTermChecker.getSelection();		
        }
        @Override
          public void widgetDefaultSelected(SelectionEvent e) {
        }
  	});
    final Group rules = new Group(c2, SWT.NONE);
    rules.setText("Rule list:");
    rules.setLayout(new GridLayout(2, false));
    rules.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    l = new Label(rules, SWT.NONE);
    l.setText("Rules");

    //TODO table
    
    
    Composite rulesetts = new Composite(rules, SWT.NONE);
    rulesetts.setLayout(new GridLayout(1,true));
    rulesetts.setLayoutData(new GridData(GridData.FILL_BOTH));
    list = new List(rulesetts, SWT.H_SCROLL|SWT.V_SCROLL);
    list.setLayoutData(new GridData(GridData.FILL_BOTH));
    list.addSelectionListener(new SelectionListener() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        int sel = list.getSelectionIndex();
        if(sel>-1)
        {
          JSFormula rule = crh.rules.get(sel);
          acp.setText(rule.getFormula());
          aname.setText(rule.getName());
          recalc();
          recEnabled(rs, true);
        }
      }
      
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    Composite buttons = new Composite(rulesetts, SWT.NONE);
    buttons.setLayout(new GridLayout(5,false));
    buttons.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    
    add = new Button(buttons, SWT.NONE);
    add.setText("Add");
    add.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event event) { 
        if(add.isDisposed())
          return;
        int i = 1;
        String name = "Rule "+i;
        while(checkWas(name)){
          i++;
          name = "Rule "+i;
        }
        crh.addRule(name, "");
        updatesRulesSet(true);
      }

      private boolean checkWas(String name) {
        if(crh.rules!=null)
          for(JSFormula f: crh.rules)
            if(f.getName().toLowerCase().equals(name.toLowerCase()))
              return true;
        return false;
      }
    });
    remove = new Button(buttons, SWT.NONE);
    remove.setText("Remove");
    remove.addListener(SWT.Selection, new Listener(){
      @Override
      public void handleEvent(Event event) {
	    if(remove.isDisposed())
          return;
        int sel = list.getSelectionIndex();
        if(sel > -1){
          crh.removeRule(sel);
          updatesRulesSet(true);
          recEnabled(rs, false);
        }
      }
    });
    Button clear = new Button(buttons, SWT.NONE);
    clear.setText("Clear");
    clear.addListener(SWT.Selection, new Listener(){
      @Override
      public void handleEvent(Event event) {
	    if(clear.isDisposed())
          return;
        crh.rules.clear();
        updatesRulesSet(true);
        recEnabled(rs, false);
      }
    });
    Button imp = new Button(buttons, SWT.NONE);
    imp.setText("Import from string");
    imp.addListener(SWT.Selection, new Listener(){
      @Override
      public void handleEvent(Event event) {
  	    if(imp.isDisposed())
          return;
        Pair<String, Boolean> id = RMessages.openInputDialog("Import checker rules as string", "Enter checker rules exported from some project here.", "", null);
        if(id.getSecond()) {
          addCheckerRulesAsString(id.getFirst());
          updatesRulesSet(true);
          recEnabled(rs, false);
        }
      }
    });

    Button export = new Button(buttons, SWT.NONE);
    export.setText("Export to string");
    export.addListener(SWT.Selection, new Listener(){
      @Override
      public void handleEvent(Event event) {
        RMessages.openBigInfo("Exported checker rules", getCheckerRulesAsString(), true);
      }
    });
    updatesRulesSet(true);
    
    
    rs = new Group(c2, SWT.NONE);
    rs.setText("Rule settings:");
    rs.setLayout(new GridLayout(1, false));
    rs.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label la = new Label(rs, SWT.NONE);
    la.setText("Name:");
    aname = new Text(rs, SWT.NONE);
    aname.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    aname.addModifyListener(new ModifyListener(){
    
      @Override
      public void modifyText(ModifyEvent e) {
	    if(aname.isDisposed())
          return;
        recalc();
        boolean up = false;
        int sel = list.getSelectionIndex();
        String newMsg = null;
        boolean changeMsg = false;
        if(sel>-1){
          String name = aname.getText();
          boolean err = false;
          if(name.isEmpty()){
            err = true;
            newMsg = "Rule name cannot be empty";
            changeMsg = true;
          }for(int i = 0;i<crh.rules.size();i++)
            if(sel!=i && crh.rules.get(i).getName().equals(name)){
              newMsg = "Rule name was already used";
              changeMsg = true;
              err = true;
            }
          if(!err){
            newMsg = null;
            changeMsg = true;
            crh.rules.get(sel).setName(name);
            up = true;
          }
        }
        if(changeMsg)
          updateStatus(newMsg, true);//TODO this thread may be a display one?
        if(up){
          updatesRulesSet(false);
          list.setSelection(sel);
        }
      }
    });
    la = new Label(rs, SWT.NONE);
    la.setText("Body:");
    acp = new Text(rs, SWT.MULTI);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.heightHint = 50;
    acp.setLayoutData(gd);
    acp.addModifyListener(new ModifyListener(){
    
      @Override
      public void modifyText(ModifyEvent e) {
	    if(acp.isDisposed())
          return;
        recalc();
        boolean up = false;
        int sel = list.getSelectionIndex();
        if(sel>-1){
          crh.rules.get(sel).setFormula(acp.getText());
          up = true;
        }
        if(up){
          updatesRulesSet(false);
          list.setSelection(sel);
        }
      } 
        //}
    });
    Group nodes = new Group(c2,SWT.NONE);
    nodes.setText("Testing subject:");
    nodes.setLayout(new GridLayout(1, false));
    nodes.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    nodesel = new Text(nodes, SWT.NONE);
    nodesel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    nodesel.setText("/Requirements");
    nodesel.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
	    if(nodesel.isDisposed())
          return;
        snode = db.getNode(nodesel.getText());
        recalc();
      }
    });

    Label l4 = new Label(nodes, SWT.NONE);
    l4.setText("Rule execution result on testing subject:");
    tn = new Label(nodes, SWT.BORDER);
    tn.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    initControls();
    recEnabled(rs, false);
    return c;
  }
  
  private String getCheckerRulesAsString(){
    StringBuilder sb = new StringBuilder();
    for(JSFormula jf: crh.rules){
      sb.append(jf.getName());
      sb.append(";|;");
      sb.append(jf.getFormula());
      sb.append("\n");
    }
    return sb.toString();
  }
  private void addCheckerRulesAsString(String first) {
    String[] sep = first.split("\n");
    for(String s:sep){
      if(!s.trim().isEmpty()){
        String[] chs = s.split(";\\|;");
        if(chs.length>1) {
          for(JSFormula f: crh.rules) {
            if(f.getName().equals(chs[0])) { 
              crh.removeRule(f);
              crh.rules.remove(f);
            }
          }
          crh.rules.add(new JSFormula(chs[0], chs[1], crh));
        }
      }      
    }
  }
  private void recalc(){
    TreeNode node = snode;
    if(node == null)
      node = db.getNode(Requirement.getTypeRootQId());
    String atext = aname.getText();
    String acptext = acp.getText();
    TreeNode fnode = node;
    final Label tn = CheckerRulesProps.this.tn;
    final CheckerRulesHolder crh = CheckerRulesProps.this.crh;
    WorkspaceJob j = new WorkspaceJob("Calculate formula on "+(node!=null?node.getUserVisibleName():"null node")){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        JSFormula cr = new JSFormula(atext, acptext, crh);
        Object msg;
        Pair<Object, Boolean> res = cr.runOnNode(fnode); 
        Object fmsg = res.getFirst();
        boolean exc = res.getSecond();//TODO adds color
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
          @Override
          public void run() {
            if(tn.isDisposed())
              return;
            tn.setText("");
            if(fmsg!=null)
              tn.setText(fmsg.toString());              
          }
        });
        return Status.OK_STATUS;
      }
      
    };
    j.schedule();
  }
  protected void updatesRulesSet(boolean updateRec) {
    if(list!=null){
      list.removeAll();
      if(crh!=null)
      {
        java.util.List<JSFormula> l = crh.rules;
        if(l != null)
          for(JSFormula cr: l)
            list.add(cr.getName());
      }
      if(rs!=null && updateRec)
        recEnabled(rs, false);
      
    }
  }

  private void initControls() {
    recEnabled(c, true);
  }
  @Override
  public void setElement(com.unitesk.requality.vscode.ui.runtime.IAdaptable element) {
    if (element instanceof IProject) {
      p = (IProject) element;
      try {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
		db = et.getTreeDB(p, null, et.getLocalName(p));
	  } catch (IOException e) {
		RLogger.logError(e);
	  }
      if(db==null)
        return;
      crh = new CheckerRulesHolder(db.getRootNode());
      updatesRulesSet(true);
      if(enableUVIdChecker!=null)
        enableUVIdChecker.setSelection(UVIdProjectChecker.isEnabled(db));
      if(enableDefTermChecker!=null) {
        enableDefTermChecker.setSelection(DefTermProjectChecker.isEnabled(db));
      }
    }
  };
  //actions when new selection is performed

  private boolean settsChanged(java.util.List<JSFormula> rules) {
    CheckerRulesHolder crh = db.getAttributeRules().getCheckerRules();
    return !crh.sameRules(rules);
  }

  //Changing settings to default
  @Override
  protected void performDefaults() {
    if(inParallelUpdate())
      return;
    tn.setText("");
    aname.setText("");
    list.removeAll();
    crh.rules.clear();
    acp.setText("");
    aname.setText("");
    nodesel.setText("/Requirements");
    updatesRulesSet(true);
    enableUVIdChecker.setSelection(true);
    enableDefTermChecker.setSelection(true);
    recalc();
    initControls();
    super.performDefaults();
  }

  @Override
  public boolean isOkOperationInTransaction() {
    return false;//TODO true
  }
  @Override
  public void prepareVars() {
  }
  @Override
  public IStatus okPressedAction(IProgressMonitor monitor) {
    if(actionsStarted)
      return Status.CANCEL_STATUS;
    actionsStarted = true;
    try {
      if(db == null)
        return Status.CANCEL_STATUS;
      db.getAttributeRules().getCheckerRules().setRules(crh.rules);
      UVIdProjectChecker.setEnabled(db, uvIDCheckerEnabledState);
      DefTermProjectChecker.setEnabled(db, defTermCheckerEnabledState);
      TreeDB fdb = db;
      JobManager.jm.startJob("Restart checkers after setting changes", new RunnableWithMonitor() {
        @Override
        public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
          Global.runCheckers(fdb, true, true);
        }
      }, false);
      return Status.OK_STATUS;
    }finally {
      actionsStarted = false;
    }
  }

  @Override
  public boolean settingsChanged() {
    CheckerRulesHolder crh = db.getAttributeRules().getCheckerRules();
    return !crh.sameRules(this.crh.rules) || UVIdProjectChecker.isEnabled(db)!=uvIDCheckerEnabledState 
    		|| DefTermProjectChecker.isEnabled(db)!=defTermCheckerEnabledState;
  }

  @Override
  public TreeDB getTreeDB() {
    return db;
  }

  @Override
  public boolean inputCorrect() {
    return true;//no correctness checks
  }
}
