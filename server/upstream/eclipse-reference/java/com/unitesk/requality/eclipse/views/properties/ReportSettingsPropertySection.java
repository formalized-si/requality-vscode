/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.eclipse.views.properties;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.themes.ITheme;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.GenerateReportHandler;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.editors.panels.SelectRequirementPanel;
import com.unitesk.requality.eclipse.views.panels.AttributesPanel;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;

public class ReportSettingsPropertySection extends TreeNodePropertiesTab {
  private static String LBL_ROOT = "Root requirement:";
  private static String LBL_TEMPLATE = "Template:";
  //private static String NOT_INSTALLED = "Not avalible";
  private static String RP_GEN = "Generate report for related projects and parts";
  private static String PROJECT_NUM = "Use project-scope numeration";
  private static String INCL_ROOT  = "Show root data in generation";
  // Widgets
  private SelectRequirementPanel rootReqPanel;
  private Combo templates;
  private Text textId;
  private ControlDecoration reqIdDecorator;
  private AttributesPanel attrPanel;
 // private Label reportInfo;
  private Button gen;
  private TemplatesManager tm = TemplatesManager.getOne(null);
  private Composite par;
  Composite panelmid;
  
  private String selectedTemplateId;
  private boolean selectedIsTemplate;
  private ReportSettings report;
  private Composite customGUIpanel;
  private TreeDB db;
  private Composite chPanel;
  private Button enCPGen;
  private Button enGNGen;
  private Button inclRoot;
  protected boolean enableCross;

  public void resizeScrolledComposite() {
  }
  private boolean isData() {
    return node == null || node.getType().equals(ReportData.TYPE_NAME);
  }

  @Override
  protected void setTreeNode(TreeNode node) {
    if (node != null
        && !node.getType().equals(ReportSettings.TYPE_NAME)
        && !node.getType().equals(ReportData.TYPE_NAME) || enCPGen.isDisposed()) {
      return;
    }
    this.node = node;
    report = (ReportSettings) node;
    Requirement req = null;
    TemplatesManager tmb = null;
    synchronized (this) {
      if(node!=null)
        tmb = tm = TemplatesManager.getOne(node.getTreeDB());
      else
        tmb = tm = TemplatesManager.getOne(null);//generic
    }
    if(node!=null) {
      enCPGen.setSelection(report.getCrossProjectFlag());
      db = node.getTreeDB();
      if (report.getStartRequirementQId() == null) {
        req = (Requirement) db.getNode(Requirement.getTypeRootQId(), Path.QID);
      } else {
        req = (Requirement) db.getNode(report.getStartRequirementQId(), Path.QID);
      }
      enGNGen.setSelection(
    		  report.attrs().getBooleanAttributeValue(ReportSettings.ATTR_GN, false));
      inclRoot.setSelection(
              report.attrs().getBooleanAttributeValue(ReportSettings.ATTR_ROOT_INCLUDED, true));
    }
    attrPanel.setNode(node, isDisposed);
    attrPanel.setEnabled(attrPanel.getViewer(), !isData());
    if (req != null) rootReqPanel.setRequirement(req);
    loadSelectedVars();
    final ReportSettings report = ReportSettingsPropertySection.this.report;
    final Combo templates = ReportSettingsPropertySection.this.templates;
    final String selectedTemplateId = ReportSettingsPropertySection.this.selectedTemplateId;
    final Button enCPGen = ReportSettingsPropertySection.this.enCPGen;
    final Button enGNGen = ReportSettingsPropertySection.this.enGNGen;
    final Button inclRoot = ReportSettingsPropertySection.this.inclRoot;
    final Button gen = ReportSettingsPropertySection.this.gen;
    final TemplatesManager tma = tmb; 
    WorkspaceJob j = new WorkspaceJob("Load templates"){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        synchronized (ReportSettingsPropertySection.this) {
          if(isDisposed || report == null || ReportSettingsPropertySection.this.report!=report || tma == null)
            return Status.CANCEL_STATUS;
        }
        TreeDB db = report.getTreeDB();
        Template t = tma.getTemplate(report);
        List<String> loadLabels = tma.getLabelOrder();
        Iterator<String> it = tma.getIdOrder().iterator();
        Iterator<Boolean> istempl = tma.getIsTemplate().iterator();
        String curId = "";
        boolean curT = false;
        int i = 0;
        for (; i < tma.getIsTemplate().size() && !selectedTemplateId.equals(curId); i++) {
          curId = it.next();
          curT = istempl.next();
        }
        if(i > tma.getIsTemplate().size())
        {
          i = tma.getIsTemplate().size()+2;
          curId = t.getId();
          curT = true;
        }
        int fi = i;
        String fcurId = curId;
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
          @Override
          public void run() {
            if(isDisposed || templates.isDisposed() || ReportSettingsPropertySection.this.report!=report)
              return;
            templates.removeAll();
            for(String s:loadLabels)
              templates.add(s);
            if(t.getNotExists()){
              templates.add("[Lost] "+t.getGroup());
              templates.add("  - [Lost] "+t.getName());
            } 
            templates.select(fi - 1);
            if (!selectedTemplateId.equals(fcurId)) {
          //    if (!templates.getItem(templates.getItemCount() - 1).equals(NOT_INSTALLED))
               // templates.add(NOT_INSTALLED);
              templates.select(templates.getItemCount() - 1);
            }
            enCPGen.setEnabled(!isData() && t != null && t.getParameterValue(Template.PARAM_SUPPORT_CROSSPROJECT));
            enGNGen.setEnabled(!isData() && t != null && t.getParameterValue(Template.PARAM_SUPPORT_GLOBAL_NUMERATION));
            inclRoot.setEnabled(!isData() && t != null && t.getParameterValue(Template.PARAM_ROOT_INCLUDED));
            
            gen.setEnabled(!isData()&& t!=null && !t.getNotExists());
          }
        });
        return Status.OK_STATUS;
      }
      
    };
    j.schedule();
    //Template t = TemplatesManager.getTemplate(report);
    templates.setEnabled(!isData());
    String tid = "";
    if(report!=null)
      tid = report.getTemplateId();
//    if(t!=null)
//      reportInfo.setText(t.getDescription());
    rootReqPanel.setTreeDB(db);
    rootReqPanel.setEnabled(!isData());
    textId.setText(node!=null?node.getId():"");
//    if(t!=null)
//      reportInfo.setText(t.getDescription());
    updateGUIElements(node, customGUIpanel, rootReqPanel);
    final Composite par = ReportSettingsPropertySection.this.par;
    final TabbedPropertySheetPage page = ReportSettingsPropertySection.this.page;
    if(par!=null){
      EclipseAPI.getDisplay().asyncExec(new Runnable(){
      
        @Override
        public void run() { 
          if(isDisposed || par.isDisposed())
            return;
          par.pack(true);
          if(par.getSize().x<par.getParent().getSize().x)
            par.setSize(new Point(par.getParent().getSize().x, par.getSize().x));
          par.layout(true,true);
          par.update();
          //par.getShell().layout();
         // par.getParent().layout();
          page.resizeScrolledComposite();
          //par.getShell().layout(false);
        }
      });
    }
  }

  private ControlDecoration createDecorator(Text text) {
    ControlDecoration controlDecoration = new ControlDecoration(text, SWT.LEFT | SWT.TOP);
    FieldDecoration fieldDecoration =
        FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);
    controlDecoration.setImage(fieldDecoration.getImage());
    return controlDecoration;
  }
  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    par = parent;
    super.createControls(parent, aTabbedPropertySheetPage);
    parent.setLayout(new GridLayout(2, false));
    parent.getParent().setLayout(new GridLayout(1, true));
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    parent.setBackgroundMode(SWT.INHERIT_FORCE);//fixes wrong colors
    TabbedPropertySheetWidgetFactory wf = aTabbedPropertySheetPage.getWidgetFactory();
    wf.createLabel(parent, "Id:");

    textId = wf.createText(parent, "");
    textId.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    reqIdDecorator = createDecorator(textId);
    reqIdDecorator.hide();
    textId.addKeyListener(
        new KeyListener() {

          @Override
          public void keyReleased(KeyEvent e) {
            try {
              if (node == null) {
                return;
              }
              if (node.getTreeDB().getTreeLogic().isIdValidExc(node, textId.getText().trim())) {
                reqIdDecorator.hide();
              } else {
                reqIdDecorator.setDescriptionText(
                    "Element Id is not correct and Element Id should not be empty");
                reqIdDecorator.show();
              }
            } catch (SameIdExistsInChildren e1) {
              reqIdDecorator.setDescriptionText("Element with the same Id already exists");
            } catch (IdEmpty e1) {
              reqIdDecorator.setDescriptionText("Element Id is empty");
            } catch (IdContainsWrongChars e1) {
              reqIdDecorator.setDescriptionText(
                  "Element Id contains wrong character - " + e1.getWrongCharacter());
            }
          }

          @Override
          public void keyPressed(KeyEvent e) {}
        });
    textId.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating || textId.isDisposed() || node == null) return;
            if (!textId.getText().equals(textId.getText().trim())) {
              textId.setText(textId.getText().trim());
            }
            if (node.getId().equals(textId.getText())) {
              return;
            }
            String newId = textId.getText().trim();
            final TreeNode node = ReportSettingsPropertySection.this.node;
            final Text textId = ReportSettingsPropertySection.this.textId;
            WorkspaceJob j = new WorkspaceJob("Change report settings id "+node.getUserVisibleName()){
              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(node == null)
                  return Status.CANCEL_STATUS;
                TreeDB db = node.getTreeDB();
                boolean drop = false;
                if (db.getTreeLogic().isIdValid(node, newId)) {
                  boolean started = db.startTransaction("Change Report Settings Id", true);
                  if(started) {
                    try{
                      node.setId(newId.trim());
                      node.commitAttributes();
                      if(started) {
                        db.commit();
                        started = false;
                      }
                    }catch(Exception e){
                      RLogger.logError(e);
                    }
                    finally {
                      if(started)
                        db.rollback();
                    }
                  }else drop = true;
                } else
                  drop = true;
                if(drop){
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    @Override
                    public void run() {
                      if(isDisposed || textId.isDisposed()||node == null)
                        return;
                      textId.setText(node.getId());
                    }
                  });
                }
                return Status.OK_STATUS;
              }
            };
            j.schedule();
          }
        });
    wf.createLabel(parent, LBL_ROOT);
    rootReqPanel = new SelectRequirementPanel(parent, SWT.NONE, db);
    rootReqPanel.setCanSelectRoot(true);
    rootReqPanel.addListener(
        SWT.Modify,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            Requirement freq = rootReqPanel.getSelectedRequirement();
            final TreeNode node = ReportSettingsPropertySection.this.node;
            final ReportSettings report = ReportSettingsPropertySection.this.report;
            final SelectRequirementPanel rootReqPanel = ReportSettingsPropertySection.this.rootReqPanel;
            WorkspaceJob j = new WorkspaceJob("Set root requirement for report settings "+(node!=null?node.getUserVisibleName():"null node")){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(node == null)
                  return Status.CANCEL_STATUS;
                Requirement req = freq;
                TreeDB db = report.getTreeDB();
                boolean started = db.startTransaction("Set root requirement for Report Settings "+node.getUserVisibleName(), true);
                if (started) {
                  try{
                    report
                        .attrs()
                        .setConstAttribute(ReportSettings.ATTR_ROOT_REQUIREMENT, freq!=null?freq.getQualifiedId():"", Flag.HIDDEN);
                    report.commitAttributes();
                    if(started) {
                      db.commit();
                      started = false;
                    }
                  }catch(Exception e){
                    RLogger.logError(e);
                  }finally {
                    if(started)
                      db.rollback();
                  }
                } else {
                  if (report.getStartRequirementQId() == null) {
                    req = (Requirement) db.getNode(Requirement.getTypeRootQId(), Path.QID);
                  } else {
                    req = (Requirement) db.getNode(report.getStartRequirementQId(), Path.QID | Path.UV);
                  }
                  Requirement freq = req;
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    @Override
                    public void run() {
                      if(isDisposed || rootReqPanel.isDisposed()||freq == null)
                        return;
                      rootReqPanel.setRequirement(freq); 
                    }
                  });
                }
                return Status.OK_STATUS;
              }
            
            };
            j.schedule();
          }
        });
    panelmid = new Composite(parent, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    panelmid.setLayoutData(gd);
    GridLayout gl = new GridLayout(5, false);
    gl.horizontalSpacing = gl.verticalSpacing = 0;
    panelmid.setLayout(gl);
   

   /* reportInfo = new Label(parent, SWT.BORDER);
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    data.horizontalSpan = 2;
    data.verticalAlignment = SWT.TOP;
    reportInfo.setLayoutData(data);
*/	chPanel = new Composite(parent, SWT.CHECK);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    chPanel.setLayoutData(gd);
    chPanel.setLayout(new FillLayout());
    
    enCPGen = new Button(chPanel, SWT.CHECK);
    enCPGen.setText(RP_GEN);
    enCPGen.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        //..String tid = report.getTemplateId();
        boolean sel = enCPGen.getSelection();
        if(report.getCrossProjectFlag()!=sel){
          final ReportSettings report = ReportSettingsPropertySection.this.report;
          final Button enCPGen = ReportSettingsPropertySection.this.enCPGen;
          WorkspaceJob j = new WorkspaceJob("Change crossproject settings for "+report.getUserVisibleName()){
  
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) {
              if(report == null)
                return Status.CANCEL_STATUS;
              TreeDB db = report.getTreeDB();
              boolean started = db.startTransaction("Change crossproject settings for "+report.getUserVisibleName());
              if(started){
                try{
                  TemplatesManager tma = tm;
                  synchronized(ReportSettingsPropertySection.this){
                    tma = tm;
                  }
                  Template t = tma.getTemplate(report);
                  if(t != null && t.getParameterValue(Template.PARAM_SUPPORT_CROSSPROJECT))
                  {
                    report.setCrossProjectFlag(sel);
                    report.commitAttributes();
                  }
                  if(started) {
                    db.commit();
                    started = false;
                  }
                }catch(Exception e){
                  RLogger.logError(e);
                  db.rollback();
                }finally {
                  if(started){
                    db.rollback();
                  }
                }
              }else
                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  @Override
                  public void run() {
                    if(isDisposed || report == null || enCPGen.isDisposed())
                      return;
                    enCPGen.setSelection(report.getCrossProjectFlag());
                  }
                });
              return Status.OK_STATUS;
            }
            
          };
          j.schedule();
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
  

    enGNGen = new Button(chPanel, SWT.CHECK);
    enGNGen.setText(PROJECT_NUM);
    enGNGen.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        //..String tid = report.getTemplateId();
        boolean sel = enGNGen.getSelection();
        if(report.attrs().getBooleanAttributeValue(ReportSettings.ATTR_GN, false)!=sel){
          final ReportSettings report = ReportSettingsPropertySection.this.report;
          final Button enGNGen = ReportSettingsPropertySection.this.enGNGen;
          WorkspaceJob j = new WorkspaceJob("Change project-scoped numeration settings for "+report.getUserVisibleName()){
  
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) {
              if(report == null)
                return Status.CANCEL_STATUS;
              TreeDB db = report.getTreeDB();
              boolean started = db.startTransaction("Change project-scoped numeration settings for "+report.getUserVisibleName());
              if(started){
                try{
                  TemplatesManager tma = null;
                  synchronized (ReportSettingsPropertySection.this) {
                    tma = tm;
                  }
                  Template t = tma.getTemplate(report);
                  if(t != null && t.getParameterValue(Template.PARAM_SUPPORT_GLOBAL_NUMERATION))
                  {
                    if(sel)
                      report.attrs().setConstAttribute(ReportSettings.ATTR_GN, true, 0);
                    else
                      report.attrs().removeAttribute(ReportSettings.ATTR_GN);
                    report.commitAttributes();
                  }
                  if(started) {
                    db.commit();
                    started = false;
                  }
                }catch(Exception e){
                  RLogger.logError(e);
                  db.rollback();
                }finally {
                  if(started){
                    db.rollback();
                  }
                }
              }else
                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  @Override
                  public void run() {
                    if(isDisposed || report == null || enGNGen.isDisposed())
                      return;
                    enGNGen.setSelection(report.attrs().getBooleanAttributeValue(ReportSettings.ATTR_GN, false));
                  }
                });
              return Status.OK_STATUS;
            }
            
          };
          j.schedule();
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
  
    inclRoot = new Button(chPanel, SWT.CHECK);
    inclRoot.setText(INCL_ROOT);
    inclRoot.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        //..String tid = report.getTemplateId();
        boolean sel = inclRoot.getSelection();
        if(report.attrs().getBooleanAttributeValue(ReportSettings.ATTR_ROOT_INCLUDED, true)!=sel){
          final ReportSettings report = ReportSettingsPropertySection.this.report;
          final Button inclRoot = ReportSettingsPropertySection.this.inclRoot;
          WorkspaceJob j = new WorkspaceJob("Change project-scoped numeration settings for "+report.getUserVisibleName()){
  
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) {
              if(report == null)
                return Status.CANCEL_STATUS;
              TreeDB db = report.getTreeDB();
              boolean started = db.startTransaction("Change root included settings for "+report.getUserVisibleName());
              if(started){
                try{
                  TemplatesManager tma = null;
                  synchronized (ReportSettingsPropertySection.this) {
                    tma = tm;
                  }
                  Template t = tma.getTemplate(report);
                  if(t != null && t.getParameterValue(Template.PARAM_ROOT_INCLUDED))
                  {
                    if(!sel)
                      report.attrs().setConstAttribute(ReportSettings.ATTR_ROOT_INCLUDED, false, 0);
                    else
                      report.attrs().removeAttribute(ReportSettings.ATTR_ROOT_INCLUDED);
                    report.commitAttributes();
                  }
                  if(started) {
                    db.commit();
                    started = false;
                  }
                }catch(Exception e){
                  RLogger.logError(e);
                  db.rollback();
                }finally {
                  if(started){
                    db.rollback();
                  }
                }
              }else
                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  @Override
                  public void run() {
                    if(isDisposed || report == null || inclRoot.isDisposed())
                      return;
                    inclRoot.setSelection(report.attrs().getBooleanAttributeValue(ReportSettings.ATTR_ROOT_INCLUDED, false));
                  }
                });
              return Status.OK_STATUS;
            }
            
          };
          j.schedule();
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    wf.createLabel(panelmid, LBL_TEMPLATE);
    createSelectTemplatePanel(panelmid)
        .addListener(
            SWT.Modify,
            new Listener() {
              @Override
              public void handleEvent(Event event) {
                if(templates.isDisposed())
                  return;
                TreeDB db = report!=null?report.getTreeDB():null;
                TemplatesManager tma = null;
                synchronized(ReportSettingsPropertySection.this){
                  tma = tm;
                }
                if(templates.getSelectionIndex()>=tma.getIdOrder().size())
                {
                  Template t = tma.getTemplate(report);
                  selectedIsTemplate = true;
                  selectedTemplateId = t.getId();
                  gen.setEnabled(false);
                }
                else{
                  selectedTemplateId = tma.getIdOrder().get(templates.getSelectionIndex());
                  selectedIsTemplate = tma.getIsTemplate().get(templates.getSelectionIndex());
                  gen.setEnabled(true);
                }
                enCPGen.setEnabled(true);
                enGNGen.setEnabled(false);
                inclRoot.setEnabled(false);
                if (selectedIsTemplate) {
                  Template t = tma.getTMap().get(selectedTemplateId);
                  final ReportSettings report = ReportSettingsPropertySection.this.report;
                  final Button enCPGen = ReportSettingsPropertySection.this.enCPGen;
                  final Button enGNGen = ReportSettingsPropertySection.this.enGNGen;
                  final Button inclRoot = ReportSettingsPropertySection.this.inclRoot;
                  final String selectedTemplateId = ReportSettingsPropertySection.this.selectedTemplateId;
                  final Composite par = ReportSettingsPropertySection.this.par;
                  final Composite customGUIpanel = ReportSettingsPropertySection.this.customGUIpanel;
                  final TreeNode node = ReportSettingsPropertySection.this.node;
                  final SelectRequirementPanel rootReqPanel = ReportSettingsPropertySection.this.rootReqPanel;
                  WorkspaceJob j = new WorkspaceJob("Update report settings for "+(report!=null?report.getUserVisibleName():"null node")){

                    @Override
                    public IStatus runInWorkspace(IProgressMonitor monitor) {
                      if(report == null)
                        return Status.CANCEL_STATUS;
                      TreeDB db = report.getTreeDB();
                      if (t!=null && !t.getNotExists()) {
                        boolean started = db.startTransaction("Update Report Data", true);
                        if(started)
                          try{
                            report.attrs().setConstAttribute(ReportSettings.ATTR_TEMPLATE, selectedTemplateId, Flag.HIDDEN);
                            TemplatesManager.getOne(report.getTreeDB()).saveReportSettings(report, t, null);
                            if(t != null) {
                              if(!t.getParameterValue(Template.PARAM_SUPPORT_CROSSPROJECT)){
                                report.setCrossProjectFlag(false);
                                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                                
                                  @Override
                                  public void run() {
                                    if(isDisposed || enCPGen.isDisposed())
                                      return;
                                    enCPGen.setEnabled(false); 
                                  }
                                });
                              }
                              if(!t.getParameterValue(Template.PARAM_ROOT_INCLUDED)){
                                report.attrs().removeAttribute(ReportSettings.ATTR_ROOT_INCLUDED);
                                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                                  @Override
                                  public void run() {
                                    if(isDisposed || inclRoot.isDisposed())
                                        return;
                                    inclRoot.setEnabled(false); 
                                  }
                                });
                              }
                              if(!t.getParameterValue(Template.PARAM_SUPPORT_GLOBAL_NUMERATION)) {
                                report.attrs().removeAttribute(ReportSettings.ATTR_GN);
                                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                                  @Override
                                  public void run() {
                                    if(isDisposed || enGNGen.isDisposed())
                                        return;
                                    enGNGen.setEnabled(false); 
                                  }
                                });                            	  
                              }
                            }
                            report.commitAttributes();
                            if(started) {
                              db.commit();
                              started = false;
                            }
                          }catch(Exception e){
                            RLogger.logError(e);
                          }finally {
                            if(started)
                              db.rollback();
                          }
                      }
                      EclipseAPI.getDisplay().asyncExec(new Runnable(){
                        @Override
                        public void run() {
                          if(isDisposed || par.isDisposed())
                            return;
                          updateGUIElements(node, customGUIpanel, rootReqPanel);
                        }
                      });
                      return Status.OK_STATUS;
                    }
                    
                  };
                  j.schedule();
                  //TODO modify
                  //GenerateReportHandler.generateReport((ReportSettings)ReportSettings);
                  // OpenReportHandler.openReport((ReportSettings)ReportSettings);
                }else{
                  //reportInfo.setText(tm.getGMap().get(selectedTemplateId).getDescription());
                  gen.setEnabled(false);
                  enCPGen.setEnabled(false);
                  enGNGen.setEnabled(false);
                  inclRoot.setEnabled(false);
                }
              }
            });

    customGUIpanel = new Composite(panelmid, SWT.NONE);
    RowLayout rl = new RowLayout(SWT.HORIZONTAL);
    rl.center = true;
    rl.wrap = false;
    rl.fill = true;
    customGUIpanel.setLayout(rl);
    GridData gridData = new GridData(GridData.FILL_BOTH);
    gridData.grabExcessHorizontalSpace = true;
    gridData.grabExcessVerticalSpace = true;
    customGUIpanel.setLayoutData(gridData);
    
    gen = new Button(panelmid, SWT.NONE);
    gen.setText("Generate");
    gen.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            if(par.isDisposed() || report == null)
              return;
            TreeDB db = report.getTreeDB();
            TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
            if(tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db))){
              String wo = CoreUtils.getWorkspaceOperation();
              if(!wo.isEmpty())
                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  
                  @Override
                  public void run() {
                    MessageDialog.openError(EclipseAPI.getDisplay().getActiveShell(),
                        "Report generation error","Cannot generate report: operation '"+wo+"' is performing now.");
                  }
                });
              else{
                SelectionRecord nds = new SelectionRecord(report);
                WorkspaceJob wj = ((com.unitesk.requality.eclipse.handlers.report.GenerateReportHandler)
                    (GenerateReportHandler.getOne())).getReportJob(nds, 
                        "Generate report ("+report.getTreeDB().getVisibleName()+") "+report.getUserVisibleName(), 
                        Collections.emptyMap(), true);
                wj.schedule();
              }
            }
          }
        });

    attrPanel = new AttributesPanel(fsid, parent);
    GridData data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 2;
    attrPanel.getControl().setLayoutData(data);
  }

  protected void loadSelectedVars() {
    selectedTemplateId = node == null? null : 
      node.attrs().getStringAttributeValue(ReportSettings.ATTR_TEMPLATE);
    if (selectedTemplateId == null) {
      selectedTemplateId = Template.DEFAULT_ID;
      selectedIsTemplate = true;
    }
  }

  public void updateGUIElements(TreeNode node, Composite customGUIpanel, SelectRequirementPanel rootReqPanel) {
    ICommandService cmdService = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(ICommandService.class);
    WorkspaceJob j = new WorkspaceJob("Update gui elements"){//TODO rewrite to display job?

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        Template t = getTemplate();
        if (isDisposed || t == null || node == null) return Status.CANCEL_STATUS;

        Map<String, Map<String, String>> mapParams = t.getGUIElements();
        Map<String, IHandler[]> handls = new HashMap<>();
        
        for(Entry<String, Map<String, String>> e:mapParams.entrySet()){
          String id = e.getKey();
          Map<String, String> params = mapParams.get(id);
          String type = params.get("_type");
          if (type != null && type.equals("button")) {
            String comm = params.get("_command");
            if(comm!=null){
              final IHandler[] hs = Activator.getDefault().getCommandHandlers(cmdService, comm);
              if(hs!=null)
                handls.put(id, hs);
            }
          }
        }
        EclipseAPI.getDisplay().syncExec(new Runnable(){
        
          @Override
          public void run() {
            if(isDisposed || t == null || customGUIpanel.isDisposed())
              return;
            for (Control c : customGUIpanel.getChildren()) c.dispose();
            rootReqPanel.setEnabled(t.getParameterValue(Template.PARAM_SUPPORT_CROSSPROJECT) && !isData());
            for (String id : mapParams.keySet()) {
              Map<String, String> params = mapParams.get(id);
              String type = params.get("_type");
              if (id.equals("settings")) {
                //TODO global settings - layout etc
              } else if (type != null) {
                if (type.equals("button")) {
                  IHandler[] hs = handls.get(id);
                  if (hs != null && hs.length > 0) {
                    Button b = new Button(customGUIpanel, SWT.NONE);
                    b.setEnabled(!isData());
                    b.setText(params.get("_text"));
                    b.addMouseListener(
                        new MouseListener() {
        
                          @Override
                          public void mouseUp(MouseEvent e) {
                            // TODO Auto-generated method stub
        
                          }
        
                          @Override
                          public void mouseDown(MouseEvent e) {
                            Map<String, Object> params = new HashMap<>();
                            params.put("target", report);
                            params.put("shell", b.getShell());
                            for (IHandler h : hs)
                              try {
                                Activator.executeCommand(h, params);
                              } catch (ExecutionException e1) {
                                // TODO Auto-generated catch block
                                e1.printStackTrace();
                              }
                          }
        
                          @Override
                          public void mouseDoubleClick(MouseEvent e) {
                            // TODO Auto-generated method stub
        
                          }
                        });
                  }
                } else if (type.equals("label")) {
                  Label label = new Label(customGUIpanel, SWT.NONE);
                  String lab = params.get("_text");
                  if (lab != null) label.setText(lab);
                }
              }
            }
    
            customGUIpanel.layout(true,true);
            customGUIpanel.update();
        
            customGUIpanel.getParent().layout(true,true);
            customGUIpanel.getParent().update();
          }
        });
        

        return Status.OK_STATUS;
      }
    };
    j.schedule();
  }

  public Template getTemplate() {
    TemplatesManager tma = null;
    synchronized (this) {
      tma = tm;
	}
    return tma.getTMap().get(selectedTemplateId);
  }

  private Composite createSelectTemplatePanel(Composite parent) {
    final Composite panel = new Composite(parent, SWT.NONE);
    panel.setLayout(new GridLayout());
    //panel.setBackgroundMode(SWT.INHERIT_FORCE);
    templates = new Combo(panel, SWT.NONE | SWT.READ_ONLY);

    ITheme theme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
    ColorRegistry colors = theme.getColorRegistry();
    Set<String >cols = colors.getKeySet();
    templates.setBackground(colors.get("org.eclipse.ui.editors.backgroundColor"));
    
    final TreeNode node = this.node;
    final TreeDB db = node!=null?node.getTreeDB():null;
    TemplatesManager tma = null;
    synchronized (this) {
      tma = this.tm;
	}
    final TemplatesManager tm = tma;
    final Combo templates = this.templates;
    final String selectedTemplateId = this.selectedTemplateId;
    final SelectRequirementPanel rootReqPanel = this.rootReqPanel;
    final Composite customGUIpanel = this.customGUIpanel;
    WorkspaceJob j = new WorkspaceJob("Update templates panel"){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if(node == null || isDisposed)
          return Status.CANCEL_STATUS;
        if (tm.getTMap().size() == 0) {
          return Status.CANCEL_STATUS;
        }
        loadSelectedVars();
        List<String> l = tm.getLabelOrder();
        EclipseAPI.getDisplay().syncExec(new Runnable(){
        
          @Override
          public void run() {
            if(isDisposed || templates.isDisposed())
              return;
            templates.removeAll();
            for(String s:l)
              templates.add(s);
            
            Iterator<String> it = tm.getTMap().keySet().iterator();
            String curId = "";
            int i = 0;
            for (; i < tm.getTMap().size() && !selectedTemplateId.equals(curId); i++) {
              curId = it.next();
            }
            templates.select(i - 1); 
          }
        });
        return Status.OK_STATUS;
      }
      
    };
    j.schedule();
    templates.addSelectionListener(
        new SelectionListener() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            panel.notifyListeners(SWT.Modify, new Event());
            updateGUIElements(node, customGUIpanel, rootReqPanel);
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            widgetSelected(e);
          }
        });
    return panel;
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return true; //TODO fix;
  }

  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_ID);
    ids.add(ReportSettings.ATTR_ROOT_REQUIREMENT);
    ids.add(ReportSettings.ATTR_TEMPLATE);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      if(ids.contains(s)||!s.startsWith("_"))
        return true;
    return false;
  };
}
