package com.unitesk.requality.core.operation.mass;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.ReqIFConverter;
import com.unitesk.requality.tool.reqif.ReqIFModel;
import com.unitesk.requality.tool.reqif.Specification;

public class MassDoorsIDImporter extends AbstractNodeOperation {

  public static String SPEC = "com.unitesk.pm.specifications",
      TREEDBS = "com.unitesk.pm.treedbs",
      PATH = "com.unitesk.requality.reqIFFIleName";
  public static final String ID = "com.unitesk.requality.doorsIdImporter";
  private ReqIFModel model;
  private Map<String, Specification> ss = null;

  public MassDoorsIDImporter() {
    super("Sets foreign ids on nodes");
  }

  @Override
  public String getName() {
    return "Doors Id Importer";
  }

  Map<String, String> setts = null;

  @Override
  public void applySettings(Map<String, String> args) {
    setts = args;
    model = null;
    String path = setts.get(PATH);
    ss = null;
    if (path != null) {
      java.io.File f = new java.io.File(path);
      java.io.File p = f.getParentFile();
      // TODO Auto-generated method stub
      model = new ReqIFModel(p.toURI().toString());
      try {
        String[] specs = setts.get(SPEC).split("\\|\\|\\|");
        String[] dbs = setts.get(TREEDBS).split("\\|\\|\\|");
        FileInputStream fis = new FileInputStream(f);
        try{
          model.loadDoors(ReqIFConverter.loadDoc(fis));
          ss = new HashMap<>();
          for (Specification si : model.getSpecifications())
            for (int i = 0; i < specs.length; i++)
              if (si.getId().equals(specs[i])) {
                ss.put(dbs[i], si);
              }
        }
        finally{
          fis.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void performAction(TreeNode root) {

    // model loaded
    Specification s = ss.get(root.getTreeDB().getProjectName());
    model.buildOriginMaps(s, root.getTreeDB(), ReqIFModel.ORIGIN);
    DeepFirstTreeWalker dftw =
        new DeepFirstTreeWalker(root.getTreeDB().getNode(Requirement.getTypeRootQId()));
    Iterator<TreeNode> it = dftw.iterator();
    while (it.hasNext()) {
      TreeNode node = it.next();
      String fid = model.getFidMapping(root.getTreeDB()).get(node.getUUId());
      if (fid != null) {
        String sfid = node.getForeignID();
        String exfid = node.getExternalForeignID();
        if (sfid == null || !sfid.equals(fid) && !sfid.equals(exfid)) {
          node.attrs().setConstAttribute(FIDUpdater.getFIDName(node.getTreeDB()), fid, 0);
          node.commitAttributes();
        }
      }
    }
  }
}
