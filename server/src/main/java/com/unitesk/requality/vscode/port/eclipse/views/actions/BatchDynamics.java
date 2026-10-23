package com.unitesk.requality.vscode.port.eclipse.views.actions;

import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.action.ActionContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.CompoundContributionItem;

import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.GetterAttributeNames;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.AbstractMassOperation;
import com.unitesk.requality.core.operation.mass.AbstractNodeOperation;
import com.unitesk.requality.core.operation.mass.LibreOfficeAfterProcessing;
import com.unitesk.requality.core.operation.mass.MassAttributeTypeChange;
import com.unitesk.requality.core.operation.mass.MassCheckersReset;
import com.unitesk.requality.core.operation.mass.MassIDRenames;
import com.unitesk.requality.core.operation.mass.MassKindFixer;
import com.unitesk.requality.core.operation.mass.MassRefFixer;
import com.unitesk.requality.core.operation.mass.MassTNHeaderExplode;
import com.unitesk.requality.core.operation.mass.TermsHandler;
import com.unitesk.requality.core.operation.mass.TestMassOps;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.EnumDefExtractDialog;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.MassAttributeRetype;

public class BatchDynamics extends CompoundContributionItem {

  public BatchDynamics() {
    super();
  }

  public BatchDynamics(String id) {
    super(id);
  }

  @Override
  protected IContributionItem[] getContributionItems() {
    SelectionRecord sels = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (sels == null || sels.fullSelection.size() != 1) return new IContributionItem[0];
    TreeNode node = sels.getFirstNode();
    IContributionItem[] items =
        new IContributionItem[] {
          new ActionContributionItem(
              new SingleBatchProcessor(node, "Mass attribute type change") {

                @Override
                public AbstractMassOperation initBatchProcessor(TreeNode node) {
                  Set<String> names =
                      GetterAttributeNames.getFromRoot(node, EnumDefExtractDialog.filter);

                  MassAttributeRetype mar =
                      new MassAttributeRetype(
                          PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                          node.getTreeDB(),
                          names);
                  if (mar.open() == Window.OK) {
                    Object[] res = mar.getResult();
                    String name = (String) res[0];
                    AttributeType at = (AttributeType) res[1]; //ENUM error?
                    String newName = name;
                    if (res[2] != null) newName = (String) res[2];
                    MassAttributeTypeChange match = new MassAttributeTypeChange(name, newName, at);
                    return match;
                  }

                  return null;
                }
              }),
          new ActionContributionItem(
              new SingleBatchProcessor(node, "Mass incorrect struct text node remove") {

                @Override
                public AbstractMassOperation initBatchProcessor(TreeNode node) {
                  //new DeepFirstTreeWalker(node,true,true,false);
                  MassTNHeaderExplode mtn = new MassTNHeaderExplode();
                  return mtn;
                  //return null;

                }
              }),
          new ActionContributionItem(
              new SingleBatchProcessor(node, "Mass numerical id fixer") {

                @Override
                public AbstractNodeOperation initBatchProcessor(TreeNode node) {
                  MassIDRenames mir = new MassIDRenames();
                  return mir;
                  //return null;
                }
              }),
          new ActionContributionItem(
              new SingleBatchProcessor(node, "Mass problems reset") {

                @Override
                public AbstractNodeOperation initBatchProcessor(TreeNode node) {
                  MassCheckersReset mir = new MassCheckersReset();
                  return mir;
                  //return null;
                }
              }),
          new ActionContributionItem(
              new SingleBatchProcessor(node, "Mass 'Вид' fixer") {

                @Override
                public AbstractNodeOperation initBatchProcessor(TreeNode node) {
                  MassKindFixer mir = new MassKindFixer();
                  return mir;
                  //return null;
                }
              }),
          new ActionContributionItem(
              new SingleBatchProcessor(node, "Mass reference fixer") {

                @Override
                public AbstractNodeOperation initBatchProcessor(TreeNode node) {
                  MassRefFixer mir = new MassRefFixer();
                  return mir;
                  //return null;
                }
              }),
          new ActionContributionItem(
              new SingleBatchProcessor(
                  node, "After-import processing for libreoffice html documents") {

                @Override
                public AbstractNodeOperation initBatchProcessor(TreeNode node) {
                  LibreOfficeAfterProcessing mir = new LibreOfficeAfterProcessing();
                  return mir;
                  //return null;
                }
              }),
          new ActionContributionItem(
              new SingleBatchProcessor(
                  node, "Terms handler") {

                @Override
                public AbstractNodeOperation initBatchProcessor(TreeNode node) {
                  TermsHandler mir = new TermsHandler();
                  return mir;
                  //return null;
                }
              }),
          new ActionContributionItem(
             new SingleBatchProcessor(node, "Test op") {

                @Override
                public AbstractMassOperation initBatchProcessor(TreeNode node) {
                  //new DeepFirstTreeWalker(node,true,true,false);
                  TestMassOps ops = new TestMassOps();
                  return ops;
                  //return null;
                }
              })
        };
    return items;
  }
}
