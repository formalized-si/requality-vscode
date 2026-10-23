package com.unitesk.requality.eclipse.editors.browser;

public interface IJSCommand {
  String getJS();

  boolean isEvaluate();

  void onComplete(Object result, ReqDocBrowser docBrowser);

  void onError(Exception e);
}
