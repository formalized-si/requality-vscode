package com.unitesk.requality.vscode.ui.workbench.internal;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.ISafeRunnable;
import com.unitesk.requality.vscode.ui.runtime.SafeRunner;
import com.unitesk.requality.vscode.ui.workbench.IElementFactory;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IPersistableElement;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.core.log.RLogger;

/**
 * {@code org.eclipse.ui.internal.WorkingSet}: a working set of elements; the elements are persisted with their factory
 * ids and restored from the memento when first needed.
 */
public class WorkingSet extends AbstractWorkingSet {
  private static final String DEFAULT_ID = WorkingSetRegistry.DEFAULT_PAGE_ID;

  private String editPageId;

  public WorkingSet(String name, String label, IAdaptable[] elements) {
    super(name, label);
    internalSetElements(elements);
  }

  protected WorkingSet(String name, String label, IMemento memento) {
    super(name, label);
    workingSetMemento = memento;
    if (workingSetMemento != null) {
      String uniqueId = workingSetMemento.getString("id");
      if (uniqueId != null) {
        setUniqueId(uniqueId);
      }
    }
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object instanceof WorkingSet workingSet) {
      return Objects.equals(workingSet.getName(), getName()) && Objects.equals(workingSet.getId(), getId())
          && Objects.equals(workingSet.getElementsArray(), getElementsArray());
    }
    return false;
  }

  @Override
  public String toString() {
    return "WS [name=" + getName() + ", elements=" + getElementsArray() + ", id=" + getId() + "]";
  }

  @Override
  public boolean isEditable() {
    WorkingSetDescriptor descriptor = getDescriptor(null);
    return descriptor != null && descriptor.isEditable();
  }

  @Override
  public String getId() {
    return editPageId;
  }

  @Override
  public ImageDescriptor getImageDescriptor() {
    WorkingSetDescriptor descriptor = getDescriptor(DEFAULT_ID);
    if (descriptor == null) {
      return null;
    }
    return descriptor.getIcon();
  }

  @Override
  public int hashCode() {
    int hashCode = getName().hashCode();
    if (editPageId != null) {
      hashCode &= editPageId.hashCode();
    }
    return hashCode;
  }

  @Override
  void restoreWorkingSet() {
    internalSetElements(restoreFromMemento());
  }

  private IAdaptable[] restoreFromMemento() {
    IMemento[] itemMementos = workingSetMemento.getChildren("item");
    Set<IAdaptable> items = new LinkedHashSet<>();
    for (final IMemento itemMemento : itemMementos) {
      final String factoryID = itemMemento.getString("factoryID");
      if (factoryID == null) {
        RLogger.logInfo("Unable to restore working set item - no factory ID.");
        continue;
      }
      final IElementFactory factory = ElementFactories.get(factoryID);
      if (factory == null) {
        RLogger.logInfo("Unable to restore working set item - cannot instantiate factory: " + factoryID);
        continue;
      }
      SafeRunner.run(new ISafeRunnable() {
        @Override
        public void handleException(Throwable exception) {
          // logged by the runner
        }

        @Override
        public void run() throws Exception {
          IAdaptable item = factory.createElement(itemMemento);
          if (item != null) {
            items.add(item);
          }
        }
      });
    }
    return items.toArray(new IAdaptable[items.size()]);
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
      memento.putString("editPageId", editPageId);
      for (IAdaptable adaptable : elements) {
        final IPersistableElement persistable = Adapters.adapt(adaptable, IPersistableElement.class);
        if (persistable != null) {
          final IMemento itemMemento = memento.createChild("item");
          itemMemento.putString("factoryID", persistable.getFactoryId());
          SafeRunner.run(new ISafeRunnable() {
            @Override
            public void handleException(Throwable exception) {
              // logged by the runner
            }

            @Override
            public void run() throws Exception {
              persistable.saveState(itemMemento);
            }
          });
        }
      }
    }
  }

  @Override
  public void setElements(IAdaptable[] newElements) {
    AbstractWorkingSet oldWorkingSet = clone();
    internalSetElements(newElements);
    fireWorkingSetChanged(IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE, oldWorkingSet);
  }

  @Override
  public void setId(String pageId) {
    editPageId = pageId;
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  public boolean isSelfUpdating() {
    WorkingSetDescriptor descriptor = getDescriptor(null);
    return descriptor != null && descriptor.getUpdaterClassName() != null;
  }

  @Override
  public boolean isAggregateWorkingSet() {
    return false;
  }

  private WorkingSetDescriptor getDescriptor(String defaultId) {
    String id = getId();
    if (id == null) {
      id = defaultId;
    }
    if (id == null) {
      return null;
    }
    return WorkingSetRegistry.get().getWorkingSetDescriptor(id);
  }

  /** The elements as the element adapter of the working set type keeps them (the elements themselves without one). */
  @Override
  public IAdaptable[] adaptElements(IAdaptable[] objects) {
    AbstractWorkingSetManager manager = getAbstractManager();
    WorkingSetDescriptor descriptor = getDescriptor(null);
    if (manager == null || descriptor == null || descriptor.getElementAdapterClassName() == null) {
      return objects;
    }
    return manager.getElementAdapter(descriptor).adaptElements(this, objects);
  }

  private AbstractWorkingSetManager getAbstractManager() {
    IWorkingSetManager manager = getManager();
    return manager instanceof AbstractWorkingSetManager ? (AbstractWorkingSetManager) manager : null;
  }

}
