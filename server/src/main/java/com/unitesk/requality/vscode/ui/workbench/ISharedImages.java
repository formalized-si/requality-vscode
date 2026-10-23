package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.ui.ISharedImages}: the workbench images used by Requality (icons/shared). */
public interface ISharedImages {
  String IMG_ELCL_REMOVE = "IMG_ELCL_REMOVE";
  String IMG_OBJ_ELEMENT = "IMG_OBJ_ELEMENTS";
  String IMG_OBJ_FILE = "IMG_OBJ_FILE";
  String IMG_OBJ_FOLDER = "IMG_OBJ_FOLDER";
  String IMG_OBJ_PROJECT = "IMG_OBJ_PROJECT";
  /** {@code IDE.SharedImages.IMG_OBJ_PROJECT_CLOSED}: the image of a closed project (cprj_obj). */
  String IMG_OBJ_PROJECT_CLOSED = "IMG_OBJ_PROJECT_CLOSED";
  String IMG_OBJS_ERROR_TSK = "IMG_OBJS_ERROR_TSK";
  String IMG_OBJS_WARN_TSK = "IMG_OBJS_WARN_TSK";
  String IMG_OBJS_INFO_TSK = "IMG_OBJS_INFO_TSK";
  String IMG_TOOL_BACK = "IMG_TOOL_BACK";
  String IMG_TOOL_FORWARD = "IMG_TOOL_FORWARD";
  String IMG_TOOL_UP = "IMG_TOOL_UP";
  String IMG_TOOL_BACK_DISABLED = "IMG_TOOL_BACK_DISABLED";
  String IMG_TOOL_FORWARD_DISABLED = "IMG_TOOL_FORWARD_DISABLED";
  String IMG_TOOL_UP_DISABLED = "IMG_TOOL_UP_DISABLED";
  String IMG_TOOL_DELETE = "IMG_TOOL_DELETE";
  String IMG_TOOL_NEW_WIZARD = "IMG_TOOL_NEW_WIZARD";
  String IMG_ETOOL_CLEAR = "IMG_ETOOL_CLEAR";
  String IMG_ELCL_COLLAPSEALL = "IMG_ELCL_COLLAPSEALL";
  String IMG_ELCL_SYNCED = "IMG_ELCL_SYNCED";

  Image getImage(String symbolicName);

  ImageDescriptor getImageDescriptor(String symbolicName);
}
