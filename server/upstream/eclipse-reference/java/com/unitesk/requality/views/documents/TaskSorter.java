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

package com.unitesk.requality.views.documents;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import com.unitesk.requality.core.operation.document.Task;
import com.unitesk.requality.core.sorting.TreeNodeComparator;

/** Class with different sort options for tasks list after update processor */
public class TaskSorter extends ViewerComparator {

  public static final int PICTURE = 1;
  public static final int DESCRIPTION = 2;
  public static final int ACTION = 3;
  public static final int STATUS = 4;

  private static final int ASCENDING = 0;
  private static final int DESCENDING = 1;

  private int column;

  private int direction;

  public void doSort(int column) {
    if (column == this.column) {
      direction = 1 - direction;
    } else {
      this.column = column;
      direction = ASCENDING;
    }
  }

  public int compare(Viewer viewer, Object o1, Object o2) {

    Task task1 = (Task) o1;
    Task task2 = (Task) o2;

    int result;

    switch (column) {
      case DESCRIPTION:
        result = compareDescriptions(task1, task2);
        break;
      case ACTION:
        result = compareActions(task1, task2);
        break;
      case STATUS:
        result = compareStatuses(task1, task2);
        break;
      case PICTURE:
        result = compareLocations(task1, task2);
        break;
      default:
        result = compareLocations(task1, task2);
    }

    if (direction == DESCENDING) {
      result = -result;
    }

    return result;
  }

  protected int compareDescriptions(Task task1, Task task2) {
    return TreeNodeComparator.getComparator(task1.getReq().getTreeDB()).compare(task1.getReq(), task2.getReq());
  }

  protected int compareStatuses(Task task1, Task task2) {
    return super.compare(null, task1.getStatus().toString(), task2.getStatus().toString());
  }

  protected int compareActions(Task task1, Task task2) {
    return super.compare(null, task1.getActionDescription(), task2.getActionDescription());
  }

  protected int compareLocations(Task task1, Task task2) {
    return TreeNodeComparator.getComparator(task1.getReq().getTreeDB()).compare(task1.getReq(), task2.getReq());
  }
}
