package com.unitesk.requality.vscode.ui.compare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.core.Icons;

/**
 * {@code org.eclipse.compare.CompareConfiguration}: the labels, images, editability and properties of a comparison,
 * and the images of the kinds of differences (the base image of an element with the overlay of its kind).
 */
public class CompareConfiguration {
  public static final String IGNORE_WHITESPACE = "IGNORE_WHITESPACE";
  public static final String MIRRORED = "MIRRORED";
  public static final String USE_OUTLINE_VIEW = "USE_OUTLINE_VIEW";

  /** The overlays of the kinds (org.eclipse.compare icons/full/ovr16), not mirrored and mirrored (index + 16). */
  private static final String[] fgImages = new String[32];
  static {
    fgImages[Differencer.ADDITION] = "add_ov";
    fgImages[Differencer.LEFT + Differencer.ADDITION] = "r_inadd_ov";
    fgImages[Differencer.RIGHT + Differencer.ADDITION] = "r_outadd_ov";
    fgImages[Differencer.DELETION] = "del_ov";
    fgImages[Differencer.LEFT + Differencer.DELETION] = "r_indel_ov";
    fgImages[Differencer.RIGHT + Differencer.DELETION] = "r_outdel_ov";
    fgImages[Differencer.LEFT + Differencer.CHANGE] = "r_inchg_ov";
    fgImages[Differencer.RIGHT + Differencer.CHANGE] = "r_outchg_ov";
    fgImages[Differencer.CONFLICTING + Differencer.ADDITION] = "confadd_ov";
    fgImages[Differencer.CONFLICTING + Differencer.DELETION] = "confdel_ov";
    fgImages[Differencer.CONFLICTING + Differencer.CHANGE] = "confchg_ov";
    fgImages[16 + Differencer.ADDITION] = "add_ov";
    fgImages[16 + Differencer.LEFT + Differencer.ADDITION] = "inadd_ov";
    fgImages[16 + Differencer.RIGHT + Differencer.ADDITION] = "outadd_ov";
    fgImages[16 + Differencer.DELETION] = "del_ov";
    fgImages[16 + Differencer.LEFT + Differencer.DELETION] = "indel_ov";
    fgImages[16 + Differencer.RIGHT + Differencer.DELETION] = "outdel_ov";
    fgImages[16 + Differencer.LEFT + Differencer.CHANGE] = "inchg_ov";
    fgImages[16 + Differencer.RIGHT + Differencer.CHANGE] = "outchg_ov";
    fgImages[16 + Differencer.CONFLICTING + Differencer.ADDITION] = "confadd_ov";
    fgImages[16 + Differencer.CONFLICTING + Differencer.DELETION] = "confdel_ov";
    fgImages[16 + Differencer.CONFLICTING + Differencer.CHANGE] = "confchg_ov";
  }

  /** {@code ICompareUIConstants.COMPARE_IMAGE_WIDTH}. */
  static final int COMPARE_IMAGE_WIDTH = 22;

  private String leftLabel;
  private String rightLabel;
  private String ancestorLabel;
  private Image leftImage;
  private Image rightImage;
  private Image ancestorImage;
  private boolean leftEditable = true;
  private boolean rightEditable = true;
  private final Map<String, Object> properties = new HashMap<>();
  private final List<IPropertyChangeListener> listeners = new ArrayList<>();
  private Object container;
  private boolean disposed;

  public CompareConfiguration() {
  }

  public String getLeftLabel(Object input) {
    return leftLabel;
  }

  public void setLeftLabel(String label) {
    leftLabel = label;
  }

  public String getRightLabel(Object input) {
    return rightLabel;
  }

  public void setRightLabel(String label) {
    rightLabel = label;
  }

  public String getAncestorLabel(Object input) {
    return ancestorLabel;
  }

  public void setAncestorLabel(String label) {
    ancestorLabel = label;
  }

  public Image getLeftImage(Object input) {
    return leftImage;
  }

  public void setLeftImage(Image image) {
    leftImage = image;
  }

  public Image getRightImage(Object input) {
    return rightImage;
  }

  public void setRightImage(Image image) {
    rightImage = image;
  }

  public Image getAncestorImage(Object input) {
    return ancestorImage;
  }

  public void setAncestorImage(Image image) {
    ancestorImage = image;
  }

  public boolean isLeftEditable() {
    return leftEditable;
  }

  public void setLeftEditable(boolean editable) {
    leftEditable = editable;
  }

  public boolean isRightEditable() {
    return rightEditable;
  }

  public void setRightEditable(boolean editable) {
    rightEditable = editable;
  }

  /** Whether the left and right sides are shown swapped ({@link #MIRRORED}). */
  public boolean isMirrored() {
    Object o = getProperty(MIRRORED);
    return o instanceof Boolean && (Boolean) o;
  }

  public void setProperty(String key, Object newValue) {
    Object oldValue = properties.get(key);
    properties.put(key, newValue);
    if (oldValue == null || !oldValue.equals(newValue))
      fireChange(key, oldValue, newValue);
  }

  public Object getProperty(String key) {
    return properties.get(key);
  }

  public synchronized void addPropertyChangeListener(IPropertyChangeListener listener) {
    if (!listeners.contains(listener))
      listeners.add(listener);
  }

  public synchronized void removePropertyChangeListener(IPropertyChangeListener listener) {
    listeners.remove(listener);
  }

  private void fireChange(String key, Object oldValue, Object newValue) {
    List<IPropertyChangeListener> ls;
    synchronized (this) {
      ls = new ArrayList<>(listeners);
    }
    PropertyChangeEvent event = new PropertyChangeEvent(this, key, oldValue, newValue);
    for (IPropertyChangeListener l : ls)
      l.propertyChange(event);
  }

  /** The overlay image of the kind of difference, or null. */
  public Image getImage(int kind) {
    if (disposed)
      return null;
    String overlay = overlay(kind);
    return overlay == null ? null : new Image(null, Icons.resource(overlay).getAbsolutePath());
  }

  /** The base image with the overlay of the kind of difference on its right (DiffImageDescriptor). */
  public Image getImage(Image base, int kind) {
    if (disposed)
      return null;
    String path = Icons.composeBeside(base == null ? null : base.getPath(), overlay(kind), COMPARE_IMAGE_WIDTH, false);
    return path == null ? null : new Image(null, path);
  }

  private String overlay(int kind) {
    String name = fgImages[(kind & 15) + (isMirrored() ? 16 : 0)];
    return name == null ? null : "icons/compare/ovr16/" + name + ".png";
  }

  /** {@code CompareConfiguration.getContainer}: the compare container (the compare editor input) of the comparison. */
  public Object getContainer() {
    return container;
  }

  public void setContainer(Object container) {
    this.container = container;
  }

  public void dispose() {
    disposed = true;
  }
}
