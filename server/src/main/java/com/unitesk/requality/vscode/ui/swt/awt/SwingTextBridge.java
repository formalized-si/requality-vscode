package com.unitesk.requality.vscode.ui.swt.awt;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.custom.StyledText;
import com.unitesk.requality.vscode.ui.swt.custom.StyleRange;
import com.unitesk.requality.vscode.ui.swt.events.FocusListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * Shows a Swing {@link JTextComponent} as an SWT {@link StyledText} and keeps the two in sync on the UI thread: the
 * text of the document (initially, and on every document change), the background attributes of its character
 * elements (as style ranges, refreshed on attribute changes too), the editability, and the focus (the StyledText's
 * focus events are dispatched to the Swing focus listeners). Edits of the StyledText update the document without
 * echoing back: the {@code syncing} flag suppresses the listeners while one side is being written.
 */
class SwingTextBridge {
  private final JTextComponent swing;
  private final StyledText text;
  private Document doc;
  private boolean syncing;

  private final DocumentListener documentListener = new DocumentListener() {
    @Override
    public void insertUpdate(DocumentEvent e) {
      pushFromSwing();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      pushFromSwing();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      pushFromSwing();
    }
  };

  SwingTextBridge(Composite parent, JTextComponent swing) {
    this.swing = swing;
    int style = SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | (swing.isEditable() ? SWT.NONE : SWT.READ_ONLY);
    text = new StyledText(parent, style);
    text.setEditable(swing.isEditable());
    text.addModifyListener(e -> fromText());
    text.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(com.unitesk.requality.vscode.ui.swt.events.FocusEvent e) {
        dispatchFocus(java.awt.event.FocusEvent.FOCUS_GAINED);
      }

      @Override
      public void focusLost(com.unitesk.requality.vscode.ui.swt.events.FocusEvent e) {
        dispatchFocus(java.awt.event.FocusEvent.FOCUS_LOST);
      }
    });
    swing.addPropertyChangeListener(evt -> {
      if ("document".equals(evt.getPropertyName()))
        bindDocument();
      else if ("editable".equals(evt.getPropertyName()))
        text.setEditable(swing.isEditable());
    });
    bindDocument();
  }

  private void bindDocument() {
    if (doc != null)
      doc.removeDocumentListener(documentListener);
    doc = swing.getDocument();
    doc.addDocumentListener(documentListener);
    pushFromSwing();
  }

  private void dispatchFocus(int id) {
    java.awt.event.FocusEvent event = new java.awt.event.FocusEvent(swing, id);
    for (java.awt.event.FocusListener l : swing.getFocusListeners()) {
      if (id == java.awt.event.FocusEvent.FOCUS_GAINED)
        l.focusGained(event);
      else
        l.focusLost(event);
    }
  }

  /** Writes the document (text and styles) to the StyledText. */
  private void pushFromSwing() {
    if (syncing)
      return;
    syncing = true;
    try {
      String s = documentText();
      if (!s.equals(text.getText()))
        text.setText(s);
      text.setStyleRanges(styles());
    } finally {
      syncing = false;
    }
  }

  /** Writes the edited text of the StyledText to the document, changing only the characters that differ. */
  private void fromText() {
    if (syncing)
      return;
    syncing = true;
    try {
      String old = documentText();
      String now = text.getText();
      int max = Math.min(old.length(), now.length());
      int prefix = 0;
      while (prefix < max && old.charAt(prefix) == now.charAt(prefix))
        prefix++;
      int suffix = 0;
      while (suffix < max - prefix && old.charAt(old.length() - 1 - suffix) == now.charAt(now.length() - 1 - suffix))
        suffix++;
      int removed = old.length() - prefix - suffix;
      String inserted = now.substring(prefix, now.length() - suffix);
      if (removed > 0)
        doc.remove(prefix, removed);
      if (!inserted.isEmpty())
        doc.insertString(prefix, inserted, null);
      text.setStyleRanges(styles());
    } catch (BadLocationException e) {
      throw new IllegalStateException(e);
    } finally {
      syncing = false;
    }
  }

  private String documentText() {
    try {
      return doc.getText(0, doc.getLength());
    } catch (BadLocationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The background ranges of the document's character elements. */
  private List<StyleRange> styles() {
    List<StyleRange> out = new ArrayList<>();
    for (Element root : doc.getRootElements())
      collect(root, out);
    return out;
  }

  private void collect(Element e, List<StyleRange> out) {
    if (e.isLeaf()) {
      // StyleConstants.getBackground falls back to black when no background is set: only a defined one is shown.
      java.awt.Color bg = (java.awt.Color) e.getAttributes().getAttribute(StyleConstants.Background);
      int length = e.getEndOffset() - e.getStartOffset();
      if (bg != null && length > 0)
        out.add(new StyleRange(e.getStartOffset(), length, null,
            new Color(text.getDisplay(), bg.getRed(), bg.getGreen(), bg.getBlue())));
      return;
    }
    for (int i = 0; i < e.getElementCount(); i++)
      collect(e.getElement(i), out);
  }
}
