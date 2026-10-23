package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.tool.message.RMessages;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * The shells of the workbench as SWT and Eclipse give them: when a shell closes, the activation returns to the shell
 * under it; the workbench window always has a shell, which the plug-in uses as the parent of its questions (the format
 * update question of a catalog came while no shell was active).
 */
class WorkbenchShellTest {
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    h.initialize(h.workspace);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  @Test
  void theActivationReturnsToTheShellUnderAClosedShell() {
    Display d = Display.getDefault();
    d.syncExec(() -> {
      Shell wizard = new Shell();
      wizard.open();
      Shell question = new Shell(wizard);
      question.open();
      assertSame(question, d.getActiveShell());
      question.dispose();
      assertSame(wizard, d.getActiveShell(), "the wizard is active again");
      wizard.dispose();
    });
  }

  @Test
  void aQuestionOfTheCoreIsShownWhenNoShellIsActive() throws Exception {
    Display d = Display.getDefault();
    d.syncExec(() -> {
      for (Shell s : d.getShells())
        if (s.isOpened())
          s.dispose();
    });
    Shell window = d.syncCall(() -> PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
    assertNotNull(window, "the shell of the workbench window");
    assertFalse(d.syncCall(window::isDisposed));
    int before = h.requests("client/showMessage").size();
    AtomicBoolean ok = new AtomicBoolean();
    // the question of the plug-in (MessageDialog.openConfirm on the window shell: a modal message of VS Code)
    h.answer("client/showMessage", p -> "OK");
    Pair<AtomicBoolean, OperationResult> answer = CompletableFuture.supplyAsync(() -> RMessages
        .openQuestionOkCancel("Project Loading", "Do you want to update the project?", false, true, () -> ok.set(true), null))
        .get(60, TimeUnit.SECONDS);
    List<JsonObject> asked = h.requests("client/showMessage");
    assertTrue(asked.size() > before, "the question asked");
    assertEquals("Project Loading", asked.get(asked.size() - 1).get("title").getAsString(), asked.get(asked.size() - 1).toString());
    h.await("the answer handled", v -> answer.getFirst().get());
    assertTrue(ok.get(), "the OK action ran");
    assertEquals(true, answer.getFirst().get());
  }
}
