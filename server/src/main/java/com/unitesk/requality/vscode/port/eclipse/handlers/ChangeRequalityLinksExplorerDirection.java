package com.unitesk.requality.vscode.port.eclipse.handlers;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolItem;

import com.unitesk.requality.vscode.port.eclipse.ui.ImageContainer;
import com.unitesk.requality.vscode.port.eclipse.ui.RequalityLCNF;

public class ChangeRequalityLinksExplorerDirection extends AbstractHandler {
  private ImageContainer ih = new ImageContainer();
  private Image oldImage = null;
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    Display d = Display.getCurrent();
    RequalityLCNF.toggleReferenceDirection();
    com.unitesk.requality.vscode.ui.swt.widgets.Event e = (com.unitesk.requality.vscode.ui.swt.widgets.Event) (event.getTrigger());
    ToolItem ti = ((ToolItem) e.widget);
    for (RequalityLCNF cnf : RequalityLCNF.getActiveNaivgators().values()) {
      cnf.getCommonViewer().setInput(cnf.getCommonViewer().getInput());
    }
    switch (RequalityLCNF.getReferenceDirection()) {
      case 1:
        {
          oldImage = ih.getImage(d,"icons/downarrow.png");
          ti.setImage(oldImage);
        }
        break;
      case 2:
        {
          oldImage = ih.getImage(d,"icons/uparrow.png");
          ti.setImage(oldImage);
        }
        break;
      case 3:
        {
          oldImage = ih.getImage(d,"icons/biarrow.png");
          ti.setImage(oldImage);
        }
        break;
    }
    ti.addDisposeListener(new DisposeListener() {
      
      @Override
      public void widgetDisposed(DisposeEvent e) {
        ih.dispose();
      }
    });
    return true;
  }
  @Override
  public void dispose() {
    ih.dispose();
    if(oldImage!=null)
      oldImage.dispose();
    super.dispose();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
