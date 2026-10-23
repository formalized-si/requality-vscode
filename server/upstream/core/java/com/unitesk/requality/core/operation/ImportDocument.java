package com.unitesk.requality.core.operation;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.ITreeLogic.PastingIntoLeafException;
import com.unitesk.requality.core.tree.ITreeLogic.ProjectNotLoadedException;
import com.unitesk.requality.document.OmnivorousImporter;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.message.RMessages;

public class ImportDocument {
  private boolean create;
  private Document lastImportedDoc = null;
  private File folderTarget = null;
  private TreeNode importTarget;
  public ImportDocument(boolean cr, IDocStatusConsumer ic, TreeNode importTarget, File folderTarget){
    create = cr;
    this.importTarget = importTarget;
    this.folderTarget = folderTarget;
    setConsumer(ic);
  }
  public static ImportDocument getOne(boolean cr,IDocStatusConsumer d, TreeNode importTarget, File folderTarget){
    return new ImportDocument(cr, d, importTarget, folderTarget);
  }
  private IDocStatusConsumer dc = null;
  public void setConsumer(IDocStatusConsumer f){
    dc = f;
  }
  public interface IDocStatusConsumer{
    void updateStatus(int error, String string, Exception e);

    Boolean getStatusOk();

    Throwable getStatusException();

  }

  public boolean resourcesExists(List<?> resources) {
    for (Object resource : resources) {
      if (resourceExactExists(resource)) return true;
    }
    return false;
  }
  private void updateStatus(int error, String string, Exception e) {
    if(dc!=null)
      dc.updateStatus(error, string, e);
  }
  public Document importFilesFirst(Map<String, Document> fileToDoc,Map<String, String> fileToName, List<File> files, TreeNode parentFolder, IProgressMonitor monitor, double it) {
      Document last = null;
      last = importResourcesFirst(fileToDoc,fileToName, files, parentFolder, monitor, it);
      if (last == null && dc!=null && dc.getStatusOk()!=null) {
        boolean okState = dc.getStatusOk();
        Throwable exc = dc.getStatusException();
        if (!okState && exc != null) {

          RMessages.openError("Import failed", 
              "Fatal error occured during the importing process",
              CoreUtils.getTrace(exc), true);
          dc.updateStatus(0, null, null);
          return null;
        } else if (!okState) {
          RMessages.openError("Import problem", 
              "Error occured during the importing process","", true);
          dc.updateStatus(0, null, null);
          return last;
        }
      }
      return last;
  }
  public Document importFilesSecond(Map<String, Document> fileToDoc,Map<String, String> fileToName, List<File> files, TreeNode parentFolder, IProgressMonitor monitor, double it) {
      Document last = null;
      last = importResourcesSecond(fileToDoc,fileToName, files, parentFolder, monitor, it);
      if (last == null) {
        boolean okState = dc.getStatusOk();
        Throwable exc = dc.getStatusException();
        if (!okState && exc != null) {

          RMessages.openError("Import failed", 
              "Fatal error occured during the importing process",
              CoreUtils.getTrace(exc), true);
          dc.updateStatus(0, null, null);
          return null;
        } else if (!okState) {
          RMessages.openError("Import problem", 
              "Error occured during the importing process","", true);
          dc.updateStatus(0, null, null);
          return last;
        }
      }
      return last;
  }
  //start transaction and commit, please!
  public void createFolders(Map<TreeNode, List<File>> resMapping, File source, TreeNode importTarget, List<File> files, IProgressMonitor monitor) {
   // IPath sourcePath = new Path(sourceNameField.getText());
   // monitor.beginTask("import document folders");
    int maxstage = monitor.getMax()/4;
    double now = 0, it = maxstage*1.0f/files.size();
    int itnow = 0;
    TreeDB db = importTarget.getTreeDB();
    db.startChangeRow();
    try {
	    for (File f : files) {
	      String filePath = f.getAbsolutePath(),
	          sourcePath = source.getAbsolutePath();
	      String relativePath =
	          filePath
	              .substring(sourcePath.length()+1,filePath.length());
	      String[]p=relativePath.split("\\"+File.separator);
	      TreeNode parent = createFolder(importTarget, Arrays.copyOfRange(p,0,p.length-1), monitor);
	      if (parent == null) parent = importTarget;
	      if (resMapping.get(parent) == null) resMapping.put(parent, new ArrayList<>());
	      resMapping.get(parent).add(f);
	      now+=it;
	      int itplus = (int)now;
	      if(itplus!=itnow){
	        monitor.worked(itplus-itnow);
	        itnow = itplus;
	      }
	    }
	    return;
    }finally {
    	db.stopChangeRow();
    }
  }

  //start transaction and commit, please!
  public TreeNode createFolder(TreeNode parent, String[] folderPath, com.unitesk.requality.core.operation.IProgressMonitor monitor) {
    if (parent instanceof DocFolder && folderPath.length>0) {
      String folderName = folderPath[0];
      if(folderName.isEmpty())
        return null;
      TreeNode folder = parent.findWorkChild(folderName);
      //TODO try it
      if (folder == null)
        try {
          if (parent.getTreeDB().getTreeLogic().canBeCreatedInExc(parent.getType(), parent)) {
            folder = parent.createChild(folderName, parent.getType());
          } else {
            RMessages.openError(
                "Add Element",
                "Element of type '"
                    + parent.getTypeLabel()
                    + "' cannot be created in "
                    + parent.getUserVisibleName(false), "", false);
            return null;
          }
        } catch (PastingIntoLeafException e) {
          RMessages.openError(
              "Create Element",
              "Can't add "
                  + parent.getTypeLabel()
                  + " to "
                  + parent.getTypeLabel()
                  + " with "
                  + (parent.getType().equals(TestPurpose.TYPE_NAME) ? "non-" : "")
                  + "Test Purpose child", CoreUtils.getTrace(e), create);
          return null;
        } catch (ProjectNotLoadedException e) {
          RMessages.openError(
              "Create Element",
              "Project "
                  + parent.getTreeDB().getVisibleName()
                  + " is not loaded",
                  CoreUtils.getTrace(e),false);
          return null;
        }
      if (folder == null) return null;
      if (folderPath.length > 1 && !folderPath[1].isEmpty()){
        TreeNode df = createFolder(folder, Arrays.copyOfRange(folderPath, 1, folderPath.length), monitor);
        if(df!=null)
          return df;
      }
      return folder;
    }
    return null;
  }
  protected Document importResourcesFirst(
      Map<String, Document> fileToDoc,Map<String, String> fileName, final List<File> files, final TreeNode importDestination, IProgressMonitor monitor, double max) {
    //error flag for more than 2 docs
    TreeDB db = importDestination.getTreeDB();
    double it = max*1f/files.size();
    double now = 0;
    int nowi = 0;
    db.startChangeRow();
    try {
      //monitor.initMax(files.size());
   //   monitor.beginTask(
    //      "Importing documents"); // TEXT10
      try {
        // TODO: Transaction start
        // db.startTransaction();
        for(File sourceFile:files)
        {
          now+=it;
          String filename = sourceFile.getName();
          String filePath = sourceFile.getPath();
          Document doc = createDocument(filename, filePath, importDestination);
          String resFileName = doc.getResourceName();
          String fn = sourceFile.getCanonicalFile().toPath().toString();
          fileName.put(fn, resFileName);
          fileToDoc.put(fn, doc);
          int nowi2 = (int) now;
          if(nowi2!=nowi){
            monitor.worked(nowi2-nowi);
            nowi = nowi2;
          }
        }
        // ODO:  Transaction end
        // db.commit();

      } catch (Exception e) {
        e.printStackTrace();
        updateStatus(0x4, "Fatal error", e);
      } finally {
        try {
          ResManager.getManager().getTracker().refresh(db, -1);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        // monitor.doneTask();
      }
    } finally {
      db.stopChangeRow();
    }
    if (dc.getStatusOk() == null || !dc.getStatusOk()) return null;
    return lastImportedDoc;
  }
  public Document createDocument(String filename, String filePath, TreeNode importDestination) {
    String basename = filename;
    filename = filename.substring(0, filename.lastIndexOf(".")) + ".xhtml";
    int counter;
    if ((counter = getNextDocId(basename, filePath, importDestination, false)) > 0) {
      filename =
          filename.substring(0, filename.lastIndexOf(".")) + "_" + counter + ".xhtml";
    }
    Document doc = (Document) importDestination.createChild(filename, Document.TYPE_NAME);
    doc.setResourceName(filename);
    doc.commitAttributes();
	return doc;
  }
  protected Document importResourcesSecond(
      Map<String, Document> fileToDoc,Map<String,String> fileName, final List<File> files, final TreeNode importDestination, IProgressMonitor monitor, double max) {
    //error flag for more than 2 docs
    TreeDB db = importDestination.getTreeDB();
    double it = max*1f/files.size();
    double now = 0;
    int nowi = 0;
    db.startChangeRow();
    try {
      //monitor.initMax(files.size());
   //   monitor.beginTask(
    //      "Importing documents"); // TEXT10
      try {
        // TODO: Transaction start
        // db.startTransaction();
        
        for (File sourceFile : files) {
          now+=it;
          importFile(fileToDoc.get(sourceFile.getCanonicalFile().toPath().toString()),fileName.get(sourceFile.getCanonicalFile().toPath().toString()), fileToDoc,sourceFile, importDestination);
          int nowi2 = (int) now;
          if(nowi2!=nowi){
            monitor.worked(nowi2-nowi);
            nowi = nowi2;
          }
        }
        // ODO:  Transaction end
        // db.commit();

      } catch (Exception e) {
        e.printStackTrace();
        updateStatus(0x4, "Fatal error", e);
      } finally {
        try {
          ResManager.getManager().getTracker().refresh(db, -1);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
       // monitor.doneTask();
      }
    } finally {
      db.stopChangeRow();
    }
    if (dc.getStatusOk() == null || !dc.getStatusOk()) return null;
    return lastImportedDoc;
  }
  private void importFile(Document doc, String filename, Map<String, Document> docFiles, File sourceFile, TreeNode importDestination) throws Exception {
    

//              ResourceStorage rs = EclipseTracker.getResourceStorage(doc.getTreeDB());
//              IFolder resFolder = rs.getResourceFolder(doc.getUUId());
//              if (!resFolder.exists()) {
//                resFolder.create(true, true, null);
//              }
    File resFolder = doc.getNodeResourcesFile();
    if(!resFolder.exists())
      resFolder.mkdirs();
    String outFileName =  filename;

    if (!doc.hasResource(outFileName)) {
      OmnivorousImporter importer = new OmnivorousImporter();

//                OutputStream os = new OutputStream(){
//                  @Override
//                  public void write(int b) throws IOException {
//                    getShell().getDisplay().syncExec(new Runnable(){
//                      @Override
//                      public void run() {
//                        String m = getMessage();
//                        if(m == null)
//                          m = "";
//                        setMessage(m+(char)b);
//                      }
//                    });
//                  }
//                };
      PrintStream ps = new PrintStream(System.out, true);
      try{
        importer.importDocument(
            doc,
            sourceFile,
            docFiles,
            outFileName,
            importDestination.getTreeDB(),
            importDestination
                .getTreeDB()
                .getNode(
                    Requirement.getTypeRootQId(), com.unitesk.requality.core.model.Path.QID),
         ps);
        lastImportedDoc = doc;
        updateStatus(0x0, "loaded", null);
      }
      finally{
        ps.close();
        //os.close();
      }

    }
  }


  public int resourceExists(Object resource) {
    return getNextDocId(resource, null, create);
  }

  public boolean resourceExactExists(Object resource) {
    return resourceExactExists(resource, null, create);
  }

  public boolean resourceExactExists(Object resource, TreeNode folder, boolean createFolders) {
	if(resource instanceof ArrayList) {
		ArrayList resources = (ArrayList) resource;
		for(Object res:resources)
			if(resourceExactExists(res, folder, createFolders))
				return true;
		return false;
	}
    File fileToImport = null;
    if(resource.getClass().getName().endsWith("MinimizedFileSystemElement")){
        try {
          fileToImport = (File) resource.getClass().getMethod("getFileSystemObject").invoke(resource);
        } catch (IllegalAccessException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (IllegalArgumentException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (NoSuchMethodException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (SecurityException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
   
    if (resource instanceof File) fileToImport = (File) resource;
    else
      if(fileToImport == null)
      // wtf just happened?
        return false;
    String filename = fileToImport.getName();
    filename = filename.substring(0, filename.lastIndexOf("."));
    if (folder == null && importTarget == null) return false;
    else if (folder == null) folder = importTarget;
    if (createFolders) {
      String parentFolderPath = folderTarget.getPath().toString().replace(File.separatorChar, '/');
      String filePath = fileToImport.getPath().toString().replace(File.separatorChar, '/');
      String path = filePath.substring(parentFolderPath.length()+1, filePath.length());
      String[]tokens = path.split("/");
      TreeNode fst = folder;
      if(tokens.length>1) {
        for(int i = 0;i<tokens.length-1 && fst!=null;i++) {
          fst = fst.findWorkChild(tokens[i]);
        }
      }
      if(fst == null) {
        return false;//no folder 
      }else
        folder = fst;
    }
    if(folder!=null){
      List<TreeNode> children = folder.getSortedWorkChildren(Document.TYPE_NAME);
      int counter = 0;
      for (TreeNode doc : children) {
        if (doc instanceof Document) {
          String docName = doc.getId();
          if (docName == null || !docName.contains(".")) continue;
          docName = docName.substring(0, docName.lastIndexOf("."));
          if(docName.equals(filename))
            return true;
        }
      }
    }
    return false;
  }
  public int getNextDocId(Object resource, TreeNode folder, boolean createFolders) {
    File fileToImport = null;
    if(resource.getClass().getName().endsWith("MinimizedFileSystemElement")){
        try {
          fileToImport = (File) resource.getClass().getMethod("getFileSystemObject").invoke(resource);
        } catch (IllegalAccessException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (IllegalArgumentException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (NoSuchMethodException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (SecurityException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
   
    if (resource instanceof File) fileToImport = (File) resource;
    else
      if(fileToImport == null)
      // wtf just happened?
        return 0;
    String filename = fileToImport.getName();
    String filePath = fileToImport.toPath().toString();
    return getNextDocId(filename, filePath, folder, createFolders);
  }
  public int getNextDocId(String filename, String filePath, TreeNode folder, boolean createFolders) {
   
    filename = filename.substring(0, filename.lastIndexOf("."));
    if (folder == null && importTarget == null) return 0;
    else if (folder == null) folder = importTarget;
    if (createFolders) {
      String parentFolderPath = folderTarget.toPath().toString();
      String path = filePath.substring(parentFolderPath.length()+1, filePath.length());
      String[]tokens = path.split("/");
      TreeNode fst = folder;
      if(tokens.length>1) {
        for(int i = 0;i<tokens.length-1 && fst!=null;i++) {
          fst = fst.findWorkChild(tokens[i]);
        }
      }
      if(fst == null) {
        return 0;//no folder 
      }else
        folder = fst;
//      DeepFirstTreeWalker walker = new DeepFirstTreeWalker(folder, true, false, true);
//      boolean sameFolderAlreadyExists = false;
//      for (TreeNode descendant : walker) {
//        if (descendant.getId().equals(parentFolder) || descendant.equals(folder)) {
//          sameFolderAlreadyExists = true;
//          break;
//        }
//      }

//      if (!sameFolderAlreadyExists) return 0;
    }
    if(folder!=null){
      List<TreeNode> children = folder.getSortedWorkChildren(Document.TYPE_NAME);
      int counter = 0;
      boolean existExact = false;
      for (TreeNode doc : children) {
        if (doc instanceof Document) {
          String docName = doc.getId();
          if (docName == null || !docName.contains(".")) continue;
          docName = docName.substring(0, docName.lastIndexOf("."));
          if(docName.equals(filename)) {
            existExact = true; 
            counter = counter == 0 ? 1 :counter;
          }
          if (docName.startsWith(filename + "_")) {
            try {
              int num = Integer.parseInt(docName.substring(filename.length()+1));
              counter = Math.max(counter, num+1);
            }catch(NumberFormatException nfe) {
            }
          }
        }
      }
      if(!existExact)
        return 0;
      return counter;
    }
    return 0;
  }
}
