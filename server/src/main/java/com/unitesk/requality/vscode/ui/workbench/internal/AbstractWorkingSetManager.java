package com.unitesk.requality.vscode.ui.workbench.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.ISafeRunnable;
import com.unitesk.requality.vscode.ui.runtime.SafeRunner;
import com.unitesk.requality.vscode.ui.workbench.IElementFactory;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetElementAdapter;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetUpdater;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IPersistableElement;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.XMLMemento;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetEditWizard;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetNewWizard;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetPage;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetSelectionDialog;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkingSetEditWizard;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkingSetNewWizard;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkingSetSelectionDialog;
import com.unitesk.requality.core.log.RLogger;

/**
 * {@code org.eclipse.ui.internal.AbstractWorkingSetManager}: the working sets of the workbench, their most recently used
 * list, the property change notification and the persistence of the working sets to an XML memento.
 */
public abstract class AbstractWorkingSetManager implements IWorkingSetManager {
  /** The default size of the most recently used list (the workbench preference RECENTLY_USED_WORKINGSETS_SIZE). */
  static final int DEFAULT_RECENT_LENGTH = 4;

  private final SortedSet<AbstractWorkingSet> workingSets = new TreeSet<>(
      (o1, o2) -> o1.getUniqueId().compareTo(o2.getUniqueId()));
  private final List<IWorkingSet> recentWorkingSets = new ArrayList<>();
  private final List<IPropertyChangeListener> listeners = new CopyOnWriteArrayList<>();
  private int recentWorkingSetsLength = DEFAULT_RECENT_LENGTH;
  private final java.util.Map<String, IWorkingSetUpdater> updaters = new java.util.HashMap<>();
  private final java.util.Map<String, IWorkingSetElementAdapter> elementAdapters = new java.util.HashMap<>();

  /** The updater of a type without one: it manages nothing. */
  private static final IWorkingSetUpdater NULL_UPDATER = new IWorkingSetUpdater() {
    @Override
    public void add(IWorkingSet workingSet) {
    }

    @Override
    public boolean remove(IWorkingSet workingSet) {
      return true;
    }

    @Override
    public boolean contains(IWorkingSet workingSet) {
      return true;
    }

    @Override
    public void dispose() {
    }
  };

  /** The element adapter of a type without one: the elements are kept as they are. */
  private static final IWorkingSetElementAdapter IDENTITY_ADAPTER = new IWorkingSetElementAdapter() {
    @Override
    public IAdaptable[] adaptElements(IWorkingSet ws, IAdaptable[] elements) {
      return elements;
    }

    @Override
    public void dispose() {
    }
  };

  /** The order of the working sets in the lists: by label, then by name (WorkingSetComparator). */
  static final Comparator<IWorkingSet> COMPARATOR = (o1, o2) -> {
    java.text.Collator collator = java.text.Collator.getInstance();
    int result = collator.compare(o1.getLabel(), o2.getLabel());
    if (result == 0) {
      result = o1.getName().compareTo(o2.getName());
    }
    return result;
  };

  protected AbstractWorkingSetManager() {
  }

  @Override
  public void dispose() {
    listeners.clear();
    synchronized (updaters) {
      for (IWorkingSetUpdater updater : updaters.values()) {
        updater.dispose();
      }
    }
    for (IWorkingSetElementAdapter adapter : elementAdapters.values()) {
      adapter.dispose();
    }
  }

  /** The updater of the type, created on first use (the NULL updater when the type has none). */
  IWorkingSetUpdater getUpdater(WorkingSetDescriptor descriptor) {
    String updaterId = descriptor.getId();
    synchronized (updaters) {
      IWorkingSetUpdater updater = updaters.get(updaterId);
      if (updater == null) {
        updater = descriptor.createWorkingSetUpdater();
        boolean fireChange = true;
        if (updater == null) {
          updater = NULL_UPDATER;
          fireChange = false;
        }
        updaters.put(updaterId, updater);
        if (fireChange) {
          firePropertyChange(CHANGE_WORKING_SET_UPDATER_INSTALLED, null, updater);
        }
      }
      return updater;
    }
  }

  /** The element adapter of the type, created on first use (the identity adapter when the type has none). */
  IWorkingSetElementAdapter getElementAdapter(WorkingSetDescriptor descriptor) {
    IWorkingSetElementAdapter elementAdapter = elementAdapters.get(descriptor.getId());
    if (elementAdapter == null) {
      elementAdapter = descriptor.createWorkingSetElementAdapter();
      if (elementAdapter == null) {
        elementAdapter = IDENTITY_ADAPTER;
      } else {
        elementAdapters.put(descriptor.getId(), elementAdapter);
      }
    }
    return elementAdapter;
  }

  /** Adds the working set to the updater of its type (if the type has one). */
  private void addToUpdater(final IWorkingSet workingSet) {
    WorkingSetDescriptor descriptor = WorkingSetRegistry.get().getWorkingSetDescriptor(workingSet.getId());
    if (descriptor == null || descriptor.getUpdaterClassName() == null) {
      return;
    }
    IWorkingSetUpdater updater = getUpdater(descriptor);
    SafeRunner.run(new ISafeRunnable() {
      @Override
      public void handleException(Throwable exception) {
        // logged by the runner
      }

      @Override
      public void run() throws Exception {
        if (!updater.contains(workingSet)) {
          updater.add(workingSet);
        }
      }
    });
  }

  /** Removes the working set from the updater of its type. */
  private void removeFromUpdater(final IWorkingSet workingSet) {
    IWorkingSetUpdater updater = updaters.get(workingSet.getId());
    if (updater != null) {
      SafeRunner.run(new ISafeRunnable() {
        @Override
        public void handleException(Throwable exception) {
          // logged by the runner
        }

        @Override
        public void run() throws Exception {
          updater.remove(workingSet);
        }
      });
    }
  }

  // ---- working set creation

  @Override
  public IWorkingSet createWorkingSet(String name, IAdaptable[] elements) {
    return new WorkingSet(name, name, elements);
  }

  @Override
  public IWorkingSet createAggregateWorkingSet(String name, String label, IWorkingSet[] components) {
    return new AggregateWorkingSet(name, label, components);
  }

  @Override
  public IWorkingSet createWorkingSet(IMemento memento) {
    return restoreWorkingSet(memento);
  }

  // ---- working set management

  @Override
  public void addWorkingSet(IWorkingSet workingSet) {
    IWorkingSet wSet = getWorkingSet(workingSet.getName());
    if (wSet != null)
      throw new IllegalArgumentException("working set with same name already registered");
    internalAddWorkingSet(workingSet);
  }

  private void internalAddWorkingSet(IWorkingSet workingSet) {
    AbstractWorkingSet abstractWorkingSet = (AbstractWorkingSet) workingSet;
    workingSets.add(abstractWorkingSet);
    abstractWorkingSet.connect(this);
    addToUpdater(workingSet);
    firePropertyChange(CHANGE_WORKING_SET_ADD, null, workingSet);
  }

  protected boolean internalRemoveWorkingSet(IWorkingSet workingSet) {
    boolean workingSetRemoved = workingSets.remove(workingSet);
    boolean recentWorkingSetRemoved = recentWorkingSets.remove(workingSet);
    if (workingSetRemoved) {
      ((AbstractWorkingSet) workingSet).disconnect();
      removeFromUpdater(workingSet);
      firePropertyChange(CHANGE_WORKING_SET_REMOVE, workingSet, null);
    }
    return workingSetRemoved || recentWorkingSetRemoved;
  }

  @Override
  public IWorkingSet[] getWorkingSets() {
    SortedSet<IWorkingSet> visibleSubset = new TreeSet<>(COMPARATOR);
    for (IWorkingSet workingSet : workingSets) {
      if (workingSet.isVisible()) {
        visibleSubset.add(workingSet);
      }
    }
    return visibleSubset.toArray(new IWorkingSet[visibleSubset.size()]);
  }

  @Override
  public IWorkingSet[] getAllWorkingSets() {
    IWorkingSet[] sets = workingSets.toArray(new IWorkingSet[workingSets.size()]);
    Arrays.sort(sets, COMPARATOR);
    return sets;
  }

  @Override
  public IWorkingSet getWorkingSet(String name) {
    if (name == null || workingSets == null) {
      return null;
    }
    for (IWorkingSet workingSet : workingSets) {
      if (name.equals(workingSet.getName())) {
        return workingSet;
      }
    }
    return null;
  }

  // ---- recent working set management

  @Override
  public IWorkingSet[] getRecentWorkingSets() {
    return recentWorkingSets.toArray(new IWorkingSet[recentWorkingSets.size()]);
  }

  @Override
  public void addRecentWorkingSet(IWorkingSet workingSet) {
    internalAddRecentWorkingSet(workingSet);
    saveState();
  }

  /** Adds the working set to the top of the most recently used list (the invisible ones are not kept). */
  protected void internalAddRecentWorkingSet(IWorkingSet workingSet) {
    if (!workingSet.isVisible()) {
      return;
    }
    recentWorkingSets.remove(workingSet);
    recentWorkingSets.add(0, workingSet);
    sizeRecentWorkingSets();
  }

  /** Adds the element, adapted by each working set, to the working sets that accept it. */
  @Override
  public void addToWorkingSets(final IAdaptable element, IWorkingSet[] workingSets) {
    for (final IWorkingSet workingSet : workingSets) {
      SafeRunner.run(new ISafeRunnable() {
        @Override
        public void handleException(Throwable exception) {
          // logged by the runner
        }

        @Override
        public void run() throws Exception {
          IAdaptable[] adaptedNewElements = workingSet.adaptElements(new IAdaptable[] { element });
          if (adaptedNewElements.length == 1) {
            IAdaptable[] elements = workingSet.getElements();
            IAdaptable[] newElements = Arrays.copyOf(elements, elements.length + 1);
            newElements[newElements.length - 1] = adaptedNewElements[0];
            workingSet.setElements(newElements);
          }
        }
      });
    }
  }

  @Override
  public void setRecentWorkingSetsLength(int length) {
    if (length < 1 || length > 99) {
      throw new IllegalArgumentException("Invalid recent working sets length: " + length);
    }
    recentWorkingSetsLength = length;
    sizeRecentWorkingSets();
    saveState();
  }

  @Override
  public int getRecentWorkingSetsLength() {
    return recentWorkingSetsLength;
  }

  private void sizeRecentWorkingSets() {
    int maxLength = getRecentWorkingSetsLength();
    while (recentWorkingSets.size() > maxLength) {
      recentWorkingSets.remove(recentWorkingSets.size() - 1);
    }
  }

  // ---- property listeners

  @Override
  public void addPropertyChangeListener(IPropertyChangeListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removePropertyChangeListener(IPropertyChangeListener listener) {
    listeners.remove(listener);
  }

  /**
   * Notifies the property change listeners about a change to the list of working sets. VSCODE: the server has one UI
   * thread, so the listeners are notified synchronously.
   */
  protected void firePropertyChange(String changeId, Object oldValue, Object newValue) {
    if (listeners.isEmpty()) {
      return;
    }
    final PropertyChangeEvent event = new PropertyChangeEvent(this, changeId, oldValue, newValue);
    for (IPropertyChangeListener listener : listeners) {
      SafeRunner.run(new ISafeRunnable() {
        @Override
        public void handleException(Throwable exception) {
          // logged by the runner
        }

        @Override
        public void run() throws Exception {
          listener.propertyChange(event);
        }
      });
    }
  }

  /** Fires the change of the working set (called by the working set itself). */
  public void workingSetChanged(IWorkingSet changedWorkingSet, String propertyChangeId, Object oldValue) {
    firePropertyChange(propertyChangeId, oldValue, changedWorkingSet);
  }

  // ---- equals and hash code

  @Override
  public boolean equals(Object object) {
    if (object == null) {
      return false;
    }
    if (this == object) {
      return true;
    }
    if (!getClass().getName().equals(object.getClass().getName())) {
      return false;
    }
    AbstractWorkingSetManager other = (AbstractWorkingSetManager) object;
    return Objects.equals(other.workingSets, workingSets);
  }

  @Override
  public int hashCode() {
    return workingSets.hashCode();
  }

  // ---- Persistence

  /** Saves all persistable working sets (the aggregates after the others) in the memento. */
  public void saveWorkingSetState(IMemento memento) {
    // break the sets into aggregates and non aggregates. The aggregates should be saved after the non-aggregates
    // so that on restoration all necessary aggregate components can be found.
    List<IWorkingSet> standardSets = new ArrayList<>();
    List<IWorkingSet> aggregateSets = new ArrayList<>();
    for (AbstractWorkingSet set : workingSets) {
      if (set instanceof AggregateWorkingSet) {
        aggregateSets.add(set);
      } else {
        standardSets.add(set);
      }
    }
    saveWorkingSetState(memento, standardSets);
    saveWorkingSetState(memento, aggregateSets);
  }

  private void saveWorkingSetState(final IMemento memento, List<IWorkingSet> list) {
    for (IWorkingSet set : list) {
      final IPersistableElement persistable = set;
      SafeRunner.run(new ISafeRunnable() {
        @Override
        public void handleException(Throwable exception) {
          // logged by the runner
        }

        @Override
        public void run() throws Exception {
          // create a dummy node to write too - the write could fail so we shouldn't soil the final memento until we're
          // sure it succeeds.
          XMLMemento dummy = XMLMemento.createWriteRoot("workingSet");
          dummy.putString("factoryID", persistable.getFactoryId());
          persistable.saveState(dummy);
          // if the dummy was created successfully copy it to the real output
          IMemento workingSetMemento = memento.createChild("workingSet");
          workingSetMemento.putMemento(dummy);
        }
      });
    }
  }

  /** Recreates all working sets from the memento and adds them to the manager. */
  protected void restoreWorkingSetState(IMemento memento) {
    for (IMemento child : memento.getChildren("workingSet")) {
      AbstractWorkingSet workingSet = (AbstractWorkingSet) restoreWorkingSet(child);
      if (workingSet != null) {
        internalAddWorkingSet(workingSet);
      }
    }
  }

  /** Recreates a working set from the memento, or returns null when it could not be created. */
  protected IWorkingSet restoreWorkingSet(final IMemento memento) {
    String factoryID = memento.getString("factoryID");
    if (factoryID == null) {
      // if the factory id was not set in the memento then assume that the memento was created using saveState, and
      // should be restored using WorkingSetFactory
      factoryID = AbstractWorkingSet.FACTORY_ID;
    }
    final IElementFactory factory = ElementFactories.get(factoryID);
    if (factory == null) {
      RLogger.logInfo("Unable to restore working set - cannot instantiate factory: " + factoryID);
      return null;
    }
    final IAdaptable[] adaptable = new IAdaptable[1];
    SafeRunner.run(new ISafeRunnable() {
      @Override
      public void handleException(Throwable exception) {
        // logged by the runner
      }

      @Override
      public void run() throws Exception {
        adaptable[0] = factory.createElement(memento);
      }
    });
    if (adaptable[0] == null) {
      RLogger.logInfo("Unable to restore working set - cannot instantiate working set: " + factoryID);
      return null;
    }
    if (!(adaptable[0] instanceof IWorkingSet)) {
      RLogger.logInfo("Unable to restore working set - element is not an IWorkingSet: " + factoryID);
      return null;
    }
    return (IWorkingSet) adaptable[0];
  }

  /** Saves the most recently used working sets in the memento. */
  protected void saveMruList(IMemento memento) {
    for (IWorkingSet workingSet : recentWorkingSets) {
      IMemento mruMemento = memento.createChild("mruList");
      mruMemento.putString("name", workingSet.getName());
    }
  }

  /** Restores the most recently used working sets from the memento. */
  protected void restoreMruList(IMemento memento) {
    IMemento[] mruWorkingSets = memento.getChildren("mruList");
    for (int i = mruWorkingSets.length - 1; i >= 0; i--) {
      String workingSetName = mruWorkingSets[i].getString("name");
      if (workingSetName != null) {
        IWorkingSet workingSet = getWorkingSet(workingSetName);
        if (workingSet != null) {
          internalAddRecentWorkingSet(workingSet);
        }
      }
    }
  }

  /** The descriptors of the editable types of the ids, or of all the types that can be created when ids is null. */
  private static WorkingSetDescriptor[] getSupportedEditableDescriptors(String[] supportedWorkingSetIds) {
    WorkingSetRegistry registry = WorkingSetRegistry.get();
    if (supportedWorkingSetIds == null) {
      return registry.getNewPageWorkingSetDescriptors();
    }
    List<WorkingSetDescriptor> result = new ArrayList<>(supportedWorkingSetIds.length);
    for (String supportedWorkingSetId : supportedWorkingSetIds) {
      WorkingSetDescriptor desc = registry.getWorkingSetDescriptor(supportedWorkingSetId);
      if (desc != null && desc.isEditable()) {
        result.add(desc);
      }
    }
    return result.toArray(new WorkingSetDescriptor[result.size()]);
  }

  @Override
  public IWorkingSetEditWizard createWorkingSetEditWizard(IWorkingSet workingSet) {
    String editPageId = workingSet.getId();
    WorkingSetRegistry registry = WorkingSetRegistry.get();
    IWorkingSetPage editPage = null;
    if (editPageId != null) {
      editPage = registry.getWorkingSetPage(editPageId);
    }
    // the following block kind of defeats IWorkingSet.isEditable() and it doesn't make sense for there to be a default
    // page in such a case.
    if (editPage == null) {
      editPage = registry.getDefaultWorkingSetPage();
      if (editPage == null) {
        return null;
      }
    }
    WorkingSetEditWizard editWizard = new WorkingSetEditWizard(editPage);
    editWizard.setSelection(workingSet);
    return editWizard;
  }

  @Override
  public IWorkingSetNewWizard createWorkingSetNewWizard(String[] workingSetIds) {
    WorkingSetDescriptor[] descriptors = getSupportedEditableDescriptors(workingSetIds);
    if (descriptors.length == 0) {
      return null;
    }
    return new WorkingSetNewWizard(descriptors);
  }

  @Override
  public IWorkingSetSelectionDialog createWorkingSetSelectionDialog(Shell parentShell, boolean multi) {
    return createWorkingSetSelectionDialog(parentShell, multi, null);
  }

  @Override
  public IWorkingSetSelectionDialog createWorkingSetSelectionDialog(Shell parentShell, boolean multi,
      String[] workingSetIds) {
    return new WorkingSetSelectionDialog(parentShell, multi, workingSetIds);
  }

  /** Saves the state of the manager; the concrete manager decides where. */
  protected abstract void saveState();

  /** The working sets of the manager, in the order of their unique id (for the subclasses). */
  protected SortedSet<AbstractWorkingSet> workingSetsInternal() {
    return workingSets;
  }
}
