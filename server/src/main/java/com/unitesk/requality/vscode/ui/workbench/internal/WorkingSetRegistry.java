package com.unitesk.requality.vscode.ui.workbench.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.registry.WorkingSetRegistry}: the working set types of the {@code org.eclipse.ui.workingSets}
 * extension point, by id. The IDE contributes the "Resource" type, the default one.
 */
public class WorkingSetRegistry {
  /** The id of the default working set type of the IDE (its page edits resource working sets). */
  static final String DEFAULT_PAGE_ID = "org.eclipse.ui.resourceWorkingSetPage";

  private static final String PL_WORKINGSETS = "org.eclipse.ui.workingSets";
  private static WorkingSetRegistry instance;

  private final Map<String, WorkingSetDescriptor> workingSetDescriptors = new LinkedHashMap<>();

  private WorkingSetRegistry() {
    for (IConfigurationElement ws : ExtensionRegistry.get().getConfigurationElementsFor(PL_WORKINGSETS))
      if ("workingSet".equals(ws.getName()) && ws.getAttribute("id") != null
          && !workingSetDescriptors.containsKey(ws.getAttribute("id")))
        workingSetDescriptors.put(ws.getAttribute("id"), new WorkingSetDescriptor(ws));
  }

  /** The registry of the working set types of this server. */
  public static synchronized WorkingSetRegistry get() {
    if (instance == null)
      instance = new WorkingSetRegistry();
    return instance;
  }

  /** The working set type of the id, or null. */
  public WorkingSetDescriptor getWorkingSetDescriptor(String id) {
    return id == null ? null : workingSetDescriptors.get(id);
  }

  /** All the working set types. */
  public WorkingSetDescriptor[] getWorkingSetDescriptors() {
    return workingSetDescriptors.values().toArray(new WorkingSetDescriptor[0]);
  }

  /** The types that have a page class (the ones a new working set can be created with). */
  public WorkingSetDescriptor[] getNewPageWorkingSetDescriptors() {
    List<WorkingSetDescriptor> result = new ArrayList<>();
    for (WorkingSetDescriptor d : workingSetDescriptors.values())
      if (d.getPageClassName() != null)
        result.add(d);
    return result.toArray(new WorkingSetDescriptor[0]);
  }

  /** Whether a type with a page class exists. */
  public boolean hasNewPageWorkingSetDescriptor() {
    return getNewPageWorkingSetDescriptors().length > 0;
  }

  /** The working set page of the type (a new page instance), or null. */
  public com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetPage getWorkingSetPage(String pageId) {
    WorkingSetDescriptor descriptor = getWorkingSetDescriptor(pageId);
    return descriptor == null ? null : descriptor.createWorkingSetPage();
  }

  /** The page of the default (IDE resource) type, or null. */
  public com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetPage getDefaultWorkingSetPage() {
    return getWorkingSetPage(DEFAULT_PAGE_ID);
  }

  /** The descriptor of the default type, or null. */
  WorkingSetDescriptor getDefaultWorkingSetDescriptor() {
    return getWorkingSetDescriptor(DEFAULT_PAGE_ID);
  }
}
