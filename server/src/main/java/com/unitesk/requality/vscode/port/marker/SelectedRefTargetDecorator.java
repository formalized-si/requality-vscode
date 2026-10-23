package com.unitesk.requality.vscode.port.marker;

import java.util.Collections;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProviderListener;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.FontData;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;

public class SelectedRefTargetDecorator extends RequalityDecorator {

  public SelectedRefTargetDecorator() {
    EclipseAPI.getDisplay().asyncExec(new Runnable(){
    
      @Override
      public void run() {
        synchronized (this) {
          back = EclipseAPI.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
          text = EclipseAPI.getDisplay().getSystemColor(SWT.COLOR_WHITE); 
        }
      }
    });
  }
  @Override
  public void dispose() {
    synchronized (this) {
      if (back != null) {
        back.dispose();
        text.dispose();
      }
    }
  }

  Color back, text;
  @Override
  public void decorate(Object element, final IDecoration decoration) {
    if (!TreeNode.class.isInstance(element) || element == null) {
      return;
    }
    TreeNode node = (TreeNode) element;
    TreeDB db = node.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if(!tsm.isAvailiable(db.getLocalName(), tsm.getRevisionInfo(db))||db.isDisposed())
      return;
    if (node.getTreeDB().getLinkResolver().getRefTargetState(node) > 0)
      EclipseAPI.getDisplay()
        .syncExec(
           new Runnable() {
             @Override
             public void run() {
               if(text!=null&&text.isDisposed())
                 return;
               synchronized (SelectedRefTargetDecorator.this) {
                 if(back == null){
                    back = EclipseAPI.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);
                    text = EclipseAPI.getDisplay().getSystemColor(SWT.COLOR_WHITE);
                  }
                }
                decoration.setForegroundColor(text);
                decoration.setBackgroundColor(back);
                Font old = EclipseAPI.getDisplay().getSystemFont();
                FontData fd = new FontData(old.getFontData()[0].toString());
                fd.setStyle(fd.getStyle() | SWT.ITALIC);
                Font f = EclipseAPI.getFont(fd);
                decoration.setFont(f);
             }
           });
          // TODO: change depth if it slows down much
  }

  @Override
  public boolean isLabelProperty(Object element, String property) {
    TreeNode node = (TreeNode) element;
    return node.getTreeDB().getLinkResolver().getRefTargetState(node) > 0;
    
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
  public Set<String> getRelatedKeys() {
    // TODO Auto-generated method stub
    return Collections.emptySet();
  }
}
