package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.EditorsSocketService;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.document.IDocumentSocketServiceStateListener;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ModuleEditor;
import com.unitesk.requality.nodetype.Requirement;

public abstract class OpenEditorExternalHandler extends AbstractHandler {
	protected TreeNode node = null;
	private RunnableWithMonitor externalEditorJob = new RunnableWithMonitor() {
		@Override
		public void run(IProgressMonitor pm) {
			EditorsSocketService socservice = new EditorsSocketService(node, editor, null, 0, null, null);
		    socservice.addStatusListener(new IDocumentSocketServiceStateListener() {
		      @Override
		      public void close() {
		    	pm.cancel();
		      }
		    });
		    socservice.systemAttrs.addAll(attrs);
            Integer port = socservice.getPort();
		    String sess = socservice.getSession();
		    String url = ResourcesProvider.getOne().formEditorURL(
		    		editor, node, sess, port).toString();
		    String surl = port!=null?url+"/?port="+port+"&document="+node.getSUUId()+"&session="+sess+"&":"np";
		    socservice.initSocket(sess);
		    try {
				PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(surl));
			} catch (PartInitException e) {
				e.printStackTrace();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		    while(!pm.isCanceled()&&!socservice.isDisposed())
				try {
					Thread.currentThread().join(200);
				} catch (InterruptedException e) {
				}
		    pm.isCanceled();
		    socservice.dispose(true);
	    }
	};
	private String editor;
	private Set<String> attrs;
	public OpenEditorExternalHandler() {
		this(ResourcesServlet.MODULEEDITOR, ModuleEditor.defaultAttrs);
	}
	public OpenEditorExternalHandler(String edi, Set<String> attrs) {
		this.editor = edi;
		this.attrs = attrs;
	}
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		JobManager.jm.startJob("External "+editor+" editor on '"+node.getUserVisibleName()+"'", externalEditorJob, true);
		return null;
	}
	@Override
	public boolean isEnabled() {
		SelectionRecord sel = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
		if(sel == null || sel.fullSelection.size()!=1)
		  return false;
		node = sel.getFirstNode();
		if(node == null)
          return false;
		return super.isEnabled()&&node.getQualifiedId().startsWith(Requirement.getTypeRootQId());
	}
}
