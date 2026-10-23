package com.unitesk.requality.eclipse.ui.dialogs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.eclipse.RequalityPlugin;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.editors.EditorUtils;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.tests.TestDialogProvider;
import com.unitesk.requality.tool.message.IMessageConsumer;
import com.unitesk.requality.tool.message.InputValidator;
import com.unitesk.requality.tool.message.RMessages;
import com.unitesk.requality.views.documents.ToDoList;

public class EclipseModalMessageConsumer implements IMessageConsumer {
  public static final String SHELL_PARAM = "shell";
  @Override
  public void openError(String title, String message, String errorStatus, boolean wait) {
    IWorkbench w = PlatformUI.getWorkbench();
    Runnable r =  new Runnable() {
      public void run() {
        Shell activeShell =
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        if(errorStatus == null || errorStatus.isEmpty())
          ErrorDialog.openError(
     	    activeShell, title, null, new Status(IStatus.ERROR, RequalityPlugin.ID, 0, message, null));
        else
          ErrorDialog.openError(
            activeShell, title, message, new Status(IStatus.ERROR, RequalityPlugin.ID, 0, errorStatus, null));        
      }
    };
    if(!wait)
      w.getDisplay()
        .asyncExec(r);
    else
      w.getDisplay()
        .syncExec(r);

  }

  private Object getParam(Pair<String, Object>[] metaParams, String key) {
    for(Pair<String, Object> s: metaParams)
      if(s.getFirst().equals(key))
        return s.getSecond();
    return null;
  }
  @Override
  public void openInfo(String title, String message, boolean wait, Pair<String, Object> ... metaParams) {
    IWorkbench w = PlatformUI.getWorkbench();
    Runnable r = 
        new Runnable() {
      public void run() {
        Shell shell = (Shell) getParam(metaParams, SHELL_PARAM);
        if(shell == null)
          shell =
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        MessageDialog.openInformation(shell, title, message);
      }

    };
    if(wait)
      w.getDisplay()
        .syncExec(r);
    else
      w.getDisplay()
        .asyncExec(r);
  }

  @Override
  public void openBigInfo(String title, String message, boolean wait) {
    IWorkbench w = PlatformUI.getWorkbench();
    Runnable r = 
        new Runnable() {
      public void run() {
        Shell activeShell =
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        BigMessageDialog.open(
            activeShell,
            title,
            "",
            message,
            "",
            new String[] {IDialogConstants.OK_LABEL});
      }
    };
    if(wait)
      w.getDisplay()
        .syncExec(r);
    else
      w.getDisplay()
        .asyncExec(r);
  }
  private boolean lastquestionboxvalue;
  @Override
  public boolean openQuestion(String title, String message, boolean defval, boolean wait) {
    IWorkbench w = null;
    Runnable r = null;
    synchronized (this) {
      lastquestionboxvalue = defval;
      w = PlatformUI.getWorkbench();
      r = new Runnable() {
  
        @Override
        public void run() {
          Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
          lastquestionboxvalue =
              com.unitesk.requality.eclipse.ui.dialogs.BigMessageDialog.open(
                  shell, title, "", message, "");
        }
      };
    }
    if(wait){
      w.getDisplay()
        .syncExec(r);
      return lastquestionboxvalue;
    }else
      w.getDisplay()
        .asyncExec(r);
    return defval;
  }

  @Override
  public boolean openQuestionOkCancel(String title, String message, boolean defval, boolean wait) {
    IWorkbench w = null;
    Runnable r = null;
    synchronized (this) {
      lastquestionboxvalue = defval;
      w = PlatformUI.getWorkbench();
      r = new Runnable() {
  
        @Override
        public void run() {
          Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
          lastquestionboxvalue =
              com.unitesk.requality.eclipse.ui.dialogs.BigMessageDialog.open(
                  shell, title, "", message, "",new String[]{IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL});
        }
      };
    }
    if(wait){
      w.getDisplay()
        .syncExec(r);
      return lastquestionboxvalue;
    }else
      w.getDisplay()
        .asyncExec(r);
    return defval;
  }
  @Override
  public Document openSelectDoc(Document doc, Document defDoc) {
	  AtomicReference<Document> doca = new AtomicReference<Document>();
	  EclipseAPI.getDisplay().syncExec(new Runnable() {
		
		@Override
		public void run() {
			Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		      SelectDocDialog dialog =
		          new SelectDocDialog(shell, doc.getTreeDB()) {
		            @Override
		            public boolean canSelect() {
		              if (!super.canSelect()) return false;
		              if (!selectedNode.getType().equals(Document.TYPE_NAME)) return false;
		              if (selectedNode.getQualifiedId().equals(doc.getQualifiedId()))
		                return false;
		              return true;
		            }
		          };

	          if (dialog.open() == SelectDocDialog.OK
	              && dialog.getResult() instanceof Document) {
	            doca.set((Document) dialog.getResult());
	          }
		}
	});
	Document d = doca.get();
	if(d!=null)
	  return d;
    return defDoc;
  }
  static Map<String, String> knownNames = new HashMap<>();
  static {
    knownNames.put("ToDoList", "com.unitesk.requality.views.ToDoList");
  }

  @Override
  public Object openView(TreeDB db, String name, boolean async, String ... args) {
    String id = knownNames.get(name);
    if(id == null)
      id = name;
    String fid = id;
    AtomicReference<Object> ret = new AtomicReference<Object>();
    Display d = EclipseAPI.getDisplay();
    Runnable r = 
        new Runnable() {
          public void run() {
            Shell sh = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            switch (fid) {
              case RMessages.DOWNLOAD_LINK_DIALOG:{
                RequalityPlugin.applyDownloadDialog(sh, args[0]);
              }break;
              case RegistryFixMessageDialog.NAME:{
                String[]strs = new String[args.length-2];
                for(int i = 2;i < args.length;i++)
                  strs[i-2] = args[i];
                RegistryFixMessageDialog selDialog = new RegistryFixMessageDialog(sh, args[0], args[1], strs);
                selDialog.open();
              }break;
              case SelectTestPurposeDialog.NAME:{
                SelectNodeDialog selDialog = new SelectTestPurposeDialog(sh, db);
                int okcanc = selDialog.open();
                if(okcanc!=selDialog.CANCEL) {
                  ret.set(selDialog.getResult());
                }else
                    ret.set(null);
              }break;
              case TemplatesSelectionDialog.NAME:{
                if(args.length<1)
                  RLogger.logError("No arguments provided for TemplatesSelectionDialog call");
                else {
                  TemplatesSelectionDialog tsd = new TemplatesSelectionDialog(sh, db, args[0]);
                  ret.set(tsd.open());
                }
              }break;
              case TestDialogProvider.CREATE_DIALOG:{
                
                ret.set(TestDialogProvider.getOne().fireTestCreation(
                    db, args[0], sh));
              }break;
              case RMessages.ID_CHANGE_DIALOG:{
                TreeNode node = db.getNode(args[0]);
                boolean checkId = args[4].equals("true");
                InputDialog id =
                    new InputDialog(
                        sh,
                        args[1],
                        args[2],
                        args[3],
                        new IInputValidator() {
                          @Override
                          public String isValid(String newText) {
                            if (newText.length() == 0) return "id cannot be empty";
                            if (checkId) {
                              if (!node.getTreeDB().getTreeLogic().isChildIdValid(node, newText))
                                return newText
                                    + " is not valid id for the child of "
                                    + node.getUserVisibleName(true)
                                    + "("
                                    + node.getType()
                                    + ")";
                            }
                            return null;
                          }
                        });
                if(id.open()==id.OK)
                    ret.set(id.getValue());
              }
              case RMessages.ID_FILE_DIALOG:
            	String exts = args[0];
            	FileDialog dialog = new FileDialog(sh, SWT.OPEN);
                dialog.setFilterExtensions(exts.split("\\|"));
                //dialog.setFilterPath(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getWorkbench().getP);
                ret.set(dialog.open());
              break;
              case EditElementDialog.NAME:
                EditElementDialog edd =
                  new EditElementDialog(sh, db.getNode(args[0]), args[1], true);
                edd.setTransactionIgnore(true);
                if(edd.open()==edd.OK)
                    ret.set(edd.getReturnCode());
              break;
              default:
              {
                try {
                  ToDoList viewPart;
                  viewPart =
                      (ToDoList)
                          PlatformUI.getWorkbench()
                              .getActiveWorkbenchWindow()
                              .getActivePage()
                              .showView(fid);
                  if(viewPart!=null)
                    viewPart.refresh(db);
                  else
                    RLogger.logError("cannot instantiate "+name);
                } catch (PartInitException e) {
                  RLogger.logError(e);
                }
              }
            }
            
          }
        };
    
    if(async)
      d.asyncExec(r);
    else
      d.syncExec(r);
    return ret.get();

  }

  @Override
  public Pair<String, Boolean> openInputDialog(String title, String message, String def, InputValidator inputValidator) {
	InputDialog dlg =
	  new InputDialog(
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
		title,
		message,
		def,
        new IInputValidator() {
			@Override
			public String isValid(String newText) {
			  if(inputValidator == null)
			    return null;
		      return inputValidator.isValid(newText);
			}
        }
	);
	if(dlg.open() == dlg.OK)
	  return new Pair<>(dlg.getValue(), true);
	return new Pair<>(def, false);
  }

  @Override
  public Pair<AtomicBoolean, OperationResult> openQuestionOkCancel(String title, String message, boolean def, boolean waitFor,
      Runnable okActons, Runnable cancelActions) {
     final AtomicBoolean actionOver = new AtomicBoolean(false);
     final AtomicBoolean resa = new AtomicBoolean(false);
     Display d = EclipseAPI.getDisplay();
     Runnable r = (
          new Runnable() {
            int it = 0;
            public void run() {
              Shell sh = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
              if(sh.isDisposed()||!sh.isEnabled())
                return;
              if (MessageDialog.openConfirm(sh, title, message)) {
                RLogger.logDebug("oksel");
                ;
                resa.set(true);
                if (okActons != null) {
                  new WorkspaceJob("Apply "+title){
                    @Override
                    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                      try {
                        okActons.run();
                        return Status.OK_STATUS;
                      }finally {
                        actionOver.set(true);
                      }
                    }
                  }.schedule();
                      
                }
              } else {
                RLogger.logDebug("cancel");
                if (cancelActions != null) { 
                  new WorkspaceJob("Cancel "+title){
                  
                    @Override
                    public IStatus runInWorkspace(IProgressMonitor monitor) {
                      try {
                        cancelActions.run();
                        return Status.OK_STATUS;
                      }finally {
                        actionOver.set(true);
                      }
                    }
                  }.schedule();
                }
              }
          }
        });
     if(!waitFor) {
       d.asyncExec(r);
       return new Pair<>(actionOver, OperationResult.PARALLEL);
     }
     else {
       d.syncExec(r);
       return new Pair<>(actionOver, resa.get()?OperationResult.SUCEEDED:OperationResult.UNSUCEEDED);
     }
  }

  @Override
  public Object openEditor(String editorid, TreeNode node, TreeNode sel) {
    UIJob uj = new UIJob("open editor "+editorid+" for node "+node.getUserVisibleName() ) {
      
      @Override
      public IStatus runInUIThread(IProgressMonitor monitor) {
        EditorUtils.openEditor(node, sel, editorid, null);
//        NodeEditorInput nei = new NodeEditorInput(node, sel, editorid);
//        try {
//          PlatformUI.getWorkbench()
//              .getActiveWorkbenchWindow()
//              .getActivePage()
//              .openEditor(nei, editorid, false);
//        } catch (PartInitException e) {
//          e.printStackTrace();
//        }
        return Status.OK_STATUS;
      }
    };
    uj.schedule();
    return null;
  }
}
