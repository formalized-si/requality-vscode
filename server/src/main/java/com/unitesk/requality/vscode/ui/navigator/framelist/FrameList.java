package com.unitesk.requality.vscode.ui.navigator.framelist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;

/**
 * {@code org.eclipse.ui.internal.navigator.framelist.FrameList}: the frames of a navigator (Go Into, Back, Forward,
 * Up), with the current one; property change events {@link #P_CURRENT_FRAME} and {@link #P_RESET}.
 */
public class FrameList {
  /** Property name constant for the current frame. */
  public static final String P_CURRENT_FRAME = "currentFrame";
  /** Property name constant for the reset. */
  public static final String P_RESET = "reset";

  private final IFrameSource source;
  private final List<IPropertyChangeListener> listeners = new CopyOnWriteArrayList<>();
  private List<Frame> frames;
  private int current;

  public FrameList(IFrameSource source) {
    this.source = source;
    init();
  }

  public void addPropertyChangeListener(IPropertyChangeListener listener) {
    if (!listeners.contains(listener))
      listeners.add(listener);
  }

  public void back() {
    if (current > 0)
      setCurrent(current - 1);
  }

  protected void firePropertyChange(PropertyChangeEvent event) {
    for (IPropertyChangeListener listener : listeners)
      listener.propertyChange(event);
  }

  public void forward() {
    if (current < frames.size() - 1)
      setCurrent(current + 1);
  }

  public Frame getCurrentFrame() {
    return getFrame(current);
  }

  public int getCurrentIndex() {
    return current;
  }

  public Frame getFrame(int index) {
    if (index < 0 || index >= frames.size())
      return null;
    return frames.get(index);
  }

  public IFrameSource getSource() {
    return source;
  }

  public void gotoFrame(Frame frame) {
    for (int i = frames.size(); --i > current;)
      frames.remove(i);
    frame.setParent(this);
    int index = frames.size();
    frame.setIndex(index);
    frames.add(frame);
    setCurrent(index);
  }

  private void init() {
    Frame frame = source.getFrame(IFrameSource.CURRENT_FRAME, 0);
    frame.setParent(this);
    frame.setIndex(0);
    frames = new ArrayList<>();
    frames.add(frame);
    current = 0;
  }

  public void removePropertyChangeListener(IPropertyChangeListener listener) {
    listeners.remove(listener);
  }

  /** Resets the frame list: only the current state of the viewer is kept. */
  public void reset() {
    init();
    firePropertyChange(new PropertyChangeEvent(this, P_RESET, null, getFrame(current)));
  }

  void setCurrent(int newCurrent) {
    if (newCurrent < 0 || newCurrent >= frames.size())
      throw new IllegalArgumentException("Frame index " + newCurrent);
    int oldCurrent = this.current;
    if (oldCurrent != newCurrent) {
      updateCurrentFrame();
      this.current = newCurrent;
      firePropertyChange(new PropertyChangeEvent(this, P_CURRENT_FRAME, getFrame(oldCurrent), getFrame(newCurrent)));
    }
  }

  public void setCurrentIndex(int index) {
    if (index != -1 && index != current)
      setCurrent(index);
  }

  public int size() {
    return frames.size();
  }

  /** Replaces the current frame by the current state of the source (full context). */
  public void updateCurrentFrame() {
    Frame frame = source.getFrame(IFrameSource.CURRENT_FRAME, IFrameSource.FULL_CONTEXT);
    frame.setParent(this);
    frame.setIndex(current);
    frames.set(current, frame);
  }
}
