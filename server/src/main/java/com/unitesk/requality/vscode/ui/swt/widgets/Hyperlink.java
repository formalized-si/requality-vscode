package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.SWT;

/** {@code org.eclipse.ui.forms.widgets.Hyperlink}: a single link. */
public class Hyperlink extends Link {
  /** {@code org.eclipse.ui.forms.events.IHyperlinkListener}. */
  public interface IHyperlinkListener {
    void linkActivated(HyperlinkEvent e);

    default void linkEntered(HyperlinkEvent e) {
    }

    default void linkExited(HyperlinkEvent e) {
    }
  }

  /** {@code org.eclipse.ui.forms.events.HyperlinkEvent}. */
  public static class HyperlinkEvent {
    private final Object href;
    private final String label;

    public HyperlinkEvent(Object href, String label) {
      this.href = href;
      this.label = label;
    }

    public Object getHref() {
      return href;
    }

    public String getLabel() {
      return label;
    }
  }

  private final List<IHyperlinkListener> listeners = new CopyOnWriteArrayList<>();
  private Object href;
  private String label = "";

  public Hyperlink(Composite parent, int style) {
    super(parent, style);
    addListener(SWT.Selection, e -> {
      HyperlinkEvent he = new HyperlinkEvent(href, label);
      for (IHyperlinkListener l : listeners)
        l.linkActivated(he);
    });
  }

  @Override
  public void setText(String text) {
    label = text == null ? "" : text;
    super.setText("<a>" + label.replace("<", "&lt;") + "</a>");
  }

  @Override
  public String getText() {
    return label;
  }

  public void setHref(Object href) {
    this.href = href;
  }

  public Object getHref() {
    return href;
  }

  public void setUnderlined(boolean b) {
  }

  public void addHyperlinkListener(IHyperlinkListener l) {
    listeners.add(l);
  }

  public void removeHyperlinkListener(IHyperlinkListener l) {
    listeners.remove(l);
  }
}
