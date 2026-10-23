package com.unitesk.requality.repo;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.FileSpaceTimePoint;
import com.unitesk.requality.core.storage.IResTracker;

public interface ICVSClient {

  public boolean isStaticOne();
  public void setIsStatic(boolean state);

/**
   * Получить идентификатор клиента (должен быть уникальным среди всех клиентов)
   *
   * @return идентификатор клиента
   */
  public String getID();

  /**
   * Полчить содержимое файла заданной ревизии
   *
   * @param filePathInRepo -- путь до файла относительно крневой папки репозитория
   * @param rev -- ревизия, искомого файла
   * @param monitor -- прогресс-монитор, в котором нужно отображать пройцесс выполнения операции
   *     (может принимать значение null)
   * @return -- содержимой файла
   * @throws Exception в случае, возниконвения проблем с соединенийм или репозиторием (например,
   *     файл данной ревизии отсутствует, так как носит другое имя)
   */
  public String getFileContentFromRepoS(String filePathInRepo, String rev, IProgressMonitor monitor)
      throws Exception;

  /**
   * Получить содержимое файла заданной ревизии с указанием стержневой ревизии
   *
   * @param filePathInRepo -- путь до файла относительно крневой папки репозитория
   * @param pegRev -- стержневая ревизия
   * @param revOfIntrst -- ревизия, искомого файла
   * @param monitor -- прогресс-монитор, в котором нужно отображать пройцесс выполнения операции
   *     (может принимать значение null)
   * @return -- содержимой файла
   * @throws Exceptionв в случае, возниконвения проблем с соединенийм или репозиторием
   */
  public String getFileContentFromRepoS(
      String filePathInRepo, String pegRev, String revOfIntrst, IProgressMonitor monitor)
      throws Exception;

  /**
   * Получить список ревизий для заданного файла (ресурса) проекта
   *
   * @param componentFile -- файл (ресурс) проекта, для которого нужно ролучить список ревизий
   * @param monitor -- прогресс-монитор, в котором нужно отображать пройцесс выполнения операции
   *     (может принимать значение null)
   * @return список "пространсвенно-временных" точек файла (тройки путь до файла-ревизия-метка
   *     времени создания/изменения файла)
   * @throws Exception в случае, возниконвения проблем с соединенийм или репозиторием
   */
  public ArrayList<FileSpaceTimePoint> getResourceRevisions(
      TreeNode componentFile, IProgressMonitor monitor) throws Exception;

  public Iterator<FileSpaceTimePoint> getResourceRevisions(TreeDB p, Object monitor);
  
  /**
   * Получить список ревизий для заданного файла (ресурса) проекта, начиная с заданной ревизии
   *
   * @param componentFile -- файл (ресурс) проекта, для которого нужно ролучить список ревизий
   * @param startRev -- ревизия, с которой нужно начать
   * @param monitor -- прогресс-монитор, в котором нужно отображать пройцесс выполнения операции
   *     (может принимать значение null)
   * @return список "пространсвенно-временных" точек файла (тройки путь до файла-ревизия-метка
   *     времени создания/изменения файла)
   * @throws Exception в случае, возниконвения проблем с соединенийм или репозиторием
   */
  public ArrayList<FileSpaceTimePoint> getResourceRevisions(
      TreeNode componentFile, String startRev, IProgressMonitor monitor) throws Exception;

  /**
   * Связать SVC клиента с заданныйм проектом
   *
   * @param proj -- проект, с которым нужно связать SVC клиента
   * @throws Exception в случае, возниконвения проблем с соединенийм или репозиторием
   */

  public void init(IResTracker rt, URI proj, String name) throws Exception;
  
  /**
   * Узнать ревизию локальной копии файла
   *
   * @param localFile -- путь к файлу проекта, для которого нужно узнать, в какой ревизии он
   *     находится
   * @return ревизию, в которой находится заданный локальный файл
   * @throws Exception в случае, возниконвения проблем с соединенийм или репозиторием
   */
  public String getLocalCopyRevision(TreeNode localFile) throws Exception;

  /**
   * Накатить изменения в файле в репозиторий
   *
   * @param file -- файл, чьи изменения нужно накатить
   * @param commitMessage -- сообщение, которое нужно ассойиировать с накатываемыми изменениями
   * @param monitor -- прогресс-монитор, в котором нужно отображать пройцесс выполнения операции
   *     (может принимать значение null)
   * @throws Exception в случае, возниконвения проблем с соединенийм или репозиторием (например,
   *     локальный файл находится в более ранней ревизии, чем его копия в репозитории, или файл
   *     вообще был удалён из репозитория)
   */
  public void commit(TreeNode file, String commitMessage, IProgressMonitor monitor) throws Exception;

  /**
   * Получить УРС репозитория
   *
   * @return -- УРС репозитория
   */
  public String getRepoURL();

  /**
   * Получить относительный путь в репозитории, и номер его последней ревизии для локального файла
   *
   * @param componentFile -- локальный файл, для которого нужно получить информацию
   * @return массив строк с информацией; в ячейке с индексом 0 находится строка, хранящая
   *     относительный путь файла в репозитории, в ячейке 1 -- номер ревизии
   * @throws Exception в случае, возниконвения проблем с соединенийм или репозиторием (например,
   *     несоответствие транспортных протоколов, или УРС репозитория)
   */
  public String[] getRelativePathAndLatestRevForAFile(TreeNode componentFile) throws Exception;
  /**
   * Исключить из рассмотрания клиента репозитория (Team provider) директорию .reqcsv
   *
   * @throws Exception В случае возникновения проблем с репозиторием или попыткой выполнить эту
   *     операцию до init
   */
  public void excludeDotReqCSVDir() throws Exception;

  File getContainerFromRepoS(String filePathInRepo, String rev, IProgressMonitor monitor)
      throws Exception;
  
  /**
   * Получить дерево требований на момент определенной ревизии из репозитория
   * @param mon -- монитор прогресса операций. Необходимо начинать и завершать задачи чтобы отображалось их выполнение в UI
   * @param rev -- номер ревизии, из которой нужно получить дерево требований
   *
   * @return дерево требований на момент указанной ревизии
   * @throws Exception в случае, возниконвения проблем с соединенийм или репозиторием
   */
  public Pair<File, Boolean> getProjectContentByRevision(IResTracker rt, RevisionInfo ri, String fullId, String gitId, boolean waitLoaded, IProgressMonitor mon) throws Exception;
  public boolean freeRevision(String commit) throws Exception;
  public RevisionInfo getProjectRevisionByDate(Date date) throws Exception;
  public void setIgnore(TreeNode node, boolean b);
  public void setIgnore(TreeNode par, String id, boolean b);
  public boolean getIsIgnored(TreeNode par, String id);
  public boolean getIsIgnored(TreeNode par);
  public RevisionInfo getRevisionInfo(String rev, IProgressMonitor mon) throws Exception;

  public RepoTreeState<File, String> getContainerByPath(String s, TreeDB db) throws Exception;

  public RevisionInfo getHeadRevision(TreeDB db) throws Exception;

  public void resetToHead(IProgressMonitor mon);

  public String getProjectName();

}
