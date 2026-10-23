package com.unitesk.requality.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.tests.CoverageStorage;

public class StatisticManagerCLI {
  private TreeNode req;
  private CoverageStorage cs;
  private Integer rq = 0;
  private Integer tp = 0;
  private Integer leaves = 0;
  private Map<String, Integer> covCount = new HashMap<>();
  private List<UUID> calculated = new ArrayList<>();

  public StatisticManagerCLI(TreeNode rootReq, CoverageStorage cs) {
    cs.loadStorage();
    req = rootReq;
    this.cs = cs;
  }

  public Map<String, Integer> getArrayData(Requirement rootReq, CoverageStorage cs) {
    createNewRecord(rootReq, cs);
    Map<String, Integer> data = new HashMap<>();
    data.put("number of requirements", rq);
    data.put("number of test purposes", tp);
    data.put("number of leaves", leaves);
    data.put("number coverage (green status)", covCount.get("green"));
    data.put("number coverage (red status)", covCount.get("red"));
    data.put("number of non coveratble (gray status)", covCount.get("gray"));
    float sum = covCount.get("green") + covCount.get("red");
    data.put(
        "percent coverage (green status)", Math.round((float) covCount.get("green") * 100 / sum));
    data.put("percent coverage (red status)", Math.round((float) covCount.get("red") * 100 / sum));
    return data;
  }

  private void createNewRecord(Requirement rootReq, CoverageStorage cs) {
    cs.loadStorage();
    req = rootReq;
    this.cs = cs;
    rq = 0;
    tp = 0;
    leaves = 0;
    covCount.put("green", 0);
    covCount.put("gray", 0);
    covCount.put("red", 0);
    calculated = new ArrayList<>();

    int sc = req.sizeShownChildren();
    if (req.getType().equals(Requirement.TYPE_NAME)) rq += 1;
    for (int i = 0; i < sc; i++) {
      TreeNode node = req.getShownChild(i);
      this.calculateStatistics(node);
    }
  }

  private void calculateStatistics(TreeNode sc) {
    if (calculated.contains(sc.getUUId())) return;
    // check node is requirement
    if (sc.getType().equals(Requirement.TYPE_NAME)) rq += 1;
    // check node is test purpose
    if (sc.getType().equals(TestPurpose.TYPE_NAME)) tp += 1;
    boolean isCoverable = CoverageStorage.isCoverable(sc, null);
    int cnt = sc.sizeWorkChildren();
    for(TreeNode node:sc.getWorkChildren()) {
      if(node.getType().equals(Comment.TYPE_NAME) || !CoverageStorage.isCoverable(node, null))
        cnt--;
    }
    if (cnt == 0) {//TODO specify filters
      leaves += 1;
      // check if node is directly covered by test
      if (cs.isCovered(sc.getQualifiedId())) {
        covCount.put("green", covCount.get("green") + 1);
      } else if (isCoverable){
        covCount.put("red", covCount.get("red") + 1);
      } else {
        covCount.put("gray", covCount.get("gray") + 1);  
      }
    }
    calculated.add(sc.getUUId());
    for (int i = 0; i < sc.sizeShownChildren(); i++) {
      TreeNode node = sc.getShownChild(i);
      this.calculateStatistics(node);
    }
  }
}
