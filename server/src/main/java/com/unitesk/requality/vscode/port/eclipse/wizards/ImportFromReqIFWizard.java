package com.unitesk.requality.vscode.port.eclipse.wizards;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.workbench.IImportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import org.w3c.dom.Document;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.tool.ReqIFConverter;
import com.unitesk.requality.tool.reqif.AttributeDefinition;
import com.unitesk.requality.tool.reqif.ReqIFModel;
import com.unitesk.requality.tool.reqif.SpecObjectType;

public class ImportFromReqIFWizard extends Wizard implements IImportWizard {
  public static String NEW_PROJECT_CONST = "~Add new project~";
  public static String IGNORE_ATTRIBUTE_CONST = "~Ignore attribute~";
  public static String ADD_NEW_ATTRIBUTE_CONST = "~Add new attribute~";
  public static String DOORS_ATTRIBUTES = "~Doors reqif conversion~";

  private IStructuredSelection selection;
  private TreeDB db;
  private WorkspaceFileSelectionPage fp;
  private ReqMappingPage map0, map, map2;

  public ImportFromReqIFWizard() {
    super();
  }

  private Map<IResource, Document> models = new HashMap<>();
  private Map<Document, ReqIFModel> mdls = new HashMap<>();

  protected ReqIFModel getModel() {
    Document doc = getDoc();
    if (doc != null) {
      if (!mdls.containsKey(doc)) {
        IFile f = getRes();
        if (f != null) {
          ReqIFModel mod = new ReqIFModel(f.getParent().getLocationURI().toString()); //TODO
          mod.loadDoors(doc);
          mdls.put(doc, mod);
        }
      }
      return mdls.get(doc);
    }
    return null;
  }

  protected IFile getRes() {
    IFile res = fp.getSelectedResource();
    if (res == null) return null;
    return res;
  }

  protected Document getDoc() {
    IFile res = getRes();
    if (res == null) return null;
    if (!models.containsKey(res))
      try {
        if (res.exists()) {
          res.setCharset("UTF-8", null);
          try (InputStream is = res.getContents()){
            models.put(res, ReqIFConverter.loadDoc(is));
          }
        }
      } catch (CoreException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      } catch (IOException e) {
        RLogger.logError(e);
      }
    return models.get(res);
  }

  @Override
  public void addPages() {
    addPage(fp);
    addPage(map0);
    addPage(map);
    addPage(map2);
  };

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
    setWindowTitle("ReqIF to Requality import wizard");
    this.selection = selection;
    DialogSettings ds = new DialogSettings("importFromReqIf");
    fp = new WorkspaceFileSelectionPage(ds, new String[] {"reqif", "xml"});
    map0 =
        new ReqMappingPage(ds, this) {
          Map<String, String> specs = ReqIFConverter.loadReqIFSpecifications(getModel());

          @Override
          public void updateSecondField() {
            Set<String> names = new HashSet<>();
            names.add(NEW_PROJECT_CONST);
            EclipseTracker et =  EclipseTracker.getTracker(DBNamesManager.DEFAULT);
            for (IProject proj : et.getProjects())
              names.add(et.getLocalName(proj));//TODO local names not equal to project
            map0.initSecondField(names);
          }

          @Override
          public void updateFirstField() {
            specs = ReqIFConverter.loadReqIFSpecifications(getModel());
            if (specs == null || specs.size() < 1) return;
            Set<String> ret = new HashSet<>();
            for (String s : specs.keySet()) ret.add(specs.get(s) + TreeDB.DATA_SEPARATOR + s);
            map0.initFirstField(ret);
            updateSecondField();
          }

          @Override
          protected boolean isClose(String s, String k) {
            return firstPass ? (k.length() > 4 && (s).startsWith(k)) : k.equals(NEW_PROJECT_CONST);
          }
        };
    map0.setTitle("Specification to project mapping");
    map0.setDescription("Map ReqIF specifications to Requality projects.");

    map = new ReqMappingPage(ds, this);

    map2 =
        new ReqMappingPage(ds, this) {
          @Override
          public void updateSecondField() {
            Map<String, String> mapp1 = map.getMapping();
            if (mapp1 == null || mapp1.size() < 1) return;
            Set<String> ret = new HashSet<>();
            ret.add(IGNORE_ATTRIBUTE_CONST);
            ret.add(ADD_NEW_ATTRIBUTE_CONST);
            ret.addAll(ReqIFConverter.renames.keySet());
            map2.initSecondField(ret);
            boolean inited = false;
            for(String s:map2.result.getItems())
              if(s.equals(DOORS_ATTRIBUTES))
                inited = true;
            if(!inited){
              map2.result.add(DOORS_ATTRIBUTES);
              for(String s:map2.type.getItems()){
                if(!com.unitesk.requality.core.attribute.Attribute.checkNameIsValid(s, false))
                  map2.addLink(s,com.unitesk.requality.core.attribute.Attribute.getValidName(s));
              }
              for(String s:map2.type.getItems()){
                if(s.toLowerCase().contains("text") || s.toLowerCase().contains("descr"))
                  map2.addLink(s,"_description");
                if(s.toLowerCase().contains("name"))
                  map2.addLink(s,"_nameorid");                
              }
            }
            //TODO load reqs
          }

          @Override
          public void updateFirstField() {
            Map<String, String> mapp1 = map.getMapping();
            if (mapp1 == null || mapp1.size() < 1) return;
            ReqIFModel doc = getModel();
            if (doc != null) {
              Set<String> ret = new HashSet<>();
              for (SpecObjectType sot : doc.getSpecObjectTypes()) {
                for (AttributeDefinition def : sot.getAttributeDefinitions())
                  ret.add(sot.getName() + "." + def.getName());
                //ret.add(s+".LongName");
              }
              map2.initFirstField(ret);
            }
          }

          @Override
          protected boolean isClose(String s, String k) {
            if (map2.firstPass) {
              if (k.length() > 0 && s.endsWith(k)) return true;
              for (String i : ReqIFConverter.renames.keySet())
                for (String vl : ReqIFConverter.renames.get(i))
                  if (s.endsWith(vl) && i.equals(k)) return true;
              return false;
            }
            return k.equals(ADD_NEW_ATTRIBUTE_CONST);
          }
        };
    map2.setTitle("Attributes mapping");
    map2.setDescription("Map ReqIF attributes to Requality attributes.");
  }

  @Override
  public boolean canFinish() {
    return map.getMapping() != null
        && !map.getMapping().isEmpty()
        && fp.getSelectedResource() != null
        && fp.getSelectedResource().exists()
        && map0.getMapping() != null
        && !map0.getMapping().isEmpty();
  };

  @Override
  public boolean performFinish() {

    Map<String, String> mapp1 = map.getMapping();
    Map<String, String> mapp2 = map2.getMapping();
    Map<String, String> mapp0 = map0.getMapping();
    Map<String, TreeDB> nmap = new HashMap<>();
    for (String s : mapp0.keySet()) {
      String pr = mapp0.get(s);
      if (!pr.equals(NEW_PROJECT_CONST)) {
        IProject proj = Activator.getProjectByName(pr);
        if (proj != null && proj.exists())
          try {
            EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
            nmap.put(s, et.getTreeDB(proj,null, et.getLocalName(proj)));
          } catch (IOException e) {
            RLogger.logError(e);
          }
		else nmap.put(s, null);
      } else nmap.put(s, null);
    }    
    WorkspaceJob wj = new WorkspaceJob("Import from ReqIF"){
        
        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
          ReqIFConverter.convertToCatalog(getModel(), mapp1, mapp2, nmap);
          return Status.OK_STATUS;
        }
      };
      wj.schedule();
    return true;
  }

  public IFile getSelectedFile() {
    return fp.getSelectedResource();
  }
}
