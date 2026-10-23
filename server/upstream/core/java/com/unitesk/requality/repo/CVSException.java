package com.unitesk.requality.repo;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

public class CVSException extends Exception {

  public static final int CVCLMANAGER_WORKINGPLACEFAILED = 1;
  public static final int CVCLMANAGER_HISTORYFOLDERFAILED = 2;
  public static final int CVCLMANAGER_TEMPFOLDERFAILED = 3;
  public static final int CVCLMANAGER_TEMPFILEFAILED = 4;
  public static final int CVCLMANAGER_NOTIMPLEMENTED = 5;
  public static final int CVCLMANAGER_REPOMISMATCH = 6;
  public static final int CVCLMANAGER_PACKFOLDERFAILED = 7;
  public static final int CVCLMANAGER_FAILED = 8;
  public static final int CVCLMANAGER_COMPHISTFILEFAILED = 9;
  public static final int CVCLMANAGER_NOTEAMPROVIDER = 10;
  public static final int CVCLMANAGER_REPOINTERFACEFAILED = 11;
  public static final int CVCLMANAGER_REPO_AUTH_EFAILED = 12;
  public static final int CVCLMANAGER_OK_NO_PACKAGE_FOUND = 13;
  public static final int CVCLMANAGER_OK_NO_FILE_IN_REPO_OR_NO_CONNECTION = 14;
  public static final int CVCLMANAGER_OK_REV_MISMATCH = 15;
  public static final int CVCLMANAGER_OK_COMMIT_NO_CHANGES = 16;
  public static final int CVCLMANAGER_OK_REVERT_NO_COMPONENT_IN_REPO = 17;
  public static final int CVCLMANAGER_OK_REVERT_NO_CHANGES = 18;
  /** */
  private static final long serialVersionUID = -8593904906883548013L;

  private int code = 0;
  
  public CVSException() {
    this(CVCLMANAGER_FAILED, null);
  }

  public CVSException(int code, String string) {
    super(string);
    this.code = code;
  }

  private static String exceptionToString(Exception ec) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ec.printStackTrace(new PrintWriter(baos));
    return baos.toString();
  }

  public CVSException(String string, Exception ec) {
    super(string + exceptionToString(ec));
  }

  public int getCode(){// TODO Auto-generated method stub
    return code;
  }
}
