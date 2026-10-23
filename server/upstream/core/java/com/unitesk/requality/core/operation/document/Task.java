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

import java.util.UUID;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.Requirement.TransferStatus;

/** Class representing task object - an entity of tasks list after update processor */
public class Task {

  private Requirement req;
  private Action action;
  private String oldDocQID;
  private String newDocQID;
  private UUID toDoItemUUID;
  private Status status;

  public UUID getToDoItemUUID() {
    return toDoItemUUID;
  }

  public String getNewDocQID() {
    return newDocQID;
  }

  public void setNewDocQID(String newDocQID) {
    this.newDocQID = newDocQID;
  }

  public String getDescription() {
    return req.getUserVisibleName();
  }

  public String getOldDocQID() {
    return oldDocQID;
  }

  public void setOldDocQID(String oldDocQID) {
    this.oldDocQID = oldDocQID;
  }

  public Task(Requirement req, Action action, String oldDocQID, String newDocQID, UUID toDoItemId) {
    this.req = req;
    this.action = action;
    this.newDocQID = newDocQID;
    this.oldDocQID = oldDocQID;
    this.toDoItemUUID = toDoItemId;
    this.status = Status.CHECK;
  }

  public Task(
      Requirement req,
      Action action,
      String oldDocQID,
      String newDocQID,
      UUID toDoItemId,
      Status status) {
    this(req, action, oldDocQID, newDocQID, toDoItemId);
    this.status = status;
  }

  public Action getAction() {
    return action;
  }

  public void setAction(Action action) {
    this.action = action;
  }

  public Requirement getReq() {
    return req;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public enum Action {
    VERIFY,
    ADD_LOCATIONS,
    FIND_LOCATIONS;

    public boolean isBetterThan(TransferStatus other) {
      return this.ordinal() > other.ordinal();
    }
  }

  public enum Status {
    CHECK,
    COMPLETED;
  }

  public boolean isCompleted() {
    return status == Status.COMPLETED;
  }

  public String getActionDescription() {
    String result = "";
    switch (action) {
      case ADD_LOCATIONS:
        result = "Add Locations";
        break;
      case FIND_LOCATIONS:
        result = "Find Locations";
        break;
      case VERIFY:
        result = "Verify";
        break;
    }

    return result;
  }
}
