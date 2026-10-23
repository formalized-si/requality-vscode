package com.unitesk.requality.core.storage.changelog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.repo.RevisionInfo;

public class ChangeLogRecord {
  public String version;
  public String authorEmail;
  public String authorName;
  public String message;
  public String commit;
  public String date;
  public String getVersion() {
    if(version.length() == 1)
      return "0"+version;
    return version;
  }
  public String getDate() {
    return date;
  }
  private Map<String, SimpleDateFormat> bakedPatterns = new HashMap<>();
  public long getDateAsLong() {
    try {
      Date d = RevisionInfo.getDateFormat().parse(date);
      return d.getTime();
    }catch(Exception e) {
      RLogger.logError(e);
    }
	return -1;
  }
  public String getFormattedDate(String pattern) {
    try {
      Date d = RevisionInfo.getDateFormat().parse(date);
      SimpleDateFormat sdf = bakedPatterns.get(pattern);
      if(sdf == null) {
        sdf = new SimpleDateFormat(pattern);
        bakedPatterns.put(pattern, sdf);
      }
      return sdf.format(d);
    }catch(Exception e) {
      RLogger.logError(e);
    }
	return date;
  }
  public String getMessage() {
    return message;
  }
  public String getCommit() {
    return commit;
  }
  public ChangeLogRecord(String version, String commit, String authorName, String authorEmail, String message, String date) {
    this.version = version;
    this.authorEmail = authorEmail;
    this.authorName = authorName;
    this.message = message;
    this.commit = commit;
    this.date = date;
  }
  public ChangeLogRecord clone() {
	return new ChangeLogRecord(version, commit, authorName, authorEmail, message, date);
  }
  public String getFullAuthor() {
    return (authorEmail == null || authorEmail.isEmpty())?authorName:(authorName+" <"+authorEmail+">");
  }
}
