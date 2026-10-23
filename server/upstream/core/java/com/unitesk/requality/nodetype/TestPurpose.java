package com.unitesk.requality.nodetype;

import java.net.URI;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class TestPurpose extends TreeNode {

  public static final String TYPE_NAME = "TestPurpose";
  public static final String ATTR_EXPECTED_RESULTS = "_expectedResults";
  public static final String ATTR_STATUS = "_status";
  public static final String ATTR_AUTHOR = "_author";
  public static final String ATTR_NAME = "_name";
  public static final String ATTR_TEST_METHOD = "_testMethod";
  public static final String ATTR_TEST_LOCATION = "_testLocation";

  {
    Attribute.registerStatusName(ATTR_STATUS);
    Attribute.registerHTMLNames(ATTR_EXPECTED_RESULTS);
    Attribute.registerStatusName(ATTR_TEST_METHOD);
    Attribute.registerStatusName(ATTR_TEST_LOCATION);
  }

  // Constructor for eclipse instancer
  public TestPurpose() {
    super(null, null);
  }

  public TestPurpose(TreeDB db, UUID id) {
    super(db, id);
  }

  public String getTargetRequirementQId() {
    if (getShownParent() == null) return null;
    return getShownParent().getQualifiedId();
  }

  public void setTargetRequirementQId(String targetReqQId) {
    if (!targetReqQId.equals(getWorkParent().getQualifiedId())) {
      setParent(getTreeDB().getNode(targetReqQId, Path.QID | Path.UV | Path.UUID));
    }
  }

  public String getExpectedResults(String prefix, String postfix, String basePath) {
    String desc = getExpectedResults();
    if (desc == null) return desc;
    try {
      URI path = getNodeResourcesPath();
      if (path == null) return null;
      return RequalityCLI.getReportDescription(
          this, ATTR_EXPECTED_RESULTS, path.toString(), desc, prefix, postfix, basePath);
    } catch (Exception e) { // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  public String getExpectedResults(String basePath) {
    String result = getExpectedResults("<div>", "</div>", basePath);
    return result;
  }

  public String getExpectedResults() {
    return attrs().getStringAttributeValue(ATTR_EXPECTED_RESULTS, "");
  }

  public void setExpectedResults(String expectedResults) {
    attrs().setAttribute(new AttributeDefinition(getUUId(), ATTR_EXPECTED_RESULTS, new 
        HtmlValueProvider(AttributeType.HTML, expectedResults, 
          TempFolderFilesProvider.getNewNotInited(AttributeType.HTML)),Flag.HIDDEN));
  }

  @Override
  public boolean canEditAttributesInTable() {
    return true;
  }
}
