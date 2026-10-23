package com.unitesk.tika.parser.microsoft;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.microsoft.SummaryExtractor;

public class PubSummaryExtractor extends SummaryExtractor {
  public PubSummaryExtractor(Metadata md) {
    super(md);
  }
}
