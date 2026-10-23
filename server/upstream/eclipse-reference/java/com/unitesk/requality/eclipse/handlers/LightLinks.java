package com.unitesk.requality.eclipse.handlers;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.ISources;
import org.eclipse.ui.handlers.HandlerUtil;

import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.ui.RequalityLCNF;

public class LightLinks extends AbstractHandler {
  public LightLinks() {
    for(RequalityLCNF cnf:RequalityLCNF.getActiveNaivgators().values()) {
      if(cnf.lightLinks!=null)
        cnf.lightLinks.dispose();
      cnf.lightLinks = this;
    }
  }
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    Object o = HandlerUtil.getVariable( event.getApplicationContext(),
        ISources.ACTIVE_PART_NAME);
    if(!(o instanceof RequalityLCNF))
      return null;
    RequalityLCNF cnf = (RequalityLCNF) o;
    LightLinksHighlighter hl = LightLinksHighlighter.getHl(cnf); 
    hl.triggerEnabled();
    updateNodeColoring(cnf);
    return null;
  }
  public void updateNodeColoring(RequalityLCNF cnf) {
    cnf.lightLinks = this;
    LightLinksHighlighter hl = LightLinksHighlighter.getHl(cnf); 
    cnf.lightLinks.updateButtonLabel(hl, cnf);
    if(hl.isEnabled()) {
      TreeNode rootNode = cnf.getRootTreeNode();
      Map<String, Map<TreeNode, String>> rev = RelManager.getRevertLinks(rootNode, true, true);
      Map<String, Map<TreeNode, String>> dir = RelManager.getLinks(rootNode, true);
      //Map<String, TreeNode[]> sel = new HashMap<>(SelectionManager.getOne().getSelection(cnf.getFSId()));
      Map<String, List<TreeNode>> dirM = new HashMap<>();
      Map<String, List<TreeNode>> revM = new HashMap<>();
      for(Entry<String, Map<TreeNode, String>> en:dir.entrySet()) {
        Map<TreeNode, String>nds = en.getValue();
        if(nds!=null) {
          for(Entry<TreeNode, String> en2:nds.entrySet()) {
            TreeNode nde = en2.getKey();
            String tp = nde.getType();
            if(nde != rootNode)//TODO test
            {
              if(!dirM.containsKey(tp))
                dirM.put(tp, new LinkedList<>());
              dirM.get(tp).add(nde);
            }
          }
        }
      }
      for(Entry<String, Map<TreeNode, String>> en:rev.entrySet()) {
        Map<TreeNode, String>nds = en.getValue();
        if(nds!=null) {
          for(Entry<TreeNode, String> en2:nds.entrySet()) {
            TreeNode nde = en2.getKey();
            String tp = nde.getType();
            if(nde != rootNode)//TODO test
            {
              if(!revM.containsKey(tp))
                revM.put(tp, new LinkedList<>());
              revM.get(tp).add(nde);
            }
          }
        }
      }
      Map<String, TreeNode[]> dirA = new HashMap<>();
      Map<String, TreeNode[]> revA = new HashMap<>();
      for(Entry<String, List<TreeNode>> s:dirM.entrySet())
        dirA.put(s.getKey(), s.getValue().toArray(new TreeNode[s.getValue().size()]));
      for(Entry<String, List<TreeNode>> s:revM.entrySet())
        revA.put(s.getKey(), s.getValue().toArray(new TreeNode[s.getValue().size()]));
      hl.colorNodes(new Pair<>(dirA, revA));
    }else
      hl.colorNodes(null);
  }
  private RequalityLCNF contectLCNF = null;
  private Image im;
  private DisposeListener oldList;
  protected ToolItem oldTi;
  @Override
  public void setEnabled(Object evaluationContext) {
    Object o = HandlerUtil.getVariable(evaluationContext,
        ISources.ACTIVE_PART_NAME);
    if(o instanceof RequalityLCNF)
      contectLCNF = (RequalityLCNF) o;
    else
      contectLCNF = null;
  }
  
  @Override
  public void dispose() {
    super.dispose();
    if(im!=null)
      im.dispose();
    if(oldList!=null && oldTi!=null)
      if(!oldTi.isDisposed())
        oldTi.removeDisposeListener(oldList);
  }
  @Override
  public boolean isEnabled() {
    if(contectLCNF == null)
      return false;
    //if (LinksDecorator.isButtonEnable(contectLCNF)) {
    JobManager.jm.startJob("update button state", new RunnableWithMonitor() {
      @Override
      public void run(IProgressMonitor pm) {
        updateButtonLabel(LightLinksHighlighter.getHl(contectLCNF), contectLCNF);
      }
    }, false);
    //}
    return contectLCNF.getSelectedOrRootTreeNode()
        != null;
  }
  
  private void updateButtonLabel(LightLinksHighlighter hl, RequalityLCNF view) {
    Display d = EclipseAPI.getDisplay();
    if (d != null)
      d.syncExec(
          new Runnable() {
            @Override
            public void run() {
              if (view != null&&!view.getCommonViewer().getControl().isDisposed()) {
                ToolBarManager manager =
                    (ToolBarManager) view.getViewSite().getActionBars().getToolBarManager();
                if(oldTi!=null && oldList!=null && !oldTi.isDisposed())
                  oldTi.removeDisposeListener(oldList);
                oldTi = null;
                for (ToolItem iti : manager.getControl().getItems()) {
                  Object o = iti.getData();
                  if (o != null && o instanceof ContributionItem) {
                    if ("com.unitesk.requality.commands.LightLinks"
                        .equals(((ContributionItem) o).getId())) oldTi = iti;
                  }
                }
                if (oldTi != null) {
                  if (!hl.isEnabled()) {
                    if(im!=null)
                      im.dispose();
                    im = (Image)
                            Activator.getImageDescriptor("/icons/link_break.png")
                                .createResource(d);
                    
                  } else {
                    if(im!=null)
                      im.dispose();
                    im = (Image)
                            Activator.getImageDescriptor("/icons/light_link.png")
                                .createResource(d);
                  }
                  if(im!=null) {
                    oldTi.setImage(im);
                    final Image im2 = im;
                    if(oldList!=null)
					  oldTi.removeDisposeListener(oldList);
                    oldList = one->{
                      im2.dispose();
                    };
                    oldTi.addDisposeListener(oldList); 
                  }
                }
              }
            }
          });
  }

}