/**
 * ***************************************************************************** Institute for
 * System Programming of the Russian Academy of Sciences Copyright (c) 2009-2013 All rights
 * reserved. This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * ****************************************************************************
 */
package com.unitesk.requality.core.storage;

import java.util.Date;

// класс описывающий файл в "пространстве-времени" некоторого репозитория, то есть ревизия файла и его имя на момент ревизии
//TODO replace by existing revision-related rev data
@Deprecated
public class FileSpaceTimePoint {
  private String revision = "-1";
  private String path = "";
  private Date date = null;
  private String msg = "";
  private String author = null;
  private boolean broken = false;

  public String getRevision() {
    return revision;
  }

  public String getPath() {
    return path;
  }
  
  public void setBroken(boolean val) {
    broken = val;
  }
  public boolean isBroken() {
    return broken;
  }

  public void setRevision(String rev) {
    revision = rev;
  }

  public void setPath(String pth) {
    path = pth;
  }

  public Date getDate(){
    return date;
  }
  public void setDate(Date v){
    date = v;
  }
//  public long getEpochTimeSec() {
//    return secondsFromEpoch;
//  }
//
//  public void setEpochTimeSec(long eTime) {
//    secondsFromEpoch = eTime;
//  }

  public void setMessage(String Msg) {
    msg = Msg;
  }

  public String getMessage() {
    return msg;
  }

  public void setAuthor(String athr) {
    if (athr == null || athr.isEmpty()) author = null;
    else author = athr;
  }

  public String getAuthor() {
    return author;
  }

  public long getLongDate() {
    return date!=null?date.getTime():-1;
  }

}
