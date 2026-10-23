package com.unitesk.requality.eclipse.ui;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.eclipse.Activator;

public class ImageContainer {

  private Map<String, Map<Display, Image>> mapIm = new HashMap<>();
  public Image getImage(Display d, String string) {
    Map<Display, Image> di = mapIm.get(string);
    if(di == null) {
      di = new HashMap<>();
      mapIm.put(string, di);
    }
    Image im = di.get(d);
    if(im == null) {
      ImageDescriptor id = Activator.getImageDescriptor(string);
      if(id == null) {
        id = Activator.getImageDescriptor("icons/unitesk.png");
        RLogger.logError("Image not found! "+string);
      }
      im = id.createImage();
      di.put(d, im);
    }
    return im;
  }
  public void dispose() {
    for(Map<Display, Image> im:mapIm.values())
      for(Image i:im.values())
        i.dispose();
    mapIm.clear();
  }

}
