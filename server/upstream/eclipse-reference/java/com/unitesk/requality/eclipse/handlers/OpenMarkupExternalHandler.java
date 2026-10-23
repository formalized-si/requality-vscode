package com.unitesk.requality.eclipse.handlers;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.DocumentsSocketService;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.document.IDocumentSocketServiceStateListener;
import com.unitesk.requality.nodetype.Document;

public class OpenMarkupExternalHandler extends AbstractHandler {
  protected TreeNode node = null;
  private RunnableWithMonitor externalEditorJob = new RunnableWithMonitor() {

    @Override
    public void run(IProgressMonitor pm) {
        DocumentsSocketService socservice = new DocumentsSocketService(node, null, null, null);
        socservice.addStatusListener(new IDocumentSocketServiceStateListener() {
          @Override
          public void close() {
          pm.cancel();
          }
        });
        String sess = socservice.getSession();
        socservice.initSocket(sess);
            URI openPath = ResourcesProvider.getOne().formDocumentURL((Document)node, socservice.getSession(), socservice.getPort());
            if(openPath == null)
              openPath = ((Document)node).getHTMLPath();

        String url = openPath.toString();
            Integer port = socservice.getPort();
        String surl = port!=null?url+"/?port="+port+"&document="+node.getSUUId()+"&session="+sess+"&":"np";
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
        socservice.dispose(true);
      }
  };
  public OpenMarkupExternalHandler() {
  }
  public OpenMarkupExternalHandler(String edi, Set<String> attrs) {
  }
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    JobManager.jm.startJob("External markup editor on '"+node.getUserVisibleName()+"'", externalEditorJob, true);
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
    return node.getQualifiedId().startsWith("/Documents");
  }
}
