package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.viewers.DecoratingLabelProvider}. */
public class DecoratingLabelProvider extends LabelProvider {
  private final ILabelProvider provider;
  private final ILabelDecorator decorator;

  public DecoratingLabelProvider(ILabelProvider provider, ILabelDecorator decorator) {
    this.provider = provider;
    this.decorator = decorator;
  }

  @Override
  public String getText(Object element) {
    String t = provider.getText(element);
    if (decorator != null) {
      String d = decorator.decorateText(t, element);
      if (d != null)
        return d;
    }
    return t;
  }

  @Override
  public Image getImage(Object element) {
    Image i = provider.getImage(element);
    if (decorator != null) {
      Image d = decorator.decorateImage(i, element);
      if (d != null)
        return d;
    }
    return i;
  }

  public ILabelProvider getLabelProvider() {
    return provider;
  }
}
