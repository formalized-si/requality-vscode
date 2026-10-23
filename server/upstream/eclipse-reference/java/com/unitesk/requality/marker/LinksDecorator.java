package com.unitesk.requality.marker;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import com.unitesk.requality.core.model.ITreeNodeProvider;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.handlers.LightLinksHighlighter;
import com.unitesk.requality.eclipse.ui.RequalityLCNF;
import com.unitesk.requality.eclipse.views.PrefixAndTreeNode;

public class LinksDecorator extends RequalityDecorator implements ILabelDecorator {
  Color back, back2;
//  private static Map<RequalityLCNF,Pair<Map<String, TreeNode[]>,Map<String, TreeNode[]>>> colorNodes = new HashMap<>();
//  private TreeNode root = null;
  private String lname = null;
  private RequalityLCNF cnf = null;

  public LinksDecorator() {
    super();
  }
  
  public RequalityLCNF getLCNF() {
    if(cnf!=null)
      return cnf;
    FSId fs = SelectionManager.getOne().getFirstActiveSource(EventSource.LCNF);
    if(fs!=null) {
      RequalityLCNF cnf = RequalityLCNF.getActiveNaivgators().get(fs.getRootId().getId());
      if(cnf!=null)
        return cnf;
    }
    return null;
  }

  public LinksDecorator(RequalityLCNF cnf, TreeNode node) {
    super();
    this.cnf = cnf;
    if(node!=null)
      lname = node.getTreeDB().getLocalName();
//    root = node;
  }

  public static boolean isButtonEnable(RequalityLCNF cnf) {
    return LightLinksHighlighter.getHl(cnf).isEnabled();
  }

//  public static void setColorNode(RequalityLCNF cnf, Pair<Map<String, TreeNode[]>,Map<String, TreeNode[]>> nodes) {
//    LinksDecorator.colorNodes.put(cnf, nodes);
//  }

  @Override
  public void dispose() {
    if (back != null) {
      back.dispose();
    }
    if(back2!=null)
      back2.dispose();
  }

  @Override
  public void decorate(Object telement, final IDecoration decoration) {
    if (PrefixAndTreeNode.class.isInstance(telement))
      telement = ((PrefixAndTreeNode) telement).getNode();
    if (TreeNode.class.isInstance(telement)) {
      final Object element = telement;
      RequalityLCNF cnf = getLCNF();
      if(cnf == null)
         return;
      Pair<Map<String, TreeNode[]>, Map<String, TreeNode[]>> nodes = cnf.colorNodes;
      if (nodes != null) {
        if ((element instanceof TreeNode) && isButtonEnable(cnf) ) {
          TreeNode eln = (TreeNode) element;
          TreeNode[]ndt = nodes.getFirst().get(eln.getType());
          TreeNode[]ndt2 = nodes.getSecond().get(eln.getType());
          boolean update = false;
          boolean update2 = false;
          if(ndt!=null||ndt2!=null) {
            if(ndt!=null)
              for(TreeNode node:ndt) {
                if(node.getUUId().equals(eln.getUUId())) {
                  update = true;
                  break;
                }
              }
            if(ndt2!=null)
              for(TreeNode node:ndt2) {
                if(node.getUUId().equals(eln.getUUId())) {
                  update2 = true;
                  break;
                }
              }
              
            if(update||update2) {
              boolean up1=update, up2 = update2;
              final Display display = EclipseAPI.getDisplay();
              display.syncExec(
                new Runnable() {
                  @Override
                  public void run() {
                    if(up1) {
                      if(back != null&&back.isDisposed())
                        return;
                      if(back == null || back.getDevice()!=display) {
                        if(back!=null)
                          back.dispose();
                        back = new Color(display, 255, 129, 163); // Color(171,221,250);
                      }
                      decoration.setBackgroundColor(back);
                    }
                    if(up2) {
                      if(back2 != null&&back2.isDisposed())
                        return;
                      if(back2 == null || back2.getDevice()!=display) {
                        if(back2!=null)
                          back2.dispose();
                        back2 = new Color(display, 171,221,250); 
                      }
                      decoration.setBackgroundColor(back2);
                    }
                  }
               });
            }
          }
        }
      }
    }
  }

  @Override
  public boolean isLabelProperty(Object element, String property) {
    return false;
  }

  @Override
  public void addListener(ILabelProviderListener listener) {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeListener(ILabelProviderListener listener) {
    // TODO Auto-generated method stub

  }

  @Override
  public Image decorateImage(Image arg0, Object arg1) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String decorateText(String arg0, Object arg1) {
    if (lname!=null && arg1 instanceof ITreeNodeProvider) {
      ITreeNodeProvider pf = (ITreeNodeProvider) arg1;
      TreeNode argNode = pf.getNode();
      if(argNode != null) {
        TreeDB argTree = argNode.getTreeDB();
        TreeDB db = argTree.getTracker().getTreeDB(argTree, lname, null, false, null);
        TreeNode root = db.getRootNode();//TODO load tree from nodeprovider
        if (root != null && !root.isSameTree(argNode))
          return argTree.getProjectName() + "::";
      }
    }
    return arg1.toString();
  }

  @Override
  public Set<String> getRelatedKeys() {
    return Collections.emptySet();
  }

  public void setNode(TreeNode nodes) {
    lname = nodes.getTreeDB().getLocalName();
  }
}