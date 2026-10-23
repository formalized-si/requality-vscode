package com.unitesk.requality.vscode.ui.swt.widgets;

import java.time.LocalDateTime;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.DateTime}: DATE, TIME or CALENDAR (months are 0-based like SWT). */
public class DateTime extends Composite {
  private int year, month, day, hours, minutes, seconds;

  public DateTime(Composite parent, int style) {
    super(parent, style, "datetime");
    LocalDateTime now = LocalDateTime.now();
    year = now.getYear();
    month = now.getMonthValue() - 1;
    day = now.getDayOfMonth();
    hours = now.getHour();
    minutes = now.getMinute();
    seconds = now.getSecond();
    send();
    created();
  }

  private void send() {
    set("value", new int[] { year, month, day, hours, minutes, seconds });
  }

  public int getYear() {
    return year;
  }

  public int getMonth() {
    return month;
  }

  public int getDay() {
    return day;
  }

  public int getHours() {
    return hours;
  }

  public int getMinutes() {
    return minutes;
  }

  public int getSeconds() {
    return seconds;
  }

  public void setYear(int v) {
    year = v;
    send();
  }

  public void setMonth(int v) {
    month = v;
    send();
  }

  public void setDay(int v) {
    day = v;
    send();
  }

  public void setDate(int y, int m, int d) {
    year = y;
    month = m;
    day = d;
    send();
  }

  public void setHours(int v) {
    hours = v;
    send();
  }

  public void setMinutes(int v) {
    minutes = v;
    send();
  }

  public void setSeconds(int v) {
    seconds = v;
    send();
  }

  public void setTime(int h, int m, int s) {
    hours = h;
    minutes = m;
    seconds = s;
    send();
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
    addTyped(SWT.DefaultSelection, l);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if ("selection".equals(type)) {
      com.google.gson.JsonArray v = d.getAsJsonArray("value");
      year = v.get(0).getAsInt();
      month = v.get(1).getAsInt();
      day = v.get(2).getAsInt();
      hours = v.get(3).getAsInt();
      minutes = v.get(4).getAsInt();
      seconds = v.get(5).getAsInt();
      setQuiet("value", new int[] { year, month, day, hours, minutes, seconds });
      notifyListeners(SWT.Selection, new Event());
      return;
    }
    super.onClientEvent(type, d);
  }
}
