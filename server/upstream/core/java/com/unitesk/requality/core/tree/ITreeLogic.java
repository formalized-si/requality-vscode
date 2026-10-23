package com.unitesk.requality.core.tree;

import java.util.Set;

import com.unitesk.requality.core.model.TreeNode;

/** Maybe use as base of extensions */
public interface ITreeLogic {
  @SuppressWarnings("serial")
  public static class TreeLogicException extends Exception {}
  @SuppressWarnings("serial")
  public static class ProjectNotLoadedException extends TreeLogicException {}
  @SuppressWarnings("serial")
  public static class NodeIdException extends TreeLogicException {}

  @SuppressWarnings("serial")
  public static class HierarchicalException extends TreeLogicException {}

  @SuppressWarnings("serial")
  public static class WrongProjectException extends HierarchicalException {}

  @SuppressWarnings("serial")
  public static class PastingIntoLeafException extends HierarchicalException {}

  @SuppressWarnings("serial")
  public static class SameIdExistsInChildren extends HierarchicalException {}

  @SuppressWarnings("serial")
  public static class IdEmpty extends NodeIdException {}

  @SuppressWarnings("serial")
  public static class IdContainsWrongChars extends NodeIdException {
    char wrongchar;

    public IdContainsWrongChars(char c) {
      wrongchar = c;
    }

    public char getWrongCharacter() {
      return wrongchar;
    }
  }

  /**
   * Method to check if it possible to 'what' to be parent of 'child' TreeNode
   *
   * @param what possible parent node
   * @param child possible child node
   * @return true if is possible to 'what' to be parent of 'child' TreeNode
   * @throws WrongProjectException
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   * @throws ProjectNotLoadedException 
   */
  public boolean canBeParentOfExc(TreeNode what, TreeNode child)
      throws WrongProjectException, SameIdExistsInChildren, IdEmpty, IdContainsWrongChars, ProjectNotLoadedException;

  /**
   * Method to check if it possible to 'what' to be child of 'parent' TreeNode Throws exceptions
   *
   * @param what possible child node
   * @param parent possible parent node
   * @return true if is possible to 'what' to be child of 'parent'
   * @throws PastingIntoLeafException special paste exception
   * @throws WrongProjectException
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   * @throws ProjectNotLoadedException 
   */
  public boolean canBeChildOfExc(TreeNode what, TreeNode parent)
      throws PastingIntoLeafException, WrongProjectException, SameIdExistsInChildren, IdEmpty,
          IdContainsWrongChars, ProjectNotLoadedException;

  /**
   * Method to check if it possible to 'what' type(for ex. "TestStep") to be child of 'parent'
   * TreeNode Throws exceptions
   *
   * @param what type of possible child node
   * @param parent possible parent node
   * @return true if is possible to 'what' to be child of 'parent' TreeNode
   * @throws PastingIntoLeafException special paste exception
   * @throws ProjectNotLoadedException 
   */
  public boolean canBeChildOfExc(String whatType, TreeNode parent) throws PastingIntoLeafException, ProjectNotLoadedException;

  /**
   * Method to check if it possible to change id of 'node' to 'newId' Throws exceptions
   *
   * @param node node to check possibility of id change
   * @param newId id to check
   * @return true if it possible to change id of 'node' to 'newId'
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean isIdValidExc(TreeNode node, String newId)
      throws SameIdExistsInChildren, IdEmpty, IdContainsWrongChars;

  /**
   * Method to check if it possible to add node with id 'id' to node 'parent' Throws exceptions
   *
   * @param parent possible parent node to node with id equal to 'id'
   * @param node child to check id for
   * @param id id to check possibility of adding it to node 'parent' as child
   * @return true if it possible to 'parent' to have child with id 'id'
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean isChildIdValidExc(TreeNode parent, TreeNode node, String id)
      throws SameIdExistsInChildren, IdEmpty, IdContainsWrongChars;

  /**
   * Method to check if it possible to add node with id 'id' to node 'parent' Throws exceptions
   *
   * @param parent possible parent node to node with id equal to 'id'
   * @param id id to check possibility of adding it to node 'parent' as child
   * @return true if it possible to 'parent' to have child with id 'id'
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean isChildIdValidExc(TreeNode parent, String id)
      throws SameIdExistsInChildren, IdEmpty, IdContainsWrongChars;

  /**
   * Check if it possible to create node with type 'whatType' and id 'newId' to node 'parent' Throws
   * exceptions
   *
   * @param whatType type of node to create in 'parent'
   * @param newId id of node to create in 'parent'
   * @param parent node where checking id performing
   * @return true if it possible to create node with type 'whatType' and id 'newId' in 'parent' node
   * @throws PastingIntoLeafException special paste exception
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   * @throws ProjectNotLoadedException 
   */
  public boolean canBeCreatedInExc(String whatType, String newId, TreeNode parent)
      throws PastingIntoLeafException, SameIdExistsInChildren, IdEmpty, IdContainsWrongChars, ProjectNotLoadedException;

  /**
   * Check if it is possible to create copy of 'what' TreeNode in 'parent' TreeNode. Throws
   * exceptions
   *
   * @param what TreeNode which have to be copied
   * @param newParent TreeNode where 'what' should be copied
   * @param changeId if true, then methods checks possibility of adding 'what' with possible id
   *     altering, otherwise, id seemed to be the same
   * @return true if it possible to add copy of 'what' Node to 'newParent' node. Note changeId
   *     parameter.
   * @throws WrongProjectException
   * @throws PastingIntoLeafException
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   * @throws ProjectNotLoadedException 
   */
  public boolean canBeCopiedToExc(TreeNode what, TreeNode newParent, boolean changeId)
      throws PastingIntoLeafException, WrongProjectException, SameIdExistsInChildren, IdEmpty,
          IdContainsWrongChars, ProjectNotLoadedException;

  /**
   * Check if it possible to create node with type 'whatType' and id 'newId' to node 'parent' Throws
   * exceptions
   *
   * @param whatType type of node to create in 'parent'
   * @param parent node where checking id performing
   * @return true if it possible to create node with type 'whatType' and id 'newId' in 'parent' node
   * @throws PastingIntoLeafException special paste exception
   * @throws SameIdExistsInChildren
   * @throws ProjectNotLoadedException 
   */
  boolean canBeCreatedInExc(String whatType, TreeNode parent)
      throws PastingIntoLeafException, SameIdExistsInChildren, ProjectNotLoadedException;

  /**
   * Method to check if it possible to 'what' to be parent of 'child' TreeNode Throws exceptions
   *
   * @param what possible parent node
   * @param child possible child node
   * @return true if is possible to 'what' to be parent of 'child' TreeNode
   * @throws WrongProjectException
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean canBeParentOf(TreeNode what, TreeNode child);

  /**
   * Method to check if it possible to 'what' type(for ex. "TestStep") to be parent of 'child'
   * TreeNode
   *
   * @param what type of possible parent node
   * @param child possible child node
   * @return true if is possible to 'what' to be parent of 'child' TreeNode
   */
  public boolean canBeParentOf(String whatType, TreeNode child);

  /**
   * Method to check if it possible to 'what' to be child of 'parent' TreeNode
   *
   * @param what possible child node
   * @param parent possible parent node
   * @return true if is possible to 'what' to be child of 'parent'
   * @throws PastingIntoLeafException special paste exception
   * @throws WrongProjectException
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean canBeChildOf(TreeNode what, TreeNode parent);

  /**
   * Method to check if it possible to 'what' type(for ex. "TestStep") to be child of 'parent'
   * TreeNode
   *
   * @param what type of possible child node
   * @param parent possible parent node
   * @return true if is possible to 'what' to be child of 'parent' TreeNode
   * @throws PastingIntoLeafException special paste exception
   */
  public boolean canBeChildOf(String whatType, TreeNode parent);

  /**
   * Method to check if it possible to change id of 'node' to 'newId'
   *
   * @param node node to check possibility of id change
   * @param newId id to check
   * @return true if it possible to change id of 'node' to 'newId'
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean isIdValid(TreeNode node, String newId);

  /**
   * Method to check if it possible to add node with id 'id' to node 'parent'
   *
   * @param parent possible parent node to node with id equal to 'id'
   * @param node child to check id for
   * @param id id to check possibility of adding it to node 'parent' as child
   * @return true if it possible to 'parent' to have child with id 'id'
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean isChildIdValid(TreeNode parent, TreeNode node, String id);

  /**
   * Method to check if it possible to add node with id 'id' to node 'parent'
   *
   * @param parent possible parent node to node with id equal to 'id'
   * @param id id to check possibility of adding it to node 'parent' as child
   * @return true if it possible to 'parent' to have child with id 'id'
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean isChildIdValid(TreeNode parent, String id);

  /**
   * Check if it possible to create node with type 'whatType' and id 'newId' to node 'parent'
   *
   * @param whatType type of node to create in 'parent'
   * @param newId id of node to create in 'parent'
   * @param parent node where checking id performing
   * @return true if it possible to create node with type 'whatType' and id 'newId' in 'parent' node
   * @throws PastingIntoLeafException special paste exception
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean canBeCreatedIn(String whatType, String newId, TreeNode parent);

  /**
   * Check if it is possible to create copy of 'what' TreeNode in 'parent' TreeNode.
   *
   * @param what TreeNode which have to be copied
   * @param newParent TreeNode where 'what' should be copied
   * @param changeId if true, then methods checks possibility of adding 'what' with possible id
   *     altering, otherwise, id seemed to be the same
   * @return true if it possible to add copy of 'what' Node to 'newParent' node. Note changeId
   *     parameter.
   * @throws WrongProjectException
   * @throws PastingIntoLeafException
   * @throws SameIdExistsInChildren
   * @throws IdContainsWrongChars
   * @throws IdEmpty
   */
  public boolean canBeCopiedTo(TreeNode what, TreeNode newParent, boolean changeId);

  /**
   * Method to return possible linkto: contruction's names for given node type
   *
   * @param treeNodeType type of TreeNode to get names of linkto: constructions
   * @return List of possible linkto: contruction's names for TreeNode type and all its attributes
   */
  public Set<String> getPossibleLink(String treeNodeType);

  /**
   * Method to check possibility to delete a node
   *
   * @param node node to be deleted
   * @param internal TODO
   * @return true if node can be deleted
   */
  public boolean canBeDeleted(TreeNode node);

  /**
   * Method to return possible linkto: contruction's names for given node type and concrete
   * attribute
   *
   * @param treeNodeType type of TreeNode to get names of linkto: constructions
   * @param treeNodeAttribute name of attribute to get names of linktos
   * @return List of possible linkto: contruction's names for TreeNode type and concrete attribute
   */
  public Set<String> getPossibleLinkForAttribute(String treeNodeType, String treeNodeAttribute);

  /**
   * Method to get all possible linkto: linked types for given linkTo: name
   *
   * @param linkToType
   * @return List of possible types of linked TreeNodes
   */
  public Set<String> getLinkedNodeTypesToLink(String linkToType);

  /**
   * Check if it possible to create node with type 'whatType' and id 'newId' to node 'parent'
   *
   * @param whatType type of node to create in 'parent'
   * @param parent node where checking id performing
   * @return true if it possible to create node with type 'whatType' and id 'newId' in 'parent' node
   * @throws PastingIntoLeafException special paste exception
   * @throws SameIdExistsInChildren
   */
  boolean canBeCreatedIn(String whatType, TreeNode parent);

  /**
   * Method to check a possibility of node coping
   *
   * @return true if node can be copied
   */
  boolean isCopyable(TreeNode node);

  /**
   * Method to check a possibility for node type to be copy-pasted as siblink of target node
   *
   * @return true if node can be copied
   */
  boolean isSiblingPasteAvalible(String node);

  /**
   * Method to check if it possible to 'what' type(for ex. "TestStep") to be child of 'parent'
   * TreeNode
   *
   * @param whatType type of possible child node
   * @param parentType possible parent node type
   * @return true if is possible to 'what' to be child of 'parent' TreeNode
   * @throws PastingIntoLeafException special paste exception
   */
  boolean canBeChildOf(String whatType, String parentType);

  /**
   * Method to check if it possible to 'what' type(for ex. "TestStep") to be parent of TreeNode of
   * 'childType' type
   *
   * @param what type of possible parent node
   * @param child possible child node type
   * @return true if is possible to 'what' to be parent of 'child' TreeNode
   */
  boolean canBeParentOf(String whatType, String childType);

  /**
   * This method is created to support classes that are not needed in asking before rename on
   * copying
   *
   * @return true if asking is not needed
   */
  boolean isAutoRenamable(String type);

  boolean canBeChildOf(TreeNode what, TreeNode parent, boolean checkid);

  boolean canBeChildOfExc(TreeNode what, TreeNode parent, boolean checkid)
      throws PastingIntoLeafException, WrongProjectException, SameIdExistsInChildren, IdEmpty,
          IdContainsWrongChars, ProjectNotLoadedException;

  boolean canBeTemplate(String type);
}
