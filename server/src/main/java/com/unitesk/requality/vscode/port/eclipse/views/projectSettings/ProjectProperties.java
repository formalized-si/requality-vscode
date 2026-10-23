package com.unitesk.requality.vscode.port.eclipse.views.projectSettings;

import java.io.IOException;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.ScrolledComposite;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPropertyPage;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.ReportNodeIdIdentificationMode;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;

public class ProjectProperties extends RequalityPropertyPage implements IWorkbenchPropertyPage {
  private Combo sbllist, nametype, reportNodeIdMode;
  private TreeNode nodeToStore;
  private IProject p;
  private Label helpLabel;
  private Button mixcheck;
  private Button desctext;
  private Text idPref;
  private Text idReportPattern;
  protected int sblindex, ridmodeindex = -1;
  
  private String idPrefix;
  private String idReportPatternString;
  
  //text on top of preference page
  private static final String desciption_text = "Sort method:";
  private static final String
      LOCATIONS_TEXT =
          "Requirements are sorted by location position in document. "
              + "If there are several documents sorting depends on document names. "
              + "If requirement has several locations sorting depends only on the first location. "
              + "Requirements without locations are at the end of the list and are sorted by name.",
      NAMES_TEXT =
          "Requirements are sorted by name (first, in ascending order, then in alphabetical order). "
              + "In case of coincidence or lack of names requirements are sorted by id.",
      INDEX_TEXT =
          "Requirements are sorted by index. Initial value of index will be determined by names.";


  //text on top of preference page

  public ProjectProperties() {}

  @Override
  protected Control createContents(Composite parent) {
    Composite c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(1, true));
    c.setLayoutData(new GridData(GridData.FILL_BOTH));

    final Group container2 = new Group(c, SWT.NONE);
    container2.setText("Sort options:");
    container2.setLayout(new GridLayout(2, false));
    container2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Label l = new Label(container2, SWT.NONE);
    l.setLayoutData(new GridData());
    l.setText(desciption_text);

    SortingSettings st = TreeDB.loadSettings(nodeToStore);
    sbllist = new Combo(container2, SWT.NONE);
    for (SortingMethod sm : SortingMethod.values()) sbllist.add(SortingSettings.getSortingName(sm));
    SortingMethod[] sms = SortingMethod.values();
    for (int i = 0; i < sms.length; i++) if (sms[i] == st.getMethod()) sbllist.select(i);
    sbllist.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            if(sbllist.isDisposed())
              return;
            sblindex = sbllist.getSelectionIndex();
            if (helpLabel != null) {
              int sel = sbllist.getSelectionIndex();
              if (sel < 0) sel = 0;
              SortingMethod sm = SortingMethod.values()[sel];
              helpLabel.setText(
                  sm == SortingMethod.BY_LOCATIONS
                      ? LOCATIONS_TEXT
                      : sm == SortingMethod.BY_INDEX ? INDEX_TEXT : NAMES_TEXT);
              container2.requestLayout();
            }
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {}
        });

    sbllist.setLayoutData(new GridData());
    sblindex = sbllist.getSelectionIndex();
    helpLabel = new Label(container2, SWT.WRAP);

    GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
    gd.horizontalSpan = 2;
    gd.widthHint = 200;
    helpLabel.setLayoutData(gd);
    helpLabel.setText(
        st.getMethod() == SortingMethod.BY_LOCATIONS
            ? LOCATIONS_TEXT
            : st.getMethod() == SortingMethod.BY_INDEX ? INDEX_TEXT : NAMES_TEXT);

    mixcheck = new Button(container2, SWT.CHECK);
    GridData gd4 = new GridData();
    gd4.horizontalSpan = 2;
    mixcheck.setLayoutData(gd4);
    mixcheck.setText("Sort elements types separately");
    if (nodeToStore != null) mixcheck.setSelection(st.getSeparateByTypesFlag());

    Label helpLabel2 = new Label(container2, SWT.WRAP);

    gd = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
    gd.horizontalSpan = 2;
    gd.widthHint = 200;
    helpLabel2.setLayoutData(gd);
    helpLabel2.setText(
        "Sorting can be made separately for different kinds of elements. If check box is checked then elements will be sorted separately.");

    container2.layout();
    final Group container3_5 = new Group(c, SWT.NONE);
    container3_5.setText("Identification settings:");
    container3_5.setLayout(new GridLayout(2, false));
    container3_5.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    l = new Label(container3_5, SWT.NONE);
    l.setText("Full name mode:");

    nametype = new Combo(container3_5, SWT.NONE);
    nametype.add("By Full Name");
    nametype.add("By User-Visible Name");
    nametype.add("By Qualified Id");
    nametype.add("By Sequential Id");
    if(nodeToStore!=null)
      nametype.select(nodeToStore.getTreeDB().getFullNameMode());
    
    l = new Label(container3_5, SWT.NONE);
    l.setText("Id prefix:");
    idPref = new Text(container3_5, SWT.NONE);
    idPref.setText(IDMechanics.getShownIdPrefix(nodeToStore.getTreeDB()));
    idPref.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    idPref.setEditable(true);
    //TODO adds info that uppercase is prefferable?
    idPref.addModifyListener(new ModifyListener() {
    @Override
    public void modifyText(ModifyEvent e) {
      idPrefix = idPref.getText();
    }
  });

    final Group container3_75 = new Group(c, SWT.NONE);
    container3_75.setText("Report-related element identefication:");
    container3_75.setLayout(new GridLayout(2, false));
    container3_75.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    
    l = new Label(container3_75, SWT.NONE);
    l.setText("Identefication mechanics:");
    reportNodeIdMode = new Combo(container3_75, SWT.NONE);
    reportNodeIdMode.add("By Sequential Id");
    reportNodeIdMode.add("By Full Name (By project setts.)");
    reportNodeIdMode.add("By custom pattern");
    reportNodeIdMode.addSelectionListener(new SelectionListener() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        int ord = reportNodeIdMode.getSelectionIndex();
        if(ord>-1)//or what?
          idReportPattern.setEnabled(ReportNodeIdIdentificationMode.values()[ord]==ReportNodeIdIdentificationMode.BY_PATTERN);//enable custom pattern only if corresponding mode is enabled
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
          widgetSelected(e);
      }
    });
      
    l = new Label(container3_75, SWT.NONE);
    l.setText("Custom pattern:");
    idReportPattern = new Text(container3_75, SWT.NONE);
    idReportPattern.setText(IDMechanics.getShownIdPrefix(nodeToStore.getTreeDB()));
    idReportPattern.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    idReportPattern.setEditable(true);
    //TODO adds info that uppercase is prefferable?
    idReportPattern.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        idReportPatternString = idReportPattern.getText();
      }
    });
    //TODO adds test for pattern?
    
    final Group container5 = new Group(c, SWT.NONE);
    container5.setText("Requality Explorer settings:");
    container5.setLayout(new GridLayout(2, false));
    container5.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    l = new Label(container5, SWT.NONE);
    l.setText("Show description on elements");

    desctext = new Button(container5, SWT.CHECK);
    desctext.setSelection(!nodeToStore.getTreeDB().getLabelIsSimple());

    if(nodeToStore!=null) {
      int sel = IDMechanics.getReportNodeIdentificationMode(nodeToStore.getTreeDB()).ordinal();
      reportNodeIdMode.select(sel);
      idReportPattern.setText(IDMechanics.getNodeReportIdPattern(getTreeDB(), null));
      idReportPattern.setEnabled(ReportNodeIdIdentificationMode.values()[sel]==ReportNodeIdIdentificationMode.BY_PATTERN);
    }
    return c;
  }

  Composite isc;
  ScrolledComposite sc;

  @Override
  public void setElement(com.unitesk.requality.vscode.ui.runtime.IAdaptable element) {
    if (element instanceof IProject) {
      p = (IProject) element;
      TreeDB db = null;
      try {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        db = et.getTreeDB(p, null, et.getLocalName(p));
      } catch (IOException e) {
         RLogger.logError(e);
      }
      if (db != null) {
        nodeToStore = db.getRootNode();
        SortingSettings st = db.getSortingSettings();
        if (sbllist != null) {
          SortingMethod[] sms = SortingMethod.values();
          for (int i = 0; i < sms.length; i++) if (sms[i] == st.getMethod()) sbllist.select(i);
        }
        if (mixcheck != null) mixcheck.setSelection(st.getSeparateByTypesFlag());
        if (desctext != null) desctext.setSelection(!db.getLabelIsSimple());
        if (idPref!=null) idPref.setText(IDMechanics.getShownIdPrefix(db));
      }
    }
  };
  //actions when new selection is performed
  
  private void fireOutClear(TreeNode nodeToStore2) {
    for (UUID uid : nodeToStore2.getActiveWorkChildrenUUIDs()) {
      TreeNode node = nodeToStore2.getTreeDB().getCachedNodeOrVirt(uid);
      if (node != null) {
        node.updateLocalAttribute(TreeNode.ATTR_LABEL);
        node.commitAttributes();
        fireOutClear(node);
      }
    }
    nodeToStore2.clearShownChildren();
  }

  //Changing settings to default
  @Override
  protected void performDefaults() {
    if(inParallelUpdate())
      return;
    sbllist.select(1);
    helpLabel.setText(LOCATIONS_TEXT);
    mixcheck.setSelection(false);
    desctext.setSelection(false);
    nametype.select(0);
    desctext.setSelection(false);
    helpLabel.getParent().layout();    
    super.performDefaults();
  }

  @Override
  public boolean isOkOperationInTransaction() {
    return true;
  }

  private int ntindex;
  private boolean descindex, mixindex;
  @Override
  public void prepareVars() {
    sblindex = sbllist.getSelectionIndex();
    ridmodeindex = reportNodeIdMode.getSelectionIndex();
    ntindex = nametype.getSelectionIndex();
    descindex = desctext.getSelection();
    mixindex =mixcheck.getSelection();
  }
  @Override
  public IStatus okPressedAction(IProgressMonitor monitor) {
    if(nodeToStore==null || actionsStarted)
      return Status.CANCEL_STATUS;
    actionsStarted = true;
    try {
      TreeDB ndb = nodeToStore.getTreeDB();
      int sel = sblindex;
      if (sel == -1) sel = 0;
      if (ridmodeindex == -1) ridmodeindex = 0;
    //  boolean b = nodeToStore.getTreeDB().startTransaction("Change project settings for "+nodeToStore.getTreeDB().getSemiResName());
      try{
        ndb
            .setSortingMethod(
                SortingSettings.init(SortingMethod.values()[sel], mixindex), false);
        //        nodeToStore.attrs().setAttribute(TreeDB.SORT_METHOD, .toString());
        //    nodeToStore.attrs().setAttribute(TreeDB.SORT_SEP_TYPES,);
    
        nodeToStore.getTreeDB().setFullNameMode(ntindex);
        nodeToStore.getTreeDB().setLabelIsSimple(!descindex);
        if(idPrefix!=null && !idPrefix.equals(IDMechanics.getShownIdPrefix(ndb)))
          IDMechanics.changeIdPrefix(ndb, idPrefix, false);
        if(idReportPatternString!=null && !idReportPatternString.equals(IDMechanics.getNodeReportIdPattern(ndb, null)))
          IDMechanics.changeReportPattern(ndb, idReportPatternString, false);
        if(ridmodeindex!=IDMechanics.getReportNodeIdentificationMode(ndb).ordinal())
          IDMechanics.changeNodeReportIdMode(ndb, ReportNodeIdIdentificationMode.values()[ridmodeindex], false);
        nodeToStore.commitAttributes();
        fireOutClear(nodeToStore);
       // if(b)
       //   nodeToStore.getTreeDB().commit();
          }catch(Exception r){
       //     if(b)
       //       nodeToStore.getTreeDB().rollback();
      }
      return Status.OK_STATUS;
    }finally {
      actionsStarted = false;
    }
  }

  @Override
  public boolean settingsChanged() {
    TreeDB db = nodeToStore.getTreeDB();
    if(db.getLabelIsSimple()==desctext.getSelection())
      return true;
    int sblindex = sbllist.getSelectionIndex(),
        ntindex = nametype.getSelectionIndex(),
        repnodepattern = reportNodeIdMode.getSelectionIndex();
    boolean descindex = desctext.getSelection(),
        mixindex = mixcheck.getSelection();
    if (sblindex == -1) sblindex = 0;
    SortingSettings ss = db.getSortingSettings();
    SortingSettings ssn = SortingSettings.init(SortingMethod.values()[sblindex], mixindex);
    if(!SortingSettings.isSame(ss,ssn))
      return true;
    if(idPrefix!=null && !idPrefix.equals(IDMechanics.getShownIdPrefix(db)))
      return true;
    if(idReportPatternString!=null && !idReportPatternString.equals(IDMechanics.getNodeReportIdPattern(db, null)))
      return true;
    if(repnodepattern != IDMechanics.getReportNodeIdentificationMode(db).ordinal())
      return true;
    return db.getLabelIsSimple()==descindex || ntindex!=db.getFullNameMode();
  }

  @Override
  public TreeDB getTreeDB() {
    return nodeToStore.getTreeDB();
  }

  @Override
  public boolean inputCorrect() {
    return true;//no checks for now
  }
}
