package com.unitesk.requality.tool;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.core.sorting.TreeNodeComparator;

/**
 * Индекс для упорядочивания множества объектов. Перед или после любого индекса, а также между
 * любыми двумя индексам можно вставить ещё один. Это позволяет изменять индекс только одного
 * объекта при изменении его места в общем порядке.
 *
 * <p>Индекс N.F имеет натуральную N и дробную F части. N и F - хранятся как строки, т.е.
 * ограничение на величину значительно больше, чем у int.
 */
public class Index implements Comparable<Index> {
  private static String NumberRegex = "(-){0,1}\\d*";
  public static String MAX_VALUE;
  static{
    char[]c = new char[255];
    Arrays.fill(c, '9');
    MAX_VALUE = new String(c);
  }
  private static Index undefInd = Index.create(MAX_VALUE);
  private static final char D0 = '0';
  private static final char D1 = '1';
  private static final char D9 = '9';
  private static final int N10 = 10;
  private static Index Index0 = create(D0 + "");
  protected String natural;
  protected String fraction;

  public boolean isUndefined() {
    return undefInd == this;
  }

  private Index(String natural, String fraction) {
    // ЦЕЛАЯ ЧАСТЬ
    // не хотим проверять на null
    if (natural == null) natural = "";
    // только цифры
    if (!natural.matches(NumberRegex))
      throw new IllegalArgumentException("illegal natural part: " + natural);
    // нормализация - убираем лидирующие 0
    int first = 0;
    boolean minus = false;
    while (natural.startsWith("-")) {
      minus = !minus;
      natural = natural.substring(1);
    }
    for (; first < natural.length(); first++) {
      if (natural.charAt(first) != D0) break;
    }
    if (first > 0) natural = natural.substring(first);

    // ДРОБНАЯ ЧАСТЬ
    // не хотим проверять на null
    if (fraction == null) fraction = "";
    // только цифры
    if (!fraction.matches(NumberRegex))
      throw new IllegalArgumentException("illegal number fraction part: " + fraction);
    // нормализация - убираем 0 в конце
    int last = fraction.length() - 1;
    for (; last >= 0; last--) {
      if (fraction.charAt(last) != D0) break;
    }
    if (last < fraction.length() - 1) fraction = fraction.substring(0, last + 1);
    if (natural.equals("") && fraction.equals("")) {
      natural = "0";
      minus = false;
    }
    this.natural = (minus ? "-" : "") + natural;
    this.fraction = fraction;
  }

  protected static Index create(String natural, String fraction) {
    return new Index(natural, fraction);
  }

  public static Index create(String str) {
    if(undefInd!=null && str.equals(MAX_VALUE))
      return undefInd;
	int dotIndex = str.indexOf('.');
    String natural;
    String fraction;
    if (dotIndex >= 0) {
      natural = str.substring(0, dotIndex);
      fraction = str.substring(dotIndex + 1);
    } else {
      natural = str;
      fraction = "";
    }
    if (!natural.matches(NumberRegex) || !fraction.matches(NumberRegex)) {
      natural = "";
      fraction = "";
    }
    return create(natural, fraction);
  }

  public static Index createOne() {
    return create("" + D1);
  }

  public static Index getZero() {
    return Index0;
  }

  @Override
  public String toString() {
    if (fraction.length() > 0) {
      return natural + '.' + fraction;
    } else {
      if (natural.length() > 0) {
        return natural;
      } else {
        return "" + D0;
      }
    }
  }

  @Override
  public int compareTo(Index index) {
    if(index == undefInd)
    {
      if(this != undefInd)
        return -1;
    }else
      if(this == undefInd)
        return 1;
	int res = compareNaturalSigned(natural, index.natural);
    if (res != 0) return res;
    return compareFraction(
        fraction, index.fraction, natural.startsWith("-"), index.natural.startsWith("-"));
  }

  public Index findGreaterIndex() {
    if (isUndefined()) return undefInd; // bad situation :(
    return create(incNatural(natural), null);
  }

  private boolean sumMoreOne(String s, String s2) {
    while(s.length()<s2.length())
      s = s + "0";
    while(s2.length()<s.length())
      s2 = s2 + "0";
    s = sumNatural(s, s2);
    return s.length()>s2.length();
  }

  public Index findMiddleIndex(Index other) {
    if (other.isUndefined()) return this.findGreaterIndex();
    if (this.isUndefined()) return other.findGreaterIndex();
    int compare = compareTo(other);
    if (compare == 0) return this;
    if (compare > 0) return other.findMiddleIndex(this);

    if (compareNaturalSigned(natural, other.natural) < 0) {
      String[] middleNat =
          middleNatural(natural, other.natural, sumMoreOne(fraction, other.fraction)).split("\\|");
      return Index.create(middleNat[0], middleFraction(fraction, other.fraction, middleNat[1]));
    } else {
      return Index.create(natural, middleFraction(fraction, other.fraction, "0"));
    }
  }

  private static String middleNatural(String natural2, String natural3, boolean sumMoreOne) {
    return div2Natural(sumNatural(natural2, natural3, sumMoreOne), sumMoreOne);
  }

  static int compareNatural(String left, String right) {
    if (left.length() == 0) left = "0";
    if (right.length() == 0) right = "0";
    int leftLength = left.length();
    int rightLength = right.length();
    if (leftLength < rightLength) return -1;
    if (leftLength > rightLength) return 1;
    for (int i = 0; i < leftLength; i++) {
      char leftDigit = left.charAt(i);
      char rightDigit = right.charAt(i);
      if (leftDigit < rightDigit) return -1;
      if (leftDigit > rightDigit) return 1;
    }
    return 0;
  }

  static int compareNaturalSigned(String left, String right) {
    boolean m1 = left.startsWith("-"), m2 = right.startsWith("-");
    if (m1 && m2) return -compareNatural(left, right);
    if (!m1 && !m2) return compareNatural(left, right);
    if (m1 && !m2) return -1;
    return 1;
  }

  static String incNatural(String natural) {
    StringBuilder res = new StringBuilder();
    boolean extra = true;
    boolean minus = false;
    if (natural.startsWith("-")) {
      minus = true;
      natural = natural.substring(1);
    }
    for (int i = natural.length() - 1; i >= 0; i--) {
      char digit = natural.charAt(i);
      int d = char2digit(digit);
      if (extra) {
        if (digit == D9 && !minus) {
          res.append(D0);
        } else {
          if (digit == D0 && minus) {
            res.append(D9);
          } else {
            res.append(digit2char(d + (minus ? -1 : 1)));
            extra = false;
          }
        }
      } else {
        res.append(natural.charAt(i));
      }
    }
    if (extra && !minus) res.append(D1);
    if (extra && minus) minus = false;
    return (minus ? "-" : "") + res.reverse().toString();
  }

  // not recommended to use it for negative indexes
  static String middleNatural(String n1, String n2) {
    return middleNatural(n1, n2, false);
  }

  // not recommended to use it for negative indexes
  static String sumNatural(String n1, String n2) {
    return sumNatural(n1, n2, false);
  }

  static String sumNatural(String n1, String n2, boolean sumMoreOne) {
    if(n1.equals(MAX_VALUE) || n2.equals(MAX_VALUE))
      return MAX_VALUE;
    StringBuilder res = new StringBuilder();
    int extra = 0;
    while (n1.length() < n2.length()) n1 = "0" + n1;
    while (n2.length() < n1.length()) n2 = "0" + n2;
    for (int i1 = n1.length() - 1, i2 = n2.length() - 1; i1 >= 0 || i2 >= 0; i1--, i2--) {
      int d1 = i1 >= 0 ? char2digit(n1.charAt(i1)) : 0;
      int d2 = i2 >= 0 ? char2digit(n2.charAt(i2)) : 0;
      int d = d1 + d2 + extra;
      if (sumMoreOne) {
        sumMoreOne = false;
        d++;
      }
      if (d >= N10) {
        d -= N10;
        extra = 1;
      } else {
        extra = 0;
      }
      res.append(digit2char(d));
    }
    if (extra == 1) res.append(D1);

    return res.reverse().toString();
  }

  static String div2Natural(String n) {
    return div2Natural(n, false);
  }

  static String div2Natural(String n, boolean minormoreone) {
    if(n.equals(MAX_VALUE))
      return MAX_VALUE;
    StringBuilder res = new StringBuilder();
    int extra = 0;
    boolean first = true;
    for (int i = 0; i < n.length(); i++) {
      int d = char2digit(n.charAt(i)) + extra * N10;
      extra = d % 2;
      // don't append leading zeroes
      if (!first || d > 1) {
        res.append(digit2char(d / 2));
        first = false;
      }
    }
    res.append("|");
    if (minormoreone) extra = 1;
    res.append(extra);
    return res.toString();
  }

  static int compareFraction(String left, String right, boolean minusleft, boolean minusright) {
    if (minusleft && !minusright) return -1;
    if (!minusleft && minusright) return 1;
    if (minusleft && minusright) return -left.compareTo(right);
    return left.compareTo(right);
    // int leftLength = left.length();
    // int rightLength = right.length();
    // int length = Math.min( leftLength, rightLength );
    // for( int i = 0; i < length; i++ )
    // {
    // char leftDigit = left.charAt(i);
    // char rightDigit = right.charAt(i);
    // if( leftDigit < rightDigit ) return -1;
    // if( leftDigit > rightDigit ) return 1;
    // }
    // if( leftLength < rightLength ) return -1;
    // if( leftLength > rightLength ) return 1;
    // return 0;
  }

  static String middleFraction(String f1, String f2, String add) {
    return div2Fraction(sumFraction(f1, f2), add);
  }

  static String sumFraction(String left, String right) {
    StringBuilder res = new StringBuilder();
    int leftLength = left.length();
    int rightLength = right.length();
    int length = Math.max(leftLength, rightLength);
    int extra = 0;
    boolean first = true;
    for (int i = length - 1; i >= 0; i--) {
      int d1 = i < leftLength ? char2digit(left.charAt(i)) : 0;
      int d2 = i < rightLength ? char2digit(right.charAt(i)) : 0;
      int d = d1 + d2 + extra;
      if (d >= N10) {
        d -= N10;
        extra = 1;
      } else {
        extra = 0;
      }
      if (!first || d > 0) {
        res.append(digit2char(d));
        first = false;
      }
    }
    if (extra == 1) {
      res.append('.');
      res.append(D1);
    }

    return res.reverse().toString();
  }

  static String div2Fraction(String fraction) {
    return div2Fraction(fraction, "0");
  }

  static String div2Fraction(String fraction, String add) {
    int extra = add.equals("1") ? 1 : 0;
    if (fraction.startsWith("" + D1 + '.')) {
      fraction = fraction.substring(2);
      extra = (extra + 1) % 2;
    }
    StringBuilder res = new StringBuilder();
    for (int i = 0; i < fraction.length(); i++) {
      int d = char2digit(fraction.charAt(i)) + extra * N10;
      extra = d % 2;
      res.append(digit2char(d / 2));
    }
    if (extra > 0) res.append(digit2char(extra * N10 / 2));
    return res.toString();
  }

  static String greaterFraction(String fraction) {
    StringBuilder res = new StringBuilder();
    boolean found = false;
    for (int i = 0; i < fraction.length(); i++) {
      int d = char2digit(fraction.charAt(i));
      int r = (d + N10) / 2;
      res.append(r);
      if (r > d) {
        found = true;
        break;
      }
    }
    if (!found) res.append(digit2char(N10 / 2));
    return res.toString();
  }

  static int char2digit(char ch) {
    return ch - D0;
  }

  static char digit2char(int digit) {
    return (char) (digit + D0);
  }

  public static Index getNextIndex(String type, TreeNode parent, TreeNode node) {
    return getNextIndex(type, parent, node, true);
  }

  public static SortingSettings DEFAULT_INDEX_SORTING =
      SortingSettings.init(SortingMethod.BY_INDEX, false);

  public static Index getFuncOrIndex(TreeNode node, Map<UUID, Index> func) {
    Index i = func.get(node.getUUId());
    if (i == null) {
      i = node.getIndex();
    }
    return i;
  }

  public static Index getNextIndex(TreeNode parent, Map<UUID, Index> indexfunc, TreeNode node) {
    Index ind = Index.createOne();
    if (node == parent) node = null; // TODO check
    List<TreeNode> children =
        parent.getSortedShownChildren(
            new Comparator<TreeNode>() {
              @Override
              public int compare(TreeNode o1, TreeNode o2) {
                Index i1 = getFuncOrIndex(o1, indexfunc);
                Index i2 = getFuncOrIndex(o2, indexfunc);
                if (i1 == null) return i2 == null ? 0 : -1;
                if (i2 == null) return 1;
                return i1.compareTo(i2);
              }
            },
            null);
    boolean found = false;
    if (node != null) {
      int size = children.size();
      int i = 0;
      for (TreeNode n1:children) {
//        TreeNode n1 = children[i];
        if (n1.getUUId().equals(node.getUUId())) {
          found = true;
          if (i < size - 1) {
            TreeNode n2 = children.get(i+1);
            return getFuncOrIndex(n1, indexfunc).findMiddleIndex(getFuncOrIndex(n2, indexfunc));
          } else {
            return getFuncOrIndex(n1, indexfunc).findGreaterIndex();
          }
        }
        i++;
      }
    }
    if (!found || node == null) {
      if (children.size() > 0) {
        {
          Index indx = getFuncOrIndex(children.get(children.size() - 1), indexfunc);
          if (!indx.isUndefined()) ind = indx.findGreaterIndex();
          else ind = indx; // bad situation....
        }
      }
    }
    return ind;
  }

  public static Index getNextIndex(String type, TreeNode parent, TreeNode node, boolean tobegin) {
    // TODO type may be deprecated
    Index ind = Index.createOne();
    if (node == parent) node = null; // TODO check
    List<TreeNode> children =
        parent.getSortedShownChildren(
            TreeNodeComparator.getComparator(DEFAULT_INDEX_SORTING), null);
    boolean found = false;
    if (node != null) {
      int i = 0, size = children.size();
      for (TreeNode n1:children) {
        //TreeNode n1 = children[i];
        if (n1.getUUId().equals(node.getUUId())) {
          found = true;
          if (i < size - 1) {
            TreeNode n2 = children.get(i + 1);
            return n1.getIndex().findMiddleIndex(n2.getIndex());
          } else {
            return n1.getIndex().findGreaterIndex();
          }
        }
        i++;
      }
    }
    if (!found || node == null) {
      if (children.size() > 0) {
        if (tobegin) {
          Index indx = children.iterator().next().getIndex();
          if (indx.isUndefined()) ind = new Index("1", "");
          else ind = indx.findMiddleIndex(Index.getZero());
        } else {
          Index indx = children.get(children.size()-1).getIndex();
          if (!indx.isUndefined()) ind = indx.findGreaterIndex();
          else ind = indx; // bad situation....
        }
      }
    }
    return ind;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Index) {
      Index sec = (Index) obj;
      return compareTo(sec) == 0;
    }
    return false;
  }

  public static Index getUndefined() {
    return undefInd;
  }
}
