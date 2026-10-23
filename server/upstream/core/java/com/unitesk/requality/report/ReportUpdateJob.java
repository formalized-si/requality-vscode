package com.unitesk.requality.report;

import java.io.File;

import com.unitesk.requality.core.model.TreeNode;

public class ReportUpdateJob {
  TreeNode node;
  String fileName;
  File baseFile;
  String upSeq;
  String tempFileContent;
  String port;
  public ReportUpdateJob(
      TreeNode node, String fileName, File baseFile, String upSeq, String tempFileContent, String port) {
    this.node = node;
    this.fileName = fileName;
    this.baseFile = baseFile;
    this.upSeq = upSeq;
    this.tempFileContent = tempFileContent;
    this.port = port;
  }
	
}
