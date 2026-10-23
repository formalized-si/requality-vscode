package com.unitesk.requality.tests;

import com.unitesk.requality.core.descriptors.TestGeneratorDescriptor;

/**
 * Interface for test generators provider. Used to support test generation in different plugins.
 *
 * @author kildishev
 */
public interface ITestGeneratorsProvider {
  /**
   * Get list of test generator descriptors that are available for current test generator(and
   * current plugin)
   *
   * @param id unique identifier of test generator
   * @param name name of test generator defined in plugin
   * @return list of test generator descriptors, can be loaded from plugin
   */
  public TestGeneratorDescriptor[] getDescriptors();
}
