/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.vscode.port.views.documents;

import com.unitesk.requality.vscode.ui.jface.viewers.ITableLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.core.operation.document.Task;
import com.unitesk.requality.vscode.port.eclipse.Activator;

/** Class providing content of the table in view with tasks list after update processor */
public class TaskLabelProvider extends LabelProvider implements ITableLabelProvider {

  public static final String COMPLETE = "green_dot";
  public static final String INCOMPLETE = "yellow_dot";
  public static final String FAILED = "red_dot";

  public String getColumnText(Object element, int columnIndex) {
    String result = "";
    Task task = (Task) element;
    switch (columnIndex) {
      case 0:
        break;
      case 1:
        result = task.getDescription();
        break;
      case 2:
        result = task.getActionDescription();
        break;
      case 3:
        result = task.getStatus().toString();
        break;
      default:
        break;
    }
    return result;
  }

  public Image getColumnImage(Object element, int columnIndex) {
    if (columnIndex == 0) {
      return getImage(element);

    } else {
      return null;
    }
  }

  public Image getImage(Object element) {
    String picStatus = FAILED;
    if (((Task) element).getAction().equals(Task.Action.ADD_LOCATIONS)) {
      picStatus = INCOMPLETE;
    } else if (((Task) element).getAction().equals(Task.Action.VERIFY)) {
      picStatus = COMPLETE;
    }
    return Activator.getDefault().getImageRegistry().get(picStatus);
  }
}
