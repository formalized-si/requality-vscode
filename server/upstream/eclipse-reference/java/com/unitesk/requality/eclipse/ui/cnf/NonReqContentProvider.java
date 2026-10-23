/*******************************************************************************
 * Copyright (c) 2003, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.unitesk.requality.eclipse.ui.cnf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.internal.navigator.resources.plugin.WorkbenchNavigatorPlugin;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.model.WorkbenchContentProvider;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.repo.RevisionInfo;

/**
 * @since 3.2
 */
public class NonReqContentProvider extends WorkbenchContentProvider {

  private static final Object[] NO_CHILDREN = new Object[0];
  private static final String REQ_CATALOG_NATURE = EclipseTracker.REQ_CATALOG_NATURE;
  private Viewer viewer = null;
  private IResTracker tr;
    /**
     *
     */
  TreeState[]listenedStates = new TreeState[] {TreeState.HALTED, TreeState.LOADED, TreeState.LOADED_NO_CACHE, TreeState.RESTART, TreeState.LOADING, TreeState.READY};
  IStateChangeHandler stateListener = new AAllTreeStateChangeHandler() {
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
      RLogger.logDebug("state "+lname+" "+state,5);
      Display d = EclipseAPI.getDisplay();
      if(d!=null && !d.isDisposed())
        d.asyncExec(new Runnable() {
        
        @Override
        public void run() {
          if(!viewer.getControl().isDisposed())
            viewer.refresh();
        }
        });
    }
  };
    public NonReqContentProvider() {
        super();//TODO duplication?
        tr = ResManager.getManager().getTracker();
        tr.getTreeStateManager().addStateHook("*", listenedStates, stateListener);
    }

    @Override
    public Object[] getElements(Object element) {
      TreeStateManager tsm = TreeStateManager.getOne(ResManager.getManager().getTracker());
      if(element instanceof IWorkspaceRoot){
        IWorkspaceRoot r = (IWorkspaceRoot) element;
        IProject[] pr = r.getProjects();
        List<Object> pl = new LinkedList<>();
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        tsm = et.getTreeStateManager();
        for(IProject p:pr)
        {
          String lname = et.getLocalName(p);
          if(p.isAccessible() && p.isOpen() && EclipseTracker.isLoadableRequalityProject(p))
          {
            TreeDB db = null;
            String pname = null;
            try {
              db = et.getTreeDB(p, null, lname);
              pname = db!=null?db.getProjectName():null;
            } catch (IOException e) {
            // TODO Auto-generated catch block
              RLogger.logError(e);
            }
            if(pname == null)
              pname = et.getProjectName(p);
            RevisionInfo ri = TreeStateManager.getRevisionInfo(db);
            TreeState ts = tsm.getState(lname, ri);
//            boolean trCheck = false;
//            synchronized(et) {
//              trCheck = et.getNewHandlings().contains(
//                EclipseTracker.getUrlString(p.getLocationURI(), pname));
//            }
//            if(db == null && ts!=TreeState.HALTED || trCheck || ts.equals(TreeState.NOT_LOADED) ||ts.equals(TreeState.RESTART) || ts.equals(TreeState.LOADING)|| tsm.getProjectLoadingState(et, lname, ri) 
//                || !TreeState.isDisposed(ts) && !tsm.areCachesInited(et, lname, ri))
//  //                if(et.getErrorState(p)||!Activator.isCorrect(p))
//  //                {
//  //                  //et.removeNature(p);
//  //                }else
//              
//              pl.add(getNotLoadedObject(p, false));
//            else
            pl.add(p);
          }else
            pl.add(p);
        }
        return pl.toArray();
      }
      return super.getChildren(element);
    }

    @Override
    public Object[] getChildren(Object element) {
        if(element instanceof IResource){
          IWorkbenchAdapter adapter = getAdapter(element);
          if (adapter != null) {
            return adapter.getChildren(element);
          }
          return new Object[0];
        }
        return NO_CHILDREN;
    }

    @Override
    public boolean hasChildren(Object element) {
        try {
            if (element instanceof IContainer) {
                IContainer c = (IContainer) element;
                if (!c.isAccessible())
                    return false;
                return c.members().length > 0;
            }
        } catch (CoreException ex) {
            WorkbenchNavigatorPlugin.getDefault().getLog().log(
                    new Status(IStatus.ERROR, WorkbenchNavigatorPlugin.PLUGIN_ID, 0, ex.getMessage(), ex));
            return false;
        }

        return super.hasChildren(element);
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
      super.inputChanged(viewer, oldInput, newInput);
      this.viewer = viewer;
      this.viewer.getControl().addDisposeListener(new DisposeListener() {
        
        @Override
        public void widgetDisposed(DisposeEvent e) {
          tr.getTreeStateManager().removeStateHook("*", listenedStates, stateListener);        
        }
      });
    }


    /**
     * Process the resource delta.
     *
     * @param delta
     */
    @Override
    protected void processDelta(IResourceDelta delta) {

        Control ctrl = viewer.getControl();
        if (ctrl == null || ctrl.isDisposed()) {
            return;
        }


        final Collection<Runnable> runnables = new ArrayList<Runnable>();
        processDelta(delta, runnables);

        if (runnables.isEmpty()) {
            return;
        }

        //Are we in the UIThread? If so spin it until we are done
        if (ctrl.getDisplay().getThread() == Thread.currentThread()) {
            runUpdates(runnables);
        } else {
          final Viewer viewer = this.viewer;
            ctrl.getDisplay().asyncExec(new Runnable(){
                @Override
                public void run() {
                    //Abort if this happens after disposes
                    Control ctrl = viewer.getControl();
                    if (ctrl == null || ctrl.isDisposed()) {
                        return;
                    }

                    runUpdates(runnables);
                }
            });
        }

    }

    /**
     * Process a resource delta. Add any runnables
     */
    private void processDelta(IResourceDelta delta, Collection<Runnable> runnables) {
        //he widget may have been destroyed
        // by the time this is run. Check for this and do nothing if so.
        Control ctrl = viewer.getControl();
        if (ctrl == null || ctrl.isDisposed()) {
            return;
        }

        // Get the affected resource
        final IResource resource = delta.getResource();
        // If any children have changed type, just do a full refresh of this
        // parent,
        // since a simple update on such children won't work,
        // and trying to map the change to a remove and add is too dicey.
        // The case is: folder A renamed to existing file B, answering yes to
        // overwrite B.
        IResourceDelta[] affectedChildren = delta
                .getAffectedChildren(IResourceDelta.CHANGED);
        for (int i = 0; i < affectedChildren.length; i++) {
            if ((affectedChildren[i].getFlags() & IResourceDelta.TYPE) != 0) {
              if(resource instanceof IProject)
                runnables.add(getRefreshRunnable(resource));
              return;
            }
        }

        // Check the flags for changes the Navigator cares about.
        // See ResourceLabelProvider for the aspects it cares about.
        // Notice we don't care about F_CONTENT or F_MARKERS currently.
        int changeFlags = delta.getFlags();
        if ((changeFlags & (IResourceDelta.OPEN | IResourceDelta.SYNC
                | IResourceDelta.TYPE | IResourceDelta.DESCRIPTION)) != 0) {
//          Runnable updateRunnable =  new Runnable(){
//              public void run() {
//                  ((StructuredViewer) viewer).update(resource, null);
//              }
//          };
//          runnables.add(updateRunnable);

            /* support the Closed Projects filter;
             * when a project is closed, it may need to be removed from the view.
             */
            runnables.add(getRefreshRunnable(resource.getParent()));
        }
        // Replacing a resource may affect its label and its children
        if ((changeFlags & IResourceDelta.REPLACED) != 0) {
            runnables.add(getRefreshRunnable(resource));
            return;
        }


        // Handle changed children .
        for (int i = 0; i < affectedChildren.length; i++) {
            processDelta(affectedChildren[i], runnables);
        }

        // @issue several problems here:
        //  - should process removals before additions, to avoid multiple equal
        // elements in viewer
        //   - Kim: processing removals before additions was the indirect cause of
        // 44081 and its varients
        //   - Nick: no delta should have an add and a remove on the same element,
        // so processing adds first is probably OK
        //  - using setRedraw will cause extra flashiness
        //  - setRedraw is used even for simple changes
        //  - to avoid seeing a rename in two stages, should turn redraw on/off
        // around combined removal and addition
        //   - Kim: done, and only in the case of a rename (both remove and add
        // changes in one delta).

        IResourceDelta[] addedChildren = delta
                .getAffectedChildren(IResourceDelta.ADDED);
        IResourceDelta[] removedChildren = delta
                .getAffectedChildren(IResourceDelta.REMOVED);

        if (addedChildren.length == 0 && removedChildren.length == 0) {
            return;
        }

        final Object[] addedObjects;
        final Object[] removedObjects;

        // Process additions before removals as to not cause selection
        // preservation prior to new objects being added
        // Handle added children. Issue one update for all insertions.
        int numMovedFrom = 0;
        int numMovedTo = 0;
        if (addedChildren.length > 0) {
            addedObjects = new Object[addedChildren.length];
            for (int i = 0; i < addedChildren.length; i++) {
                addedObjects[i] = addedChildren[i].getResource();
                if ((addedChildren[i].getFlags() & IResourceDelta.MOVED_FROM) != 0) {
                    ++numMovedFrom;
                }
            }
        } else {
            addedObjects = new Object[0];
        }

        // Handle removed children. Issue one update for all removals.
        if (removedChildren.length > 0) {
            removedObjects = new Object[removedChildren.length];
            for (int i = 0; i < removedChildren.length; i++) {
                removedObjects[i] = removedChildren[i].getResource();
                if ((removedChildren[i].getFlags() & IResourceDelta.MOVED_TO) != 0) {
                    ++numMovedTo;
                }
            }
        } else {
            removedObjects = new Object[0];
        }
        // heuristic test for items moving within same folder (i.e. renames)
        final boolean hasRename = numMovedFrom > 0 && numMovedTo > 0;

        Runnable addAndRemove = new Runnable(){
            @Override
            public void run() {
                if (viewer instanceof AbstractTreeViewer) {
                    AbstractTreeViewer treeViewer = (AbstractTreeViewer) viewer;
                    // Disable redraw until the operation is finished so we don't
                    // get a flash of both the new and old item (in the case of
                    // rename)
                    // Only do this if we're both adding and removing files (the
                    // rename case)
                    if (hasRename) {
                        treeViewer.getControl().setRedraw(false);
                    }
                    try {
                        if (addedObjects.length > 0) {
                            treeViewer.add(resource, addedObjects);
                        }
                        if (removedObjects.length > 0) {
                            treeViewer.remove(removedObjects);
                        }
                    }
                    finally {
                        if (hasRename) {
                            treeViewer.getControl().setRedraw(true);
                        }
                    }
                } else {
                    ((StructuredViewer) viewer).refresh(resource);
                }
            }
        };
        runnables.add(addAndRemove);
    }

    /**
     * Return a runnable for refreshing a resource.
     * @param resource
     * @return Runnable
     */
    private Runnable getRefreshRunnable(final IResource resource) {
        return new Runnable(){
            @Override
            public void run() {
              //TODO mb change to tree
              TreeStateManager tsm = TreeStateManager.getOne(ResManager.getManager().getTracker());
              if(resource instanceof IProject){
                IProject p = (IProject) resource;
                try {
                  if(p.isAccessible() && p.isOpen() && EclipseTracker.isLoadableRequalityProject(p))
                  {
                    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
                    String name = et.getLocalName(p);
                    TreeDB db = et
                        .getTreeDB(p, null, name);
                    if(db!=null && tsm.getState(name, null)!=TreeState.NOT_LOADED&& tsm.getState(name, null)!=TreeState.RESTART){
                      ((StructuredViewer) viewer).refresh();
                    }
                  }
                } catch (IOException  e) {
                  // TODO Auto-generated catch block
                  RLogger.logError(e);
                }
              }
              ((StructuredViewer) viewer).refresh(resource);
            }
        };
    }

    /**
     * Run all of the runnables that are the widget updates
     * @param runnables
     */
    private void runUpdates(Collection<Runnable> runnables) {
        for (Runnable runnable : runnables) {
            runnable.run();
        }

    }

}
