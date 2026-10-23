/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sebastian Davids <sdavids@gmx.de> - bug 77332 - [Markers] Add task dialog improvements
 *     Alexander Fedorov <alexander.fedorov@arsysop.ru> - ongoing support
 *******************************************************************************/
package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.ide.undo.CreateMarkersOperation;
import com.unitesk.requality.vscode.ui.ide.undo.UpdateMarkersOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;

/**
 * {@code org.eclipse.ui.views.markers.internal.DialogMarkerProperties}: shows the properties of a new or existing
 * marker (Description, Creation time, Path, On element, Location) and creates or modifies it on OK, as an undoable
 * operation of the workspace. The title is "Properties" unless given; a new marker is added with the "Add" button.
 */
public class DialogMarkerProperties extends Dialog {
  /** Eclipse MarkerMessages (English) of the dialog. */
  static final String PROPERTIES_TITLE = "Properties";
  static final String DESCRIPTION_TEXT = "Desc&ription: ";
  static final String CREATION_TIME_TEXT = "Creation &time: ";
  static final String FOLDER_TEXT = "Pat&h: ";
  static final String LOCATION_TEXT = "&Location: ";
  static final String RESOURCE_TEXT = "On &element: ";
  static final String ADD_BUTTON = "&Add";
  static final String LINE_NUMBER_LABEL = "line {0}";
  static final String CREATE_MARKER = "Add Marker";
  static final String MODIFY_MARKER = "Modify Marker";
  static final String MODIFY = "Modify";
  static final String CREATE = "Add";
  static final String QUALIFIED_COMMAND_TITLE = "{0} {1}";
  static final String MODIFY_TASK_TITLE = "Modify Task";
  static final String ERROR = "Error: ";

  /** The marker being shown, or null for a new marker. */
  private IMarker marker;
  /** The resource on which to create a new marker. */
  private IResource resource;
  /** The type of marker to be created. */
  private String type = IMarker.MARKER;
  /** The initial attributes to use when creating a new marker. */
  private Map<String, Object> initialAttributes;
  private Text descriptionText;
  private Text creationTime;
  private Text resourceText;
  private Text folderText;
  private Text locationText;
  private boolean dirty;
  private String title;
  /** The name used to describe the specific kind of marker, for the undo label ("Add Bookmark", "Modify Task"). */
  private String markerName;

  /** Creates the dialog: a new marker unless {@link #setMarker} is given. */
  public DialogMarkerProperties(Shell parentShell) {
    super(parentShell);
  }

  /** Creates the dialog with the title of the dialog. */
  public DialogMarkerProperties(Shell parentShell, String title) {
    super(parentShell);
    this.title = title;
  }

  /** Creates the dialog with the title and the name of the kind of marker (for the undo label). */
  public DialogMarkerProperties(Shell parentShell, String title, String markerName) {
    super(parentShell);
    this.title = title;
    this.markerName = markerName;
  }

  /** Sets the marker to show or modify, or null to create a new marker. */
  public void setMarker(IMarker marker) {
    this.marker = marker;
    if (marker != null) {
      try {
        type = marker.getType();
      } catch (CoreException e) {
        // the marker is gone: the type stays
      }
    }
  }

  /** The marker being created or modified; for a new marker this is null until the dialog is closed with OK. */
  protected IMarker getMarker() {
    return marker;
  }

  /** Sets the resource on which to create the new marker. */
  public void setResource(IResource resource) {
    this.resource = resource;
  }

  protected IResource getResource() {
    return resource;
  }

  /** Sets the initial attributes of a new marker (the description, the priority...). */
  protected void setInitialAttributes(Map<String, Object> initialAttributes) {
    this.initialAttributes = initialAttributes;
  }

  protected Map<String, Object> getInitialAttributes() {
    if (initialAttributes == null)
      initialAttributes = new HashMap<>();
    return initialAttributes;
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText(title == null ? PROPERTIES_TITLE : title);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    // initialize resources/properties
    if (marker != null) {
      resource = marker.getResource();
      initialAttributes = attributesOf(marker);
    } else if (resource == null) {
      resource = ResourcesPlugin.getWorkspace().getRoot();
    }

    Composite comp = (Composite) super.createDialogArea(parent);
    Composite composite = new Composite(comp, SWT.NULL);
    GridLayout layout = new GridLayout(2, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    createDescriptionArea(composite);
    if (marker != null) {
      createSeperator(composite);
      createCreationTimeArea(composite);
    }
    createAttributesArea(composite);
    if (resource != null) {
      createSeperator(composite);
      createResourceArea(composite);
    }
    updateDialogFromMarker();
    updateEnablement();
    return composite;
  }

  protected void createSeperator(Composite parent) {
    Label seperator = new Label(parent, SWT.NULL);
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    seperator.setLayoutData(gridData);
  }

  private void createCreationTimeArea(Composite parent) {
    Label label = new Label(parent, SWT.NONE);
    label.setText(CREATION_TIME_TEXT);
    creationTime = new Text(parent, SWT.SINGLE | SWT.READ_ONLY);
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, ADD_BUTTON, true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  private void createDescriptionArea(Composite parent) {
    Label label = new Label(parent, SWT.NONE);
    label.setText(DESCRIPTION_TEXT);
    descriptionText = new Text(parent, SWT.SINGLE | SWT.BORDER);
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = convertHorizontalDLUsToPixels(400);
    descriptionText.setLayoutData(gridData);
    descriptionText.addModifyListener(e -> markDirty());
  }

  /** Subclasses add their attributes (priority, completion) to the dialog. */
  protected void createAttributesArea(Composite parent) {
  }

  private void createResourceArea(Composite parent) {
    Label resourceLabel = new Label(parent, SWT.NONE);
    resourceLabel.setText(RESOURCE_TEXT);
    resourceText = new Text(parent, SWT.SINGLE | SWT.WRAP | SWT.READ_ONLY | SWT.BORDER);
    resourceText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Label folderLabel = new Label(parent, SWT.NONE);
    folderLabel.setText(FOLDER_TEXT);
    folderText = new Text(parent, SWT.SINGLE | SWT.WRAP | SWT.READ_ONLY | SWT.BORDER);
    folderText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    Label locationLabel = new Label(parent, SWT.NONE);
    locationLabel.setText(LOCATION_TEXT);
    locationText = new Text(parent, SWT.SINGLE | SWT.WRAP | SWT.READ_ONLY | SWT.BORDER);
    locationText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
  }

  protected void updateDialogFromMarker() {
    if (marker == null) {
      updateDialogForNewMarker();
      return;
    }
    descriptionText.setText(stringAttribute(marker, IMarker.MESSAGE));
    if (creationTime != null)
      creationTime.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
          .format(new Date(creationTimeOf(marker))));
    if (resourceText != null)
      resourceText.setText(marker.getResource().getName());
    if (folderText != null)
      folderText.setText(containerName(marker.getResource()));
    if (locationText != null) {
      String line = stringAttribute(marker, IMarker.LINE_NUMBER);
      locationText.setText(line.isEmpty() ? "" : LINE_NUMBER_LABEL.replace("{0}", line));
    }
    descriptionText.selectAll();
  }

  protected void updateDialogForNewMarker() {
    if (resource != null && resourceText != null && folderText != null) {
      resourceText.setText(resource.getName());
      IPath path = resource.getFullPath();
      int n = path.segmentCount() - 1; // n is the number of segments in container, not path
      if (n > 0) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; ++i) {
          if (i != 0)
            sb.append('/');
          sb.append(path.segment(i));
        }
        folderText.setText(sb.toString());
      }
    }
    if (initialAttributes != null) {
      Object description = initialAttributes.get(IMarker.MESSAGE);
      if (description instanceof String)
        descriptionText.setText((String) description);
      descriptionText.selectAll();
      Object line = initialAttributes.get(IMarker.LINE_NUMBER);
      if (line instanceof Integer && locationText != null)
        locationText.setText(LINE_NUMBER_LABEL.replace("{0}", String.valueOf(line)));
    }
  }

  @Override
  protected void okPressed() {
    if (marker == null || isEditable())
      saveChanges();
    super.okPressed();
  }

  protected void markDirty() {
    dirty = true;
  }

  protected boolean isDirty() {
    return dirty;
  }

  private void saveChanges() {
    Map<String, Object> attrs = getMarkerAttributes();
    if (marker == null) {
      if (resource == null)
        return;
      execute(new CreateMarkersOperation(type, attrs, resource, getCreateOperationTitle()));
    } else if (isDirty()) {
      execute(new UpdateMarkersOperation(marker, attrs, getModifyOperationTitle(), true));
    }
  }

  private void execute(IUndoableOperation op) {
    try {
      WorkspaceUndoUtil.execute(op, null, getShell());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), ERROR, null, e.getStatus());
    }
  }

  /** The attributes of the marker as the dialog sets them (the description; subclasses add their own). */
  protected Map<String, Object> getMarkerAttributes() {
    Map<String, Object> attrs = getInitialAttributes();
    attrs.put(IMarker.MESSAGE, descriptionText.getText());
    return attrs;
  }

  protected void updateEnablement() {
    descriptionText.setEnabled(isEditable());
  }

  /** A new marker is always editable; an existing one when it is user editable. */
  protected boolean isEditable() {
    if (marker == null)
      return true;
    return marker.getAttribute(IMarker.USER_EDITABLE, true);
  }

  protected void setType(String type) {
    this.type = type;
  }

  protected String getModifyOperationTitle() {
    if (markerName == null)
      return MODIFY_MARKER;
    return QUALIFIED_COMMAND_TITLE.replace("{0}", MODIFY).replace("{1}", markerName);
  }

  protected String getCreateOperationTitle() {
    if (markerName == null)
      return CREATE_MARKER;
    return QUALIFIED_COMMAND_TITLE.replace("{0}", CREATE).replace("{1}", markerName);
  }

  @Override
  protected boolean isResizable() {
    return true;
  }

  /** The name of the containing folder of a resource ("" for a project or the workspace root). */
  static String containerName(IResource r) {
    IPath path = r.getFullPath();
    int n = path.segmentCount() - 1;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; ++i) {
      if (i != 0)
        sb.append('/');
      sb.append(path.segment(i));
    }
    return sb.toString();
  }

  /** The attributes of a marker ({@code IMarker.getAttributes} throws for a deleted marker: none then). */
  static Map<String, Object> attributesOf(IMarker m) {
    try {
      return m.getAttributes();
    } catch (CoreException e) {
      return new HashMap<>();
    }
  }

  static long creationTimeOf(IMarker m) {
    try {
      return m.getCreationTime();
    } catch (CoreException e) {
      return 0;
    }
  }

  /** The value of an attribute as the dialogs show it ("" when it is not set). */
  static String stringAttribute(IMarker m, String name) {
    Object v = null;
    try {
      v = m.getAttribute(name);
    } catch (CoreException e) {
      // the marker is gone: no value
    }
    return v == null ? "" : v.toString();
  }
}
