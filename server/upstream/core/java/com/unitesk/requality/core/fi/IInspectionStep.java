package com.unitesk.requality.core.fi;

import java.util.List;
import com.unitesk.requality.core.model.TreeNode;

public interface IInspectionStep {
  
  String getId();

  List<String> getRelatedElements();

  String getRule();

  String getResponse();

  String getPrevNumber();

  String getState();

  String getQuestion();

  IInspectionResult getInspection();
}