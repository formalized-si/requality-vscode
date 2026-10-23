/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.core.descriptors;

import com.unitesk.requality.tests.ITestGenerator;

/**
 * Wrap over test generator
 *
 * @author kildishev
 */
public class TestGeneratorDescriptor implements Comparable<TestGeneratorDescriptor> {
  private String name;
  private String id;
  private ITestGenerator generator;

  public TestGeneratorDescriptor(String id, String name, ITestGenerator docProc) {
    this.id = id;
    this.name = name;
    this.generator = docProc;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public ITestGenerator getGenerator() {
    return generator;
  }

  @Override
  public int compareTo(TestGeneratorDescriptor o) {
    return name.compareTo(o.getName());
  }

  @Override
  public String toString() {
    return name;
  }
}
