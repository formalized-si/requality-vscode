package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.ui.RequalityLCNF;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.vscode.port.marker.LinksDecorator;

public class LightLinksHighlighter{
  private static Map<RequalityLCNF, LightLinksHighlighter> hls = new HashMap<>();
  
  public static LightLinksHighlighter getHl(RequalityLCNF lcnf) {
    LightLinksHighlighter highlighter = hls.get(lcnf);
    if(highlighter == null)
    {
      highlighter = new LightLinksHighlighter(lcnf);
      hls.put(lcnf, highlighter);
      EclipseAPI.getDisplay().asyncExec(new Runnable() {
        @Override
        public void run() {
          if(lcnf.getCommonViewer().getControl().isDisposed()) {
            hls.remove(lcnf);
          }else
            lcnf.getCommonViewer().getControl().addDisposeListener(new DisposeListener() {
              @Override
              public void widgetDisposed(DisposeEvent e) {
                hls.remove(lcnf);
              }
            });
        }
      });
    }
    return highlighter;
  }
  private boolean enabled = false;
  public RequalityLCNF lcnf = null;
  //Pair<Map<String, TreeNode[]>,Map<String, TreeNode[]>> oldNodes = null;
  public LightLinksHighlighter(RequalityLCNF lcnf) {
    this.lcnf = lcnf;
  }
  public void colorNodes(Pair<Map<String, TreeNode[]>,Map<String, TreeNode[]>> nodes) {
    colorNodes(nodes, lcnf.colorNodes);
  }
  
  public void triggerEnabled() {
    enabled = !enabled;
  }

  public void colorNodes(Pair<Map<String, TreeNode[]>,Map<String, TreeNode[]>> nodes, Pair<Map<String, TreeNode[]>,Map<String, TreeNode[]>> oldNodes) {
    EclipseAPI.getDisplay()
        .syncExec(
            new Runnable() {
              @Override
              public void run() {
                FSId fs = SelectionManager.getOne().getFirstActiveSource(EventSource.LCNF);
                RequalityLCNF viewLCNF = null;
                if(fs!=null) {
                  viewLCNF = RequalityLCNF.getActiveNaivgators().get(fs.getRootId().getId());
                }
                RequalityCNF viewCNF =
                    RequalityCNF.activeNavigator;

                if (viewLCNF != null && viewCNF != null&&!viewLCNF.getCommonViewer().getControl().isDisposed()) {
                  Object[] elmsLCNF = viewLCNF.getCommonViewer().getExpandedElements();
                  viewLCNF.colorNodes = nodes;

                  Set<TreeNode>plainNodes = new HashSet<>();

                  if (LinksDecorator.isButtonEnable(viewLCNF)) {
                    if (nodes != null) {
                      List<Object> list =
                          new ArrayList<>(
                              Arrays.asList(
                                  RequalityCNF.activeNavigator
                                      .getCommonViewer()
                                      .getExpandedElements()));
                      if(nodes.getFirst()!=null && nodes.getFirst().size()>0) {
                        for(Entry<String, TreeNode[]> en:nodes.getFirst().entrySet()){
                          TreeNode[] nda = en.getValue();
                          if(nda!=null) 
                            for(TreeNode node:nda){
                              if(node == null)
                                continue;
                              if(!plainNodes.contains(node))
                                plainNodes.add(node);
                              TreeNode nde = node.getWorkParent();
                              while (nde != null) {
                                if(!list.contains(nde))
                                  list.add(nde);
                                nde = nde.getWorkParent();
                              }
                            }
                        }
                      }
                      if(nodes.getSecond()!=null && nodes.getSecond().size()>0) {
                        for(Entry<String, TreeNode[]> en:nodes.getSecond().entrySet()){
                          TreeNode[] nda = en.getValue();
                          if(nda!=null) 
                            for(TreeNode node:nda){
                              if(node == null)
                                continue;
                              if(!plainNodes.contains(node))
                                plainNodes.add(node);
                              TreeNode nde = node.getWorkParent();
                              while (nde != null) {
                                if(!list.contains(nde))
                                  list.add(nde);
                                nde = nde.getWorkParent();
                              }
                            }
                        }
                      }
                      RequalityCNF.activeNavigator
                          .getCommonViewer()
                          .setExpandedElements(list.toArray());
                    }
                    for(TreeNode node:plainNodes){
                      viewLCNF.getCommonViewer().refresh(node);
                      viewCNF.refresh(node);
                    }
                  }
                  if (oldNodes != null) {
                    if(oldNodes.getFirst()!=null)
                      for(Entry<String, TreeNode[]> en:oldNodes.getFirst().entrySet())
                      {
                        TreeNode[]nds = en.getValue();
                        if(nds!=null)
                          for(TreeNode node:nds)
                          {
                            viewLCNF.getCommonViewer().refresh(node);
                            viewCNF.refresh(node);
                          }
                      }
                    if(oldNodes.getSecond()!=null)
                      for(Entry<String, TreeNode[]> en:oldNodes.getSecond().entrySet())
                      {
                        TreeNode[]nds = en.getValue();
                        if(nds!=null)
                          for(TreeNode node:nds)
                          {
                            viewLCNF.getCommonViewer().refresh(node);
                            viewCNF.refresh(node);
                          }
                      }
                  }
                  viewLCNF.getCommonViewer().setExpandedElements(elmsLCNF);
                }
              }
            });
  }
  public boolean isEnabled() {
    return enabled;
  }
}