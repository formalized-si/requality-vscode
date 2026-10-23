package com.unitesk.requality.core.api;

import java.util.List;
import com.unitesk.requality.core.model.TreeNode;
@ApiExtension
public interface IAPIChangePropogatorExtensions {
  public List<IAttributeChangePropagator> getAttributeChangePropogators(TreeNode node);
}
