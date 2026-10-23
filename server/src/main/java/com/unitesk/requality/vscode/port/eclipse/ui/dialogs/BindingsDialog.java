package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.InvalidRegistryObjectException;
import com.unitesk.requality.vscode.ui.runtime.Platform;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.PopupDialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.commands.ICommandService;
import com.unitesk.requality.vscode.ui.workbench.IContextActivation;
import com.unitesk.requality.vscode.ui.workbench.IContextService;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.handlers.NewElementHandler;
import com.unitesk.requality.vscode.port.eclipse.handlers.create.AHotKeyCommand;

/** Find dialog */
public class BindingsDialog extends PopupDialog {
  private static final String CID = "com.unitesk.bindings.context";
  private static final String EXTENSION_NAME_BINDS = "org.eclipse.ui.bindings";
  private static final String EXTENSION_NAME_COMMANDS = "org.eclipse.ui.commands";
  private static final String EXTENSION_NAME_CREATE_NODE = "com.unitesk.requality.commands.CreateNode";
  private static String TITLE = "Requality hotkey dialog";
  private List names;
  private IContextActivation ica = null;
  private TreeNode node = null;
  private Display display;
  public BindingsDialog(Shell shell, TreeNode treeNode) {
    super(shell, PopupDialog.INFOPOPUP_SHELLSTYLE, false, false, false,
        false, true, null, null);
    this.node = treeNode;
    shell.addDisposeListener(new DisposeListener() {
      @Override
      public void widgetDisposed(DisposeEvent e) {
        deActivateContext();	
      }
	});
    //setShellStyle(SWT.DIALOG_TRIM | SWT.CLOSE | SWT.MODELESS | SWT.BORDER | SWT.TITLE);
  }
  private Map<String,Map<String,String>> lparams;
  private Runnable actionsOnHanlderEnded = new Runnable() {
    @Override
    public void run() {
      deActivateContext();
      for(Entry<String, LinkedList<IHandler>> en:lhandlers.entrySet()) {
        if(en.getValue()!=null) 
          for(IHandler h:en.getValue())
            if(h instanceof AHotKeyCommand) {
              ((AHotKeyCommand)h).setActionsBeforeRun(null);
              ((AHotKeyCommand)h).setActionsOnCompleted(null);
            }
      }
      if(display==null || display.isDisposed())
        return;
      display.syncExec(new Runnable() {
        @Override
        public void run() {
          BindingsDialog.this.close();
        }
      });
    }
  };
  @Override
  protected Control createDialogArea(Composite parent) {
    display = parent.getDisplay();
    parent.getShell().setText(TITLE);
    Composite comp1 = new Composite(parent, SWT.NONE);
    comp1.setLayout(new GridLayout(1,false));
    names = new List(comp1, SWT.None);
    
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.minimumHeight = 300;
    gd.minimumWidth = 300;
    names.setLayoutData(gd);
    final Display display = parent.getShell().getDisplay();
    WorkspaceJob j = new WorkspaceJob("load key bounds"){
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        getBinds();
        display.syncExec(new Runnable() {
          @Override
          public void run() {
            parent.forceFocus();
          }
        });
        return Status.OK_STATUS;
      }
    };
    j.schedule();
    
    names.addSelectionListener(new SelectionListener(){
      @Override
      public void widgetSelected(SelectionEvent e) {
        int sel = names.getSelectionIndex();
        if(sel == -1)
          return ;
        String sels = names.getItem(sel);
        

        ICommandService cmdService = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(ICommandService.class);
        WorkspaceJob j = new WorkspaceJob("Init commands on binding panel"){

          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) {
            String comm = getBinds().get(sels);
            //com.unitesk.requality.vscode.ui.commands.IHandler[] hndls = Activator.getDefault().getCommandHandlers(cmdService, comm);
            LinkedList<IHandler> hndls = lhandlers.get(sels);
            if(hndls!=null){
              String tp = null;
              tp = lbindsType.get(sels);
              //if(tp==null)
              for(com.unitesk.requality.vscode.ui.commands.IHandler h:hndls){
                try {
                  boolean b = h.isEnabled();
                  if(b && h instanceof AHotKeyCommand)
                  {
                    AHotKeyCommand neh = (AHotKeyCommand) h;
                    String[]tps = null;
                    if(neh instanceof NewElementHandler) {
                      tps = new String[] {((NewElementHandler)neh).getElementType()};
                      if(tps !=null && !tp.isEmpty())
                      {
                        b = false;
                        for(String s:tps)
                          if(s.equals(tp))
                            b = true;
                      }
                    }else
                      b = neh.isEnabled();
                  }
                  
                  if(b){
                    h.execute(new ExecutionEvent(lparams.get(sels), this, null));
                    break;
                  }
                } catch (com.unitesk.requality.vscode.ui.commands.ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
              }
            }
			return Status.OK_STATUS;
          }
        };
        j.schedule();
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    ICommandService cmdService = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(ICommandService.class);
    WorkspaceJob ja = new WorkspaceJob("Init commands on binding panel"){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        getBinds();
        return Status.OK_STATUS;
      }
      
    };
    ja.schedule();
    activateContext();
    return comp1;
  }

  Map<String, String> lbindsType = null;
  Map<String, LinkedList<com.unitesk.requality.vscode.ui.commands.IHandler>> lhandlers = null;
  Map<String, String> lbinds = null;
  Set<String> keys = null;
private IContextService contextService;
  private Map<String, String> getBinds() {
    synchronized (this) {
      if (lbinds == null) {
        lbindsType = new TreeMap<>();
        lbinds = new TreeMap<>();
        lparams = new HashMap<>();
        lhandlers = new HashMap<>();
        keys = new HashSet<>();
        AtomicReference<ICommandService> acs = new AtomicReference<>();
        AtomicReference<IExtensionRegistry> aer = new AtomicReference<>();
        EclipseAPI.getDisplay().syncExec(new Runnable(){
          @Override
          public void run() {
            acs.set(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(ICommandService.class));
            aer.set(Platform.getExtensionRegistry());
          }
        });
        ICommandService cmdService = acs.get();
        IExtensionRegistry reg = aer.get();
        IConfigurationElement[] configurationElements =
            reg.getConfigurationElementsFor(EXTENSION_NAME_BINDS);
        IConfigurationElement[] commands =
            reg.getConfigurationElementsFor(EXTENSION_NAME_COMMANDS);
        Map<String, String> commandNames = new HashMap<>();
        for(IConfigurationElement ie:commands)
        {
          String name = ie.getAttribute("name");
          String id = ie.getAttribute("id");
          if(name == null)
            name = id;
          commandNames.put(id, name);
        }
        for (IConfigurationElement conf : configurationElements) {
          String s = conf.getAttribute("contextId");
          if(s!=null && s.equals(CID))
            try {
              String cid = conf.getAttribute("commandId");
              if(cid!=null)
              {
                boolean b = false;
                String cname = commandNames.get(cid);
                if(cname!=null){
                  String key = conf.getAttribute("sequence");
                  //TODO replace by BracesHandler ?
                  Map<String,String> par = new HashMap<>();
                  for(IConfigurationElement el :conf.getChildren("parameter"))
                    if(el!=null){
                      String id = el.getAttribute("id");
                      String val = el.getAttribute("value");
                      if(id!=null && val!=null){
                        cname = cname.replace("{"+id+"}", val);
                        par.put(id, val);
                      }
                    }
                  
                  com.unitesk.requality.vscode.ui.commands.IHandler[] hndls = Activator.getDefault().getCommandHandlers(cmdService, cid);
                  String tp = "";
                  if(cid.equals(EXTENSION_NAME_CREATE_NODE)){
                    tp = par.get("requality.parameter.NodeType");
                    b = node.getTreeDB().getTreeLogic().canBeChildOf(par.get("requality.parameter.NodeType"), node);
                  }
                  LinkedList<com.unitesk.requality.vscode.ui.commands.IHandler> handlers = null;
                  for(com.unitesk.requality.vscode.ui.commands.IHandler h: hndls){
                    if(h instanceof AHotKeyCommand && h.isEnabled()) {
                      AHotKeyCommand ahc = (AHotKeyCommand) h;
                      boolean addh = false;
                      if(ahc instanceof NewElementHandler) {
                        NewElementHandler handler = (NewElementHandler) ahc;
                        tp = handler.getElementType();
                        addh = true;
                      }else if (tp!=null)
                        addh = true;
                      if(addh) {
                        if(handlers == null)
                          handlers = new LinkedList<>();
                        handlers.add(h);
                      }
                      b=true;
                      ahc.setActionsBeforeRun(actionsOnHanlderEnded);
                    }
                  }
                  if(b){
                    keys.add(key);
                    String id = "("+refineMs(key)+")"+cname;
                    lbinds.put(id, cid);
                    lparams.put(id, par);
                    lbindsType.put(id, tp);
                    lhandlers.put(id, handlers);
                  }
                }
              }
            } catch (InvalidRegistryObjectException e) {
              e.printStackTrace();
              // TODO Auto-generated catch block
              //  e.printStackTrace();
            }
        }
        final List names = this.names;
        final Map<String, String> lbinds = this.lbinds;
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
          @Override
          public void run() {
            if(names.isDisposed())
              return;
            for(String s:lbinds.keySet())
               names.add(s);
          }});
        }
      }
      return lbinds;
  }

  private static String refineMs(String key) {
    return key.replace("M1","[Ctrl]").replace("M2", "[Shift]").replace("M3", "[Alt]");
  }

  void activateContext()
  {
    if(ica!=null)
        contextService.deactivateContext(ica);
    contextService = (IContextService)PlatformUI.getWorkbench()
        .getService(IContextService.class);
    ica = contextService.activateContext(CID);
  }
  void deActivateContext()
  {
    if(contextService!=null)
      contextService.deactivateContext(ica);
    ica.toString();
  }

}