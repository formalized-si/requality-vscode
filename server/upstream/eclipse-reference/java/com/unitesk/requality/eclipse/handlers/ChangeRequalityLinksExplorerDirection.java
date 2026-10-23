package com.unitesk.requality.eclipse.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ToolItem;

import com.unitesk.requality.eclipse.ui.ImageContainer;
import com.unitesk.requality.eclipse.ui.RequalityLCNF;

public class ChangeRequalityLinksExplorerDirection extends AbstractHandler {
  private ImageContainer ih = new ImageContainer();
  private Image oldImage = null;
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    Display d = Display.getCurrent();
    RequalityLCNF.toggleReferenceDirection();
    org.eclipse.swt.widgets.Event e = (org.eclipse.swt.widgets.Event) (event.getTrigger());
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
