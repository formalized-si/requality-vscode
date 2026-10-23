package com.unitesk.requality.core.storage.formatversion;

import java.io.File;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.repo.RevisionInfo;

/**
 * Interface for convertors of projects from old format of data store to new format
 *
 * @author kildishev
 */
public interface IFormatVersionConverter {

  /**
   * Method to convert project from one data storage scheme to another
   * @param proj project to convert
   * @param mon monitor for different process-related operations
   *
   * @return null is conversion was succeed or error message
   * @throws Exception 
   */
  public String convert(IResTracker tracker, RevisionInfo ri,File proj, IProgressMonitor mon) throws Exception;
  /**
   * Method to check of project to be suitable for current converter
   *
   * @param proj project to check
   * @return true if project is applicable for conversion
   */
  public String getGroup();
  public String versionFromMin();
  public String versionFromMax();
  public String versionTo();
}
