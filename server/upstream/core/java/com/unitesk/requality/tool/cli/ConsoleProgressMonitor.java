package com.unitesk.requality.tool.cli;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Stack;

import com.unitesk.requality.core.operation.IProgressMonitor;

public class ConsoleProgressMonitor implements IProgressMonitor {

  private static final DateFormat DF = new SimpleDateFormat();
  public static final ConsoleProgressMonitor ONE = new ConsoleProgressMonitor(false);
  Stack<String>tasks = new Stack<>();
  private LinkedList<Integer> localMax = new LinkedList<>();
  private LinkedList<Integer> localCnt = new LinkedList<>();
  private boolean dbLocal = false;
  //Stack<Integer>tasksMax = new Stack<>();
  int max = -1;

  public ConsoleProgressMonitor () {
    this(false);
  }
  
  public ConsoleProgressMonitor (boolean dbone) {
    dbLocal = dbone;
  }
  
  private int getInnerMax(){
    int max = localMax.peekLast();
    if(max>0)
      return max;
    return 1;
  }
  private static PrintStream tps = System.out;
  Stack<Integer>tasksStage = new Stack<>();
  int laststage = -1;
  int pstage = -1;
  boolean canceled = false;
  private static Integer STAGES_CNT = 4;
  
  @Override
  public void worked(int val) {
    localCnt.addLast(localCnt.pollLast()+val);
    if(pstage!=getPercantage()/5){
      pstage = getPercantage()/5;
      showInfo();
    }
    if(laststage!=tasksStage.peek()){
      laststage=tasksStage.peek();
      showInfo();
    }
  }

  private String getTS(){
    return DF.format(new Date());
  }  
  private void showInfo() {
    if(tasks.size() > 0)
      tps.println(getTS()+" Operation: "+tasks.peek()+" "+getPercantage()+"%");
  }

  private int getPercantage() {
    if(localCnt.size() == 0 || localMax.size() == 0)
      return 0;
    return localCnt.peekLast() *100/getInnerMax();
  }

  @Override
  public void doneAll() {
    while(tasks.size()>0)
      doneTask();
    tps.println("Progress: all done");
  }
  
  @Override
  public void beginTask(String string, int taskMax) {
    tasks.add(string);
    int stage = tasksStage.size();
    tasksStage.add(stage);
    laststage = stage;
    localMax.addLast(taskMax);
    localCnt.addLast(0);
    showInfo();
  }

  @Override
  public boolean isCanceled() {
    return canceled;
  }

  @Override
  public int getRest() {
    if(localCnt.size() == 0)
      return max;
    return localMax.peekLast()-localCnt.peekLast();
  }

  @Override
  public int getMax() {
    if(localMax.size() == 0)
      return max;
    return localMax.peekLast();
  }

  @Override
  public void doneTask() {
    tps.println("Progress: "+tasks.pop()+" - task done");
    tasksStage.pop();
    localMax.pollLast();
    localCnt.pollLast();
    showInfo();
  }
  @Override
  public void cancel() {
	canceled = true;	
  }

  public String getTask() {
    return (tasks!=null&&!tasks.isEmpty())?tasks.lastElement():null;
  }

  @Override
  public boolean isDBLocal() {
    return dbLocal;
  }
}
