package com.unitesk.requality.vscode.port.eclipse.views.projectSettings;

import java.io.IOException;
import java.util.Iterator;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPropertyPage;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.fid.Change;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.BreadthFirstTreeWalker;
import com.unitesk.requality.tool.message.RMessages;

public class FIDPolicy extends RequalityPropertyPage implements IWorkbenchPropertyPage, INodeChangeListener {
  private TreeDB db;
  private IProject p;  
  private Button enfid;
  
  private Text fidname, fidprefix;
  private KeyListener kl = new KeyListener(){
  
    @Override
    public void keyReleased(KeyEvent e) {
      if(((Text)e.getSource()).isDisposed())
        return;
      String updMsg = null;
      String val = (((Text)e.getSource()).getText());
      if(!Attribute.checkNameIsValid(val, false)){
        String msg = "Attribute name "+val+" is not valid!";
        updMsg = msg; 
      }
      updateStatus(updMsg, true);//TODO not sep display runnable?
    }
  
    @Override
    public void keyPressed(KeyEvent e) {
      // TODO Auto-generated method stub
      
    }
  }; 
  private Group container3;
 // protected Pair<Pair<String, String>, Change> change;
  
  public FIDPolicy() {}
  private void recEnabled(Control c, boolean b){
    c.setEnabled(b);
    if(c instanceof Composite){
      Composite cm = (Composite)c;
      for(Control c2:cm.getChildren())
        recEnabled(c2, b);
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    db.getRootNode().removeGuiChangeListener(this);
  }
  @Override
  protected Control createContents(Composite parent) {

    Composite c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(1, true));
    c.setLayoutData(new GridData(GridData.FILL_BOTH));

    container3 = new Group(c, SWT.NONE);
    container3.setText("Sequential Identifiers Settings:");
    container3.setLayout(new GridLayout(1, false));
    container3.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    
    enfid = new Button(container3, SWT.CHECK);
    enfid.setText("Enable Sequential Identifiers");
    enfid.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
   
    enfid.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
      if(enfid.isDisposed())
          return;
        boolean en = enfid.getSelection();
        fidname.setEnabled(en);
        fidprefix.setEnabled(en);
//        recEnabled(container3,enfid.getSelection());
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    Label l = new Label(container3,SWT.None);
    l.setText("Attribute name:");
    fidname = new Text(container3,SWT.None);
    fidname.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    fidname.addKeyListener(kl);

    l = new Label(container3,SWT.None);
    l.setText("Prefix:");
    fidprefix = new Text(container3,SWT.None);
    fidprefix.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    
    fidname.setText(FIDUpdater.DEFAULT_FID_NAME);
    enfid.setSelection(false);
    fidname.setEnabled(false);
    fidprefix.setEnabled(false);
    if(FIDUpdater.isEnabled(db)){
      fidname.setText(FIDUpdater.getFIDName(db));
      fidprefix.setText(FIDUpdater.getFIDPrefix(db));
      enfid.setSelection(true);
      fidname.setEnabled(true);
      fidprefix.setEnabled(true);
    }
    return c;
  }

  @Override
  public void setElement(com.unitesk.requality.vscode.ui.runtime.IAdaptable element) {
    if (element instanceof IProject) {
      if(db!=null)
        db.getRootNode().removeGuiChangeListener(this);
      p = (IProject) element;
      try {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        db = et.getTreeDB(p, null, et.getLocalName(p));
      } catch (IOException e) {
    // TODO Auto-generated catch block
       RLogger.logError(e);
      }
      if(db!=null)
        db.getRootNode().addGuiChangeListener(this);
    }
  };
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
   
  }

  @Override
  public void attributeChange(TreeNode source, String what, Attribute old) {
   
  }
  //Changing settings to default
  @Override
  protected void performDefaults() {
    if(inParallelUpdate())
      return;
      //saveSettings(db, false, "", "");
    setFields(false,FIDUpdater.DEFAULT_FID_NAME,"");
      //Save needs to be made by pressing ok
  }
  private void setFields(boolean enstate, String fidn, String fidpre) {
    if(enfid.isDisposed())
      return;
    enfid.setSelection(enstate);
    fidname.setText(fidn);
    fidprefix.setText(fidpre);
    fidname.setEnabled(enstate);
    fidprefix.setEnabled(enstate);
  }
  @Override
  public boolean isOkOperationInTransaction() {
    return true;
  }
  private String fn = null,fp = null;
  private boolean benvers = false;
  @Override
  public void prepareVars() {
    fn = fidname.getText();
    fp = fidprefix.getText();
    benvers = enfid.getSelection();
    if(!getControl().isDisposed())
      updateStatus(null, true); 
  }
  @Override
  public boolean okToLeave() {
    return super.okToLeave()&&getErrorMessage()==null;
  }
  @Override
  public IStatus okPressedAction(IProgressMonitor monitor) {
    if(db == null || actionsStarted)
      return Status.CANCEL_STATUS;
    actionsStarted = true;
    try {
     // AtomicBoolean s = new AtomicBoolean(false);
      boolean started = false;
     
      Display cur = EclipseAPI.getDisplay();
      String fname = fn, fpref = fp;
      if(!benvers){
        fname = null;
        fpref = "";
      }
      fpref = fpref.trim();
      if(fpref.isEmpty()||fpref.equals(" "))
        fpref = null;
 
      Pair<Pair<String, String>, Change> change = FIDUpdater.getChangeKind(db,fname,fpref);
      Change c = change.getSecond();
      if(c!=Change.NO_CHANGES){
        if(c == Change.PR_NO || c == Change.SI_NO){
          boolean b = RMessages.openQuestion("Sequential Identifiers to be deleted", "This operation will remove all previously added Sequential Identifiers. Are you sure want to do it?", false,true);
          if(!b)
            return Status.CANCEL_STATUS;
        }
        try{
         // s.set(db.startTransaction("Change sequential id settings for "+db.getResName()));
          //TODO think how to move it to job part
          String sr = FIDUpdater.setSettings(db,change,fname,fpref!=null?fpref.trim():fpref);
          if(sr!=null)
          {
            final Text fidname = this.fidname;
            RMessages.openInfo("Problems while setting sequential id", sr, false);
            cur.asyncExec(new Runnable(){
              @Override
              public void run() {
                if(fidname.isDisposed())
                  return;
                updateStatus(sr, false); 
                performDefaults();
              }
            });
            return Status.CANCEL_STATUS;
          }
        }catch(Exception e){
          RLogger.logError(e);
          return Status.CANCEL_STATUS;
        }
        finally{
        }
        started = true;
        if(change.getSecond()==Change.NO_CHANGES){
          return Status.OK_STATUS;
        }
        try{
            BreadthFirstTreeWalker dftw = new BreadthFirstTreeWalker(db.getNode(Requirement.getTypeRootQId()),false,true,true);
            Iterator<TreeNode> it = dftw.iterator();
            while(it.hasNext()){
              TreeNode node = it.next();
              FIDUpdater.updateFID(change, node);
            }
          return Status.OK_STATUS;
        }
        catch(Exception e){
          RLogger.logError(e);
          return Status.CANCEL_STATUS;
        }
        finally{
        }
      }
      return Status.OK_STATUS;
    }finally {
      actionsStarted = false;
    }
  }
  @Override
  public boolean settingsChanged() {
    final boolean benvers = enfid.getSelection();
    String fn=fidname.getText(), fp = fidprefix.getText();
    if(FIDUpdater.isEnabled(db)==benvers){
      if(!benvers)
        return false;
      if(!FIDUpdater.getFIDName(db).equals(fn))
        return true;
      String ap = FIDUpdater.getFIDPrefix(db);
      if((ap==null && fp==null) || ap!=null&&fp!=null&&ap.equals(fp))
        return false;
    }
    return true;
  }
  @Override
  public TreeDB getTreeDB() {
    return db;
  }
  @Override
  public boolean inputCorrect() {
    final boolean benvers = enfid.getSelection();
    String fn=fidname.getText(), fp = fidprefix.getText();
    if(benvers&&!Attribute.checkNameIsValid(fn, false))
      return false;
    return true;
  }
}
