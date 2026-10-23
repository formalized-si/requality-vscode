package com.unitesk.requality.vscode.ui.workbench.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IPersistableElement;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;

/**
 * {@code org.eclipse.ui.internal.AbstractWorkingSet}: the common state of the working sets (name, label, unique id,
 * elements restored lazily from the memento) and the change notification to the manager.
 */
public abstract class AbstractWorkingSet implements IAdaptable, IWorkingSet, Cloneable {
  /** The id of the factory that re-creates the working sets. */
  protected static final String FACTORY_ID = "org.eclipse.ui.internal.WorkingSetFactory";
  /** The tag of the aggregate working sets. */
  static final String TAG_AGGREGATE = "aggregate";

  private String name;
  protected List<IAdaptable> elements;
  private IWorkingSetManager manager;
  protected IMemento workingSetMemento;
  private String label;
  // Workspace wide unique id for workingsets
  private String uniqueId;
  private static int counter;
  private boolean labelBoundToName;

  public AbstractWorkingSet(String name, String label) {
    Assert.isNotNull(name, "name must not be null");
    this.name = name;
    this.label = label;
    labelBoundToName = Objects.equals(name, label);
    uniqueId = System.currentTimeMillis() + "_" + counter++;
  }

  @Override
  public <T> T getAdapter(Class<T> adapter) {
    if (adapter == IWorkingSet.class || adapter == IPersistableElement.class) {
      return adapter.cast(this);
    }
    // VSCODE: the adapter manager of the server falls back to getAdapter of the element, so no other adapters here.
    return null;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void setName(String newName) {
    Assert.isNotNull(newName, "Working set name must not be null");
    if (manager != null) {
      IWorkingSet wSet = manager.getWorkingSet(newName);
      if (wSet != this) {
        Assert.isTrue(wSet == null, "working set with same name already registered");
      }
    }
    AbstractWorkingSet oldWorkingSet = clone();
    name = newName;
    fireWorkingSetChanged(IWorkingSetManager.CHANGE_WORKING_SET_NAME_CHANGE, oldWorkingSet);
    if (labelBoundToName) {
      setLabel(newName);
    }
  }

  public void connect(IWorkingSetManager manager) {
    Assert.isTrue(this.manager == null, "A working set can only be connected to one manager");
    this.manager = manager;
  }

  public void disconnect() {
    this.manager = null;
  }

  protected void fireWorkingSetChanged(String property, Object oldValue) {
    if (manager instanceof AbstractWorkingSetManager)
      ((AbstractWorkingSetManager) manager).workingSetChanged(this, property, oldValue);
  }

  protected void internalSetElements(IAdaptable[] newElements) {
    Assert.isNotNull(newElements, "Working set elements array must not be null");
    elements = Arrays.asList(newElements);
  }

  @Override
  public IAdaptable[] getElements() {
    List<IAdaptable> list = getElementsArray();
    return list.toArray(new IAdaptable[list.size()]);
  }

  protected List<IAdaptable> getElementsArray() {
    if (elements == null) {
      restoreWorkingSet();
      workingSetMemento = null;
    }
    return elements;
  }

  abstract void restoreWorkingSet();

  protected IWorkingSetManager getManager() {
    return manager;
  }

  @Override
  public String getFactoryId() {
    return FACTORY_ID;
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Override
  public void setLabel(String label) {
    AbstractWorkingSet oldWorkingSet = clone();
    this.label = label == null ? getName() : label;
    labelBoundToName = Objects.equals(label, name);
    fireWorkingSetChanged(IWorkingSetManager.CHANGE_WORKING_SET_LABEL_CHANGE, oldWorkingSet);
  }

  @Override
  public boolean isEmpty() {
    return getElementsArray().isEmpty();
  }

  @Override
  public final com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor getImage() {
    return getImageDescriptor();
  }

  /* package */ String getUniqueId() {
    return uniqueId;
  }

  /* package */ void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  @Override
  protected AbstractWorkingSet clone() {
    try {
      AbstractWorkingSet clone = (AbstractWorkingSet) super.clone();
      clone.disconnect();
      return clone;
    } catch (CloneNotSupportedException e) {
      // ignore
    }
    return null;
  }
}
