package com.unitesk.requality.vscode.ui.workbench.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IAggregateWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.core.log.RLogger;

/**
 * {@code org.eclipse.ui.internal.AggregateWorkingSet}: the union of its component working sets, kept up to date when a
 * component changes or is removed.
 */
public class AggregateWorkingSet extends AbstractWorkingSet implements IAggregateWorkingSet, IPropertyChangeListener {
  private IWorkingSet[] components;

  /** Prevents stack overflow on cyclic element inclusions. */
  private boolean inElementConstruction = false;

  public AggregateWorkingSet(String name, String label, IWorkingSet[] components) {
    super(name, label);
    IWorkingSet[] componentCopy = new IWorkingSet[components.length];
    System.arraycopy(components, 0, componentCopy, 0, components.length);
    internalSetComponents(componentCopy);
    constructElements(false);
  }

  public AggregateWorkingSet(String name, String label, IMemento memento) {
    super(name, label);
    workingSetMemento = memento;
    if (workingSetMemento != null) {
      String uniqueId = workingSetMemento.getString("id");
      if (uniqueId != null) {
        setUniqueId(uniqueId);
      }
    }
  }

  public void setComponents(IWorkingSet[] components) {
    internalSetComponents(components);
    constructElements(true);
  }

  private void internalSetComponents(IWorkingSet[] components) {
    this.components = components;
  }

  /**
   * Takes the elements from all component working sets and sets them to be the elements of this working set. Any
   * duplicates are trimmed.
   */
  private void constructElements(boolean fireEvent) {
    if (inElementConstruction) {
      String msg = "Cyclic dependency in aggregate working set " + getName();
      RLogger.logInfo(msg);
      throw new IllegalStateException(msg);
    }
    inElementConstruction = true;
    try {
      // use *linked* set to maintain predictable elements order
      Set<IAdaptable> elements = new LinkedHashSet<>();
      IWorkingSet[] localComponents = getComponentsInternal();
      for (int i = 0; i < localComponents.length; i++) {
        IWorkingSet workingSet = localComponents[i];
        try {
          IAdaptable[] componentElements = workingSet.getElements();
          elements.addAll(Arrays.asList(componentElements));
        } catch (IllegalStateException e) { // an invalid component; remove it
          IWorkingSet[] tmp = new IWorkingSet[components.length - 1];
          if (i > 0) {
            System.arraycopy(components, 0, tmp, 0, i);
          }
          if (components.length - i - 1 > 0) {
            System.arraycopy(components, i + 1, tmp, i, components.length - i - 1);
          }
          components = tmp;
          workingSetMemento = null; // toss cached info
          fireWorkingSetChanged(IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE, null);
          continue;
        }
      }
      internalSetElements(elements.toArray(new IAdaptable[elements.size()]));
      if (fireEvent) {
        fireWorkingSetChanged(IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE, null);
      }
    } finally {
      inElementConstruction = false;
    }
  }

  @Override
  public String getId() {
    return null;
  }

  /**
   * The generic working set icon ({@code IWorkbenchGraphicConstants.IMG_OBJ_WORKING_SETS}, {@code workingsets.png} of
   * org.eclipse.ui, packaged in {@code icons/shared}).
   */
  @Override
  public ImageDescriptor getImageDescriptor() {
    return ImageDescriptor.fromResource("icons/shared/workingsets.png");
  }

  /** A no-op for aggregates - their contents should be derived. */
  @Override
  public void setElements(IAdaptable[] elements) {
  }

  @Override
  public void setId(String id) {
  }

  /** Aggregates are not editable. */
  @Override
  public boolean isEditable() {
    return false;
  }

  /** Aggregates should not generally be visible in the UI. */
  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public void saveState(IMemento memento) {
    if (workingSetMemento != null) {
      // just re-save the previous memento if the working set has not been restored
      memento.putMemento(workingSetMemento);
    } else {
      memento.putString("name", getName());
      memento.putString("label", getLabel());
      memento.putString("id", getUniqueId());
      memento.putString(AbstractWorkingSet.TAG_AGGREGATE, Boolean.TRUE.toString());
      for (IWorkingSet workingSet : getComponentsInternal()) {
        memento.createChild("workingSet", workingSet.getName());
      }
    }
  }

  @Override
  public void connect(IWorkingSetManager manager) {
    manager.addPropertyChangeListener(this);
    super.connect(manager);
  }

  @Override
  public void disconnect() {
    IWorkingSetManager connectedManager = getManager();
    if (connectedManager != null) {
      connectedManager.removePropertyChangeListener(this);
    }
    super.disconnect();
  }

  /** The component working sets. */
  @Override
  public IWorkingSet[] getComponents() {
    IWorkingSet[] localComponents = getComponentsInternal();
    IWorkingSet[] copiedArray = new IWorkingSet[localComponents.length];
    System.arraycopy(localComponents, 0, copiedArray, 0, localComponents.length);
    return copiedArray;
  }

  private IWorkingSet[] getComponentsInternal() {
    if (components == null) {
      restoreWorkingSet();
      workingSetMemento = null;
    }
    return components;
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    String property = event.getProperty();
    if (property.equals(IWorkingSetManager.CHANGE_WORKING_SET_REMOVE)) {
      IWorkingSet[] localComponents = getComponentsInternal();
      for (int i = 0; i < localComponents.length; i++) {
        IWorkingSet set = localComponents[i];
        if (set.equals(event.getOldValue())) {
          ArrayList<IWorkingSet> kept = new ArrayList<>(Arrays.asList(localComponents));
          kept.remove(i);
          setComponents(kept.toArray(new IWorkingSet[0]));
        }
      }
    } else if (property.equals(IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE)) {
      IWorkingSet[] localComponents = getComponentsInternal();
      for (IWorkingSet set : localComponents) {
        if (set.equals(event.getNewValue())) {
          constructElements(true);
          break;
        }
      }
    }
  }

  @Override
  void restoreWorkingSet() {
    IWorkingSetManager manager = getManager();
    if (manager == null) {
      throw new IllegalStateException();
    }
    IMemento[] workingSetReferences = workingSetMemento.getChildren("workingSet");
    ArrayList<IWorkingSet> list = new ArrayList<>(workingSetReferences.length);
    for (IMemento memento : workingSetReferences) {
      String setId = memento.getID();
      IWorkingSet set = manager.getWorkingSet(setId);
      if (set != null) {
        list.add(set);
      }
    }
    internalSetComponents(list.toArray(new IWorkingSet[list.size()]));
    constructElements(false);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object instanceof AggregateWorkingSet workingSet) {
      return Objects.equals(workingSet.getName(), getName())
          && Arrays.equals(workingSet.getComponentsInternal(), getComponentsInternal());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getName().hashCode() & Arrays.hashCode(getComponentsInternal());
  }

  @Override
  public boolean isSelfUpdating() {
    IWorkingSet[] localComponents = getComponentsInternal();
    if (localComponents == null || localComponents.length == 0) {
      return false;
    }
    for (IWorkingSet localComponent : localComponents) {
      if (!localComponent.isSelfUpdating()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isAggregateWorkingSet() {
    return true;
  }

  @Override
  public IAdaptable[] adaptElements(IAdaptable[] objects) {
    return new IAdaptable[0];
  }

  @Override
  public String toString() {
    return "AWS [name=" + getName() + ", components=" + Arrays.toString(getComponentsInternal()) + "]";
  }
}
