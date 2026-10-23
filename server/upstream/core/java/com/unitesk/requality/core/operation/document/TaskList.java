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

package com.unitesk.requality.core.operation.document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

/** Class representing a list of tasks for tasks list after update processor */
public class TaskList {

  private ArrayList<Task> tasks;
  private Set<ITaskListViewer> changeListeners;


  public TaskList() {
    tasks = new ArrayList<>();
    changeListeners = new HashSet<>();
  }

  public TaskList(ArrayList<Task> tasks) {
    this.tasks = tasks;
    changeListeners = new HashSet<>();
  }

  public ArrayList<Task> getTasks() {
    return tasks;
  }

  public void taskChanged(Task task) {
    Iterator<ITaskListViewer> iterator = changeListeners.iterator();
    while (iterator.hasNext()) iterator.next().updateTask(task);
  }

  public void removeChangeListener(ITaskListViewer viewer) {
    changeListeners.remove(viewer);
  }

  public void addChangeListener(ITaskListViewer viewer) {
    changeListeners.add(viewer);
  }

  public Set<String> getListOfDocQIds() {
    Set<String> docQIdsSet = new HashSet<>();
    for (Task task : tasks) {
      String oldDocQId = task.getOldDocQID();
      // oldDoc = (Document) db.getNode(oldDocQId, Path.QID);

      if (oldDocQId!=null && !docQIdsSet.contains(oldDocQId)) {
        docQIdsSet.add(oldDocQId);
      }
      String newDocQId = task.getNewDocQID();
      //Document newDoc = (Document) db.getNode(newDocQId, Path.QID);
      if (newDocQId!=null && !docQIdsSet.contains(newDocQId)) {
        docQIdsSet.add(newDocQId);
      }
    }

    return docQIdsSet;
  }

  public boolean initData(TreeDB db, Runnable actionsOnInited) {
    tasks = new ArrayList<>();
    changeListeners = new HashSet<>();

    ArrayList<Requirement> reqsToDoList = new ArrayList<>();
    final ArrayList<Task>tasks = this.tasks;
    final TreeDB adb = db;
    if(db!=null) {
      JobManager.jm.startJob("Update TODO panel for "+db.getVisibleName(), new RunnableWithMonitor() {
        @Override
        public void run(IProgressMonitor pm) {
          try {
            TreeNode toDoFolder = db.getNode("/ToDoLinks", Path.QID);
            if(toDoFolder!=null)
              for (TreeNode node : new DeepFirstTreeWalker(toDoFolder)) {
                if(adb != db)
                  return;
                if (node.getType().equals(ToDoItem.TYPE_NAME)) {
      
                  String reqString = node.attrs().getStringAttributeValue(ToDoItem.ATTR_REQ_LINK);
                  if (reqString!=null) {
                    Object attr_req_link =
                            UUID.fromString(reqString);
                    UUID reqUUID = (UUID) attr_req_link;
                    TreeNode parent = db.getNode(reqUUID);
                    if (parent != null) {
                      if (parent.getType().equals(Requirement.TYPE_NAME)) {
                        Requirement req = (Requirement) parent;
      
                        reqsToDoList.add(req);
      
                        String oldDocQID = node.attrs().getStringAttributeValue(ToDoItem.OLD_DOC);
                        String newDocQID = node.attrs().getStringAttributeValue(ToDoItem.NEW_DOC);
      
                        Document oldDoc = (Document) db.getNode(oldDocQID, Path.QID);
                        Document newDoc = (Document) db.getNode(newDocQID, Path.QID);
      
                        if ((oldDoc == null) || (newDoc) == null) {
                          continue;
                        }
      
                        String status = node.attrs().getStringAttributeValue(ToDoItem.ATTR_STATUS);
      
                        Task task;
      
                        if (status == null) {
      
                            task =
                                new Task(
                                    req,
                                    Task.Action.valueOf(
                                        node.attrs().getStringAttributeValue(ToDoItem.ATTR_TRANSF_ACTION)),
                                    oldDocQID,
                                    newDocQID,
                                    node.getUUId());
                        } else {
                          task =
                              new Task(
                                  req,
                                  Task.Action.valueOf(
                                      node.attrs().getStringAttributeValue(ToDoItem.ATTR_TRANSF_ACTION)),
                                  oldDocQID,
                                  newDocQID,
                                  node.getUUId(),
                                  Task.Status.valueOf(status));
                          }
                          tasks.add(task);
                        }
                      }
                    } else {
                      RLogger.logInfo("=Cannot cast <" + reqString + "> to UUID");
                    }
                  }
                }
          }catch(Exception e) {
            RLogger.logError(e);
          }
          finally {
            actionsOnInited.run();
          }
        }
       }, false);
    }else {
      actionsOnInited.run();
    }
    return true;
  }
//  public boolean initData(Runnable actionsOnInited) {
//    return initData(db, actionsOnInited);
//  }
}
