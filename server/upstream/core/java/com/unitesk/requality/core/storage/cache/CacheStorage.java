package com.unitesk.requality.core.storage.cache;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.instancer.TreeInstancer;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ISectionModel;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.tool.CoreUtils;

public class CacheStorage implements ICacheStorageProvider {
  private static Map<CacheStorage,Long> timeOutTime = new ConcurrentHashMap<CacheStorage, Long>();
  private static Thread updateTimer = null;
  private static long CLOSE_TIMER_TIMEOUT = 15000;
  String connectionPath;
  URI connectionPathFile;
  ITreeStorage storage;
  boolean isClosed = false;
  Connection c = null;
  // String trans = null;
  private static Object AUTO_COMMIT_SYNC = CacheStorage.class;
  private static final String SECTION_VERSIONS = "SectionVersions121", MODEL_SECTION_NAME = "Name",
      MODEL_SECTION_VERSION = "Version";
  private static Map<Connection, Boolean> disposed = new HashMap<>();
  private boolean isLocal = false;
  private String prefix = "";
  // private long session = -1;
  private Map<String, Pair<Set<String>, Map<String, ISectionModel>>> sectionsModels = null;

  public static Set<String> getCacheDBNames(boolean local) {
    return API.getCacheSectionModels(local).keySet();
  }

  private Pair<Set<String>, Map<String, ISectionModel>> getSectionsModels(String db) {
    return getSectionsModels().get(db);
  }

  private Map<String, Pair<Set<String>, Map<String, ISectionModel>>> getSectionsModels() {
    if (sectionsModels == null)
      sectionsModels = API.getCacheSectionModels(isLocal);
    return sectionsModels;
  }

  private static final String LIST_PREFIX = "L";
  private static final FilenameFilter CD_FILT = new FilenameFilter() {

    @Override
    public boolean accept(File dir, String name) {
      return name.equals(CoreUtils.CACHE_FOLDER_NAME);
    }
  };
  private static final FilenameFilter ROOT_FILT = new FilenameFilter() {

    @Override
    public boolean accept(File dir, String name) {
      return name.equals("root.json");
    }
  };
  static {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
       @Override
       public void run() {
         //close all connections on application stop
         for(Entry<Connection, CacheStorage> en: cMap.entrySet()) {
           closeConnection(en.getKey());
         }
       }
    }));
  }
  // This lock can be locked in two situations. First, if there is no possibility
  // of local changes it can be locked in every moment.
  // Second, if you need to alter resources after taking this lock then you need
  // to call ITreeStorage.writeLock first.
  ReadWriteLock rwl = new ReentrantReadWriteLock();
  Lock rl = rwl.readLock();
  Lock wl = rwl.writeLock();
  private Map<String, ReentrantReadWriteLock> sLocks = new ConcurrentHashMap<>();
  private ProjSettsCacheMan pscm = null;
  private String cacheFileName;
  private ConcurrentLinkedDeque<CacheTransactionDescription> queue = new ConcurrentLinkedDeque<>();

  public synchronized ProjSettsCacheMan getPSCM() {
    if (pscm == null)
      pscm = new ProjSettsCacheMan(this, !isLocal);
    return pscm;
  }

  public synchronized ProjSettsCacheMan getPSCM(TreeDB db) {
    if(db!=null)
      return db.getCacheStorage().getPSCM();
    //TODO mb replace by storing on global cache storage
    throw new RuntimeException ("Cannot store project settings on null treedb");
  }

  public void readLock(String section) {
    if (section == null)
      rl.lock();
    else {
      ReentrantReadWriteLock rl;
      rl = sLocks.get(section);
      if (rl == null) {
        synchronized (sLocks) {
          rl = sLocks.get(section);
          if (rl == null) {
            rl = new ReentrantReadWriteLock();
            sLocks.put(section, rl);
          }
        }
      }
      rl.readLock().lock();
    }
  }

  public void readUnlock(String section) {
    if (section == null)
      rl.unlock();
    else {
      ReentrantReadWriteLock rl = sLocks.get(section);
      if (rl != null)
        rl.readLock().unlock();
    }
  }

  public synchronized void writeLock(String section) {
    if (section == null) {
      wl.lock();
    } else {
      ReentrantReadWriteLock wl = null;
      wl = sLocks.get(section);
      if (wl == null) {
        synchronized (sLocks) {
          wl = sLocks.get(section);
          if (wl == null) {
            wl = new ReentrantReadWriteLock();
            sLocks.put(section, wl);
          }
        }
      }
      wl.writeLock().lock();
    }
  }

  public synchronized void writeUnlock(String section) {
    if (section == null) {
      wl.unlock();
    } else {
      ReentrantReadWriteLock wl = sLocks.get(section);
      if (wl != null)
        wl.writeLock().unlock();
    }
  }

  private static Boolean initedJDBC = false;
  private static Map<URI, Connection> smap = new ConcurrentHashMap<>();
  private static Map<Connection, CacheStorage> cMap = new ConcurrentHashMap<>();
  public static Connection getConnection(URI connectionPath) { 
    synchronized (smap) {
      if (connectionPath != null) {
        Connection c = smap.get(connectionPath);
        try {
          if (c != null && c.isClosed())
            RLogger.logDebug("Closed connection called on "
                + CoreUtils.getTrace(Thread.currentThread().getStackTrace()), 5);
          return c != null && !c.isClosed() ? c : null;
        } catch (SQLException e) {
          RLogger.logError(e);
        }
      }
    }
    return null;
  }

  private static Object connectpool = "42";

  public Connection getConnection() {
    synchronized(connectpool) {
      if (c != null) {
        try {
          synchronized (connectpool) {
            synchronized (smap) {
              Connection sconn = smap.get(connectionPathFile);
              if(c!=null) {
                if (!c.isClosed()) {
                  if (connectionPathFile!=null && sconn == null) {
                    close();
                  }
                }else {
                  if(connectionPathFile!=null && (sconn == c || sconn == null))
                    close();
                }
              }
            }
          }
        } catch (SQLException e) {
           //TODO Auto-generated catch block
          e.printStackTrace();
        } finally {
          updateCloseTimer();
        }
        if (c != null)
          return c;
        if(isClosed)
          return null;//no init for closed storages
      }
      try {
        synchronized (connectpool) {
          synchronized (smap) {
            if (connectionPathFile != null && smap.containsKey(connectionPathFile)) {
              if (!new File(connectionPathFile).exists()) {
                Connection c = smap.remove(connectionPathFile);
                if (c != null)
                  closeConnection(c);
                isClosed = true;
              }
            }
          }
          if(isClosed)
            return null;
          testInitJDBC();
          try {
            if (connectionPathFile != null) {
              File f = new File(connectionPathFile);
              if (!f.exists())
                try {
                  Set<PosixFilePermission> pfset = new HashSet<>();
                  pfset.add(PosixFilePermission.GROUP_READ);
                  pfset.add(PosixFilePermission.GROUP_WRITE);
                  pfset.add(PosixFilePermission.OWNER_READ);
                  pfset.add(PosixFilePermission.OWNER_WRITE);
                  pfset.add(PosixFilePermission.OWNER_EXECUTE);
                  pfset.add(PosixFilePermission.GROUP_EXECUTE);
                  f.getParentFile().mkdirs();
                  try {
                    Files.setPosixFilePermissions(f.getParentFile().toPath(), pfset);
                  } catch (Exception e) {
                    // e.printStackTrace();
                  }
                  f.createNewFile();
                  f.setReadable(true, false);
                  f.setWritable(true, false);
                  f.setExecutable(true, false);
                } catch (IOException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                }
            }
            if(isClosed)
              return null;
            if (connectionPathFile != null) {
              c = DriverManager.getConnection("jdbc:sqlite:" + connectionPath);
              if(c == null)
                return null;
              cMap.put(c, this);
              isClosed = false;
              synchronized (smap) {
                Connection cold = smap.put(connectionPathFile, c);
                if(cold!=null)
                  closeConnection(cold);
              }
            }else
              RLogger.logError(connectionPath != null ? connectionPath.toString() : "null connection path");
            return c;
          } catch (SQLException se) {
            RLogger.logError(connectionPath.toString());
            RLogger.logError(se);
          } catch (Exception e) {
            // TODO Auto-generated catch block
            RLogger.logError("Cannot init connection for path: " + connectionPath);
            RLogger.logError(e);
          }
        }
      } finally {
        updateCloseTimer();
        RLogger.logDebug("CONNECTION related " + connectionPath + " " + this.toString(), 10);
      }
      return null;
    }
  }

  private void testInitJDBC() {
    if (initedJDBC == null) {
      try {
        Class.forName("org.sqlite.JDBC");
        initedJDBC = true;
      } catch (ClassNotFoundException e1) {
        // TODO Auto-generated catch block
        initedJDBC = false;
        e1.printStackTrace();
      }
    }
  }

  private void updateCloseTimer() {
    synchronized (CacheStorage.class) {
      timeOutTime.put(this, System.currentTimeMillis() + CLOSE_TIMER_TIMEOUT);
//      if (updateTimer == null) {
//        updateTimer = new Thread(new Runnable() {
//
//          @Override
//          public void run() {
//            int isTimed = 1;
//            while (isTimed>0)
//              try {
//                Thread.currentThread().join(1000);
//                isTimed = 0;
//                List<CacheStorage> rem = new LinkedList<>();
//                synchronized (CacheStorage.class) {
//                  for(Entry<CacheStorage, Long> en : timeOutTime.entrySet()) 
//                    if(System.currentTimeMillis()>en.getValue()) 
//                      rem.add(en.getKey());
//                }
//                for(CacheStorage cs: rem)
//                {
//                  if(!cs.inTransaction()) {
//                    timeOutTime.remove(cs);
//                    //cs.isClosed = true;
//                    //cs.close(cs.connectionPathFile, cs.c);
//                    //TODO fix transaction and long operation detection
//                  }else {//if we are in transaction then connection tends not to be stopped for a moment
//                    synchronized (CacheStorage.class) {
//                      timeOutTime.put(cs, System.currentTimeMillis() + CLOSE_TIMER_TIMEOUT);
//                    }
//                  }
//                }
//                synchronized (CacheStorage.class) {
//                  isTimed = timeOutTime.size();
//                }
//              } catch (InterruptedException e) {
//                e.printStackTrace();
//              }
//            updateTimer = null;
//          }
//        }, "Connection close for timeout cache storages");
//        updateTimer.start();
//      }
    }
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    try {
      return new CacheStorage(cacheFileName, isLocal, storage, prefix, rwl, rl, wl);
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return null;
  }

  public CacheStorage(String dbfile, boolean local, ITreeStorage storage, String prefix) throws Exception {
    this(dbfile, local, storage, prefix, null, null, null);
  }

  public CacheStorage(String dbfile, boolean local, ITreeStorage storage, String prefix, ReadWriteLock rwl, Lock rl,
      Lock wl) throws Exception {
    this(dbfile, local,
        (storage instanceof TreeInstancer) ? null
            : CoreUtils.getCacheDir(storage.getRootStorage().getLocation()),
        storage.getLocalName(), prefix, rwl, rl, wl);
  }

  public static Pair<URI, String> getConnectionString(java.nio.file.Path path2, String name) throws IOException {
    java.nio.file.Path p = path2;
    String connectionPath = null;
    URI connectionPathFile = null;
    if (p != null) {
      File pf = p.toFile();
      boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
      connectionPathFile = pf.toPath().resolve("" + name + ".db").toFile().getCanonicalFile().toURI();
      connectionPath = connectionPathFile.toString();
      if (isWin && connectionPath.length()>250) {
        connectionPath = "\\\\?\\"
            + (pf.toPath().resolve("" + name + ".db").toFile().getCanonicalFile()).getAbsolutePath();
      }
    }
    return new Pair<>(connectionPathFile, connectionPath);
  }

  public CacheStorage(String dbfile, boolean local, java.nio.file.Path path2, String name, String prefix,
      ReadWriteLock rwl, Lock rl, Lock wl) throws Exception {
    this.cacheFileName = dbfile;
    this.isLocal = local;
    this.prefix = prefix;
    if (rwl != null)
      this.rwl = rwl;
    if (rl != null)
      this.rl = rl;
    if (wl != null)
      this.wl = wl;
    try {
      connectionPathFile = null;
      connectionPath = null;
      Pair<URI, String> p = getConnectionString(path2, name);
      if (p != null) {
        connectionPathFile = p.getFirst();
        connectionPath = p.getSecond();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    synchronized (cMap) {
      getConnection();//init connections without duplications
    }
    initSections(dbfile);
  }

  private boolean inited = false;
  public static int DEFAULT = 0, COMMIT_SINGLE = 1, COMMIT_ALL = 2;

  // private int ac = DEFAULT;
  private static Set<String> getDatabaseNames(boolean local) {
    return API.getCacheSectionModels(local).keySet();
  }

  private Map<String, String> getStoredSectionVersions() throws Exception {
    Connection c = getConnection();
    try {
      if (connectionClosed(c))
        return Collections.emptyMap();
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    Pair<PreparedStatement, ResultSet> ret = readLocked(true, -1, SECTION_VERSIONS,
        "SELECT * FROM " + SECTION_VERSIONS);
    ResultSet rs = ret.getSecond();
    try {
      Map<String, String> vers = new HashMap<>();
      while (rs.next())
        vers.put(rs.getString(MODEL_SECTION_NAME),
            rs.getString(MODEL_SECTION_VERSION));
      return vers;
    } finally {
      ret.getFirst().close();
      if(rs!=null)
        rs.close();
    }
  }

  private void initSections(String db) throws Exception {
    if (inited)
      return;
    boolean rl = false;
    CacheTransactionDescription ctd = null;
    try {
      Connection c = getConnection();
      try {
        if (connectionClosed(c))
          return;
      } catch (SQLException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      ctd = startCacheTransaction("Init table model", COMMIT_SINGLE);
      writeLock(null);
      rl = true;
      Map<String, String> vers = Collections.emptyMap();
      try {
        vers = getStoredSectionVersions();
      } catch (Exception e) {
        RLogger.logInfo("Init table section " + SECTION_VERSIONS);

        StringBuilder sql = new StringBuilder("CREATE TABLE '");
        sql.append(SECTION_VERSIONS);
        sql.append("' ");
        sql.append("(");
        sql.append(MODEL_SECTION_NAME);
        sql.append(" ");
        sql.append("VARCHAR(255),");
        sql.append(MODEL_SECTION_VERSION);
        sql.append(" ");
        sql.append("VARCHAR(255)");
        sql.append(");");
        PreparedStatement s = null;
        try {
          s = c.prepareStatement(sql.toString());
          s.execute();
          vers = getStoredSectionVersions();
        } catch (Exception e1) {
          // TODO Auto-generated catch block
          RLogger.logError(e1);
          e1.printStackTrace();
        } finally {
          if (s != null)
            try {
              s.close();
            } catch (SQLException e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            }
        }
      }
      ResultSet tables = null;
      PreparedStatement s = null;
      try {
        if (connectionClosed(c))
          return;
        DatabaseMetaData dbm = c.getMetaData();
        // check if "employee" table is there
        Pair<Set<String>, Map<String, ISectionModel>> models = getSectionsModels(db);
        Set<String> keys = models.getFirst();
        for (String name : keys) {
          ISectionModel model = models.getSecond().get(name);
          Map<String, CType> cmodel = model.getSectionDefinition();
          boolean exists = vers.containsKey(name),
              correct = vers.getOrDefault(name, "").equals(model.getVersion());
          if (exists)
            try {
              Pair<PreparedStatement, ResultSet> rs = readLocked(true, -1, name,
                  "SELECT * FROM " + name + " LIMIT 1;");
              if(rs!=null)
                try (PreparedStatement st = rs.getFirst()){
                  exists = st!=null;
                }
              else
                exists = false;
            } catch (SQLException e) {
              exists = false;
            }
          if (!exists || !correct) {
            try {
              PreparedStatement ps = c.prepareStatement("DROP TABLE " + name);
              ps.execute();
              ps.close();
            } catch (Exception e) {
            }
            exists = false;
            vers.remove(name);
            try {
              writeLocked(MODEL_SECTION_NAME,
                  "DELETE FROM " + SECTION_VERSIONS + " WHERE " + MODEL_SECTION_NAME + "=?",
                  model.getSectionName());
            } catch (Exception e) {
              RLogger.logInfo("Exception on dropping " + model.getSectionName());
            }
          }
        }
        LinkedList<String> updateTriggers = new LinkedList<>();
        for (String name : keys) {
          ISectionModel model = models.getSecond().get(name);
          Map<String, CType> cmodel = model.getSectionDefinition();
          boolean exists = vers.containsKey(name),
              correct = vers.getOrDefault(name, "").equals(model.getVersion());
          if (exists)
            try {
              Pair<PreparedStatement, ResultSet> rs = readLocked(true, -1, name,
                  "SELECT * FROM " + name + " LIMIT 1;");
              try(PreparedStatement ps = rs.getFirst()){
                exists = ps!=null;
              }
            } catch (SQLException e) {
              exists = false;
            }
          if (!exists) {
            updateTriggers.add(name);
            boolean nocolumns = true;
            writeLocked(SECTION_VERSIONS,
                "INSERT INTO " + SECTION_VERSIONS + "(" + MODEL_SECTION_NAME + ","
                    + MODEL_SECTION_VERSION + ") VALUES (?,?);",
                model.getSectionName(), model.getVersion());
            try {
              StringBuilder sql = new StringBuilder("CREATE TABLE '");
              sql.append(name);
              sql.append("' ");
              sql.append("(");
              for (String fn : model.getSectionDefinition().keySet()) {
                nocolumns = false;
                sql.append(fn);
                sql.append(" ");
                sql.append(adaptType(model.getSectionDefinition().get(fn)));
                sql.append(", ");
              }
              if (!nocolumns)
                sql.setLength(sql.length() - 2);
              if (model.getIdFields() != null) {
                Set<String> ids = model.getIdFields();
                if (ids.size() > 0) {
                  sql.append(", ");
                  sql.append(" UNIQUE(");
                  for (String si : ids) {
                    sql.append(si);
                    sql.append(", ");
                  }
                  sql.setLength(sql.length() - 2);
                  sql.append(") ON CONFLICT REPLACE PRIMARY KEY(");
                  for (String si : ids) {
                    sql.append(si);
                    sql.append(", ");
                  }
                  sql.setLength(sql.length() - 2);
                  sql.append(")");
                }
              }
              sql.append(");");
              if (!nocolumns) {
                s = c.prepareStatement(sql.toString());
                try {
                  s.execute();
                  s.close();
                } catch (Exception e) {
                  RLogger.logDebug(CoreUtils.getTrace(e), 5);
                }
              }
            } finally {
            }

          }
        }
        for (String name : updateTriggers) {
          try {

            ISectionModel model = models.getSecond().get(name);
            List<String> triggers = model.getTriggers();
            if (triggers != null)
              for (String tr : triggers) {
                PreparedStatement ps = null;
                try {
                  ps = c.prepareStatement(tr);
                  ps.execute();
                } catch (SQLException ex) {
                  RLogger.logError("Error during init of " + tr + " trigger");
                  RLogger.logError(ex);
                } finally {
                  if (ps != null)
                    ps.close();
                }
              }
          } catch (Exception e) {
            RLogger.logError("Error during init of " + e + " trigger");
            RLogger.logError(e);
          }
        }

      } finally {
        if (s != null)
          try {
            s.close();
          } catch (SQLException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
          }
        if (tables != null)
          try {
            tables.close();
          } catch (SQLException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
          }
//        try {
//          c.close();
//        } catch (SQLException e) {
//          // TODO Auto-generated catch block
//          e.printStackTrace();
//        }
      }
      inited = true;
    } finally {
      if (ctd != null && ctd.error == null)
        commit(ctd);
      else if (ctd != null)
        RLogger.logError(ctd.error);
      if (rl)
        writeUnlock(null);
    }
  }

  private void commit() throws SQLException {
    try {
      if (connectionClosed(c))
        return;
    } catch (SQLException e1) {
      RLogger.logInfo("Connection was dropped for "+connectionPath);
    }
    c.commit();
  }

  private String adaptType(CType cType) {
    switch (cType) {
    case STRING:
      return "VARCHAR(255)";
    case BLOB:
      return "BLOB";
    case DATE:
      return "DATETIME";
    default:
      break;
    }
    return null;
  }

  public synchronized CacheTransactionDescription startForcedCacheTransaction(String name, int ac, boolean ignorecur) {
    Connection c = getConnection();
    CacheTransactionDescription ctd = new CacheTransactionDescription(name, ac);
    try {
      if (connectionClosed(c)) {
        ctd.error("Connection is closed");
        return ctd;
      }
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    if (!ignorecur) {
      if (queue.size() > 0) {
        ctd.error("Commit already started");
        return ctd;
      }
    }
    return startCacheTransaction(name, ac, true);
  }

  public synchronized CacheTransactionDescription startCacheTransaction(String name, int ac) {
    return startCacheTransaction(name, ac, true);
  }

  public synchronized CacheTransactionDescription startCacheTransaction(String name, int ac, boolean ignoreCur) {
    // if(trans != null)
    // return "Transaction "+name+" is already started";
    CacheTransactionDescription ret = new CacheTransactionDescription(name, ac);
    if (!ignoreCur) {
      ret.error("Commit " + name + " already started");
      return ret;
    }
    Connection c = getConnection();
    try {
      if (connectionClosed(c)) {
        ret.error("Connection is closed");
        return ret;
      }
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    queue.addLast(ret);
//    if(this.ac!=0) {
//      return "Commit already started";
//    }
    // writeLock(null);
    // session = rand.nextLong();
    try {
      synchronized (AUTO_COMMIT_SYNC) {
        if (ac > 0 && getAutoCommit()) {
          setAutoCommit(false);
        } else if (ac == 0 && !getAutoCommit()) {
          commit();// can be slow
          setAutoCommit(true);
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return ret;
  }

  private boolean getAutoCommit() {
    try {
      if (connectionClosed(c))
        return false;
    } catch (SQLException e1) {
      RLogger.logInfo("Connection was dropped for "+connectionPath);
    }
    try {
      return c.getAutoCommit();
    } catch (SQLException e) {
      RLogger.logError(e);
    }
    return false;
  }

  private void setAutoCommit(boolean b) {
    try {
      if (connectionClosed(c))
        return;
    } catch (SQLException e1) {
      RLogger.logInfo("Connection was dropped for "+connectionPath);
    }
    try {
      c.setAutoCommit(b);
    } catch (SQLException e) {
      RLogger.logError(e);
    }
  }

  public void rollback(CacheTransactionDescription ctd) {
    try {
      Connection c = getConnection();
      try {
        if (connectionClosed(c))
          return;
      } catch (SQLException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      if (queue.remove(ctd)) {// TODO multiple commit?
        try {
          ctd.error("already rollbacked");
          if (queue.size() == 0 && ctd.ac == 2)
            c.rollback();
          ctd = queue.peekLast();
          try {
            synchronized (AUTO_COMMIT_SYNC) {
              if ((ctd == null || ctd.ac == 0) && !getAutoCommit()) {
                if (ctd != null && ctd.ac == 2)
                  commit();// commit all already existing?, can be slow...
                setAutoCommit(true);
              } else if (ctd != null && ctd.ac > 0 && getAutoCommit())
                setAutoCommit(false);
            }
          } catch (SQLException e) {
            e.printStackTrace();
          }
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } finally {
      // writeUnlock(null);
    }
  }

  public void commit(CacheTransactionDescription ctd) {
    boolean removed = false;
    try {
      Connection c = getConnection();
      try {
        if (connectionClosed(c))
          return;
      } catch (SQLException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      if (queue.remove(ctd)) {// TODO multiple commit?
        removed = true;
        try {
          ctd.error("already commited");
          if (queue.size() == 0)
            RLogger.logDebug("end fo actions " + connectionPath, 11);
          synchronized (AUTO_COMMIT_SYNC) {
          if ((ctd == null || ctd.ac == 0) && !getAutoCommit()) {
            setAutoCommit(true);
          } else if (ctd != null && ctd.ac > 0 && getAutoCommit())
            setAutoCommit(false);
          }
          if (queue.size() == 0 && ctd.ac == 2)
            commit();
          ctd = queue.peekLast();
          synchronized (AUTO_COMMIT_SYNC) {
            if ((ctd == null || ctd.ac == 0) && !getAutoCommit()) {
              setAutoCommit(true);
          } else if (ctd != null && ctd.ac > 0 && getAutoCommit())
            setAutoCommit(false);
          }
        } catch (SQLException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } finally {
      // writeUnlock(null);
    }
  }

  public static void removeByPath(URI ppath) {
    if (ppath == null)
      throw new RuntimeException("Tree path is empty!");
    Map<URI, Connection> smap2 = null;
    synchronized (smap) {
      smap2 = new HashMap<>(smap);
    }
    for (URI s : new LinkedList<>(smap2.keySet())) {
      if (s.getPath().startsWith(ppath.getPath())) {
        Connection c = smap2.get(s);
        try {
          if (!c.isClosed())
            closeConnection(c);
        } catch (SQLException e) {
          e.printStackTrace();
        }
        Connection c2 = null;
        try {
          synchronized (smap) {
            c2 = smap.remove(s);
          }
        }finally {
          if(c2!=null)
            closeConnection(c2);
        }
      }
    }
  }

  private static void closeConnection(Connection c) {
    if(c == null)
      return;
    try {
      if(!c.isClosed())
        c.close();
    } catch (SQLException e) {
      RLogger.logError(e);
    }
    CacheStorage cs = cMap.get(c);
    if(cs!=null) {
      cs.close();
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.unitesk.requality.core.ICacheOperator#clear()
   */
  public void dispose() {
    try {
      setDisposed();
      close();
      // if(path!=null)
      // new File(path).delete();
      inited = false;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void setDisposed() {
    if(c!=null)
      disposed.put(c, true);
    isClosed = true;
    close();
    for (CacheTransactionDescription s : queue)
      s.error("Cache storage disposed " + (connectionPath != null ? connectionPath : "NO_PATH"));
  }

  public void close() {
    close(connectionPathFile, c, this);
    isClosed = true;
    queue.clear();
    inited = false;
  }

  public static void close(URI connectionPath, Connection c, CacheStorage cs) {
    try {
      if (c != null && !c.isClosed()) {
        closeConnection(c);
      }
      synchronized (smap) {
        Connection c2 = smap.get(connectionPath);
        if (c2 == c && c!=null) {
          c2 = smap.remove(connectionPath);
          closeConnection(c2);//TODO tends to be not needed
        }else if (c2!=null)
          RLogger.logInfo("Connection was in map "+connectionPath+" "+cMap.get(c2).isClosed);
      }
      if(cs!=null) {
        cs.c = null;
        if(c!=null)
          cMap.remove(c);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
    }
  }

  public synchronized void clearCached() {
    try {
      if (connectionClosed(c))
        return;
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    writeLock(null);
    try {
      dispose();
    } finally {
      writeUnlock(null);
    }
  }

  public boolean inTransaction() {
    return queue.size() > 0;
  }

  public static boolean connectionClosed(Connection c) throws SQLException {
    if (c == null || c.isClosed())
      return true;
    if (disposed.getOrDefault(c, false)) {
      if (c != null) {
        closeConnection(c);
        c = null;
      }
      return true;
    }
    return false;
  }

  public synchronized void updateOrInsert(String sectionName, Object... updates) {
    if (updates.length == 0)
      throw new RuntimeException("empty list of sets");
    if (updates.length % 2 == 1)
      throw new RuntimeException("uneven list of sets(one key have no value)");

    Connection c = getConnection();
    try {
      if (connectionClosed(c))
        return;
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    writeLock(sectionName);
    try {

      StringBuilder sbnames = new StringBuilder(), sbvalues = new StringBuilder();
      for (int i = 0; i < updates.length; i += 2) {
        sbnames.append(", ");
        sbnames.append(updates[i]);
        sbvalues.append(", ");
        sbvalues.append("?");
      }
      String exp = "INSERT OR REPLACE INTO '" + prefix + sectionName + "'(" + sbnames.substring(2) + ") VALUES ("
          + sbvalues.substring(2) + ");";
      PreparedStatement ps = null;
      try {

        boolean b = false;
        try {
          ps = c.prepareStatement(exp);
          for (int i = 0; i < updates.length / 2; i++) {
            ps.setObject(i, updates[i * 2 + 1]);// TODO test
          }
          ps.executeUpdate();
          if (!getAutoCommit() && transactionInSingleCommit())
            commit();
        } finally {
          if (ps != null)
            ps.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
        RLogger.logInfo(connectionPath + " error " + CoreUtils.getTrace(e));
      } finally {
        try {
          if (ps != null)
            ps.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } finally {
      writeUnlock(sectionName);
    }
  }

  private boolean transactionInSingleCommit() {
    CacheTransactionDescription last = queue.peekLast();
    return last != null && last.ac == 1;
  }

  public synchronized void delete(String sectionName, String condWithSign, Object... conditions) {
    if (conditions.length == 0 || condWithSign == null)
      throw new RuntimeException("empty list of conditions");
    // if(this.c == null)
    // old = getCacheMaps(uuid, section, vars.keySet());//TODO is it proper in not
    // commit version?

    Connection c = getConnection();
    try {
      if (connectionClosed(c))
        return;
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    writeLock(sectionName);
    try {
      // Statement s = c.createStatement();

      String exp = "DELETE FROM '" + prefix + sectionName + "' WHERE " + condWithSign;
      PreparedStatement ps = null;
      try {
        boolean b = false;
        try {
          ps = c.prepareStatement(exp);
          for (int i = 0; i < conditions.length; i++) {
            ps.setObject(i, conditions[i]);// TODO test
          }
          ps.executeUpdate();
          if (!getAutoCommit() && transactionInSingleCommit())
            commit();
        } finally {
          if (ps != null)
            ps.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
        RLogger.logInfo(connectionPath + " error " + CoreUtils.getTrace(e));
      } finally {
        try {
          if (ps != null)
            ps.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } finally {
      writeUnlock(sectionName);
    }
  }

  public synchronized List<Map<String, Object>> getValues(String sectionName, String[] names, String conditionWithSign,
      Object... conditions) {
    if (names.length == 0)
      throw new RuntimeException("empty list of sets");
    if (conditions.length % 2 == 1)
      throw new RuntimeException("uneven list of conditions(one key have no value)");

    Connection c = getConnection();
    try {
      if (connectionClosed(c))
        return Collections.EMPTY_LIST;
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    readLock(sectionName);
    try {
      // Statement s = c.createStatement();

      StringBuilder sbnames = new StringBuilder();
      for (int i = 0; i < names.length; i++) {
        sbnames.append(", ");
        sbnames.append(names[i]);
      }
      String exp = "SELECT " + sbnames.substring(2) + " FROM '" + prefix + sectionName + "'" + " WHERE "
          + conditionWithSign + ";";
      List<Map<String, Object>> ret = new LinkedList<>();
      ResultSet rs = null;
      try (PreparedStatement ps = c.prepareStatement(exp)) {
        for (int i = 0; i < conditions.length; i++)
          ps.setObject(i, conditions[i]);// TODO test
        rs = ps.executeQuery();
        while (rs.next()) {
          HashMap<String, Object> res = new HashMap<>();
          ret.add(res);
          for (int i = 0; i < names.length; i++)
            res.put(names[i], rs.getObject(i + 1));
        }
        return ret;
      } catch (Exception e) {
        e.printStackTrace();
        RLogger.logInfo(connectionPath + " error " + CoreUtils.getTrace(e));
      } finally {
        if (rs != null)
          try {
            rs.close();
          } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
      }
    } finally {
      readUnlock(sectionName);
    }
    return null;
  }

  public synchronized Object getFirstValue(String sectionName, String name, String conditionWithSign, Object... conditions) {
    if (name == null)
      throw new RuntimeException("empty list of sets");
    // if(this.c == null)
    // old = getCacheMaps(uuid, section, vars.keySet());//TODO is it proper in not
    // commit version?

    Connection c = getConnection();
    try {
      if (connectionClosed(c))
        return Collections.EMPTY_LIST;
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    readLock(sectionName);
    try {
      // Statement s = c.createStatement();

      String exp = "SELECT " + name + " FROM '" + prefix + sectionName + "'" + " WHERE " + conditionWithSign
          + ";";
      ResultSet rs = null;
      try (PreparedStatement ps = c.prepareStatement(exp)) {
        for (int i = 0; i < conditions.length; i++)
          ps.setObject(i + 1, conditions[i]);// TODO test
        rs = ps.executeQuery();
        while (rs.next()) {
          return rs.getObject(1);
        }
        return null;
      } catch (Exception e) {
        e.printStackTrace();
        RLogger.logInfo(connectionPath + " error " + CoreUtils.getTrace(e));
      } finally {
        if (rs != null)
          try {
            rs.close();
          } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
      }
    } finally {
      readUnlock(sectionName);
    }
    return null;
  }

  static Object[] empty = new Object[0];

  public synchronized void clearSection(String section) {
    writeLocked(section, "DELETE FROM " + section + ";", empty);
  }

  public synchronized void writeLocked(String section, String sql, Object... args) {
    writeLocked(-1, section, sql, args);
  }

  public synchronized void writeLocked(long session, String section, String sql, Object... args) {
    // System.out.println(section+" write "+sql+" ["+Arrays.toString(args)+"]" );
    if (args == null)
      throw new RuntimeException("empty list of arguments");
    // if(this.c == null)
    // old = getCacheMaps(uuid, section, vars.keySet());//TODO is it proper in not
    // commit version?
    Connection c = getConnection();
    try {
      if (connectionClosed(c))
        return;
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    boolean l = false;
    boolean was = false;
    for (CacheTransactionDescription s : queue)
      if (s.session == session)
        was = true;
    if (session == -1 || !was) {
      l = true;
      writeLock(section);
    }
    try {
      // Statement s = c.createStatement();
      PreparedStatement ps = null;
      try {
        boolean b = false;
        try {
          ps = c.prepareStatement(sql);
          for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);// TODO test
          }
          int ch = ps.executeUpdate();
          //log insert spammed a lot to the logfile
          //RLogger.logDebug("request " + sql + " resulted " + ch, 11);
          if (!getAutoCommit() && transactionInSingleCommit())
            commit();
        } catch (Exception e) {
          RLogger.logDebug(CoreUtils.getTrace(e));
        } finally {
          if (ps != null)
            ps.close();
        }
      } catch (Exception e) {
        RLogger.logInfo(connectionPath + " error " + CoreUtils.getTrace(e));
        RLogger.logInfo(sql);
      } finally {
        try {
          if (ps != null)
            ps.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } finally {
      if (l)
        writeUnlock(section);
    }
  }

  public Pair<PreparedStatement, ResultSet> readLocked(String sectionName, String sql, Object... args) {
    return readLocked(-1, sectionName, sql, args);
  }

  public Pair<PreparedStatement, ResultSet> readLocked(long session, String sectionName, String sql, Object... args) {
    try {
      return readLocked(false, session, sectionName, sql, args);
    } catch (Exception e) {
      e.printStackTrace();
      RLogger.logInfo(connectionPath + " error " + CoreUtils.getTrace(e));
    }
    return null;
  }

  @SuppressWarnings("resource")
  public Pair<PreparedStatement, ResultSet> readLocked(boolean exc, long session, String sectionName, String sql,
      Object... args) throws Exception {
    // System.out.println(sectionName+" read "+sql+" ["+Arrays.toString(args)+"]" );
    if (args == null)
      throw new RuntimeException("null list of sets");
    Connection c = getConnection();
    try {
      if (connectionClosed(c))
        return null;
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    boolean l = false;
    boolean was = false;
    for (CacheTransactionDescription s : queue)
      if (s.session == session)
        was = true;
    if (session == -1 || !was) {
      l = true;
      readLock(sectionName);
    }
    try {
      // Statement s = c.createStatement();
      PreparedStatement ps = null;
      ResultSet rs = null;
      try {
        ps = c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
          if (args[i] instanceof String[]) {
            ps.setArray(i + 1, c.createArrayOf("VARCHAR", (String[]) args[i]));
          } else
            ps.setObject(i + 1, args[i]);// TODO test
        }
        rs = ps.executeQuery();
        return new Pair<>(ps, rs);
      } finally {
      }
    } finally {
      if (l)
        readUnlock(sectionName);
    }
  }

  public synchronized void reactProjectDeleted(TreeDB db, IResTracker track, String lname, String pname) {
//    String ct = startCacheTransaction("Clear table caches", COMMIT_ALL);
//    try{
//      Pair<Set<String>, Map<String, ISectionModel>> models = getSectionsModels(cacheFileName);
//      }
//      if(ct == null)
//        commit();
//    }
//    catch(Exception r){
//      RLogger.logError(r);
//      if(ct == null)
//        rollback();
//    }
  }

  public static void closeRecConnection(File f) {
    if (!f.exists())
      return;

    if (f.isDirectory()) {
      File[] files = f.listFiles(CD_FILT);
      if (files.length > 0) {
        closeRecConnection(files[0]);
        return;
      } else {
        File[] rf = f.listFiles(ROOT_FILT);
        if (rf.length > 0)
          return;
      }
    }
    if (f.isFile()) {
      if (f.getName().toLowerCase().endsWith(".db"))
        closeConnection(f.toURI());
    } else if (f.isDirectory()) {
      URI fui = f.toURI();
      Set<URI> set = null;
      synchronized (smap) {
        set = new HashSet<>(smap.keySet());
      }
      for (URI u : set)
        if (u.getPath().startsWith(fui.getPath()))
          closeConnection(u);
    }
  }

  public static void closeConnection(URI conn) {
    Connection c = getConnection(conn);
    if (c != null) {
      close(conn, c, cMap.get(c));// TODO set isClosed?
    }
  }

  @Override
  public synchronized CacheStorage getCacheStorage(TreeDB dbfrom) {
    return this;
  }

  @Override
  public synchronized CacheStorage getCPCacheStorage(TreeDB dbfrom) {
    throw new RuntimeException("getCP from cache storage is not supproted!");
  }
  public synchronized void renew() {
    isClosed = false;
    Connection basec = c;
    c = null;
    Connection c = smap.remove(connectionPathFile);//TODO connection?
    if(c!=null) {
      try {
        if(!c.isClosed())
          c.close();
      } catch (SQLException e) {
        RLogger.logError(e);
      }
    }
    try {
      if (basec!=null && !basec.isClosed())
        basec.close();
    } catch (SQLException e) {
      RLogger.logError(e);
    }
  }

  public synchronized boolean isDisconnected() {
    try {
      if (isClosed && c != null && !c.isClosed())
        RLogger.logError("Connection is marked as closed but the connection was not closed "+connectionPath);
      return isClosed || c == null || c.isClosed();
    } catch (SQLException e) {
      return true;
    }
  }
}
