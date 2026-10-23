package com.unitesk.requality.core.operation.document;

/**
 * Class representing location for transfer from old version to a new one. Contains position of the
 * location in plain text document and text of the location
 */
public class SpanToTransfer implements Comparable<SpanToTransfer> {
  //	protected String qualifiedId;

  protected SpanStartEnd spanPosition;
  protected double weight;
  protected String spanText;

  public SpanStartEnd getLocation() {
    return spanPosition;
  }

  public SpanToTransfer(int start, int end, String text /*, String qualifiedId*/) {
    this.spanPosition = new SpanStartEnd(start, end);
    weight = 1.;
    spanText = text;
    //		this.qualifiedId = qualifiedId;
  }

  public SpanToTransfer(int start, int end, double weight /*, String qualifiedId*/) {
    this.spanPosition = new SpanStartEnd(start, end);
    this.weight = weight;
    //		this.qualifiedId = qualifiedId;
  }

  public double getWeight() {
    return weight;
  }

  public void setWeight(double weight) {
    this.weight = weight;
  }

  public String getSpanText() {
    return spanText;
  }

  //	public String getQualifiedId() {
  //		return qualifiedId;
  //	}
  //
  //	public void setQualifiedId(String qualifiedId) {
  //		this.qualifiedId = qualifiedId;
  //	}

  @Override
  public int compareTo(final SpanToTransfer o) {
    // sorting in descending order
    return -this.spanPosition.compareTo(o.spanPosition);
  }
}
