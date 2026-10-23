package com.unitesk.requality.vscode.ui.jface.action;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.jface.action.Action}. */
public abstract class Action implements IAction {
  private final List<IPropertyChangeListener> listeners = new CopyOnWriteArrayList<>();
  private String text;
  private String id;
  private String toolTip;
  private String description;
  private boolean enabled = true;
  private boolean checked;
  private int style = AS_PUSH_BUTTON;
  private ImageDescriptor image;
  private ImageDescriptor disabledImage;
  private ImageDescriptor hoverImage;
  private String definitionId;
  private int accelerator;
  private IMenuCreator menuCreator;

  protected Action() {
  }

  protected Action(String text) {
    this.text = text;
  }

  protected Action(String text, int style) {
    this.text = text;
    this.style = style;
  }

  protected Action(String text, ImageDescriptor image) {
    this.text = text;
    this.image = image;
  }

  protected void firePropertyChange(String property, Object oldValue, Object newValue) {
    if (listeners.isEmpty() || oldValue == newValue || oldValue != null && oldValue.equals(newValue))
      return;
    PropertyChangeEvent e = new PropertyChangeEvent(this, property, oldValue, newValue);
    for (IPropertyChangeListener l : listeners)
      l.propertyChange(e);
  }

  @Override
  public void addPropertyChangeListener(IPropertyChangeListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removePropertyChangeListener(IPropertyChangeListener listener) {
    listeners.remove(listener);
  }

  @Override
  public String getText() {
    return text;
  }

  @Override
  public void setText(String text) {
    String old = this.text;
    this.text = text;
    firePropertyChange(TEXT, old, text);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getToolTipText() {
    return toolTip;
  }

  @Override
  public void setToolTipText(String t) {
    String old = toolTip;
    toolTip = t;
    firePropertyChange(TOOL_TIP_TEXT, old, t);
  }

  @Override
  public String getDescription() {
    return description != null ? description : toolTip;
  }

  @Override
  public void setDescription(String text) {
    String old = description;
    description = text;
    firePropertyChange(DESCRIPTION, old, text);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void setEnabled(boolean b) {
    boolean old = enabled;
    enabled = b;
    firePropertyChange(ENABLED, old, b);
  }

  @Override
  public boolean isChecked() {
    return checked;
  }

  @Override
  public void setChecked(boolean b) {
    boolean old = checked;
    checked = b;
    firePropertyChange(CHECKED, old, b);
  }

  @Override
  public int getStyle() {
    return style;
  }

  @Override
  public ImageDescriptor getImageDescriptor() {
    return image;
  }

  @Override
  public void setImageDescriptor(ImageDescriptor d) {
    ImageDescriptor old = image;
    image = d;
    firePropertyChange(IMAGE, old, d);
  }

  @Override
  public ImageDescriptor getDisabledImageDescriptor() {
    return disabledImage;
  }

  @Override
  public void setDisabledImageDescriptor(ImageDescriptor d) {
    disabledImage = d;
  }

  @Override
  public ImageDescriptor getHoverImageDescriptor() {
    return hoverImage;
  }

  @Override
  public void setHoverImageDescriptor(ImageDescriptor d) {
    hoverImage = d;
  }

  @Override
  public String getActionDefinitionId() {
    return definitionId;
  }

  @Override
  public void setActionDefinitionId(String id) {
    definitionId = id;
  }

  @Override
  public int getAccelerator() {
    return accelerator;
  }

  @Override
  public void setAccelerator(int keycode) {
    accelerator = keycode;
  }

  @Override
  public IMenuCreator getMenuCreator() {
    return menuCreator;
  }

  @Override
  public void setMenuCreator(IMenuCreator creator) {
    menuCreator = creator;
    if (creator != null)
      style = AS_DROP_DOWN_MENU;
  }

  @Override
  public boolean isHandled() {
    return true;
  }

  @Override
  public void run() {
  }

  @Override
  public void runWithEvent(Event event) {
    run();
  }
}
