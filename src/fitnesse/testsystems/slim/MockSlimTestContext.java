// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.testsystems.slim;

import fitnesse.testsystems.TestSummary;
import fitnesse.testsystems.slim.tables.Expectation;
import fitnesse.testsystems.slim.tables.ScenarioTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockSlimTestContext implements SlimTestContext {
  private Map<String, String> symbols = new HashMap<String, String>();
  private Map<String, ScenarioTable> scenarios = new HashMap<String, ScenarioTable>();
  private List<Expectation> expectations = new ArrayList<Expectation>();
  private TestSummary testSummary = new TestSummary();

  public String getSymbol(String symbolName) {
    return symbols.get(symbolName);
  }

  public void setSymbol(String symbolName, String value) {
    symbols.put(symbolName, value);
  }

  public void addScenario(String scenarioName, ScenarioTable scenarioTable) {
    scenarios.put(scenarioName, scenarioTable);
  }

  public ScenarioTable getScenario(String scenarioName) {
    return scenarios.get(scenarioName);
  }

  public void addExpectation(Expectation e) {
    expectations.add(e);
  }

  public Map<String, ScenarioTable> getScenarios() {
    return scenarios;
  }

  public void evaluateExpectations(Map<String, Object> results) {
    for (Expectation e : expectations) {
      Object returnValue = results.get(e.getInstructionTag());
      e.evaluateExpectation(returnValue);
    }
  }

  @Override
  public void incrementPassedTestsCount() {
    testSummary.right++;
  }

  @Override
  public void incrementFailedTestsCount() {
    testSummary.wrong++;
  }

  @Override
  public void incrementErroredTestsCount() {
    testSummary.exceptions++;
  }

  @Override
  public void incrementIgnoredTestsCount() {
    testSummary.ignores++;
  }

  public TestSummary getTestSummary() {
    return testSummary;
  }

  public void clearTestSummary() {
    testSummary = new TestSummary();
  }
}
