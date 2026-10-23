package com.unitesk.requality.vscode.ui.jface.action;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;

/** {@code org.eclipse.jface.action.IAction}. */
public interface IAction {
  int AS_UNSPECIFIED = 0x00;
  int AS_PUSH_BUTTON = 0x01;
  int AS_CHECK_BOX = 0x02;
  int AS_DROP_DOWN_MENU = 0x04;
  int AS_RADIO_BUTTON = 0x08;
  String TEXT = "text";
  String ENABLED = "enabled";
  String IMAGE = "image";
  String TOOL_TIP_TEXT = "toolTipText";
  String DESCRIPTION = "description";
  String CHECKED = "checked";
  String RESULT = "result";

  String getText();

  void setText(String text);

  String getId();

  void setId(String id);

  String getToolTipText();

  void setToolTipText(String text);

  String getDescription();

  void setDescription(String text);

  boolean isEnabled();

  void setEnabled(boolean enabled);

  boolean isChecked();

  void setChecked(boolean checked);

  int getStyle();

  ImageDescriptor getImageDescriptor();

  void setImageDescriptor(ImageDescriptor newImage);

  ImageDescriptor getDisabledImageDescriptor();

  void setDisabledImageDescriptor(ImageDescriptor newImage);

  ImageDescriptor getHoverImageDescriptor();

  void setHoverImageDescriptor(ImageDescriptor newImage);

  String getActionDefinitionId();

  void setActionDefinitionId(String id);

  int getAccelerator();

  void setAccelerator(int keycode);

  IMenuCreator getMenuCreator();

  void setMenuCreator(IMenuCreator creator);

  boolean isHandled();

  void run();

  void runWithEvent(Event event);

  void addPropertyChangeListener(IPropertyChangeListener listener);

  void removePropertyChangeListener(IPropertyChangeListener listener);
}
