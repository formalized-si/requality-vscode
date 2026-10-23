package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;

/** An {@link IDecoration} that records what lightweight decorators add (used to build explorer labels and icons). */
public class Decoration implements IDecoration {
  /** An overlay image and its quadrant. */
  public static final class Overlay {
    public final ImageDescriptor image;
    public final int quadrant;

    Overlay(ImageDescriptor image, int quadrant) {
      this.image = image;
      this.quadrant = quadrant;
    }
  }

  private final StringBuilder prefix = new StringBuilder();
  private final StringBuilder suffix = new StringBuilder();
  private final List<Overlay> overlays = new ArrayList<>();
  private int defaultQuadrant = BOTTOM_RIGHT;
  private Color foreground;
  private Color background;
  private Font font;

  @Override
  public void addPrefix(String p) {
    if (p != null)
      prefix.insert(0, p);
  }

  @Override
  public void addSuffix(String s) {
    if (s != null)
      suffix.append(s);
  }

  /** The quadrant of {@link #addOverlay(ImageDescriptor)}: the location of the decorator being applied. */
  public void setDefaultQuadrant(int quadrant) {
    defaultQuadrant = quadrant;
  }

  @Override
  public void addOverlay(ImageDescriptor overlay) {
    addOverlay(overlay, defaultQuadrant);
  }

  @Override
  public void addOverlay(ImageDescriptor overlay, int quadrant) {
    if (overlay == null)
      return;
    // Eclipse keeps the first overlay per quadrant.
    for (Overlay o : overlays)
      if (o.quadrant == quadrant)
        return;
    overlays.add(new Overlay(overlay, quadrant));
  }

  @Override
  public void setForegroundColor(Color color) {
    foreground = color;
  }

  @Override
  public void setBackgroundColor(Color color) {
    background = color;
  }

  @Override
  public void setFont(Font f) {
    font = f;
  }

  @Override
  public IDecorationContext getDecorationContext() {
    return new IDecorationContext() {
      @Override
      public Object getProperty(String property) {
        return null;
      }

      @Override
      public String[] getProperties() {
        return new String[0];
      }
    };
  }

  public String getPrefix() {
    return prefix.toString();
  }

  public String getSuffix() {
    return suffix.toString();
  }

  public List<Overlay> getOverlays() {
    return overlays;
  }

  public Color getForegroundColor() {
    return foreground;
  }

  public Color getBackgroundColor() {
    return background;
  }

  public Font getFont() {
    return font;
  }
}
