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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Iterator;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateScope;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.message.RMessages;

public class ReportTemplatesPropertySection extends TreeNodePropertiesTab {
  // Widgets
  private List templates;
  private Text namepan;
  private Text pathpan;
  private Button deletetemplate;
  private TemplatesManager tm = TemplatesManager.getOne(null);
  private String selectedTemplateId;

  @Override
  protected void setTreeNode(TreeNode node) {
    if (node == null || !node.getType().equals(ReportFolder.TYPE_NAME)||templates.isDisposed()) {
      return;
    }
    tm = TemplatesManager.getOne(node.getTreeDB());
    updateTemplates(node.getTreeDB(), "");
    selectedTemplateId = Template.DEFAULT_ID;
    final List templates = ReportTemplatesPropertySection.this.templates;
    final TemplatesManager tm = ReportTemplatesPropertySection.this.tm;
    WorkspaceJob j = new WorkspaceJob("Select node on templates section "+(node!=null?node.getUserVisibleName():"null node")){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if(isDisposed || node == null)
          return Status.CANCEL_STATUS;
        TreeDB db = node.getTreeDB();
        synchronized (this) {
          if(!tm.getTMap().isEmpty())
            selectedTemplateId = tm.getTMap().keySet().iterator().next();
        }
        Iterator<String> it = tm.getIdOrder().iterator();
        int i = 0;
        while(it.hasNext() && !it.next().equals(selectedTemplateId))
          i++;
        final int fi = i;
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
          @Override
          public void run() {
            if(isDisposed || templates.isDisposed())
              return;
            templates.select(fi);
          }
        });
        return Status.OK_STATUS;
      }
      
    };
    j.schedule();
  }

  protected boolean copyDirectory(File sourceLocation, File targetLocation) throws IOException {
    if (sourceLocation.isDirectory()) {
      if (targetLocation.exists()) {
        return false;
      } else {
        targetLocation.mkdir();
      }
      String[] children = sourceLocation.list();
      for (int i = 0; i < children.length; i++) {
        if (!copyDirectory(
            new File(sourceLocation, children[i]), new File(targetLocation, children[i])))
          return false;
      }
    } else {
      if(!targetLocation.exists()){
        targetLocation.getParentFile().mkdirs();
        targetLocation.createNewFile();
      }
      InputStream in = new FileInputStream(sourceLocation);
      OutputStream out = new FileOutputStream(targetLocation);
      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
      in.close();
      out.close();
    }
    return true;
  }

  protected void removeFile(File f) throws IOException {
    if (f.isDirectory()) {
      for (File c : f.listFiles()) removeFile(c);
    }
    if (!f.delete()) throw new FileNotFoundException("Failed to delete file: " + f);
  }
  private String old = null;
  @Override
  public void createControls(
      final Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    parent.setLayout(new GridLayout(2, false));
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    TabbedPropertySheetWidgetFactory wf = aTabbedPropertySheetPage.getWidgetFactory();
    //label name
    wf.createLabel(parent, "Name:");
    //text name
    namepan = wf.createText(parent, "", SWT.NONE);
    namepan.setEditable(false);
    namepan.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    //label path
    wf.createLabel(parent, "Path:");
    //text path
    pathpan = wf.createText(parent, "", SWT.NONE);
    pathpan.setEditable(false);
    pathpan.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    //composite for buttons
    Composite pbuttons = wf.createComposite(parent);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    pbuttons.setLayoutData(gd);
    pbuttons.setLayout(new GridLayout(3, false));
    //button for template import
    Button addtemplate = wf.createButton(pbuttons, "Add new workspace template", SWT.NONE);
    addtemplate.addListener(
        SWT.MouseDown,
        getAddTemplateListener(addtemplate, TemplateScope.WORKSPACE));

    Button addtemplate2 = wf.createButton(pbuttons, "Add new project template", SWT.NONE);
    addtemplate2.addListener(
        SWT.MouseDown,
        getAddTemplateListener(addtemplate, TemplateScope.PROJECT));
    
    //button for template deletion(not for "read-only" templates)
    deletetemplate = wf.createButton(pbuttons, "Delete template", SWT.NONE);
    deletetemplate.setEnabled(false);
    deletetemplate.addListener(
        SWT.MouseDown,
        getRemoveTemplateListener(TemplateScope.WORKSPACE));
    
    
    createSelectTemplatePanel(parent)
        .addListener(
            SWT.Modify,
            new Listener() {
              @Override
              public void handleEvent(Event event) {}
            });
    final TemplatesManager tm = this.tm;
    WorkspaceJob j = new WorkspaceJob("Preload templates"){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
          synchronized (ReportTemplatesPropertySection.this) {
            tm.getTMap();
          }
          return Status.OK_STATUS;
        }
      };
    j.schedule();
    //c.add
  }

  private Listener getRemoveTemplateListener(TemplateScope scope) {
	  return new Listener() {
          @Override
          public void handleEvent(Event event) {
        	  if(deletetemplate.isDisposed())
                  return;
                Template t = getTemplate();
                if (t == null || deletetemplate.isDisposed()) return;
                if (t.getReadOnly()) {
                  MessageDialog.openError(
                      PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                      "Remove template",
                      "Cannot delete read-only template");
                  return;
                }
                TreeDB db = node!=null?node.getTreeDB():null;
                //removeFile(new File(t.getReportPath()));
                File to = new File(URI.create(t.getReportPath()));
                final TreeNode node = ReportTemplatesPropertySection.this.node;
                final Text namepan = ReportTemplatesPropertySection.this.namepan;
                final Text pathpan = ReportTemplatesPropertySection.this.pathpan;
                WorkspaceJob j = new WorkspaceJob("Remove template"){

                  @Override
                  public IStatus runInWorkspace(IProgressMonitor monitor) {
                    if(node == null)
                      return Status.CANCEL_STATUS;
                    if(to.exists())
                      ResManager.getManager().removeTemplate(db, to, scope);
                    final TreeDB db = node.getTreeDB();
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                      @Override
                      public void run() {
                        if(isDisposed || namepan.isDisposed())
                          return;
                        String answ = updateTemplates(db, "");
                        namepan.setText("");
                        pathpan.setText("");
                        //TODO msgs
                      }
                    });
                    return Status.OK_STATUS;
                  }
                  
                };
                j.schedule();
                //updateTemplates(t.getReportPath(), size);
              }
            };
  }

  private Listener getAddTemplateListener(Control comp, TemplateScope scope) {
	 return new Listener() {
          @Override
          public void handleEvent(Event event) {
    	    if(comp.isDisposed())
              return;
            //ask for folder that contains template
            DirectoryDialog dd = new DirectoryDialog(comp.getShell());
            dd.setMessage("Select folder which contains template files");
            if(old != null)
              dd.setFilterPath(old);
            String path = dd.open();
            old = path;
            if (path == null || path.equals("")) return;
            File from = new File(path);
            if (!new File(URI.create(from.toURI().toString() + "/" + "template.xml")).exists()) {
              MessageDialog.openError(
                  PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                  "Add template",
                  "Target folder is not a template folder or Requality already contains added template!");
              return;
            }
            //copy folder to "templates" path from activator
            final TemplatesManager tm = ReportTemplatesPropertySection.this.tm;
            final Text namepan = ReportTemplatesPropertySection.this.namepan;
            final Text pathpan = ReportTemplatesPropertySection.this.pathpan;
            final TreeNode node = ReportTemplatesPropertySection.this.node;
            final TreeDB db = node!=null?node.getTreeDB():null;
            WorkspaceJob j = new WorkspaceJob("Add new template("+scope+")"){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if(node == null)
                    return Status.CANCEL_STATUS;
                //copy folder to "templates" path from activator
                  //copyDirectory(from, to);
                  tm.getTMap();//preload cache
                  File to = null;
                  try {
                    to = ResManager.getManager().addReportTemplate(db, from, scope);
                  }catch(RuntimeException re) {
                    RMessages.openError("Cannot load template "+from, "During loading of template "+from+" some problem happens: ", re.getMessage(), false);
                    return Status.CANCEL_STATUS;
                  }
                  if (to == null || from.getAbsolutePath().equals(to.getAbsolutePath())) return Status.CANCEL_STATUS;
                  final File fto = to;
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  
                    @Override
                    public void run() {
                      if(isDisposed || comp.isDisposed())
                        return;
                      String answ = updateTemplates(db, path);//TODO needs to be rewrited, by disposed state it may cause problems 
                      if(answ != null && fto.exists()){
                	    WorkspaceJob j = new WorkspaceJob("Remove template("+scope+") "+fto){

                          @Override
                          public IStatus runInWorkspace(IProgressMonitor monitor) {
                            ResManager.getManager().removeTemplate(db, fto, scope);
                            return Status.OK_STATUS;
                          }

                        };
                        j.schedule();
                      }
                      
                      if (answ == null) {
                        Template tmpl = getTemplate();
                        if (tmpl != null) {
                          namepan.setText(tmpl.getName());
                          pathpan.setText(fto.getPath());
                        } else {
                          namepan.setText("");
                          pathpan.setText("");
                        }
                      } else {
                        MessageDialog.openError(
                            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                            "Add template",
                            answ);
                        if(fto.exists()){
                          JobManager.jm.startJob("remove template("+scope+") because of error during add", 
                    		  new RunnableWithMonitor() {
								
								@Override
								public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
									ResManager.getManager().removeTemplate(db, fto, scope);
								}
							}, false);
                        }
                      }
                      
                    }
                  });
                  return Status.OK_STATUS;
                }
                
              };
              j.schedule();
              
          }
        };
  }

public Template getTemplate() {
    return tm.getTemplate(selectedTemplateId);
  }
  /**
   * Update templates list. If it is not possible or errors occurs, then return error messages
 * @param node 
   *
   * @param path path of last template added
   * @param size 
   * @return error message otherwise null
   */
  private String updateTemplates(TreeDB db, String path) {
    //LinkedHashMap<String, Template> tp2 = ;
    int tcnt = 0;
 //   tpls = new LinkedHashMap<>(Activator.getDefault().getRenewedReportTemplates());
 //   if (tpls.size() == 0) return null;
    int ic = templates.getItemCount();
    templates.removeAll();
    for (String s:tm.getLabelOrderWithSource()) 
      templates.add(s);
      //if (tp2.containsKey(tpl.getId())) tcnt++;
    if (ic == templates.getItemCount())
      return "Template wont loading - maybe Requality already contains template with the same name!";
    synchronized (this) {
	  Iterator<Template> i = tm.getTMap().values().iterator();
	  while (i.hasNext()) {
	    //String id = iid.next();
	    Template t = i.next();
	    if (t.getReportPath().equals(path)) selectedTemplateId = t.getId();
	  }
    }
    templates.setSelection(new String[] {selectedTemplateId});

    templates.pack();
    templates.getParent().layout();
    return null;
  }

  private Composite createSelectTemplatePanel(Composite parent) {
    Composite panel = new Composite(parent, SWT.NONE);
    panel.setLayout(new FillLayout());
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalSpan = 2;
    panel.setLayoutData(gd);
    templates = new List(panel, SWT.BORDER | SWT.READ_ONLY | SWT.HORIZONTAL);
    synchronized (this) {
      //TODO mb remove it or use the results and do not reset them
      if (tm.getTMap().size() == 0) {//TODO think
        return panel;
      } 
    }
    if (selectedTemplateId == null) {
      selectedTemplateId = Template.DEFAULT_ID;
    }

    templates.removeAll();
    templates.setEnabled(false);
    final TreeNode node = ReportTemplatesPropertySection.this.node;
    final List templates = ReportTemplatesPropertySection.this.templates;
    WorkspaceJob j = new WorkspaceJob("Update templates list"){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if(node == null || isDisposed)
          return Status.CANCEL_STATUS;
        TreeDB db = node.getTreeDB();
        java.util.List<String> ord = tm.getLabelOrderWithSource();
        Template t = tm.getTemplate(selectedTemplateId);
        Iterator<String> ita = tm.getIdOrder().iterator();
                
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
          @Override
          public void run() {
            if(isDisposed || templates.isDisposed())
              return;
            for(String s:ord)
              templates.add(s);
            templates.setEnabled(true);
            int i = 0;
            if(t!=null){
              while(ita.hasNext() && !ita.next().equals(selectedTemplateId))
                i++;
              templates.select(i);
            }
            
          }
        });
        return Status.OK_STATUS;
      }
    };
    j.schedule();
    Iterator<String> it = tm.getTMap().keySet().iterator();
    templates.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        if(templates.isDisposed())
          return;
     // TODO Auto-generated method stub
        int ind = templates.getSelectionIndex();
        Template t = null;
        boolean istempl = false;
        selectedTemplateId = "";
        TreeDB db = node!=null?node.getTreeDB():null;
        if(ind != -1){
          istempl = tm.getIsTemplate().get(ind);
          selectedTemplateId = tm.getIdOrder().get(ind);
          t = tm.getTemplate(selectedTemplateId);
        }
        if (t != null) {
          namepan.setText(t.getName() == null ? "" : t.getName());
          pathpan.setText(t.getReportPath() == null ? "" : t.getReportPath());
          deletetemplate.setEnabled(!t.getReadOnly());
        } else {
          deletetemplate.setEnabled(false);
          namepan.setText("");
          pathpan.setText("");
        }
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
     
      }
    });
    templates.setEnabled(true);
    return panel;
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return false; //TODO fix;
  }

  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    return false;
  };
}
