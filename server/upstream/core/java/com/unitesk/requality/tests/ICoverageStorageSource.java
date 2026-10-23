package com.unitesk.requality.tests;

import java.util.Map;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

/**
 * Source of coverage data for coverage-related reports
 *
 * @author kildishev
 */
public interface ICoverageStorageSource {
  /**
   * Get map of coverage data where key is Qualified or Full name of covered item, value is
   * TreeNodeCoverageInfo - object related to information about coverage of element by tests
   *
   * @return coverage information, key - QId of element, value - coverage data
   */
  public Map<String, TreeNodeCoverageInfo> getCoverage();

  /**
   * Get information about errors that appeared after test execution. Key - path to test, value
   * lists errors descriptions
   *
   * @return information about errors linked to tests, key - path to test, value - list of errors
   *     description
   */
  public Map<String, TestErrorInfo[]> getErrors();

  /**
   * Set settings for coverage source. Coverage source-specific. As an example - path to file from
   * which coverage data will be taken. Those parameters will be stored in report attributes and
   * used when coverage data will be loaded.
   *
   * @param setts settings for coverage source
   */
  public void setSettings(String... setts);

  /**
   * Get last error messages. In case of errors coverage source tends to write error messages in
   * special variable to return it on demand
   *
   * @return last error messages
   */
  public String getLastErrors();

  /**
   * Get settings for coverage source. Coverage source-specific. As an example - path to file from
   * which coverage data will be taken. Those parameters will be stored in report attributes and
   * used when coverage data will be loaded.
   *
   * @param node
   * @return settings for coverage source
   */
  public String[] getSettings(TreeNode node);

  /**
   * Get description for stored settings.
   *
   * @param description
   */
  public String getStoredDescription();
  /**
   * Method used to provide link to coverage storage for coverage sources.
   *
   * @param coverageStorage actual coverage storage where this coverage source will be used
   */
  public void setTargetStorage(CoverageStorage coverageStorage);

  /**
   * Get wizard to change settings of coverage source.
   *
   * @param node treeDB for project in which coverage source instance is defined
   * @return wizard for coverage source settings
   */
  public Object getWizard(TreeNode node);

  public Object getSourceObject(TreeDB db, Map<String, Object> map);
}
