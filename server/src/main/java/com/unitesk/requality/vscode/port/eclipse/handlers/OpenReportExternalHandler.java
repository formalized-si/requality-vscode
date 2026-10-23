package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.ReportSocketService;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.document.IDocumentSocketServiceStateListener;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;

public class OpenReportExternalHandler extends AbstractHandler {
  protected TreeNode node = null;
  private RunnableWithMonitor externalEditorJob = new RunnableWithMonitor() {

    @Override
    public void run(IProgressMonitor pm) {
      ReportSocketService socservice = new ReportSocketService(node, null, null);
      socservice.addStatusListener(new IDocumentSocketServiceStateListener() {
        @Override
        public void close() {
          pm.cancel();
        }
      });

      ReportData rdata = (ReportData) node;
      TreeDB db = rdata.getTreeDB();
      String reportName = rdata.getId();
      TemplatesManager tm = TemplatesManager.getOne(db);
      Template tpl = tm.getTemplate(rdata);
      URI openPath = ResourcesProvider.getOne().formReportsURL(rdata, socservice.getSession(), socservice.getPort());
      if (openPath == null)
        openPath = rdata.getSaveDir();
      String href = null;
      try {
        String templateId = rdata.getTemplateId();
        if (templateId == null) {
          tpl = tm.getTemplate(Template.DEFAULT_ID);
          rdata.attrs().setConstAttribute(ReportData.ATTR_TEMPLATE, tpl.getId(), Flag.HIDDEN);
          rdata.commitAttributes();
        } else {
          tpl = tm.getTemplate(templateId);
        }
        URI savePath = openPath;
        if (rdata.getTemplateId() != null) {
          String lastGen = rdata.attrs().getStringAttributeValue(ReportData.ATTR_LAST_GENERATED);
          if (lastGen != null)
            try {
              href = new URI(savePath + CoreUtils.URLEncode(lastGen)).toString();
            } catch (URISyntaxException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
        }
        if (tpl != null && href == null) {
          // IResource res = OpenReportHandler.getReportFolder(rdata)
          // .getFile(
          // Path.fromOSString(tpl.getTemplates().get(0)
          // .getOutputFileName()));
          // if (!res.exists()) {
          // GenerateReportHandler.generateReport(rdata);
          // }
          href = savePath + "/" + tm.getTplFileName(rdata, false, null);
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
      if(href == null) {
        RLogger.logError("Cannot register socService, url is null");
        return;
      }
      String url = href.toString();
      String sess = socservice.getSession();
      Integer port = socservice.getPort();
      String surl = port != null
          ? url + "?port=" + port + "&document=" + node.getSUUId() + "&session=" + sess + "&"
          : "np";
      socservice.initSocket(sess);
      try {
        PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(surl));
      } catch (PartInitException e) {
        e.printStackTrace();
      } catch (MalformedURLException e) {
        e.printStackTrace();
      }
//        тут пока проверка лажает
//       +Разобраться - закрытие одного отчета через 30 секунд может положить всю группу
      while (!pm.isCanceled() && !socservice.isDisposed())
        try {
          Thread.currentThread().join(200);
        } catch (InterruptedException e) {
        }
      socservice.dispose(true);
    }
  };

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    JobManager.jm.startJob("External report view on '" + node.getUserVisibleName() + "'", externalEditorJob, true);
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord sel = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (sel == null || sel.fullSelection.size() != 1)
      return false;
    node = sel.getFirstNode();
    return node != null && node.getQualifiedId().startsWith("/Reports");
  }
}
