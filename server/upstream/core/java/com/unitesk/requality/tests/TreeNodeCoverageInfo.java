package com.unitesk.requality.tests;

import java.util.HashMap;
import java.util.Map;

public class TreeNodeCoverageInfo {
  private String targetQid;
  private Map<String, Integer> allhits = new HashMap<>();
  private Map<String, String> hitLabels = new HashMap<>();
  private Integer cntAll = null;

  public void setTarget(String nodeValue) {
    targetQid = nodeValue;
  }

  public String getTargetQid() {
    return targetQid;
  }

  public void setCntAll(int parseInt) {
    cntAll = parseInt;
  }

  public int getCntAll() {
    if (cntAll != null) return cntAll;
    int minuscnt = 0;
    int cnt = 0;
    for (int i : allhits.values()) {
      if (i == -1) minuscnt++;
      else cnt += i;
    }
    return allhits.values().size() == minuscnt && minuscnt > 0 ? -1 : cnt;
  }

  public void registerTest(String path,String label) {
    registerTest(path,label, -1);
  }

  public void registerTest(String path,String label, int cnt) {
    if (cnt == -1) {
      if (!allhits.containsKey(path)) allhits.put(path, -1);
    } else {
      if (allhits.containsKey(path)) cnt += allhits.get(path);
      allhits.put(path, cnt);
    }
    if(label!=null)
      hitLabels.put(path, label);
    else
      hitLabels.put(path, path);
  }

  public String[] getTestPaths() {
    return allhits.keySet().toArray(new String[0]);
  }

  public Map<String, String> getTestLabels() {
    return hitLabels;
  }
  
  public Integer[] getTestCounts() {
    return allhits.values().toArray(new Integer[0]);
  }
}
