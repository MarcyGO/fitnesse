// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.testsystems.slim.tables;

import static fitnesse.testsystems.slim.tables.ComparatorUtil.approximatelyEqual;
import static java.lang.Character.isLetterOrDigit;
import static java.lang.Character.toUpperCase;
import static util.ListUtility.list;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fitnesse.testsystems.slim.SlimTestContext;
import fitnesse.testsystems.slim.SlimTestSystem;
import fitnesse.testsystems.slim.Table;
import fitnesse.testsystems.slim.results.ErrorResult;
import fitnesse.testsystems.slim.results.FailResult;
import fitnesse.testsystems.slim.results.IgnoreResult;
import fitnesse.testsystems.slim.results.PassResult;
import fitnesse.testsystems.slim.results.PlainResult;
import fitnesse.testsystems.slim.results.Result;
import fitnesse.wikitext.Utils;

public abstract class SlimTable {
  private static final Pattern SYMBOL_ASSIGNMENT_PATTERN = Pattern.compile("\\A\\s*\\$(\\w+)\\s*=\\s*\\Z");

  private String tableName;
  private int instructionNumber = 0;

  private List<SlimTable> children = new LinkedList<SlimTable>();
  private SlimTable parent = null;

  private SlimTestContext testContext;

  protected Table table;
  protected String id;

  public SlimTable(Table table, String id, SlimTestContext testContext) {
    this.id = id;
    this.table = table;
    this.testContext = testContext;
    tableName = getTableType() + "_" + id;
  }

  SlimTable(SlimTestContext testContext) {
    this.testContext = testContext;
  }

  public SlimTable getParent() {
    return parent;
  }

  public void addChildTable(SlimTable slimtable, int row) throws Exception {
    slimtable.id = id + "." + children.size();
    slimtable.tableName = makeInstructionTag(instructionNumber) + "/" + slimtable.tableName;
    instructionNumber++;
    slimtable.parent = this;
    children.add(slimtable);

    Table parentTable = getTable();
    Table childTable = slimtable.getTable();
    childTable.setName(slimtable.tableName);
    parentTable.appendChildTable(row, childTable);
  }

  protected void addExpectation(Expectation e) {
    testContext.addExpectation(e);
  }

  public String replaceSymbols(String s) {
    return new SymbolReplacer(s).replace();
  }

  public String replaceSymbolsWithFullExpansion(String s) {
    return new FullExpansionSymbolReplacer(s).replace();
  }


  protected abstract String getTableType();

  public abstract List<Object> getInstructions() throws SyntaxError;

  protected List<Object> prepareInstruction() {
    List<Object> instruction = new ArrayList<Object>();
    instruction.add(makeInstructionTag(instructionNumber));
    instructionNumber++;
    return instruction;
  }

  protected String makeInstructionTag(int instructionNumber) {
    return String.format("%s_%d", tableName, instructionNumber);
  }

  protected String getInstructionTag() {
    return makeInstructionTag(instructionNumber);
  }

  public String getTableName() {
    return tableName;
  }

  public String getSymbol(String variableName) {
    return testContext.getSymbol(variableName);
  }

  public void setSymbol(String variableName, String value) {
    testContext.setSymbol(variableName, value);
  }

  public Table getTable() {
    return table;
  }

  protected List<Object> constructFixture(String fixtureName) {
    return constructInstance(getTableName(), fixtureName, 0, 0);
  }

  protected String getFixtureName() {
    String tableHeader = table.getCellContents(0, 0);
    String fixtureName = getFixtureName(tableHeader);
    String disgracedFixtureName = Disgracer.disgraceClassName(fixtureName);
    return disgracedFixtureName;
  }

  protected String getFixtureName(String tableHeader) {
    if (!tableHeader.contains(":"))
      return tableHeader;
    return tableHeader.split(":")[1];
  }

  protected List<Object> constructInstance(String instanceName, String className, int classNameColumn, int row) {
    RowExpectation expectation = new ConstructionExpectation(getInstructionTag(), classNameColumn, row);
    addExpectation(expectation);
    List<Object> makeInstruction = prepareInstruction();
    makeInstruction.add("make");
    makeInstruction.add(instanceName);

    makeInstruction.add(className);
    addArgsToInstruction(makeInstruction, gatherConstructorArgumentsStartingAt(classNameColumn + 1, row));
    return makeInstruction;
  }

  protected Object[] gatherConstructorArgumentsStartingAt(int startingColumn, int row) {
    int columnCount = table.getColumnCountInRow(row);
    List<String> arguments = new ArrayList<String>();
    for (int col = startingColumn; col < columnCount; col++) {
      arguments.add(table.getUnescapedCellContents(col, row));
      addExpectation(new VoidReturnExpectation(getInstructionTag(), col, row));
    }
    return arguments.toArray(new String[arguments.size()]);
  }

  protected void addCall(List<Object> instruction, String instanceName, String functionName) {
    String disgracedFunctionName = Disgracer.disgraceMethodName(functionName);
    List<String> callHeader = list("call", instanceName, disgracedFunctionName);
    instruction.addAll(callHeader);
  }

  protected List<Object> callFunction(String instanceName, String functionName, Object... args) {
    List<Object> callInstruction = prepareInstruction();
    addCall(callInstruction, instanceName, functionName);
    addArgsToInstruction(callInstruction, args);
    return callInstruction;
  }

  protected String getInstructionId(List<Object> instruction) {
    return (String) instruction.get(0);
  }

  private void addArgsToInstruction(List<Object> instruction, Object... args) {
    for (Object arg : args)
      instruction.add(arg);
  }

  protected List<Object> callAndAssign(String symbolName, String instanceName, String functionName, String... args) {
    List<Object> callAndAssignInstruction = prepareInstruction();
    String disgracedFunctionName = Disgracer.disgraceMethodName(functionName);
    List<String> callAndAssignHeader = list("callAndAssign", symbolName, instanceName, disgracedFunctionName);
    callAndAssignInstruction.addAll(callAndAssignHeader);
    addArgsToInstruction(callAndAssignInstruction, (Object[]) args);
    return callAndAssignInstruction;
  }

  // TODO: make Response object objects instead
  protected void failMessage(int col, int row, String failureMessage) {
    String contents = table.getCellContents(col, row);
    Result failingContents = failMessage(contents, failureMessage);
    table.setCell(col, row, failingContents);
  }

  // TODO: make Response object objects instead
  protected void fail(int col, int row, String value) {
    Result failingContents = fail(value);
    table.setCell(col, row, failingContents);
  }

  // TODO: make Response object objects instead
  protected void ignore(int col, int row, String value) {
    IgnoreResult content = ignore(value);
    table.setCell(col, row, content);
  }

  // TODO: make Response object objects instead
  protected void pass(int col, int row) {
    String contents = table.getCellContents(col, row);
    Result passingContents = pass(contents);
    table.setCell(col, row, passingContents);
  }

  // TODO: make Response object objects instead
  protected void pass(int col, int row, String passMessage) {
    Result passingContents = pass(passMessage);
    table.setCell(col, row, passingContents);
  }

  // TODO: make Response object objects instead
  protected void expected(int col, int tableRow, String actual) {
    String contents = table.getCellContents(col, tableRow);
    Result failureMessage = expected(actual, contents);
    table.setCell(col, tableRow, failureMessage);
  }

  // TODO: make Response object objects instead
  public Result expected(String actual, String expected) {
    return failMessage(actual, String.format("expected [%s]", expected));
  }

  // TODO: make Response object objects instead
  protected Result fail(String value) {
    testContext.incrementFailedTestsCount();
    return new FailResult(value);
  }

  // TODO: make Response object objects instead
  protected Result failMessage(String value, String message) {
    return new PlainResult(String.format("[%s] %s", value, fail(message)));
  }

  // TODO: make Response object objects instead
  protected Result pass(String value) {
    testContext.incrementPassedTestsCount();
    return passUncounted(value);
  }

  // TODO: make Response object objects instead
  private Result passUncounted(String value) {
    return new PassResult(value);
  }

  // TODO: make Response object objects instead
  protected ErrorResult error(String value) {
    testContext.incrementErroredTestsCount();
    return new ErrorResult(value);
  }

  // TODO: make Response object objects instead
  protected IgnoreResult ignore(String value) {
    testContext.incrementIgnoredTestsCount();
    return new IgnoreResult(value);
  }

  protected Result makeExeptionMessage(String value) {
    if (value.startsWith(SlimTestSystem.MESSAGE_FAIL))
      return fail(value.substring(SlimTestSystem.MESSAGE_FAIL.length()));
    else
      return error(value.substring(SlimTestSystem.MESSAGE_ERROR.length()));
  }

  protected boolean isExceptionMessage(String value) {
    return value != null && (value.startsWith(SlimTestSystem.MESSAGE_FAIL) || value.startsWith(SlimTestSystem.MESSAGE_ERROR));
  }

  protected boolean isExceptionFailureMessage(String value) {
    return value.startsWith("Exception: ");
  }

  public boolean shouldIgnoreException(String resultKey, String resultString) {
    return false;
  }

  protected String ifSymbolAssignment(int col, int row) {
    String expected = table.getCellContents(col, row);
    Matcher matcher = SYMBOL_ASSIGNMENT_PATTERN.matcher(expected);
    return matcher.find() ? matcher.group(1) : null;
  }

  public SlimTestContext getTestContext() {
    return testContext;
  }

  protected List<Object> tableAsList() {
    List<Object> tableArgument = list();
    int rows = table.getRowCount();
    for (int row = 1; row < rows; row++)
      tableArgument.add(tableRowAsList(row));
    return tableArgument;
  }

  private List<Object> tableRowAsList(int row) {
    List<Object> rowList = list();
    int cols = table.getColumnCountInRow(row);
    for (int col = 0; col < cols; col++)
      rowList.add(table.getCellContents(col, row));
    return rowList;
  }

  public List<SlimTable> getChildren() {
    return children;
  }

  static class Disgracer {
    public boolean capitalizeNextWord;
    public StringBuffer disgracedName;
    private String name;

    public Disgracer(String name) {
      this.name = name;
    }

    public static String disgraceClassName(String name) {
      return new Disgracer(name).disgraceClassNameIfNecessary();
    }

    public static String disgraceMethodName(String name) {
      return new Disgracer(name).disgraceMethodNameIfNecessary();
    }

    private String disgraceMethodNameIfNecessary() {
      if (isGraceful()) {
        return disgraceMethodName();
      } else {
        return name;
      }
    }

    private String disgraceMethodName() {
      capitalizeNextWord = false;
      return disgraceName();
    }

    private String disgraceClassNameIfNecessary() {
      if (nameHasDotsBeforeEnd() || nameHasDollars())
        return name;
      else if (isGraceful()) {
        return disgraceClassName();
      } else {
        return name;
      }
    }

    private boolean nameHasDollars() {
      return name.indexOf("$") != -1;
    }

    private String disgraceClassName() {
      capitalizeNextWord = true;
      return disgraceName();
    }

    private boolean nameHasDotsBeforeEnd() {
      int dotIndex = name.indexOf(".");
      return dotIndex != -1 && dotIndex != name.length() - 1;
    }

    private String disgraceName() {
      disgracedName = new StringBuffer();
      for (char c : name.toCharArray())
        appendCharInProperCase(c);

      return disgracedName.toString();
    }

    private void appendCharInProperCase(char c) {
      if (isGraceful(c)) {
        capitalizeNextWord = true;
      } else {
        appendProperlyCapitalized(c);
      }
    }

    private void appendProperlyCapitalized(char c) {
      disgracedName.append(capitalizeNextWord ? toUpperCase(c) : c);
      capitalizeNextWord = false;
    }

    private boolean isGraceful() {
      boolean isGraceful = false;
      for (char c : name.toCharArray()) {
        if (isGraceful(c))
          isGraceful = true;
      }
      return isGraceful;
    }

    private boolean isGraceful(char c) {
      return !(isLetterOrDigit(c) || c == '_');
    }
  }

  /** Expectation base class for row based expectations. */
  public abstract class RowExpectation implements Expectation {
    private final int col;
    private final int row;
    private final String instructionTag;
    // Needed for Xml Formatter...
    private String actual;
    private String expected;
    private Result evaluationMessage;

    public RowExpectation(String instructionTag, int col, int row) {
      this.row = row;
      this.instructionTag = instructionTag;
      this.col = col;
    }

    /* (non-Javadoc)
     * @see fitnesse.testsystems.slim.tables.Expectation#evaluateExpectation(java.util.Map)
     */
    @Override
    public void evaluateExpectation(Object returnValue) {
      Result evaluationMessage;
      if (returnValue == null) {
        String originalContent = table.getCellContents(col, row);
        evaluationMessage = new PlainResult(originalContent, ignore("Test not run"));
      } else {
        String value;
        value = returnValue.toString();
        String originalContent = table.getCellContents(col, row);
        evaluationMessage = evaluationMessage(value, originalContent);
      }
      if (evaluationMessage != null)
        table.setCell(col, row, evaluationMessage);
    }

    Result evaluationMessage(String actual, String expected) {
      this.actual = actual;
      this.expected = expected;
      Result evaluationMessage;
      if (isExceptionMessage(actual))
        evaluationMessage = new PlainResult(expected, makeExeptionMessage(actual));
      else
        evaluationMessage = createEvaluationMessage(actual, expected);
      this.evaluationMessage = evaluationMessage;
      return evaluationMessage;
    }

    protected abstract Result createEvaluationMessage(String actual, String expected);

    public int getCol() {
      return col;
    }

    public int getRow() {
      return row;
    }

    public String getInstructionTag() {
      return instructionTag;
    }

    public String getActual() {
      return actual;
    }

    public String getExpected() {
      return expected;
    }

    public String getEvaluationMessage() {
      return evaluationMessage == null ? "" : evaluationMessage.toString();
    }
  }

  class SymbolReplacer {
    protected String replacedString;
    private Matcher symbolMatcher;
    private final Pattern symbolPattern = Pattern.compile("\\$([a-zA-Z]\\w*)");
    private int startingPosition;

    SymbolReplacer(String s) {
      this.replacedString = s;
      symbolMatcher = symbolPattern.matcher(s);
    }

    String replace() {
      replaceAllSymbols();
      return replacedString;
    }

    private void replaceAllSymbols() {
      startingPosition = 0;
      while (symbolFound())
        replaceSymbol();
    }

    private void replaceSymbol() {
      String symbolName = symbolMatcher.group(1);
      String value = formatSymbol(symbolName);
      String prefix = replacedString.substring(0, symbolMatcher.start());
      String suffix = replacedString.substring(symbolMatcher.end());
      replacedString = prefix + value + suffix;
      int replacementEnd = symbolMatcher.start() + value.length();
      startingPosition = Math.min(replacementEnd, replacedString.length());
    }

    private String formatSymbol(String symbolName) {
      String value = getSymbol(symbolName);
      if (value == null) {
        for (int i = symbolName.length() - 1; i > 0; i--) {
          String str = symbolName.substring(0, i);
          if ((value = getSymbol(str)) != null)
            return formatSymbolValue(str, value) + symbolName.substring(i, symbolName.length());
        }

        return "$" + symbolName;
      } else
        return formatSymbolValue(symbolName, value);
    }


    private boolean symbolFound() {
      symbolMatcher = symbolPattern.matcher(replacedString);
      return symbolMatcher.find(startingPosition);
    }

    protected String formatSymbolValue(String name, String value) {
      return value;
    }
  }

  class FullExpansionSymbolReplacer extends SymbolReplacer {
    FullExpansionSymbolReplacer(String s) {
      super(s);
    }

    protected String formatSymbolValue(String name, String value) {
      if (isExceptionFailureMessage(value)) {
        return String.format("$%s->[%s]", name, value);
      }
      return String.format("$%s->[%s]", name, Utils.escapeHTML(value));
    }
  }

  class VoidReturnExpectation extends RowExpectation {
    public VoidReturnExpectation(String instructionTag, int col, int row) {
      super(instructionTag, col, row);
    }

    protected Result createEvaluationMessage(String actual, String expected) {
      return new PlainResult(replaceSymbolsWithFullExpansion(expected));
    }
  }

  class SilentReturnExpectation extends RowExpectation {
    public SilentReturnExpectation(String instructionTag, int col, int row) {
      super(instructionTag, col, row);
    }

    protected Result createEvaluationMessage(String actual, String expected) {
      return null;
    }
  }

  class ConstructionExpectation extends RowExpectation {
    public ConstructionExpectation(String instructionTag, int col, int row) {
      super(instructionTag, col, row);
    }

    protected Result createEvaluationMessage(String actual, String expected) {
      if ("OK".equalsIgnoreCase(actual))
        return passUncounted(replaceSymbolsWithFullExpansion(expected));
      else
        return new ErrorResult("Unknown construction message", actual);
    }
  }

  class SymbolAssignmentExpectation extends RowExpectation {
    private String symbolName;

    SymbolAssignmentExpectation(String symbolName, String instructionTag, int col, int row) {
      super(instructionTag, col, row);
      this.symbolName = symbolName;
    }

    // TODO: make something useful for substitution
    protected Result createEvaluationMessage(String actual, String expected) {
      setSymbol(symbolName, actual);
      if (isExceptionFailureMessage(actual)) {
        return new PlainResult(String.format("$%s<-[%s]", symbolName, actual));
      } else {
        return new PlainResult(String.format("$%s<-[%s]", symbolName, Utils.escapeHTML(actual)));
      }
    }
  }

  public static interface ExpectationPassFailReporter {
    Result pass(String message);

    Result fail(String message);
  }

  class ReturnedValueExpectation extends RowExpectation implements ExpectationPassFailReporter {
    public ReturnedValueExpectation(String instructionTag, int col, int row) {
      super(instructionTag, col, row);
    }

    protected Result createEvaluationMessage(String actual, String expected) {
      Result evaluationMessage;
      String replacedExpected = Utils.unescapeHTML(replaceSymbols(expected));

      if (actual == null)
        evaluationMessage = fail("null"); //todo can't be right message.
      else if (actual.equals(replacedExpected))
        evaluationMessage = pass(announceBlank(replaceSymbolsWithFullExpansion(expected)));
      else if (replacedExpected.length() == 0)
        evaluationMessage = ignore(actual);
      else {
        Result expressionMessage = new Comparator(this, replacedExpected, actual, expected).evaluate();
        if (expressionMessage != null)
          evaluationMessage = expressionMessage;
        else if (isExceptionFailureMessage(actual)) {
          evaluationMessage = error(actual);
        } else
          evaluationMessage = failMessage(actual,
            String.format("%s [%s]", expectationAdjective(), replaceSymbolsWithFullExpansion(expected))
          );
      }

      return evaluationMessage;
    }

    protected String expectationAdjective() {
      return "expected";
    }

    private String announceBlank(String originalValue) {
      return originalValue.length() == 0 ? "BLANK" : originalValue;
    }

    @Override
    public Result pass(String message) {
      return SlimTable.this.pass(message);
    }

    @Override
    public Result fail(String message) {
      return SlimTable.this.fail(message);
    }

    protected Result failMessage(String value, String message) {
      return new PlainResult(String.format("[%s] %s", value, fail(message)));
    }
  }

  class RejectedValueExpectation extends ReturnedValueExpectation {
    public RejectedValueExpectation(String instructionTag, int col, int row) {
      super(instructionTag, col, row);
    }

    @Override
    protected String expectationAdjective() {
      return "is not";
    }

    public Result pass(String message) {
      return super.fail(message);
    }

    public Result fail(String message) {
      return super.pass(message);
    }
  }

  class Comparator {
    private String expression;
    private String actual;
    private String expected;
    private Pattern simpleComparison = Pattern.compile(
      "\\A\\s*_?\\s*(!?(?:(?:[<>]=?)|(?:[~]?=)))\\s*(-?\\d*\\.?\\d+)\\s*\\Z"
    );
    private Pattern range = Pattern.compile(
      "\\A\\s*(-?\\d*\\.?\\d+)\\s*<(=?)\\s*_\\s*<(=?)\\s*(-?\\d*\\.?\\d+)\\s*\\Z"
    );

    private Pattern regexPattern = Pattern.compile("\\s*=~/(.*)/");
    private double v;
    private double arg1;
    private double arg2;
    public String operation;
    private String arg1Text;
    private ExpectationPassFailReporter passFailReporter;
    boolean match = false;

    public Comparator(String actual, String expected) {
      this.passFailReporter = new ExpectationPassFailReporter() {
        public Result pass(String message) {
          return new PlainResult(message);
        }

        public Result fail(String message) {
          return new PlainResult(message);
        }
      };
      this.expression = Utils.unescapeHTML(replaceSymbols(expected));
      this.actual = actual;
      this.expected = expected;
    }

    public Comparator(ExpectationPassFailReporter passFailReporter, String expression, String actual, String expected) {
      this.passFailReporter = passFailReporter;
      this.expression = expression;
      this.actual = actual;
      this.expected = expected;
    }

    private Result pass(String message) {
      match = true;
      return passFailReporter.pass(message);
    }

    private Result fail(String message) {
      match = false;
      return passFailReporter.fail(message);
    }

    public boolean matches() {
      return match;
    }

    public Result evaluate() {
      Result message = evaluateRegularExpressionIfPresent();
      if (message != null)
        return message;

      operation = matchSimpleComparison();
      if (operation != null)
        return doSimpleComparison();

      Matcher matcher = range.matcher(expression);
      if (matcher.matches() && canUnpackRange(matcher)) {
        return doRange(matcher);
      } else
        return null;
    }

    private Result evaluateRegularExpressionIfPresent() {
      Matcher regexMatcher = regexPattern.matcher(expression);
      Result message = null;
      if (regexMatcher.matches()) {
        String pattern = regexMatcher.group(1);
        message = evaluateRegularExpression(pattern);
      }
      return message;
    }

    private Result evaluateRegularExpression(String pattern) {
      Result message;
      Matcher patternMatcher = Pattern.compile(pattern).matcher(actual);
      if (patternMatcher.find()) {
        message = pass(String.format("/%s/ found in: %s", pattern, actual));
      } else {
        message = fail(String.format("/%s/ not found in: %s", pattern, actual));
      }
      return message;
    }

    private Result doRange(Matcher matcher) {
      boolean closedLeft = matcher.group(2).equals("=");
      boolean closedRight = matcher.group(3).equals("=");
      boolean pass = (arg1 < v && v < arg2) || (closedLeft && arg1 == v) || (closedRight && arg2 == v);
      return rangeMessage(pass);
    }

    private Result rangeMessage(boolean pass) {
      String[] fragments = expected.replaceAll(" ", "").split("_");
      String message = String.format("%s%s%s", fragments[0], actual, fragments[1]);
      message = replaceSymbolsWithFullExpansion(message);
      return pass ? pass(message) : fail(message);

    }

    private boolean canUnpackRange(Matcher matcher) {
      try {
        arg1 = Double.parseDouble(matcher.group(1));
        arg2 = Double.parseDouble(matcher.group(4));
        v = Double.parseDouble(actual);
      } catch (NumberFormatException e) {
        return false;
      }
      return true;
    }

    private Result doSimpleComparison() {
      if (operation.equals("<") || operation.equals("!>="))
        return simpleComparisonMessage(v < arg1);
      else if (operation.equals(">") || operation.equals("!<="))
        return simpleComparisonMessage(v > arg1);
      else if (operation.equals(">=") || operation.equals("!<"))
        return simpleComparisonMessage(v >= arg1);
      else if (operation.equals("<=") || operation.equals("!>"))
        return simpleComparisonMessage(v <= arg1);
      else if (operation.equals("!="))
        return simpleComparisonMessage(v != arg1);
      else if (operation.equals("="))
        return simpleComparisonMessage(v == arg1);
      else if (operation.equals("~="))
        return simpleComparisonMessage(approximatelyEqual(arg1Text, actual));
      else if (operation.equals("!~="))
        return simpleComparisonMessage(!approximatelyEqual(arg1Text, actual));
      else
        return null;
    }

    private Result simpleComparisonMessage(boolean pass) {
      String message = String.format("%s%s", actual, expected.replaceAll(" ", ""));
      message = replaceSymbolsWithFullExpansion(message);
      return pass ? pass(message) : fail(message);

    }

    private String matchSimpleComparison() {
      Matcher matcher = simpleComparison.matcher(expression);
      if (matcher.matches()) {
        try {
          v = Double.parseDouble(actual);
          arg1Text = matcher.group(2);
          arg1 = Double.parseDouble(arg1Text);
          return matcher.group(1);
        } catch (NumberFormatException e1) {
          return null;
        }
      }
      return null;
    }
  }


}
