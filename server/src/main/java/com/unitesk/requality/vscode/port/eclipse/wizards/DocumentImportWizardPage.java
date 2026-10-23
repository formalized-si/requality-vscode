/**
 * ***************************************************************************** Copyright (c) 2006
 * IBM Corporation and others. All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * <p>Contributors: IBM Corporation - initial API and implementation
 * *****************************************************************************
 */
package com.unitesk.requality.vscode.port.eclipse.wizards;

import com.unitesk.requality.vscode.ui.resources.Project;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.MultiStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.BusyIndicator;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.FocusListener;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.DirectoryDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.jface.dialogs.ElementTreeSelectionDialog;
import com.unitesk.requality.vscode.ui.ide.dialogs.FileSystemElement;
import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.ide.IDEWorkbenchPlugin;
import com.unitesk.requality.vscode.ui.ide.dialogs.IElementFilter;
import com.unitesk.requality.vscode.ui.ide.wizards.MinimizedFileSystemElement;
import com.unitesk.requality.vscode.ui.ide.wizards.WizardFileSystemResourceImportPage1;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchContentProvider;
import com.unitesk.requality.vscode.ui.navigator.CommonNavigator;
import com.unitesk.requality.vscode.ui.ide.wizards.FileSystemStructureProvider;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ImportDocument;
import com.unitesk.requality.core.operation.ImportDocument.IDocStatusConsumer;
import com.unitesk.requality.core.operation.ProgressRunnable;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.vscode.port.eclipse.views.documents.DocLabelProvider;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;

@SuppressWarnings("restriction")
public class DocumentImportWizardPage extends WizardFileSystemResourceImportPage1
    implements Listener {
  private ImportDocument.IDocStatusConsumer sc = new IDocStatusConsumer(){
  
    @Override
    public void updateStatus(int error, String string, Exception e) {
      DocumentImportWizardPage.this.updateStatus(error, string, e);
    }

    @Override
    public Boolean getStatusOk() {
      return importStatus==null?null:importStatus.getCode()==MultiStatus.OK;
    }

    @Override
    public Throwable getStatusException() {
      return importStatus==null?null:importStatus.getException();
    }

  };
  private boolean entryChanged;
  private IStatus importStatus;
  private TreeNode importTarget;
  private Button renameImportedCheckbox;
  private Button createFoldersCheckbox;
  private DialogSettings ds;

  public DocumentImportWizardPage(IWorkbench workbench, IStructuredSelection selection, DialogSettings ds) {
    this("RequalityImportWizardPage1", workbench, selection, ds);
    selectRequalityTypes();
    EclipseTracker tt = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    lastImportedDoc = null;
    if (selection != null && selection.getFirstElement() instanceof DocFolder) {
      DocFolder folder = (DocFolder) selection.getFirstElement();
      IProject prj = tt.getProject(folder.getTreeDB());
      super.setContainerFieldValue(tt.getLocalName(prj) + folder.getQualifiedId());
      importTarget = folder;
    } else if (selection != null && selection.getFirstElement() instanceof Project) {
      Project p = (Project) selection.getFirstElement();
      TreeDB db = null;
	  try {
		db = tt.getTreeDB(p, null, tt.getLocalName(p));
	  } catch (IOException e) {
		// TODO Auto-generated catch block
		RLogger.logError(e);
	  }
	  if(db!=null)
        importTarget = db.getNode(Document.getTypeRootQId(), com.unitesk.requality.core.model.Path.QID);
    } else importTarget = null;
  }
  

  @SuppressWarnings("unchecked")
  private void selectRequalityTypes() {
    super.selectedTypes.add("xhtml");
    super.selectedTypes.add("html");
    super.selectedTypes.add("htm");
    super.selectedTypes.add("doc");
    super.selectedTypes.add("docx");
    super.selectedTypes.add("pdf");
    super.selectedTypes.add("odt");
  }

  @Override
  public void handleEvent(Event event) {
    if (event.widget == sourceBrowseButton) {
      handleSourceBrowseButtonPressed();
      return;
    }
    super.handleEvent(event);
  }

  private void openDocumentEditor(final Document lastImportedDoc) {
    if (lastImportedDoc == null) return;
    WorkspaceJob wj = new WorkspaceJob("Open editor for "+lastImportedDoc.getName()){
      int reruns = 0;
      @Override
      public boolean shouldRun() {
        boolean bl = super.shouldRun();
    	if(lastImportedDoc.getNodeResourcesFile()==null) {
    		reruns++;
    		if(reruns<100)
    			this.schedule(200);
    		else
    			RLogger.logError("Document "+lastImportedDoc.getUserVisibleName()+" cannot load resource file");
    		return false;
    	}
		return bl;
      }
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        PlatformUI.getWorkbench()
        .getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                //Taken from handler
                NodeEditorInput input = new NodeEditorInput(lastImportedDoc, ReqMarker.ID, null);
                try {
                  IDE.openEditor(
                      PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                      input,
                      ReqMarker.ID);
                } catch (PartInitException e) {
                  RLogger.logError(e);
                }
                IViewPart view =
                    PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow()
                        .getActivePage()
                        .findView(RequalityCNF.ID);
                if (view instanceof CommonNavigator) {
                  CommonNavigator navigator = (CommonNavigator) view;
                  navigator.selectReveal(new StructuredSelection(lastImportedDoc));
                }
              }
            });
        return Status.OK_STATUS;
      }
    };
    wj.setPriority(wj.DECORATE);
    wj.schedule();
  }
  @Override
  public boolean finish() {
    IUndoContext context = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
    PlatformUI.getWorkbench()
        .getOperationSupport()
        .getOperationHistory()
        .dispose(context, true, true, true);
    if (!ensureSourceIsValid(true)) return false;

    saveWidgetValues();
    boolean cfc = createFoldersCheckbox.getSelection();
    final String cnft = sourceNameField.getText();
    @SuppressWarnings("unchecked")
    Iterator<FileSystemElement> resourcesEnum = getSelectedResources().iterator();
    List<File> fileSystemObjects = new ArrayList<>();
    while (resourcesEnum.hasNext()) {
      FileSystemElement e = resourcesEnum.next();
      if (!e.isDirectory()) fileSystemObjects.add((File)e.getFileSystemObject());
    }
    ImportDocument id=ImportDocument.getOne(cfc, sc, importTarget, getSourceDirectory());
    API.getMonitorManager().runUnderMonitor("Import documents", true, new ProgressRunnable(){
    
      @Override
      public void run(com.unitesk.requality.core.operation.IProgressMonitor mon) {
	    if(importTarget == null)
          return;
	    TreeDB db = importTarget.getTreeDB();
        CacheStorage cs = db.getStorage().getCacheStorage();
        CacheTransactionDescription s = cs.startCacheTransaction("Import documents", 2);
        boolean ida = db.startTransaction("import documents",true);
        try{
          mon.beginTask("import documents", 10000);
          
          if (!cfc) {
            if (fileSystemObjects.size() > 0) {
              Map<String, String> fn = new HashMap<>();
              Map<String, Document> dc = new HashMap<>();
              lastImportedDoc = id.importFilesFirst(dc,fn,fileSystemObjects, importTarget, mon,mon.getMax()/2);
              lastImportedDoc = id.importFilesSecond(dc,fn,fileSystemObjects, importTarget, mon,mon.getMax()/2);
              if(ida) {
                db.commit();
                ida = false;
              }
              if(lastImportedDoc != null){
                if (fileSystemObjects.size() == 1)
                  openDocumentEditor(lastImportedDoc);
                return;
              }
              return;
            }
          } else {
            // TODO: undo document import
            // importTarget.getTreeDB().startTransaction();
            Map<TreeNode, List<File>> fileMap = new HashMap<>();
            id.createFolders(fileMap,new File(cnft), importTarget, fileSystemObjects,mon);
            // importTarget.getTreeDB().commit();
            Iterator<TreeNode> parents = fileMap.keySet().iterator();
            Document doc = null;
            boolean showEditor = true;
            Map<String, Document> fileToDoc = new HashMap<>();
            double it = mon.getMax()*3/8/fileMap.values().size();
            boolean b = db.startTransaction("Import documents to "+importTarget.getUserVisibleName(),true);
            try{
              Map<String, String> fn = new HashMap<>();
              while (parents.hasNext()) {
                TreeNode importDest = parents.next();
                if (id.importFilesFirst(fileToDoc,fn, fileMap.get(importDest), importDest, mon, it)!=null) {
                  if (fileSystemObjects.size() == 1)
                    if (doc == null) {
                      doc = lastImportedDoc;
                    } else {
                      showEditor = false;
                    }
                }
              }
              parents = fileMap.keySet().iterator();
              while (parents.hasNext()) {
                TreeNode importDest = parents.next();
              //  mon.beginTask("import folder "+importDest.getNameOrId());
                lastImportedDoc = id.importFilesSecond(fileToDoc,fn, fileMap.get(importDest), importDest, mon, it);
                if (lastImportedDoc!=null) {
                  if (fileSystemObjects.size() == 1)
                    if (doc == null) {
                      doc = lastImportedDoc;
                    } else {
                      showEditor = false;
                    }
                } else {
                  return;
                }
               // mon.doneTask();
              }
              if(b) {
                b = false;
                db.commit();
              }
              if(ida) {
                db.commit();
                ida = false;
              }
            }finally{
              if(b) 
                db.rollback();
            }
            if (showEditor) openDocumentEditor(doc);
            finish();
            return;
          }
          if(ida) {
            db.commit();
            ida = false;
          }
        }finally{
          mon.doneTask();
          if(ida)
            db.rollback();
          if(s != null && s.error==null)
            cs.commit(s);
        }
        MessageDialog.openInformation(
            getContainer().getShell(), "Document import", "No files were selected"); // TEXT7 (TEXT8)
      }
    });
    return true;
  }

  

 

  @Override
  protected void createOptionsGroupButtons(Group optionsGroup) {
    renameImportedCheckbox = new Button(optionsGroup, SWT.CHECK);
    renameImportedCheckbox.setFont(optionsGroup.getFont());
    renameImportedCheckbox.setText("Rename imported documents"); // TEXT1
    renameImportedCheckbox.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            updatePageCompletion();
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            updatePageCompletion();
          }
        });

    createFoldersCheckbox = new Button(optionsGroup, SWT.CHECK);
    createFoldersCheckbox.setFont(optionsGroup.getFont());
    createFoldersCheckbox.setText("Create folders"); // TEXT2
    createFoldersCheckbox.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            updatePageCompletion();
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            updatePageCompletion();
          }
        });
  }


  @Override
  protected boolean validateOptionsGroup() {
    if (renameImportedCheckbox == null) return false;
    else if (!renameImportedCheckbox.getSelection()
        && ImportDocument.getOne(createFoldersCheckbox.getSelection(), sc, importTarget, 
            getSourceDirectory()).
        resourceExactExists(super.getSelectedResources(), importTarget, createFoldersCheckbox.getSelection())) {
      super.setErrorMessage("Document with the same name already exists"); // TEXT13
      return false;
    } else if (!createFoldersCheckbox.getSelection()
        && !renameImportedCheckbox.getSelection()
        && twoResourcesInList(super.getSelectedResources())) {
      super.setErrorMessage("Documents with the same name are selected");
      return false;
    } else {
      super.setErrorMessage(null);
      return true;
    }
  }

  @Override
  protected boolean validateSourceGroup() {
    /*
     * if(new File(this.sourceNameField.getText()).exists()) {
     * super.selectionGroup.setFocus();
     * if(super.selectionGroup.getAllListItems().size()==1) {
     * super.selectionGroup.setAllSelections(true); } return
     * super.validateSourceGroup(); } return false;
     */
    return new File(this.sourceNameField.getText()).exists() && super.validateSourceGroup();
  }

  private String getFileName(Object resource) {
    File fileToImport = null;
    if (resource instanceof MinimizedFileSystemElement)
      fileToImport = (File) ((MinimizedFileSystemElement) resource).getFileSystemObject();
    else if (resource instanceof File) fileToImport = (File) resource;
    else
      // wtf just happened?
      return "";
    String filename = fileToImport.getName();
    filename = filename.substring(0, filename.lastIndexOf("."));
    return filename;
  }

  private boolean twoResourcesInList(List<?> resources) {
    for (Object resource : resources)
      for (Object resource2 : resources)
        if (resource != resource2 && getFileName(resource).equals(getFileName(resource2)))
          return true;
    return false;
  }


  private Document lastImportedDoc;

  protected Document GetLastImportedDocument() {
    return lastImportedDoc;
  }



  private void updateStatus(int severity, String message, Throwable exception) {
    if (importStatus == null || !importStatus.isMultiStatus()) {
      importStatus = new MultiStatus("Requality", 0, message, null);
    }
    ((MultiStatus) importStatus).add(new Status(severity, "Requality", message, exception));
  }

  private boolean ensureSourceIsValid(boolean local) {
    AtomicReference<String> name = new AtomicReference<>();
    EclipseAPI.getDisplay().syncExec(new Runnable(){
    
      @Override
      public void run() {
        if(createFoldersCheckbox == null || createFoldersCheckbox.isDisposed())
          return;
        name.set(getSourceDirectoryName());
      }
    });
    if(name.get()==null || name.get().isEmpty())
      return false;
    if (new File(name.get()).isDirectory()) return true;
    if (local) {
      if (new File(name.get()).isDirectory()) return true;

      displayErrorDialog("Source path doesn't exist"); // TEXT9
      sourceNameField.setFocus();
    }
    return false;
  }

  @Override
  protected boolean ensureSourceIsValid() {
    return ensureSourceIsValid(false);
  }

  protected String getSourceDirectoryName() {
    return getSourceDirectoryName(this.sourceNameField.getText());
  }

  @Override
  protected void handleSourceBrowseButtonPressed() {

    String currentSource = this.sourceNameField.getText();
    DirectoryDialog dialog = new DirectoryDialog(sourceNameField.getShell(), SWT.SAVE);
    dialog.setText("Document import"); // TEXT2
    dialog.setMessage("Select source folder"); // TEXT3
    dialog.setFilterPath(getSourceDirectoryName(currentSource));
    String selectedDirectory = dialog.open();
    
    ds.put("lastPath", selectedDirectory);
    try {
      ds.save(DocumentImportWizard.SETTS_FILE_NAME);
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

    if (selectedDirectory != null) {
      if ((getSourceDirectory(selectedDirectory) == null)
          || selectedDirectory.equals(currentSource)) return;
      setErrorMessage(null);
      setSourceName(selectedDirectory);
    }
  }

  @Override
  protected void handleContainerBrowseButtonPressed() {
    displayDestinationSelectionDialog();
  }

  private class DestinationSelectionDialog extends ElementTreeSelectionDialog {

    public DestinationSelectionDialog(
        Shell parent, ILabelProvider labelProvider, ITreeContentProvider contentProvider) {
      super(parent, labelProvider, contentProvider);
    }

    @Override
    protected Label createMessageArea(Composite parent) {
      Button createFolder = super.createButton(parent, -1, "Create folder", false); // TEXT16
      GridData data = new GridData();
      data.horizontalAlignment = SWT.END;
      data.horizontalSpan = 2;
      createFolder.setLayoutData(data);
      createFolder.addSelectionListener(
          new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
        	  if(createFolder.isDisposed())
                return;
              TreeViewer v = getTreeViewer();
              IStructuredSelection selection = (IStructuredSelection) v.getSelection();
              if (selection != null && selection.getFirstElement() instanceof DocFolder) {
                TreeNode parentFolder = (TreeNode) selection.getFirstElement();
                InputDialog folderName =
                    new InputDialog(
                        getShell(), "Create folder", "Folder name:", null, null); // TEXT17,
                // TEXT18
                folderName.open();
                // TODO: import undo
                // parentFolder.getTreeDB().startTransaction();
                API.getMonitorManager().runUnderMonitor("Create folder", true, new ProgressRunnable(){
                
                  @Override
                  public void run(com.unitesk.requality.core.operation.IProgressMonitor monitor) {
                    TreeNode newFolder = ImportDocument.getOne(createFoldersCheckbox.getSelection(), sc, importTarget, getSourceDirectory())
                        .createFolder(parentFolder, new String[]{folderName.getValue()}, 
                        monitor );
                    // parentFolder.getTreeDB().commit();
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                      @Override
                      public void run() {
                        if(v.getControl().isDisposed())
                          return;
                        v.setSelection(new StructuredSelection(newFolder));
                      }
                    });
                  }
                });
                
              }
              v.refresh();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
          });
      return null;
    }
  }

  private void displayDestinationSelectionDialog() {
    RequalityContentProvider provider = new RequalityContentProvider();
    provider.addFilter(
        new ITreeNodeFilter() {
          @Override
          public boolean select(TreeNode node) {
            return node instanceof DocFolder;
          }
        });
    DestinationSelectionDialog dialog =
        new DestinationSelectionDialog(this.getShell(), new DocLabelProvider(), provider);
    dialog.setAllowMultiple(false);
    if (importTarget != null) {
      dialog.setInitialSelection(this.importTarget);
      dialog.setInput(importTarget.getTreeDB().getRootNode());
    } else {
      dialog.setInput(ResourcesPlugin.getWorkspace().getRoot());
    }
    dialog.setTitle("Import into Folder"); // TEXT
    dialog.open();
    Object[] result = dialog.getResult();
    if (result != null && result.length > 0 && result[0] instanceof DocFolder) {
      DocFolder target = (DocFolder) result[0];
      EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
      IProject prj = et.getProject(target.getTreeDB());
      super.setContainerFieldValue(et.getLocalName(prj) + target.getQualifiedId());
      this.importTarget = target;
    }
  }
  @Override
  protected IPath getContainerFullPath() {
    IPath p = super.getContainerFullPath();
    String[]segs = p.segments();
    if(segs.length>0) {
      String proj = segs[0];
      IWorkspace workspace = IDEWorkbenchPlugin.getPluginWorkspace();
      IWorkspaceRoot rt = workspace.getRoot();
      if(!rt.exists(p.removeLastSegments(p.segmentCount() - 1)))
        for(IProject pr :rt.getProjects())
          if(pr.getLocation().lastSegment().equals(proj)) {
            segs = Arrays.copyOf(segs, 1);
            return new Path(EclipseTracker.getProjectName(pr)+Path.SEPARATOR+ String.join(Path.SEPARATOR+"", segs));
          }
    }
    return p;
  }
  protected String getSourceDirectoryName(String sourceName) {
    IPath result = new Path(sourceName.trim());

    if (result.getDevice() != null && result.segmentCount() == 0)
      result = result.addTrailingSeparator();
    else result = result.removeTrailingSeparator();

    return result.toOSString();
  }

  protected File getSourceDirectory(String selectedDirectory) {
    File sourceDirectory = new File(getSourceDirectoryName(selectedDirectory));
    if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
      return null;
    }
    return sourceDirectory;
  }

  @Override
  protected void setSourceName(String selectedDirectory) {
    if (selectedDirectory.length() > 0) {

      String[] currentItems = this.sourceNameField.getItems();
      int selectionIndex = -1;
      for (int i = 0; i < currentItems.length; i++) {
        if (currentItems[i].equals(selectedDirectory)) selectionIndex = i;
      }
      if (selectionIndex < 0) {
        int oldLength = currentItems.length;
        String[] newItems = new String[oldLength + 1];
        System.arraycopy(currentItems, 0, newItems, 0, oldLength);
        newItems[oldLength] = selectedDirectory;
        this.sourceNameField.setItems(newItems);
        selectionIndex = oldLength;
      }
      this.sourceNameField.select(selectionIndex);

      resetSelection();
    }
  }

  @Override
  protected MinimizedFileSystemElement getFileSystemTree() {
    File sourceDirectory = getSourceDirectory();
    if (sourceDirectory == null) return null;

    return selectFiles(sourceDirectory, FileSystemStructureProvider.INSTANCE);
  }

  private MinimizedFileSystemElement selectFiles(
      final File sourceDirectory, final FileSystemStructureProvider instance) {
    final MinimizedFileSystemElement[] results = new MinimizedFileSystemElement[1];

    BusyIndicator.showWhile(
        getShell().getDisplay(),
        new Runnable() {
          public void run() {
            results[0] = createRootElement(sourceDirectory, instance);
          }
        });

    return results[0];
  }

  protected MinimizedFileSystemElement createRootElement(
      File sourceDirectory, FileSystemStructureProvider instance) {
    boolean isContainer = instance.isFolder(sourceDirectory);
    String elementLabel = instance.getLabel(sourceDirectory);

    MinimizedFileSystemElement dummyParent = new MinimizedFileSystemElement("", null, true);
    dummyParent.setPopulated();
    MinimizedFileSystemElement result =
        new MinimizedFileSystemElement(elementLabel, dummyParent, isContainer);
    result.setFileSystemObject(sourceDirectory);
    result.getFiles(instance);

    return dummyParent;
  }

  @Override
  protected File getSourceDirectory() {
    return getSourceDirectory(this.sourceNameField.getText());
  }

  protected DocumentImportWizardPage(String name, IWorkbench work, IStructuredSelection selection, DialogSettings ds2) {
    super(name, work, selection);
    this.ds = ds2;
    
  }

  @Override
  protected void createSourceGroup(Composite parent) {
    createSourceDirectoryGroup(parent);
    super.createFileSelectionGroup(parent);
    createButtonsGroupReq(parent);

    String path = ds.get("lastPath");
    if(path!=null)
      setSourceName(path);
  }

  protected void createButtonsGroupReq(Composite parent) {
    Composite buttonComposite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    layout.makeColumnsEqualWidth = true;
    buttonComposite.setLayout(layout);
    buttonComposite.setFont(parent.getFont());
    GridData buttonData =
        new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    buttonData.horizontalSpan = 2;
    buttonComposite.setLayoutData(buttonData);

    selectTypesButton =
        createButton(
            buttonComposite, IDialogConstants.SELECT_TYPES_ID, "Select types", false); // TEXT4

    SelectionListener listener =
        new SelectionAdapter() {
          public void widgetSelected(SelectionEvent e) {
            handleTypesEditButtonPressed();
            resetSelection();
          }
        };
    selectTypesButton.addSelectionListener(listener);
    setButtonLayoutData(selectTypesButton);

    selectAllButton =
        createButton(buttonComposite, IDialogConstants.SELECT_ALL_ID, "Select all", false); // TEXT5

    listener =
        new SelectionAdapter() {
          public void widgetSelected(SelectionEvent e) {
            setAllSelections(true);
            updateWidgetEnablements();
          }
        };
    selectAllButton.addSelectionListener(listener);
    setButtonLayoutData(selectAllButton);

    deselectAllButton =
        createButton(
            buttonComposite, IDialogConstants.DESELECT_ALL_ID, "Deselect all", false); // TEXT6

    listener =
        new SelectionAdapter() {
          public void widgetSelected(SelectionEvent e) {
            setAllSelections(false);
            updateWidgetEnablements();
          }
        };
    deselectAllButton.addSelectionListener(listener);
    setButtonLayoutData(deselectAllButton);
  }

  @Override
  protected void updateWidgetEnablements() {
    super.updateWidgetEnablements();
    enableButtonGroup(true);
  }

  @Override
  protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    ((GridLayout) parent.getLayout()).numColumns++;

    Button button = new Button(parent, SWT.PUSH);
    button.setFont(parent.getFont());

    GridData buttonData = new GridData(GridData.FILL_HORIZONTAL);
    button.setLayoutData(buttonData);

    button.setData(new Integer(id));
    button.setText(label);

    if (defaultButton) {
      Shell shell = parent.getShell();
      if (shell != null) {
        shell.setDefaultButton(button);
      }
      button.setFocus();
    }
    return button;
  }

  private void createSourceDirectoryGroup(Composite parent) {
    Composite sourceContainerGroup = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    sourceContainerGroup.setLayout(layout);
    sourceContainerGroup.setFont(parent.getFont());
    sourceContainerGroup.setLayoutData(
        new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));

    Label groupLabel = new Label(sourceContainerGroup, SWT.NONE);
    groupLabel.setText("From directory:"); // TEXT1
    groupLabel.setFont(parent.getFont());

    sourceNameField = new Combo(sourceContainerGroup, SWT.BORDER);
    GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL);
    data.widthHint = SIZING_TEXT_FIELD_WIDTH;
    sourceNameField.setLayoutData(data);
    sourceNameField.setFont(parent.getFont());

    sourceNameField.addSelectionListener(
        new SelectionAdapter() {
          public void widgetSelected(SelectionEvent e) {
            updateFromSourceField();
          }
        });
    sourceNameField.addKeyListener(
        new KeyListener() {

          private String sourceValue;

          public void keyPressed(KeyEvent e) {
            // entryChanged = true;
            sourceValue = sourceNameField.getText();
            setPageComplete(false);
          }

          public void keyReleased(KeyEvent e) {
            if (!sourceNameField.getText().equals(sourceValue)) entryChanged = true;
            if (e.keyCode == '\r') {
              File sourcepath = new File(sourceNameField.getText());
              if (sourcepath.exists() && sourcepath.isDirectory()) {
                updateFromSourceField();
              } /*
                 * else if(sourcepath.exists() && sourcepath.isFile()) {
                 * selectSingleFile(sourceNameField.getText());
                 *
                 * }
                 */
            }
          }
        });
    sourceNameField.addFocusListener(
        new FocusListener() {
          public void focusGained(FocusEvent e) {}

          public void focusLost(FocusEvent e) {
            if (entryChanged) {
              entryChanged = false;
              updateFromSourceField();
            }
            determinePageCompletion();
          }
        });

    sourceBrowseButton = new Button(sourceContainerGroup, SWT.PUSH);
    sourceBrowseButton.setText("Browse...");
    sourceBrowseButton.addListener(SWT.Selection, this);
    sourceBrowseButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
    sourceBrowseButton.setFont(parent.getFont());
    setButtonLayoutData(sourceBrowseButton);
  }

  @SuppressWarnings("unchecked")
  /*
   * private void selectSingleFile(String filepath){ String parentFolder = new
   * File(filepath).getParent(); String filename = new
   * File(filepath).getName(); String extension = new
   * File(filepath).getName().substring(filename.indexOf('.')+1);
   * if(!extension.isEmpty() && !this.selectedTypes.contains(extension))
   * this.selectedTypes.add(extension); sourceNameField.setText(parentFolder);
   * updateFromSourceField(); Map<Object, Object> itemMap = new
   * HashMap<Object, Object>(); for(Object o :
   * super.selectionGroup.getAllListItems()) { if(o instanceof
   * MinimizedFileSystemElement) { MinimizedFileSystemElement element =
   * (MinimizedFileSystemElement) o; if(!element.isDirectory()) { File file =
   * (File) element.getFileSystemObject();
   * if(file.getPath().equals(filepath)){ List<MinimizedFileSystemElement>
   * selected = new ArrayList<MinimizedFileSystemElement>();
   * selected.add(element); itemMap.put(element.getParent(), selected); } } }
   * } super.selectionGroup.updateSelections(itemMap); }
   */
  @Override
  protected void setupSelectionsBasedOnSelectedTypes() {
    ProgressMonitorDialog dialog = new ProgressMonitorDialog(getContainer().getShell());
    final Map<FileSystemElement, List<?>> selectionMap =
        new Hashtable<>();

    final IElementFilter filter =
        new IElementFilter() {

          @SuppressWarnings("rawtypes")
          public void filterElements(Collection files, IProgressMonitor monitor)
              throws InterruptedException {
            if (files == null) {
              throw new InterruptedException();
            }
            Iterator<?> filesList = files.iterator();
            while (filesList.hasNext()) {
              if (monitor.isCanceled()) throw new InterruptedException();
              checkFile(filesList.next());
            }
          }

          public void filterElements(Object[] files, IProgressMonitor monitor)
              throws InterruptedException {
            if (files == null) {
              throw new InterruptedException();
            }
            for (int i = 0; i < files.length; i++) {
              if (monitor.isCanceled()) throw new InterruptedException();
              checkFile(files[i]);
            }
          }

          private void checkFile(Object fileElement) {
            MinimizedFileSystemElement file = (MinimizedFileSystemElement) fileElement;
            if (isExportableExtension(file.getFileNameExtension())) {
              List<FileSystemElement> elements = new ArrayList<>();
              FileSystemElement parent = file.getParent();
              if (selectionMap.containsKey(parent))
                elements = (List<FileSystemElement>) selectionMap.get(parent);
              elements.add(file);
              selectionMap.put(parent, elements);
            }
          }
        };

    IRunnableWithProgress runnable =
        new IRunnableWithProgress() {
          @SuppressWarnings("deprecation")
          public void run(final IProgressMonitor monitor) throws InterruptedException {
            monitor.beginTask("Selectioning monitoring processing", IProgressMonitor.UNKNOWN);
            getSelectedResources(filter, monitor);
          }
        };

    try {
      dialog.run(true, true, runnable);
    } catch (InvocationTargetException exception) {
      return;
    } catch (InterruptedException exception) {
      return;
    }
    getShell().update();
    updateSelections(selectionMap);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected boolean isExportableExtension(String extension) {
    if (selectedTypes == null) // ie.- all extensions are acceptable
    return true;

    Iterator<String> itr = selectedTypes.iterator();
    while (itr.hasNext()) {
      if (extension.equalsIgnoreCase(itr.next())) return true;
    }

    return false;
  }

  protected void updateFromSourceField() {
    setSourceName(sourceNameField.getText());
    super.updateWidgetEnablements();
  }

  @Override
  protected ITreeContentProvider getFileProvider() {
    return new WorkbenchContentProvider() {
      public Object[] getChildren(Object o) {
        if (o instanceof MinimizedFileSystemElement) {
          MinimizedFileSystemElement element = (MinimizedFileSystemElement) o;
          List<Object> exportableFiles = new ArrayList<>();
          for (Object file :
              element.getFiles(FileSystemStructureProvider.INSTANCE).getChildren(element))
            if (isExportableExtension(((MinimizedFileSystemElement) file).getFileNameExtension()))
              exportableFiles.add(file);
          return exportableFiles.toArray();
        }
        return new Object[0];
      }
    };
  }

  @Override
  protected ITreeContentProvider getFolderProvider() {
    return new WorkbenchContentProvider() {
      public Object[] getChildren(Object o) {
        if (o instanceof MinimizedFileSystemElement) {
          MinimizedFileSystemElement element = (MinimizedFileSystemElement) o;
          return element.getFolders(FileSystemStructureProvider.INSTANCE).getChildren(element);
        }
        return new Object[0];
      }

      public boolean hasChildren(Object o) {
        if (o instanceof MinimizedFileSystemElement) {
          MinimizedFileSystemElement element = (MinimizedFileSystemElement) o;
          if (element.getFiles().size() != 0) return getChildren(element).length > 0;

          return true;
        }
        return false;
      }
    };
  }
}
