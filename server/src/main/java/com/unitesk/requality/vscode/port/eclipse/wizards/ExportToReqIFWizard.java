package com.unitesk.requality.vscode.port.eclipse.wizards;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IExportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import org.w3c.dom.Document;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.BigMessageDialog;
import com.unitesk.requality.tool.ReqIFConverter;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.tool.reqif.ReqIFModel;
import com.unitesk.requality.tool.reqif.Specification;

public class ExportToReqIFWizard extends Wizard implements IExportWizard {

  private IStructuredSelection selection;
  private TreeDB db;
  private TextFileSelectionPage fp;
  private TextFileSelectionPage wfsp;
  private ProjectMappingPage pmp;
  private DialogSettings ds;
  private static Map<String, Document> models = new HashMap<>();
  private static Map<Document, ReqIFModel> mdls = new HashMap<>();

  protected ReqIFModel getModel() {
    return getModel(getRes());
  }

  protected static ReqIFModel getModel(String f) {
    Document doc = getDoc(f);
    try{
      if (doc != null) {
        if (!mdls.containsKey(doc)) {
          if (f != null) {
            ReqIFModel mod = new ReqIFModel(f); //TODO
            mod.loadDoors(doc);
            mdls.put(doc, mod);
          }
        }
        return mdls.get(doc);
      }
    }catch(Exception e){
      RLogger.logError(e);
    }
    return null;
  }

  protected String getRes() {
    String res = ds.get(wfsp.getVarName());
    if (res == null) return null;
    return res;
  }

  protected Document getDoc() {
    return getDoc(getRes());
  }

  protected static Document getDoc(String res) {
    if (res == null) return null;
    if (!models.containsKey(res)) models.put(res, ReqIFConverter.loadDoc(res));
    return models.get(res);
  }

  public ExportToReqIFWizard() {
    super();
    ds = new DialogSettings("com.unitesk.requality.ExportToReqIfWizard");
  }
  @Override
  public void addPages() {
    super.addPages();
    TreeDB db = getSelectedTreeDB();
    StructuredSelection sel;
    //ExternalFileSelectionPage efsp = new ExternalFileSelectionPage(ds, new String[]{"xml","reqif"});
    if (db != null) sel = new StructuredSelection(db);
    else sel = new StructuredSelection();
    //fp = new NewFilePage(sel,"");
    fp =
        new TextFileSelectionPage(
            "com.unitesk.outRFFile",
            "ReqIF export - select target location",
            "Please push \"Browse\" button, select directory and set new target file name in the corresponding field.",
            ds,
            new String[] {"reqif"},
            (byte) (TextFileSelectionPage.ONLY_NOT_EXISTS | TextFileSelectionPage.SAVE));
    wfsp =
        new TextFileSelectionPage(
            "com.unitesk.inRFFile",
            "ReqIF export - select ReqIF model file",
            "Please push \"Browse\" button and select a location of a file with ReqIF model. \nOnly DOORS ReqIF models with '.xml' extension are supported for now.",
            ds,
            new String[] {"xml", "reqif"},
            (byte) (TextFileSelectionPage.ONLY_EXISTS | TextFileSelectionPage.LOAD));
    //wfsp = new ExternalFileSelectionPage(ds, new String[]{"xml","reqif"});
    //wfsp = new WorkspaceFileSelectionPage(ds, new String[]{"xml","reqif"});

    pmp = new ProjectMappingPage(false, db, ds);
    addPage(wfsp);
    addPage(fp);
    //wfsp.setOptional(true);
    addPage(pmp);

    //fp.setDescription("Сhoose target project and select file name");
  };
  @Override
  public IWizardPage getPreviousPage(IWizardPage page) {
    IWizardPage page2 = super.getPreviousPage(page);
    if (page2 instanceof IUpdatablePage) ((IUpdatablePage) page2).update();
    return page2;
  }
  @Override
  public IWizardPage getNextPage(IWizardPage page) {
    IWizardPage page2 = super.getNextPage(page);
    if (page2 instanceof IUpdatablePage) ((IUpdatablePage) page2).update();
    return page2;
  }

  private TreeDB getSelectedTreeDB() {
    if (!(selection instanceof StructuredSelection)) return db;
    TreeDB db = this.db;
    Object o = ((StructuredSelection) selection).getFirstElement();
    IProject p = (IProject) o;
    if (o instanceof IProject)
	  try {
	    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        db = et.getTreeDB(p, null, et.getLocalName(p));
      } catch (IOException e) {
        RLogger.logError(e);
      }
    if (o instanceof TreeNode) db = ((TreeNode) o).getTreeDB();
    if (o instanceof TreeDB) db = (TreeDB) o;
    this.db = db;
    return db;
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    setWindowTitle("Requality to ReqIF export wizard");
    this.selection = selection;
  }

  @Override
  public boolean performFinish() {
    final File f = createFile(ds.get("com.unitesk.outRFFile"));
    final Map<Specification, TreeDB> map = pmp.getMapping();
    StringBuilder sb = new StringBuilder();
    for (TreeDB db : map.values()) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(db.getProjectName());
    }
    final String what = sb.toString();
    final DialogSettings ds = ExportToReqIFWizard.this.ds;
    WorkspaceJob wj =
        new WorkspaceJob("Export '" + what + "' to reqif file: " + f.getAbsolutePath()) {

          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
            //try {
            String infile = ds.get("com.unitesk.inRFFile");
            String addfile = ds.get("com.unitesk.outRFFile");
            ReqIFModel model = null;
            if (addfile != null) {
              //					File f2 = new File(addfile);
              //					File f1 = new File(infile);
              model = getModel();
              //monitor.("init old reqif");
              //org.w3c.dom.Document doc = ReqIFConverter.loadDoc(new FileInputStream(f1));
              //monitor.subTask("load old reqif to model");
              //model.loadDoors(doc);
            }
            //				if(model == null||model.isIncorrect()){
            //					//monitor.subTask("empty model was created");
            //					model = ReqIFModel.createEmpty(f.getAbsolutePath());//TODO normal creation
            //				}
            File tf;
            try {
              tf = File.createTempFile("tempreq", UUID.randomUUID().toString());
              //monitor.subTask("convert catatlog to document");
              org.w3c.dom.Document doc =
                  ReqIFConverter.convertCatalogToDocument(
                      map, model, f.getParentFile().getAbsolutePath());
              //monitor.subTask("convert document to reqif file");
              ReqIFModel.docToFile(doc, tf);
              RequalityCLI.copyfile(tf.toURI(), f.toURI());
              //							Files.copy(Files.tf,f,
              //									StandardCopyOption.REPLACE_EXISTING);
              tf.deleteOnExit();
              Set<String> list = ReqIFConverter.getCounters(model);
              StringBuilder sb = new StringBuilder();
              for (String s : list) {
                sb.append(s);
                sb.append(" ");
                sb.append(ReqIFConverter.getCounterVal(model, s));
                sb.append("\n");
              }
              final String cnts = sb.toString();
              EclipseAPI.getDisplay()
                  .asyncExec(
                      new Runnable() {
                        @Override
                        public void run() {
                          BigMessageDialog.open(
                              getShell(),
                              "Export for '" + what + "' - completed!",
                              "ReqIF export was completed",
                              "Stats:\n" + cnts,
                              "",
                              new String[] {IDialogConstants.OK_LABEL});
                          // your stuff
                        }
                      });
            } catch (Exception we) {
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              PrintStream ps = new PrintStream(baos);
              we.printStackTrace(ps);
              ps.close();
              try {
                baos.close();
              } catch (IOException e) {
                // TODO Auto-generated catch block
                RLogger.logError(e);
              }
              EclipseAPI.getDisplay()
                  .asyncExec(
                      new Runnable() {
                        @Override
                        public void run() {
                          BigMessageDialog.open(
                              getShell(),
                              "Export for '" + what + "' - error!",
                              "ReqIF export causes an error",
                              baos.toString(),
                              "",
                              new String[] {IDialogConstants.OK_LABEL});
                        }
                      });
              return Status.CANCEL_STATUS;
            } finally {
              models.clear();
            }
            //ReqIFConverter.convertCatalogToDocument(db,model))
            /*			} catch (UnsupportedEncodingException e) {
            	RLogger.logError(e);
            }*/
            return Status.OK_STATUS;
          }
        };
    wj.schedule();
    return true;
  }

  private File createFile(String string) {
    File f = new File(string);
    if (f.getParentFile() != null)
      try {
        if(!f.getParentFile().exists())
          f.getParentFile().mkdirs();
        f.createNewFile();
      } catch (IOException e) {
        RLogger.logError(e);
      }
    return f;
  }
}
