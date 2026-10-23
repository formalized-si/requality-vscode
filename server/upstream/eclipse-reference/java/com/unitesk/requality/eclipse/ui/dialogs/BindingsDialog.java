package com.unitesk.requality.eclipse.ui.dialogs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.handlers.NewElementHandler;
import com.unitesk.requality.eclipse.handlers.create.AHotKeyCommand;

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
            //org.eclipse.core.commands.IHandler[] hndls = Activator.getDefault().getCommandHandlers(cmdService, comm);
            LinkedList<IHandler> hndls = lhandlers.get(sels);
            if(hndls!=null){
              String tp = null;
              tp = lbindsType.get(sels);
              //if(tp==null)
              for(org.eclipse.core.commands.IHandler h:hndls){
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
                } catch (org.eclipse.core.commands.ExecutionException e) {
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
  Map<String, LinkedList<org.eclipse.core.commands.IHandler>> lhandlers = null;
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
                  
                  org.eclipse.core.commands.IHandler[] hndls = Activator.getDefault().getCommandHandlers(cmdService, cid);
                  String tp = "";
                  if(cid.equals(EXTENSION_NAME_CREATE_NODE)){
                    tp = par.get("requality.parameter.NodeType");
                    b = node.getTreeDB().getTreeLogic().canBeChildOf(par.get("requality.parameter.NodeType"), node);
                  }
                  LinkedList<org.eclipse.core.commands.IHandler> handlers = null;
                  for(org.eclipse.core.commands.IHandler h: hndls){
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