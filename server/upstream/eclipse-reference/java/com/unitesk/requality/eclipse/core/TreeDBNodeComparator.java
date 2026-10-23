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
package com.unitesk.requality.eclipse.core;

//Move functionality to RequalitySorter
/**
                                        * import org.eclipse.core.resources.IProject; import
                                        * org.eclipse.jface.viewers.Viewer; import
                                        * org.eclipse.jface.viewers.ViewerComparator;
                                        *
                                        * <p>import com.unitesk.requality.core.model.TreeDB; import
                                        *com.unitesk.requality.core.model.TreeNode;
                                        *
                                        * <p>public class TreeDBNodeComparator extends
                                        * ViewerComparator { @Override public int compsare(Viewer
                                        * viewer, Object o1, Object o2) { if (o1 instanceof TreeNode
                                        * && o2 instanceof TreeNode) { TreeNode n1 = (TreeNode) o1;
                                        * TreeNode n2 = (TreeNode) o2; if
                                        * (n1.getUUId().equals(n2.getUUId())) return
                                        * super.compare(viewer, o1, o2); if
                                        * (n1.getType().compareTo(n2.getType()) != 0) { return
                                        * n1.getType().compareTo(n2.getType()); } int k =
                                        * n1.getIndex().compareTo( n2.getIndex() ); if(k == 0)
                                        * return n1.getId().compareTo( n2.getId() ); return k; } if
                                        * (o1 instanceof TreeDB && o2 instanceof TreeDB) { TreeDB
                                        * db1 = (TreeDB) o1; TreeDB db2 = (TreeDB) o2; if
                                        * (db1.equals(db2)) return super.compare(viewer, o1, o2);
                                        * IProject prj1 = TreesTracker.getTracker().getProject(db1);
                                        * IProject prj2 = TreesTracker.getTracker().getProject(db2);
                                        * return prj1.getName().compareTo(prj2.getName()); } return
                                        * super.compare(viewer, o1, o2); } }
                                        */
