package com.unitesk.requality.vscode.mcp;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.mcp.McpServer.Prompt;

/**
 * The MCP prompts of Requality: the jobs of a person who works with Requality through an agent only, as ready-made
 * requests (slash commands in Claude Code). Each one names the tools in the order the job needs them.
 */
final class RequalityPrompts {
  private RequalityPrompts() {
  }

  private static String arg(JsonObject a, String key, String def) {
    return a.has(key) && !a.get(key).isJsonNull() && !a.get(key).getAsString().isBlank() ? a.get(key).getAsString().trim() : def;
  }

  static void register(McpServer mcp) {
    mcp.add(new Prompt("requirements_from_document", "Requirements from a document",
        "Turns a specification (a file to import, or a document of the project) into requirements with document locations.",
        new String[][] { { "project", "Requality project name", "true" },
            { "document", "The specification: a file path to import, or the path of a document in the project", "true" },
            { "parent", "The requirement to put the new requirements under (default /Requirements)", "false" } },
        a -> {
          String project = arg(a, "project", "");
          String doc = arg(a, "document", "");
          String parent = arg(a, "parent", "/Requirements");
          return "Turn the specification " + doc + " into requirements in the Requality project " + project + ".\n\n"
              + "1. If " + doc + " is a file, import it with import_document. Otherwise it is a document of the project.\n"
              + "2. Read the document with read_document, filter \"uncovered\". Read all parts (next_from).\n"
              + "3. Find the requirement statements (for example sentences with shall, must, should, is required to). "
              + "Plan one Requirement for each statement under " + parent + " (when " + parent + " does not exist, create "
              + "it first, with the last part of the path as its id), with a short name. When the document has "
              + "sections (the paragraphs with a heading level), group the requirements under a group requirement for "
              + "each section.\n"
              + "4. Make the plan with apply_changes, in as few calls as possible: operations create (with \"as\"), then "
              + "locate with \"quote\" set to the exact sentence (add \"paragraph\" when the sentence occurs more than once). "
              + "Give each call a summary.\n"
              + "5. Check with read_document (filter \"uncovered\") that no requirement statement is left, and with "
              + "trace_coverage under " + parent + ".\n"
              + "6. Report the requirements you created, and the uncovered text you left on purpose (titles, notes).";
        }));

    mcp.add(new Prompt("update_specification", "New version of a specification",
        "Moves the requirements of a document to its new version and brings them up to date.",
        new String[][] { { "project", "Requality project name", "true" },
            { "document", "The current document in the project, for example /Documents/spec.xhtml", "true" },
            { "file", "The file of the new version", "true" } },
        a -> {
          String project = arg(a, "project", "");
          String doc = arg(a, "document", "");
          String file = arg(a, "file", "");
          return "The specification " + doc + " of the Requality project " + project + " has a new version: " + file + ".\n\n"
              + "1. Run update_document_version with document " + doc + " and file " + file + ". It imports the new "
              + "version and moves the locations in one change; by default (detach_old) the requirements then trace to the "
              + "new version only, and the old document stays as the history.\n"
              + "2. Check each requirement with the status \"changed\", first the ones with modality_changed or "
              + "numbers_changed: compare the old and the new text and the words_removed and words_added. For the status "
              + "\"context_changed\", read the sentences that came into or went out of its paragraph. When the meaning "
              + "changed, update the name (and the description, if it has one). When the new location marks the wrong "
              + "sentence, unlocate it (new_location) and locate the right one. When the result says there are more, read "
              + "them with review_changes (change and offset).\n"
              + "3. For each requirement with the status \"lost\": when its statement is in the new version in other words "
              + "(see suggestions), locate it. When its statement is gone, look at it with get_node: do not delete a "
              + "requirement that has test purposes, children or links before I confirm it; list these for me.\n"
              + "4. Create requirements for the new_sentences that are requirements (statement true, and others that state "
              + "a rule), with their locations.\n"
              + "5. Make the changes with apply_changes in as few calls as possible, each with a summary.\n"
              + "6. Report for each requirement: unchanged, changed (old and new text), removed or new, and the ones that "
              + "wait for my decision.";
        }));

    mcp.add(new Prompt("coverage_audit", "Coverage audit",
        "Tells the traceability gaps of a project and proposes the fixes, without changes.",
        new String[][] { { "project", "Requality project name", "true" },
            { "under", "Only the requirements under this requirement (default /Requirements)", "false" } },
        a -> {
          String project = arg(a, "project", "");
          String under = arg(a, "under", "/Requirements");
          return "Audit the traceability of the Requality project " + project + " under " + under + ".\n\n"
              + "1. Run trace_coverage with refresh true. Read every page of lists that have next_offset: pass it "
              + "as offset, with refresh false and documents false on subsequent pages.\n"
              + "2. For each document with a low coverage, read the uncovered text (read_document, filter \"uncovered\") "
              + "and tell which parts look like requirement statements.\n"
              + "3. Report: requirements without document text, requirements without test purposes, broken links, "
              + "checker problems, open review items, and the uncovered requirement statements.\n"
              + "4. Propose the fixes as a list. Do not change the project unless I ask for it.";
        }));

    mcp.add(new Prompt("review_agent_changes", "Review the changes of the agent",
        "Shows what the agent changed in the catalogs, for a review, and how to revert it.",
        new String[][] { { "project", "Only the changes of this project", "false" } },
        a -> {
          String project = arg(a, "project", null);
          return "Show me what the agent changed" + (project != null ? " in the Requality project " + project : "")
              + ": run review_changes. For each change give its number, time, summary and state (done, undone or "
              + "reverted) in a short readable list. When I ask about a change, show its operations with the values before "
              + "and after (review_changes with project and change). Tell me how to take a change back: undo for the newest "
              + "changes of this session, revert_change for any change (first with dry_run; it is refused when the nodes "
              + "were changed later, and force reverts anyway).";
        }));
  }
}
