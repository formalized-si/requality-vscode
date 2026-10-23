package com.unitesk.requality.vscode.ui.workbench;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** {@code org.eclipse.ui.part.WorkbenchPart}. */
public abstract class WorkbenchPart implements IWorkbenchPart {
  private final List<IPropertyListener> listeners = new CopyOnWriteArrayList<>();
  private IWorkbenchPartSite site;
  private String partName = "";
  private String contentDescription = "";
  private String toolTip = "";
  private final Map<String, String> partProperties = new ConcurrentHashMap<>();
  private Image titleImage;

  @Override
  public String getId() {
    return site == null ? getClass().getName() : site.getId();
  }

  protected void setSite(IWorkbenchPartSite site) {
    this.site = site;
  }

  @Override
  public IWorkbenchPartSite getSite() {
    return site;
  }

  @Override
  public String getTitle() {
    return partName;
  }

  public String getPartName() {
    return partName;
  }

  protected void setPartName(String partName) {
    this.partName = partName == null ? "" : partName;
    Shell s = site == null ? null : site.getShell();
    if (s != null && !s.isDisposed())
      s.setText(this.partName);
    firePropertyChange(PROP_TITLE);
  }

  protected void setTitle(String title) {
    setPartName(title);
  }

  public String getContentDescription() {
    return contentDescription;
  }

  protected void setContentDescription(String description) {
    contentDescription = description == null ? "" : description;
    firePropertyChange(PROP_TITLE);
  }

  @Override
  public String getTitleToolTip() {
    return toolTip;
  }

  protected void setTitleToolTip(String toolTip) {
    this.toolTip = toolTip == null ? "" : toolTip;
  }

  /** {@code org.eclipse.ui.part.WorkbenchPart#setPartProperty(String, String)}: a string property of this part. */
  protected void setPartProperty(String key, String value) {
    if (value == null)
      partProperties.remove(key);
    else
      partProperties.put(key, value);
  }

  /** {@code org.eclipse.ui.part.WorkbenchPart#getPartProperty(String)}. */
  public String getPartProperty(String key) {
    return partProperties.get(key);
  }

  @Override
  public Image getTitleImage() {
    return titleImage;
  }

  protected void setTitleImage(Image image) {
    titleImage = image;
    firePropertyChange(PROP_TITLE);
  }

  @Override
  public void addPropertyListener(IPropertyListener l) {
    listeners.add(l);
  }

  @Override
  public void removePropertyListener(IPropertyListener l) {
    listeners.remove(l);
  }

  protected void firePropertyChange(int propertyId) {
    for (IPropertyListener l : listeners)
      l.propertyChanged(this, propertyId);
  }

  @Override
  public void dispose() {
    listeners.clear();
  }
}
