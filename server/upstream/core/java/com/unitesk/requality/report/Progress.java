package com.unitesk.requality.report;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.tool.CoreUtils;

public class Progress {
  URI filePath;
  private String pathGraph;
  private int width = 0, height = 10;
  private int begin = 0, end = -1;
  private double x = 1, y = 1;

  private SimpleDateFormat format =
      new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
  private Calendar date = Calendar.getInstance(),
      dateFrom = Calendar.getInstance(),
      dateTo = Calendar.getInstance();
  private AffineTransform at = AffineTransform.getRotateInstance(-Math.PI / 2, 0, 0);
  private BufferedImage ox, oy, img, nimg, lg;
  private Graphics2D g, ng, gOx, gOy, glg;

  private int[] redPercent = new int[0];
  private int[] greenPercent = new int[0];
  private int[] redCount = new int[0];
  private int[] greenCount = new int[0];

  private int[] rqCount = new int[0];
  private int[] tpCount = new int[0];
  private int[] leaves = new int[0];

  private String[] arrayDate = new String[0];
  private String[] arrayAuthor = new String[0];
  private Integer[] arrayRevision = new Integer[0];

  private boolean overlap = false;
  private Map<String, int[]> arrays = new HashMap<>();

  public Progress() {}

  public Progress(URI uri, String pathGraph) {
    this.filePath = new File(uri).getParentFile().toURI();
    // if (pathGraph.startsWith("file:"))
    // pathGraph = pathGraph.substring(5);
    this.pathGraph = pathGraph;
  }

  public String getDateTime() {
    return Calendar.getInstance().getTime().toString();
  }

  public boolean getOverlap() {
    return overlap;
  }

  public String getFunctionString(Integer type) {
    StringBuilder funcArgs = new StringBuilder();
    int[] arrayPoints;

    funcArgs.append("[");
    arrayPoints = arrays.get("pointsX");
    for (int i = 0; i < arrayPoints.length - 2; i++) {
      funcArgs.append(arrayPoints[i] + ",");
    }
    funcArgs.append(arrayPoints[arrayPoints.length - 1] + "],[");

    switch (type) {
      case 1: // for 'count'
        arrayPoints = arrays.get("firstY");
        for (int i = 0; i < arrayPoints.length - 2; i++) {
          funcArgs.append(arrayPoints[i] + ",");
        }
        funcArgs.append(arrayPoints[arrayPoints.length - 1] + "],[");

        arrayPoints = arrays.get("secondY");
        for (int i = 0; i < arrayPoints.length - 2; i++) {
          funcArgs.append(arrayPoints[i] + ",");
        }
        funcArgs.append(arrayPoints[arrayPoints.length - 1] + "],[");

        arrayPoints = arrays.get("thirdY");
        for (int i = 0; i < arrayPoints.length - 2; i++) {
          funcArgs.append(arrayPoints[i] + ",");
        }
        funcArgs.append(arrayPoints[arrayPoints.length - 1] + "]");
        break;
      case 2: // for 'percent'
        arrayPoints = arrays.get("firstY");
        for (int i = 0; i < arrayPoints.length - 2; i++) {
          funcArgs.append(arrayPoints[i] + ",");
        }
        funcArgs.append(arrayPoints[arrayPoints.length - 1] + "],[");

        arrayPoints = arrays.get("secondY");
        for (int i = 0; i < arrayPoints.length - 2; i++) {
          funcArgs.append(arrayPoints[i] + ",");
        }
        funcArgs.append(arrayPoints[arrayPoints.length - 1] + "],[]");
        break;
      case 3: // for 'amount'
        arrayPoints = arrays.get("secondY");
        for (int i = 0; i < arrayPoints.length - 2; i++) {
          funcArgs.append(arrayPoints[i] + ",");
        }
        funcArgs.append(arrayPoints[arrayPoints.length - 1] + "],[");

        arrayPoints = arrays.get("thirdY");
        for (int i = 0; i < arrayPoints.length - 2; i++) {
          funcArgs.append(arrayPoints[i] + ",");
        }
        funcArgs.append(arrayPoints[arrayPoints.length - 1] + "],[]");
        break;
    }
    return funcArgs.toString();
  }

  public int getImgHeight(Integer extr) {
    return img.getWidth() + extr;
  }

  public int getBegin() {
    return begin;
  }

  public int getEnd() {
    return end;
  }

  public boolean setBeginEnd(String dateAfter, String dateBefore) {
    if (setDates(dateAfter, dateBefore) == false) {
      return false;
    }
    setBeginEnd();
    if ((this.end <= 0) || (this.begin >= this.end)) {
      RLogger.logInfo("There is less then 2 revisions between these dates.");
      return false;
    }
    return true;
  }

  public Map<String, Integer> getArrayData(Integer num) {
    Map<String, Integer> data = new HashMap<>();
    data.put("number of requirements", rqCount[num] / 10);
    data.put("number of test purposes", tpCount[num] / 10);
    data.put("number of leaves", leaves[num] / 10);
    data.put("number coverage (green status)", greenCount[num] / 10);
    data.put("number coverage (red status)", redCount[num] / 10);
    data.put("percent coverage (green status)", Math.round((float) greenPercent[num] / 10));
    data.put("percent coverage (red status)", Math.round((float) redPercent[num] / 10));
    return data;
  }

  public String[] getarrayDate() {
    return arrayDate;
  }

  public Integer[] getarrayRevision() {
    return arrayRevision;
  }

  public void readReport(String path) {
    try {
      File file = new File(URI.create(filePath + ".reqcache/statistics_" + path + ".xml"));
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(file);
      doc.getDocumentElement().normalize();
      NodeList nlist = doc.getElementsByTagName("statistic");
      width = 0;

      for (int s = 0; s < nlist.getLength(); s++) {
        Node node = nlist.item(s);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          Element elm = (Element) node;
          if (!elm.hasAttribute("noChild") && node.hasChildNodes()) {
            width++;
          }
        }
      }
      if (width > 0) {
        arrayDate = new String[width];
        arrayAuthor = new String[width];
        arrayRevision = new Integer[width];
        redPercent = new int[width];
        greenCount = new int[width];
        redCount = new int[width];
        greenPercent = new int[width];
        rqCount = new int[width];
        tpCount = new int[width];
        leaves = new int[width];

        int n = 0;
        for (int s = 0; s < nlist.getLength(); s++) {
          float sum = 0;
          float green = 0;
          float red = 0;
          Node node = nlist.item(s);
          if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element elm = (Element) node;
            if (!elm.hasAttribute("noChild") && node.hasChildNodes()) {
              arrayDate[n] = elm.getAttribute("time");
              arrayAuthor[n] = elm.getAttribute("author");
              try {
                arrayRevision[n] = Integer.parseInt(elm.getAttribute("numRevision"));
              } catch (NumberFormatException e) {
                arrayRevision[n] = n;
              }
              if (node.hasChildNodes()) {
                try {
                  rqCount[n] =
                      Integer.parseInt(
                              elm.getElementsByTagName("reqCount")
                                  .item(0)
                                  .getAttributes()
                                  .getNamedItem("value")
                                  .getNodeValue())
                          * 10;
                  tpCount[n] =
                      Integer.parseInt(
                              elm.getElementsByTagName("tpCount")
                                  .item(0)
                                  .getAttributes()
                                  .getNamedItem("value")
                                  .getNodeValue())
                          * 10;
                  leaves[n] =
                      Integer.parseInt(
                              elm.getElementsByTagName("leaves")
                                  .item(0)
                                  .getAttributes()
                                  .getNamedItem("value")
                                  .getNodeValue())
                          * 10;
                } catch (NumberFormatException e) {
                  System.err.println("String wrong format!");
                }

                NodeList nlelm = elm.getElementsByTagName("statuses");
                Element nelm = (Element) nlelm.item(0);
                NodeList selm = nelm.getElementsByTagName("status");
                for (int l = 0; l < selm.getLength(); l++) {
                  Element celm = (Element) selm.item(l);
                  NamedNodeMap att = celm.getAttributes();
                  try {
                    if (att.getNamedItem("name").getNodeValue().equals("green")) {
                      sum += Float.parseFloat(att.getNamedItem("value").getNodeValue());
                      green += Float.parseFloat(att.getNamedItem("value").getNodeValue());
                    }
                    if (att.getNamedItem("name").getNodeValue().equals("red")) {
                      sum += Float.parseFloat(att.getNamedItem("value").getNodeValue());
                      red += Float.parseFloat(att.getNamedItem("value").getNodeValue());
                    }
                  } catch (NumberFormatException e) {
                    System.err.println(
                        "String wrong format! String: " + att.getNamedItem("value").getNodeValue());
                  }
                }

                greenCount[n] = (int) green * 10;
                redCount[n] = (int) red * 10;
                if (sum > 0) {
                  greenPercent[n] = Math.round(green * 1000 / sum);
                  redPercent[n] = Math.round(red * 1000 / sum);
                } else {
                  RLogger.logInfo("Sum of coverages is zero! There is no coverage!");
                }
              }
              n++;
            }
          }
        }
        int max = 0;
        for (int j = 0; j < width; j++) {
          if (max < rqCount[j]) {
            max = rqCount[j];
          }
        }
        max = 0;
        for (int j = 0; j < width; j++) {
          if (max < tpCount[j]) {
            max = tpCount[j];
          }
        }
      }
    } catch (FileNotFoundException e) {
      RLogger.logInfo(e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unchecked")
  public String generateGraphicSpecial(
      String graphType,
      String dateAfter,
      String dateBefore,
      String step,
      String scaleX,
      String scaleY,
      String autoscale)
      throws ParseException {

    if (width <= 0) {
      return "There is no information in revisions.";
    }
    ArrayList<Integer> arrayPoints = new ArrayList<>();
    ArrayList<Date> Dates = new ArrayList<>();
    int start = 0, points = 0;
    Integer periodStep = 0;
    height = 10;

    StringBuilder graphname = new StringBuilder(step + "_");
    graphname.append(graphType);
    graphname.append(dateFrom.getTimeInMillis() + "_");
    graphname.append(dateTo.getTimeInMillis());
    String imgPath =
        "<img style=\"max-width:900px; max-height:900px\" alt=\"User's Coverage Spesial Graphic\" src=\"./"
            + "graphics/"
            + graphname
            + ".png\"/><br>";

    if (setDates(dateAfter, dateBefore) == false) {
      return "Wrong date format or wrong dates (date 'from' can't be equals or after date 'to').";
    }
    setBeginEnd();

    if ((step == null) || (step.isEmpty())) {
      Integer diff =
          Math.round(
                  (this.dateTo.getTimeInMillis() - this.dateFrom.getTimeInMillis())
                      / (24 * 60 * 60 * 1000))
              + 1;
      if (diff <= 21 * 7) {
        diff = diff / 21 + 1;
        step = diff.toString() + " day";
      } else if (diff <= 21 * 30) {
        diff = diff / (7 * 21) + 1;
        step = diff.toString() + " week";
      } else if (diff <= 21 * 30 * 12) {
        diff = diff / (30 * 21) + 1;
        step = diff.toString() + " month";
      } else {
        diff = diff / (365 * 21) + 1;
        step = diff.toString() + " year";
      }
    }

    if (step.length() > 0) {
      if ((this.end > 0) && (this.begin < this.end)) {
        start = this.begin;
        int prd = 0;
        Map<Integer, Integer> stepMap = new HashMap<>();
        while (step.length() > 0) {
          step = step.trim();
          int c = 0;
          while (Character.isDigit(step.charAt(c))) c++;
          periodStep = Integer.parseInt(step.substring(0, c));
          while (c < step.length() && Character.isWhitespace(step.charAt(c))) c++;
          int p = c;
          while (p < step.length() && step.charAt(p) != ' ') p++;
          String period = step.substring(c, p);
          step = step.substring(p);
          if (period.equals("")) prd = Calendar.DATE;
          if (period.equals("year") || period.equals("y")) {
            prd = Calendar.YEAR;
          } else if (period.equals("month") || period.equals("m")) {
            prd = Calendar.MONTH;
          } else if (period.equals("week") || period.equals("w")) {
            prd = Calendar.DATE;
            periodStep = periodStep * 7;
          } else if (period.equals("day") || period.equals("d")) {
            prd = Calendar.DATE;
          } else {
            return "Wrong peroid. Period should be: {day(d), week(w), month(m), year(y)}.";
          }
          stepMap.put(prd, (stepMap.containsKey(prd) ? stepMap.get(prd) : 0) + periodStep);
        }
        arrayPoints.add(this.begin);
        while (dateFrom.before(dateTo)) {
          Dates.add((Date) dateFrom.getTime().clone());
          for (Integer i : stepMap.keySet()) dateFrom.add(i, stepMap.get(i));
          for (int i = this.begin; i <= this.end; i++) {
            try {
              date.setTime(format.parse(arrayDate[i]));
              if (date.before(dateFrom) || date.equals(dateFrom)) {
                points++;
                this.begin = i + 1;
              }
            } catch (ParseException e) {
              RLogger.logInfo("Unparseable date: " + arrayDate[i]);
            }
          }
          arrayPoints.add(points);
          points = 0;
        }
        Dates.add((Date) this.dateTo.getTime().clone());
        if ((arrayPoints.get(1) == 0) && (arrayPoints.get(0) > 0)) {
          arrayPoints.set(0, arrayPoints.get(0) - 1);
          arrayPoints.set(1, 1);
        }
        this.width = arrayPoints.size() - 1;
      } else {
        return "There is less then 2 revisions between these dates.";
      }
    }

    setMax(graphType, start, this.end);
    if (setScale(autoscale, scaleX, scaleY, graphType) == false) {
      return "Wrong scale. Scale should be an integer number.";
    }

    this.ox = new BufferedImage(65, (int) (width * 20 * x) + 75, BufferedImage.TYPE_INT_RGB);
    this.img =
        new BufferedImage(
            (int) (2 * height * y) + 25, (int) (width * 20 * x) + 25, BufferedImage.TYPE_INT_RGB);
    createImage(graphType);

    gOx.setColor(Color.black);
    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
    SimpleDateFormat stf = new SimpleDateFormat("HH:mm:ss");
    gOx.setFont(new Font("TimesRoman", Font.PLAIN, 10));
    double n = 0;
    if (x < 1) {
      for (int i = 0; i <= width * 20 * x; i += 20) {
        gOx.drawString(sdf.format(Dates.get((int) n)), 1, i + 7);
        gOx.drawString(stf.format(Dates.get((int) n)), 1, i + 15);
        n = n + 1 / x;
      }
    } else {
      for (int i = 0; i <= width * 20 * x; i += 20 * x) {
        gOx.drawString(sdf.format(Dates.get((int) n)), 1, i + 7);
        gOx.drawString(stf.format(Dates.get((int) n)), 1, i + 15);
        n++;
      }
    }

    paintLines();
    if (graphType.equals("percent")) {
      if (isCrossLines(redPercent.clone(), greenPercent.clone(), start, end)) {
        paintGraphLine(
            (ArrayList<Integer>) arrayPoints.clone(),
            redPercent,
            graphname + "_redPercent",
            height,
            width,
            3);
        paintGraphLine(
            (ArrayList<Integer>) arrayPoints.clone(),
            greenPercent,
            graphname + "_greenPercent",
            height,
            width,
            1);
        imgPath =
            "<img style=\"max-width:900px; max-height:900px\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + ".png\"/> \n"
                + "<img class=\"percent\" id=\"ncp\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 20;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_redPercent.png\"/> \n"
                + "<img class=\"percent\" id=\"cp\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 30;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_greenPercent.png\"/> \n";
      } else {
        paintSpecial(g, width, height, (ArrayList<Integer>) arrayPoints.clone(), redPercent, 3);
        paintSpecial(g, width, height, (ArrayList<Integer>) arrayPoints.clone(), greenPercent, 1);
      }
    } else if (graphType.equals("count")) {
      if (isCrossLines(redCount.clone(), greenCount.clone(), start, end)
          || isCrossLines(leaves.clone(), redCount.clone(), start, end)) {
        paintGraphLine(
            (ArrayList<Integer>) arrayPoints.clone(),
            redCount,
            graphname + "_redCount",
            height,
            width,
            3);
        paintGraphLine(
            (ArrayList<Integer>) arrayPoints.clone(),
            greenCount,
            graphname + "_greenCount",
            height,
            width,
            1);
        paintGraphLine(
            (ArrayList<Integer>) arrayPoints.clone(),
            leaves,
            graphname + "_leaves",
            height,
            width,
            4);
        imgPath =
            "<img style=\"max-width:900px; max-height:900px\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + ".png\"/> \n"
                + "<img class=\"count\" id=\"ncc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 10;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_redCount.png\"/> \n"
                + "<img class=\"count\" id=\"cc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 20;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_greenCount.png\"/> \n"
                + "<img class=\"count\" id=\"lc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 30;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_leaves.png\"/> \n";
      } else {
        paintSpecial(g, width, height, (ArrayList<Integer>) arrayPoints.clone(), redCount, 3);
        paintSpecial(g, width, height, (ArrayList<Integer>) arrayPoints.clone(), greenCount, 1);
        paintSpecial(g, width, height, (ArrayList<Integer>) arrayPoints.clone(), leaves, 4);
      }
    } else if (graphType.equals("amount")) {
      if (isCrossLines(tpCount.clone(), rqCount.clone(), start, end)) {
        paintGraphLine(
            (ArrayList<Integer>) arrayPoints.clone(),
            tpCount,
            graphname + "_tpCount",
            height,
            width,
            2);
        paintGraphLine(
            (ArrayList<Integer>) arrayPoints.clone(),
            rqCount,
            graphname + "_rqCount",
            height,
            width,
            4);
        imgPath =
            "<img style=\"max-width:900px; max-height:900px\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + ".png\"/> \n"
                + "<img class=\"amount\" id=\"tpc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 20;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_tpCount.png\"/> \n"
                + "<img class=\"amount\" id=\"rqc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 30;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_rqCount.png\"/> \n";
      } else {
        paintSpecial(g, width, height, (ArrayList<Integer>) arrayPoints.clone(), tpCount, 2);
        paintSpecial(g, width, height, (ArrayList<Integer>) arrayPoints.clone(), rqCount, 4);
      }
    }

    ng.drawImage(lg, 10, img.getWidth() + 100, null);
    ng.transform(at);
    ng.drawImage(img, -img.getWidth() - 35, 40, null);
    ng.drawImage(ox, -img.getWidth() - 85, 50, null);

    disposeImage();

    try {
      File folder = new File(URI.create(pathGraph + "/graphics"));
      folder.mkdirs();
      ImageIO.write(
          nimg, "png", new File(URI.create(pathGraph + "/graphics/" + CoreUtils.URLEncode(graphname.toString()) + ".png")));
    } catch (IOException io) {
      io.printStackTrace();
    }
    return imgPath;
  }

  public void paintSpecial(
      Graphics2D g, int width, int height, ArrayList<Integer> arrayPoints, int[] array, int type) {

    int offsetY = 21, offsetX = 15;
    int from = arrayPoints.get(0);
    int[] y = {offsetY, offsetY}, x = {offsetX, offsetX};
    int[] yArray = {offsetY}, xArray = {offsetX};
    ArrayList<Integer> yArrayFull = new ArrayList<>(), xArrayFull = new ArrayList<>();

    if (arrayPoints.get(1) != 0) {
      y[1] = (int) (array[from] * 2 * this.y + offsetY);
    }

    for (int i = 1; i <= width; ) {
      if (arrayPoints.get(i) != 0) {

        int length = arrayPoints.get(i);
        int[] Array = new int[length];
        System.arraycopy(array, from, Array, 0, length);
        yArray = new int[length * 4];
        xArray = new int[length * 4];

        yArray[0] = y[1];
        for (int j = 0; j < Array.length; j++) {
          yArray[j * 4 + 1] = (int) (Array[j] * 2 * this.y + offsetY);
          yArray[j * 4 + 2] = (int) (Array[j] * 2 * this.y + offsetY);
          yArray[j * 4 + 3] = (int) (Array[j] * 2 * this.y + offsetY);
          if ((j * 4 + 4) < yArray.length) {
            yArray[j * 4 + 4] = (int) (Array[j] * 2 * this.y + offsetY);
          }
        }

        xArray[0] = x[1];
        int n = 0;
        for (int j = 1; j < xArray.length - 1; j++) {
          n++;
          xArray[j] = xArray[j - 1];
          j++;
          xArray[j] = xArray[j - 1];
          j++;
          xArray[j] = (int) (n * 20 * this.x / length + xArray[0]);
          j++;
          if (j < xArray.length) {
            xArray[j] = xArray[j - 1];
          }
        }
        from += arrayPoints.get(i);
        arrayPoints.set(0, from);
        x[1] = xArray[xArray.length - 1];
        y[1] = yArray[yArray.length - 1];
        i++;

        for (int j = 0; j < length * 4; j++) {
          yArrayFull.add(Integer.valueOf(yArray[j]));
          xArrayFull.add(Integer.valueOf(xArray[j]));
        }

      } else {
        while (arrayPoints.get(i) == 0) {
          i++;
          if (i > width) break;
        }
        if (i <= width) {
          x[0] = xArray[xArray.length - 1];
          x[1] = (int) (20 * (i - 1) * this.x + offsetX);
          y[0] = yArray[yArray.length - 1];
          y[1] = y[0];
        } else {
          x[0] = xArray[xArray.length - 1];
          x[1] = (int) (20 * i * this.x + offsetX);
          y[0] = y[1] = yArray[yArray.length - 1];
        }
        for (int j = 0; j < 2; j++) {
          yArrayFull.add(Integer.valueOf(y[j]));
          xArrayFull.add(Integer.valueOf(x[j]));
        }
      }
    }

    int size = yArrayFull.size();
    yArray = new int[size];
    xArray = new int[size];
    for (int i = 0; i < size; i++) {
      yArray[i] = yArrayFull.get(i).intValue();
      xArray[i] = xArrayFull.get(i).intValue();
    }

    g.setStroke(new BasicStroke(2));
    switch (type) {
      case 1:
        g.setColor(Color.green);
        arrays.put("firstY", yArray);
        break;
      case 2:
        g.setColor(new Color(255, 0, 102)); // pink
        arrays.put("secondY", yArray);
        break;
      case 3:
        g.setColor(Color.red);
        arrays.put("secondY", yArray);
        break;
      default:
        g.setColor(new Color(30, 64, 175)); // blue
        arrays.put("thirdY", yArray);
    }
    arrays.put("pointsX", xArray);
    g.drawPolyline(yArray, xArray, size);
  }

  public String generateGraphic(String graphType, String scaleX, String scaleY, String autoscale) {

    if (width <= 0) {
      return "There is no information in revisions.";
    }
    if (width == 1) {
      return "There is less then 2 revisions.";
    }
    StringBuilder graphname = new StringBuilder("simple_");
    graphname.append(graphType);
    String imgPath =
        "<img style=\"max-width:900px; max-height:900px\" alt=\"Graphic\" src=\"./"
            + "graphics/"
            + graphname
            + ".png\"/><br>";

    setMax(graphType, 0, width - 1);
    if (setScale(autoscale, scaleX, scaleY, graphType) == false) {
      return "Wrong scale. Scale should be an integer number.";
    }

    ox = new BufferedImage((int) (width * 20 * x) + 75, 20, BufferedImage.TYPE_INT_RGB);
    img =
        new BufferedImage(
            (int) (2 * height * y) + 25,
            (int) ((width - 1) * 20 * x) + 25,
            BufferedImage.TYPE_INT_RGB);
    createImage(graphType);

    if (x < 1) {
      double n = 0;
      for (int i = 0; i < width * 20 * x; i += 20) {
        gOx.drawString(arrayRevision[(int) n].toString(), i, 10);
        n = n + 1 / x;
      }
    } else {
      for (int i = 0; i < width; i++) {
        gOx.drawString(arrayRevision[i].toString(), (int) (i * 20 * x), 10);
      }
    }

    paintLines();
    if (graphType.equals("percent")) {
      if (isCrossLines(redPercent.clone(), greenPercent.clone())) {
        arrays = new HashMap<>();
        paintGraphLine(null, redPercent, graphname + "_redPercent", height, width, 3);
        paintGraphLine(null, greenPercent, graphname + "_greenPercent", height, width, 1);
        imgPath =
            "<img style=\"max-width:900px; max-height:900px\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + ".png\"/> \n"
                + "<img class=\"percent\" id=\"ncp\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 20;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_redPercent.png\"/> \n"
                + "<img class=\"percent\" id=\"cp\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 30;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_greenPercent.png\"/> \n";
      } else {
        paint(g, width, height, redPercent, 3);
        paint(g, width, height, greenPercent, 1);
      }
    } else if (graphType.equals("count")) {
      if (isCrossLines(redCount.clone(), greenCount.clone())
          || isCrossLines(leaves.clone(), redCount.clone())) {
        arrays = new HashMap<>();
        paintGraphLine(null, redCount, graphname + "_redCount", height, width, 2);
        paintGraphLine(null, greenCount, graphname + "_greenCount", height, width, 1);
        paintGraphLine(null, leaves, graphname + "_leaves", height, width, 4);
        imgPath =
            "<img style=\"max-width:900px; max-height:900px\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + ".png\"/> \n"
                + "<img class=\"count\" id=\"ncc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 10;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_redCount.png\"/> \n"
                + "<img class=\"count\" id=\"cc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 20;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_greenCount.png\"/> \n"
                + "<img class=\"count\" id=\"lc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 30;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_leaves.png\"/> \n";
      } else {
        paint(g, width, height, redCount, 3);
        paint(g, width, height, greenCount, 1);
        paint(g, width, height, leaves, 4);
      }
    } else if (graphType.equals("amount")) {
      if (isCrossLines(tpCount.clone(), rqCount.clone())) {
        arrays = new HashMap<>();
        paintGraphLine(null, tpCount, graphname + "_tpCount", height, width, 2);
        paintGraphLine(null, rqCount, graphname + "_rqCount", height, width, 4);
        imgPath =
            "<img style=\"max-width:900px; max-height:900px\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + ".png\"/> \n"
                + "<img class=\"amount\" id=\"tpc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 20;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_tpCount.png\"/> \n"
                + "<img class=\"amount\" id=\"rqc\" style=\"max-width:900px; max-height:900px; position: absolute; top: 37px; left: 42px; z-index: 30;\" alt=\"Graphic\" src=\"./graphics/"
                + graphname
                + "_rqCount.png\"/> \n";
      } else {
        paint(g, width, height, tpCount, 2);
        paint(g, width, height, rqCount, 4);
      }
    }

    ng.drawImage(lg, 10, img.getWidth() + 75, null);
    ng.drawImage(ox, 50, img.getWidth() + 35, null);
    ng.transform(at);
    ng.drawImage(img, -img.getWidth() - 35, 40, null);
    disposeImage();

    try {
      File folder = new File(URI.create(pathGraph + "/graphics"));
      folder.mkdirs();
      ImageIO.write(
          nimg, "png", new File(URI.create(pathGraph + "/graphics/" + CoreUtils.URLEncode(graphname.toString()) + ".png")));
    } catch (IOException io) {
      io.printStackTrace();
    }
    return imgPath;
  }

  public void paint(Graphics2D g, int width, int height, int[] array, int type) {

    int[] yArray = Arrays.copyOf(array, array.length);
    for (int j = 0; j < width; j++) {
      yArray[j] = (int) (yArray[j] * 2 * y + 21);
    }

    int[] xArray = new int[width];
    for (int i = 0; i < width; i++) {
      xArray[i] = (int) (i * 20 * x + 15);
    }

    g.setStroke(new BasicStroke(2));
    switch (type) {
      case 1:
        g.setColor(Color.green);
        arrays.put("firstY", yArray);
        break;
      case 2:
        g.setColor(new Color(255, 0, 102)); // pink
        arrays.put("secondY", yArray);
        break;
      case 3:
        g.setColor(Color.red);
        arrays.put("secondY", yArray);
        break;
      default:
        g.setColor(new Color(30, 64, 175)); // blue
        arrays.put("thirdY", yArray);
    }
    g.drawPolyline(yArray, xArray, width);
    arrays.put("pointsX", xArray);
  }

  private void paintLegenda(Graphics2D glg, BufferedImage lg, String grType) {
    glg.setBackground(Color.white);
    glg.clearRect(0, 0, lg.getWidth(), lg.getHeight());
    glg.setColor(Color.black);
    glg.drawString("Legenda:", 1, 10);
    glg.setStroke(new BasicStroke(2));
    if (grType.equals("count")) {
      glg.drawString("covered", 1, 25);
      glg.drawString("not covered", 1, 40);
      glg.drawString("leaves", 1, 55);
      glg.setColor(Color.green);
      glg.drawLine(90, 20, 180, 20);
      glg.setColor(Color.red);
      glg.drawLine(90, 35, 180, 35);
      glg.setColor(new Color(30, 64, 175)); // blue
      glg.drawLine(90, 50, 180, 50);
    } else if (grType.equals("percent")) {
      glg.drawString("covered", 1, 25);
      glg.drawString("not covered", 1, 40);
      glg.setColor(Color.green);
      glg.drawLine(90, 20, 180, 20);
      glg.setColor(Color.red);
      glg.drawLine(90, 35, 180, 35);
    } else if (grType.equals("amount")) {
      glg.drawString("requirements", 1, 25);
      glg.drawString("test purposes", 1, 40);
      glg.setColor(new Color(255, 0, 102)); // pink
      glg.drawLine(90, 35, 180, 35);
      glg.setColor(new Color(30, 64, 175)); // blue
      glg.drawLine(90, 20, 180, 20);
    }
  }

  private boolean setDates(String dateAfter, String dateBefore) {
    try {
      if ((dateAfter != null) && (!dateAfter.isEmpty())) {
        dateFrom.setTime(format.parse(dateAfter));
      } else {
        dateFrom.setTime(format.parse(arrayDate[0]));
      }
      if ((dateBefore != null) && (!dateBefore.isEmpty())) {
        dateTo.setTime(format.parse(dateBefore));
      } else {
        dateTo.setTime(format.parse(arrayDate[arrayDate.length - 1]));
      }
      if (dateFrom.equals(dateTo) || dateFrom.after(dateTo)) {
        RLogger.logInfo("Wrong dates. Date 'from' can't be equals or after date 'to'.");
        return false;
      }
    } catch (ParseException e) {
      RLogger.logInfo("Wrong date format: " + e.getLocalizedMessage());
      return false;
    }
    return true;
  }

  private boolean setScale(String autoscale, String scaleX, String scaleY, String graphType) {
    if ((autoscale.equals("true")) || (autoscale.isEmpty())) {
      if (this.height * 2 * this.y + 50 != 450) {
        this.y = (450 - 50) / (double) (this.height * 2);
      }
      if (this.width * 20 * this.x + 75 != 500) {
        this.x = (500 - 75) / (double) (this.width * 20);
      }
    } else {
      try {
        if ((scaleX != null) && (!scaleX.isEmpty())) {
          this.x = Double.valueOf(scaleX) / 100;
        }
        if (this.width * 20 * this.x + 75 < 380) {
          this.x = (380 - 75) / (double) (this.width * 20);
          RLogger.logInfo("Too small scale! New scale of x is " + this.x * 100 + "%");
        }
        if (this.width * 20 * this.x + 75 > 2000) {
          this.x = (2000 - 75) / (double) (this.width * 20);
          RLogger.logInfo("Too big scale! New scale of x is " + x * 100 + "%");
        }
        if ((scaleY != null) && (!scaleY.isEmpty())) {
          this.y = Double.valueOf(scaleY) / 100;
        }
        if (this.height * 2 * this.y + 50 < 450) {
          this.y = (450 - 50) / (double) (this.height * 2);
          RLogger.logInfo("Too small scale! New scale of y is " + this.y * 100 + "%");
        }
        if (this.height * 2 * this.y + 50 > 2050) {
          this.y = (2050 - 50) / (double) (this.height * 2);
          RLogger.logInfo("Too big scale! New scale of y is " + this.y * 100 + "%");
        }
      } catch (NumberFormatException e) {
        RLogger.logInfo("Unparseable scale: " + e.getLocalizedMessage());
        return false;
      }
    }

    if (graphType.equals("percent")) {
      if (this.y * 100 % 5 != 0) {
        this.y = (int) (this.y * 100 / 5) * 0.05;
        RLogger.logInfo(
            "For percent measure y should be a multiple of five. New y is " + this.y * 100 + "%");
      }
    }
    return true;
  }

  private void setMax(String graphType, int start, int to) {
    if (graphType.equals("percent")) {
      height = 1000;
    } else if (graphType.equals("count")) {
      for (int i = start; i <= to; i++) {
        if (height < leaves[i]) {
          height = leaves[i];
        }
      }
    } else if (graphType.equals("amount")) {
      for (int i = start; i <= to; i++) {
        if (height < rqCount[i]) {
          height = rqCount[i];
        }
      }
      for (int i = start; i <= to; i++) {
        if (height < tpCount[i]) {
          height = tpCount[i];
        }
      }
    }
  }

  private void setBeginEnd() {
    this.begin = 0;
    this.end = -1;

    try {
      for (int i = 0; i < this.arrayDate.length; i++) {
        this.date.setTime(this.format.parse(this.arrayDate[i]));
        if (this.date.before(this.dateFrom) || this.date.equals(this.dateFrom)) {
          this.begin = i;
        }
        if (this.date.before(this.dateTo) || this.date.equals(this.dateTo)) {
          this.end = i;
        }
      }

      this.date.setTime(this.format.parse(this.arrayDate[0]));
      if ((this.begin == 0) && (this.dateFrom.before(this.date))) {
        dateFrom.setTime(format.parse(arrayDate[0]));
      }
      if (this.end == arrayDate.length - 1) {
        dateTo.setTime(format.parse(arrayDate[arrayDate.length - 1]));
      }
    } catch (ParseException e) {
      RLogger.logInfo("Unparseable date");
    } catch (NullPointerException e) {
      RLogger.logInfo("arrayDate.length = " + arrayDate.length);
      e.printStackTrace();
    }
  }

  private void paintLines() {
    double px = 1, py = 1;
    if (x < 1) {
      px = x;
    }
    if (y < 1) {
      py = y;
    }
    double xw = 1, yh = 1;
    if (x > 1 && x != Double.POSITIVE_INFINITY) {
      xw = x;
    }
    if (y > 1 && y != Double.POSITIVE_INFINITY) {
      yh = y;
    }
    g.setStroke(new BasicStroke(1));
    g.setColor(Color.lightGray);
    for (int i = 0; i <= height * py; i += 10) {
      g.drawLine((int) (i * yh * 2 + 20), 0, (int) (i * yh * 2 + 20), (int) (width * 20 * x) + 21);
    }

    for (int i = 0; i <= width * px; i++) {
      g.drawLine(
          15, (int) (i * 20 * xw + 15), (int) (height * 20 * y) + 25, (int) (i * 20 * xw + 15));
    }
    g.setColor(Color.black);
    g.drawLine(20, 15, 20, (int) (width * 20 * x) + 21);
    g.drawLine(20, 15, (int) (height * 20 * y) + 25, 15);
  }

  private void createImage(String graphType) {
    Integer count;
    gOx = ox.createGraphics();
    gOx.setBackground(Color.white);
    gOx.clearRect(0, 0, ox.getWidth(), ox.getHeight());
    gOx.setColor(Color.black);

    this.oy = new BufferedImage(50, (int) (height * 2 * y) + 50, BufferedImage.TYPE_INT_RGB);
    gOy = oy.createGraphics();
    gOy.setBackground(Color.white);
    gOy.clearRect(0, 0, oy.getWidth(), oy.getHeight());
    gOy.setColor(Color.black);
    gOy.drawString(graphType, 7, 10);
    if (y < 1) {
      for (int i = 0; i < height * y; i += 10) {
        count = (int) ((height - i / y) / 10);
        gOy.drawString(count.toString(), 10, i * 2 + 35);
      }
    } else {
      for (int i = 0; i < height; i += 10) {
        count = (height - i) / 10;
        gOy.drawString(count.toString(), 10, (int) (i * 2 * y) + 35);
      }
    }

    g = img.createGraphics();
    g.setBackground(Color.white);
    g.clearRect(0, 0, img.getWidth(), img.getHeight());

    lg = new BufferedImage(345, 60, BufferedImage.TYPE_INT_RGB);
    glg = lg.createGraphics();
    paintLegenda(glg, lg, graphType);

    nimg =
        new BufferedImage(img.getHeight() + 55, img.getWidth() + 165, BufferedImage.TYPE_INT_RGB);
    ng = nimg.createGraphics();
    ng.setBackground(Color.white);
    ng.clearRect(0, 0, nimg.getWidth(), nimg.getHeight());
    ng.drawImage(oy, -7, 10, null);
  }

  private void disposeImage() {
    ng.dispose();
    g.dispose();
    gOx.dispose();
    gOy.dispose();
    glg.dispose();
  }

  private boolean isCrossLines(int[] arrayFirst, int[] arraySecond) {
    return isCrossLines(arrayFirst, arraySecond, 0, width - 1);
  }

  private boolean isCrossLines(int[] arrayFirst, int[] arraySecond, int begin, int end) {
    for (int j = begin; j < end; j++) {
      arrayFirst[j] = (int) (arrayFirst[j] * 2 * y + 21);
    }
    for (int j = begin; j < end; j++) {
      arraySecond[j] = (int) (arraySecond[j] * 2 * y + 21);
    }
    overlap = false;
    for (int j = begin; j < end; j++) {
      if (arrayFirst[j] == arraySecond[j] && overlap == true) {
        break;
      } else if (arrayFirst[j] == arraySecond[j]) {
        overlap = true;
      } else {
        overlap = false;
      }
    }
    return overlap;
  }

  private void paintGraphLine(
      ArrayList<Integer> arrayPoints, int[] array, String name, int height, int width, int type) {
    BufferedImage tex =
        new BufferedImage(img.getHeight(), img.getWidth(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D gr = tex.createGraphics();
    gr.rotate(-Math.PI / 2);
    gr.translate((int) -(2 * height * y) - 25, 0);
    if (arrayPoints != null) {
      paintSpecial(gr, width, height, arrayPoints, array, type);
    } else {
      paint(gr, width, height, array, type);
    }
    try {
      File folder = new File(URI.create(pathGraph + "/graphics"));
      folder.mkdirs();
      ImageIO.write(tex, "png", new File(URI.create(pathGraph + "/graphics/" + CoreUtils.URLEncode(name) + ".png")));
    } catch (IOException io) {
      io.printStackTrace();
    }
    gr.dispose();
  }

  public void createCoverGraph() {
    BufferedImage tex =
        new BufferedImage(img.getHeight(), img.getWidth(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D gr = tex.createGraphics();
    gr.rotate(-Math.PI / 2);
    gr.translate((int) -(2 * height * y) - 25, 0);
    try {
      File folder = new File(URI.create(pathGraph + "/graphics"));
      folder.mkdirs();
      ImageIO.write(tex, "png", new File(URI.create(pathGraph + "/graphics/" + "cover.png")));
    } catch (IOException io) {
      io.printStackTrace();
    }
    gr.dispose();
    createLegImg();
  }

  private void createLegImg() {
    BufferedImage tex = new BufferedImage(195, 15, BufferedImage.TYPE_INT_ARGB);
    Graphics2D gr = tex.createGraphics();
    try {
      File folder = new File(URI.create(pathGraph + "/graphics"));
      folder.mkdirs();
      ImageIO.write(tex, "png", new File(URI.create(pathGraph + "/graphics/" + "leg.png")));
    } catch (IOException io) {
      io.printStackTrace();
    }
    gr.dispose();
  }
}
