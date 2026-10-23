package com.unitesk.requality.core.api;

import java.util.List;
import com.unitesk.requality.core.checker.INodeChecker;
import com.unitesk.requality.core.model.TreeDB;
@ApiExtension
public interface IAPICheckerProvider {

  List<INodeChecker> getCheckers(TreeDB db);
}
