package com.unitesk.requality.tests;

import com.unitesk.requality.tool.CoreUtils;

public class TestErrorInfo {
  private String name;
  private String uri;
  private String testuri;
  private String desc;
  private String[] reqs;
  private boolean isPlain;

  public TestErrorInfo(
      String name, String path, boolean isPlain, String desc, String[] reqs, String uri) {
    this.name = name;
    this.testuri = path;
    this.reqs = reqs;
    this.isPlain = isPlain;
    this.desc = desc;
    this.uri = uri;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    if (isPlain) return CoreUtils.htmlEscape(desc);
    return desc;
  }

  public boolean isPlainText() {
    return isPlain;
  }

  public String getRawTestPath() {
    return testuri;
  }

  /**
   * If empty then coverageStorage.getRequirementsForPath needs to be called
   *
   * @param storage
   * @return
   */
  public String[] getRequirementsQIDs() {
    return reqs;
  }

  public String getURI() {
    // TODO Auto-generated method stub
    return uri;
  }
}
