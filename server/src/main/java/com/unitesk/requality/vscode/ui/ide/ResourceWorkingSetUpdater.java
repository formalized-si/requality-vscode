package com.unitesk.requality.vscode.ui.ide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeEvent;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeListener;
import com.unitesk.requality.vscode.ui.resources.IResourceDelta;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetUpdater;

/**
 * {@code org.eclipse.ui.internal.ide.ResourceWorkingSetUpdater}: a working set updater that updates resource working sets on
 * resource deltas: a renamed or moved resource is replaced by its new place, a deleted resource is removed.
 */
public class ResourceWorkingSetUpdater implements IWorkingSetUpdater, IResourceChangeListener {
  /** Utility class used to help process incoming resource deltas. */
  private static class WorkingSetDelta {
    private final IWorkingSet fWorkingSet;
    private final List<IAdaptable> fElements;
    private boolean fChanged;

    /** @param workingSet the working set to track */
    WorkingSetDelta(IWorkingSet workingSet) {
      fWorkingSet = workingSet;
      fElements = new ArrayList<>(Arrays.asList(workingSet.getElements()));
    }

    /**
     * The index of this element in the list of known elements, or -1 if unknown. A resource is the same as an element of the
     * same type at the same full path (the identity of Eclipse resource handles), so an element that was moved in place is
     * found at its old path.
     */
    int indexOf(Object element) {
      int index = fElements.indexOf(element);
      if (index == -1 && element instanceof IResource resource) {
        for (int i = 0; i < fElements.size() && index == -1; i++) {
          if (fElements.get(i) instanceof IResource known && known.getType() == resource.getType()
              && known.getFullPath().equals(resource.getFullPath())) {
            index = i;
          }
        }
      }
      return index;
    }

    /** Replaces the element at the index. */
    void set(int index, IAdaptable element) {
      fElements.set(index, element);
      fChanged = true;
    }

    /** Removes the element at the index. */
    void remove(int index) {
      fElements.remove(index);
      fChanged = true;
    }

    /** Updates the working set if the delta changed it. */
    void process() {
      if (fChanged) {
        fWorkingSet.setElements(fElements.toArray(new IAdaptable[fElements.size()]));
      }
    }
  }

  private final List<IWorkingSet> fWorkingSets;

  /** Creates the updater (it listens to the resource changes of the workspace). */
  public ResourceWorkingSetUpdater() {
    fWorkingSets = new ArrayList<>();
    ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
  }

  @Override
  public void add(IWorkingSet workingSet) {
    checkElementExistence(workingSet);
    synchronized (fWorkingSets) {
      fWorkingSets.add(workingSet);
    }
  }

  @Override
  public boolean remove(IWorkingSet workingSet) {
    synchronized (fWorkingSets) {
      return fWorkingSets.remove(workingSet);
    }
  }

  @Override
  public boolean contains(IWorkingSet workingSet) {
    synchronized (fWorkingSets) {
      return fWorkingSets.contains(workingSet);
    }
  }

  @Override
  public void dispose() {
    synchronized (fWorkingSets) {
      fWorkingSets.clear();
    }
    ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
  }

  @Override
  public void resourceChanged(IResourceChangeEvent event) {
    IResourceDelta delta = event.getDelta();
    if (delta == null) {
      return;
    }
    IWorkingSet[] workingSets;
    synchronized (fWorkingSets) {
      workingSets = fWorkingSets.toArray(new IWorkingSet[fWorkingSets.size()]);
    }
    for (IWorkingSet workingSet : workingSets) {
      WorkingSetDelta workingSetDelta = new WorkingSetDelta(workingSet);
      processResourceDelta(workingSetDelta, delta);
      workingSetDelta.process();
    }
  }

  private void processResourceDelta(WorkingSetDelta result, IResourceDelta delta) {
    IResource resource = delta.getResource();
    int type = resource.getType();
    int index = result.indexOf(resource);
    int kind = delta.getKind();
    int flags = delta.getFlags();

    if (kind == IResourceDelta.CHANGED && type == IResource.PROJECT && index != -1) {
      if ((flags & IResourceDelta.OPEN) != 0) {
        result.set(index, resource);
      }
    }

    if (index != -1 && kind == IResourceDelta.REMOVED) {
      if ((flags & IResourceDelta.MOVED_TO) != 0) {
        IResource moved = ResourcesPlugin.getWorkspace().getRoot().findMember(delta.getMovedToPath());
        if (moved != null) {
          result.set(index, moved);
        } else {
          result.remove(index);
        }
      } else {
        result.remove(index);
      }
    }

    // Don't dive into closed or opened projects
    if (projectGotClosedOrOpened(resource, kind, flags)) {
      return;
    }

    for (IResourceDelta child : delta.getAffectedChildren()) {
      processResourceDelta(result, child);
    }
  }

  private boolean projectGotClosedOrOpened(IResource resource, int kind, int flags) {
    return resource.getType() == IResource.PROJECT && kind == IResourceDelta.CHANGED
        && (flags & IResourceDelta.OPEN) != 0;
  }

  /** Removes the elements that do not exist any more from the working set. */
  private void checkElementExistence(IWorkingSet workingSet) {
    List<IAdaptable> elements = new ArrayList<>(Arrays.asList(workingSet.getElements()));
    boolean changed = false;
    for (Iterator<IAdaptable> iter = elements.iterator(); iter.hasNext();) {
      IAdaptable element = iter.next();
      boolean remove = false;
      if (element instanceof IProject project) {
        remove = !project.exists();
      } else if (element instanceof IResource resource) {
        IProject project = resource.getProject();
        remove = (project != null ? project.isOpen() : true) && !resource.exists();
      }
      if (remove) {
        iter.remove();
        changed = true;
      }
    }
    if (changed) {
      workingSet.setElements(elements.toArray(new IAdaptable[elements.size()]));
    }
  }
}
