package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.jface.viewers.CellEditor}. The editing control is rendered inline by the webview; the editor
 * keeps the value and its validation like JFace.
 */
public abstract class CellEditor {
  private ICellEditorValidator validator;
  private boolean valid = true;
  private String errorMessage;
  private int style = SWT.NONE;
  private final List<ICellEditorListener> listeners = new CopyOnWriteArrayList<>();
  private boolean active;

  protected CellEditor() {
  }

  protected CellEditor(Composite parent) {
    this(parent, SWT.NONE);
  }

  protected CellEditor(Composite parent, int style) {
    this.style = style;
    create(parent);
  }

  private Control control;

  /** {@code CellEditor.create(Composite)}: creates the editor's control (hidden until the cell is edited). */
  public void create(Composite parent) {
    if (parent != null && control == null)
      control = createControl(parent);
  }

  /** {@code CellEditor.createControl}: the control receiving the inline editor's events. */
  protected Control createControl(Composite parent) {
    return new CellEditorControl(parent, style, this);
  }

  /** The text typed in the inline editor so far (with its key events). */
  void valueFromClient(com.google.gson.JsonElement value) {
    Object v = fromClient(value);
    if (v != null) {
      doSetValue(v);
      markDirty();
    }
  }

  private boolean dirty;

  public boolean isDirty() {
    return dirty;
  }

  protected void markDirty() {
    dirty = true;
  }

  public int getStyle() {
    return style;
  }

  public void setStyle(int style) {
    this.style = style;
  }

  public Control getControl() {
    return control;
  }

  protected abstract Object doGetValue();

  protected abstract void doSetValue(Object value);

  /** Description of the inline editor for the renderer. */
  abstract JsonObject clientEditor();

  /** Value typed in the renderer. */
  abstract Object fromClient(JsonElement value);

  public final Object getValue() {
    return doGetValue();
  }

  public final void setValue(Object value) {
    dirty = false;
    valid = isCorrect(value);
    doSetValue(value);
  }

  public void setValidator(ICellEditorValidator v) {
    validator = v;
  }

  public ICellEditorValidator getValidator() {
    return validator;
  }

  protected boolean isCorrect(Object value) {
    errorMessage = validator == null ? null : validator.isValid(value);
    return errorMessage == null || errorMessage.isEmpty();
  }

  public boolean isValueValid() {
    return valid;
  }

  protected void setValueValid(boolean v) {
    valid = v;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  protected void setErrorMessage(String m) {
    errorMessage = m;
  }

  public void addListener(ICellEditorListener l) {
    listeners.add(l);
  }

  public void removeListener(ICellEditorListener l) {
    listeners.remove(l);
  }

  protected void fireApplyEditorValue() {
    for (ICellEditorListener l : listeners)
      l.applyEditorValue();
  }

  protected void fireCancelEditor() {
    for (ICellEditorListener l : listeners)
      l.cancelEditor();
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }

  public boolean isActivated() {
    return active;
  }

  public void dispose() {
    if (control != null && !control.isDisposed())
      control.dispose();
    control = null;
  }

  public void setFocus() {
  }

  public LayoutData getLayoutData() {
    return new LayoutData();
  }

  /** {@code CellEditor.LayoutData}. */
  public static class LayoutData {
    public int horizontalAlignment = SWT.LEFT;
    public boolean grabHorizontal = true;
    public int minimumWidth = 50;
    public int minimumHeight = SWT.DEFAULT;
    public int verticalAlignment = SWT.CENTER;
  }
}
