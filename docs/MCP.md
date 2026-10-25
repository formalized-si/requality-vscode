# Requality MCP server

The Requality MCP server is Requality without a window.
An AI agent on your computer (for example, Claude Code, Codex or Cursor) uses it to read and change Requality catalogs.
The server uses the Model Context Protocol (MCP) on standard input and standard output.

The server contains the same Requality core as the VS Code extension (Requality branch `unstable`).
It opens catalogs of all formats. It converts a catalog in an old format to the current format when it opens the catalog.

## Install

On macOS and Linux:

```
curl -fsSL https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.sh | sh
```

On Windows (PowerShell):

```
irm https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.ps1 | iex
```

The script downloads the archive for your platform from the newest GitHub release and examines its SHA-256.
It installs the server in `~/.requality-mcp` and makes the command `~/.local/bin/requality-mcp`
(Windows: `%USERPROFILE%\.requality-mcp\requality-mcp.cmd`). It then shows the command to connect Claude Code.

To install by hand:

1. Download the archive for your platform from the GitHub releases:
   `requality-mcp-<version>-<platform>`.
   The archive contains a Java runtime.
   The archive `requality-mcp-<version>.zip` does not contain a Java runtime. It needs Java 17 or later.
2. Extract the archive.

The command is `bin/requality-mcp` (Windows: `bin\requality-mcp.cmd`).

The VS Code extension also contains this command:
`<extension folder>/dist/server/bin/requality-mcp`.
It uses the Java runtime of the extension.

## Start

The agent starts the server. You do not start it yourself.
Give the folder that contains your Requality catalogs as the argument:

```
requality-mcp /path/to/catalogs
```

Without an argument, the server uses the current folder.
The server finds the catalogs in the folder and in its subfolders.

## Connect an agent

Use the full path of the command.

**Claude Code**

```
claude mcp add requality -- /path/to/requality-mcp/bin/requality-mcp /path/to/catalogs
```

**Codex** (`~/.codex/config.toml`)

```toml
[mcp_servers.requality]
command = "/path/to/requality-mcp/bin/requality-mcp"
args = ["/path/to/catalogs"]
```

**Cursor, Claude Desktop and other agents with an `mcpServers` file**

```json
{
  "mcpServers": {
    "requality": {
      "command": "/path/to/requality-mcp/bin/requality-mcp",
      "args": ["/path/to/catalogs"]
    }
  }
}
```

On Windows, use `C:\\path\\to\\requality-mcp\\bin\\requality-mcp.cmd` as the command.

## Tools

The tools follow the jobs of a requirements engineer.

| Tool | Operation |
| --- | --- |
| `list_projects` | Lists the catalogs and their state (loading, ready, broken, closed). |
| `get_tree` | Shows the nodes under a node, to a given depth. |
| `get_node` | Shows one node: id, name, type, description, attributes, links, document locations, problems and children. |
| `search_nodes` | Finds nodes by text or by an attribute value. |
| `import_document` | Imports an HTML, XHTML, DOC, DOCX, ODT or PDF file as a document. |
| `read_document` | Shows a document paragraph by paragraph, with the coverage of each paragraph by requirements. The filter `uncovered` shows only the text that no requirement covers. |
| `apply_changes` | Makes a list of changes in one step: create, update, delete, move, link, unlink, locate (mark document text for a requirement), unlocate and resolve (close a review item). All changes are made, or none. `dry_run` checks the changes and makes none. |
| `update_document_version` | Moves the requirement locations of a document to its new version, sentence by sentence, in one step. Tells for each requirement if its text is unchanged, changed (with the old and the new text) or lost. Lists the new statements that no requirement covers. With `detach_old`, the requirements keep no locations in the old document. |
| `trace_coverage` | Shows the traceability: requirements without locations or test purposes, broken links, checker problems, document coverage and open review items. |
| `review_changes` | Shows the changes of the agent, also of earlier sessions, with the values before and after. |
| `undo`, `redo` | Undoes or redoes the last change of this session. |
| `revert_change` | Reverts a change of `review_changes` by its number, also a change of an earlier session. |
| `list_report_templates`, `generate_report` | Generate a report from a template into a folder. |
| `create_project`, `open_project` | Create an empty catalog, or open a closed catalog. |
| `server_messages` | Shows the messages of the Requality core (for example, the result of a format conversion). |

Address a node with its path (for example, `/Requirements/A/B`) or with its UUID.
Give the project name with each call. `list_projects` gives the names.

### Typical jobs

- **Requirements from a specification.** `import_document`, then `read_document` with the filter `uncovered`.
  The agent writes the requirements with `apply_changes` (operations `create` and `locate`).
  It repeats until `read_document` shows no uncovered paragraph that contains a requirement.
- **A new version of a specification.** `update_document_version` with the old document and the new file.
  The agent examines the requirements with changed or lost text and the new statements.
  It corrects the requirements with `apply_changes`.
  The operation `resolve` closes the review items of an update that a person made in the Eclipse or VS Code interface.
- **Traceability questions.** `trace_coverage` tells which requirements have no test purposes or no document text.
- **Review.** `review_changes` shows each change of the agent, also the changes of earlier sessions.
  `undo` reverts the last change of this session. `revert_change` reverts any change by its number.
  The journal is also in `<storage>/mcp-journal.jsonl`.

## Prompts

The server also gives prompts: requests for the typical jobs.
Claude Code shows them as commands, for example `/mcp__requality__requirements_from_document`.

| Prompt | Job |
| --- | --- |
| `requirements_from_document` | Make requirements from a specification file or document. |
| `update_specification` | Bring the requirements up to date with a new version of a specification. |
| `coverage_audit` | Find the traceability gaps and propose fixes, without changes. |
| `review_agent_changes` | Show the changes of the agent and how to revert them. |

## One process for each catalog

Only one Requality process can open a catalog at a time.
If VS Code shows the catalog, the MCP server does not open it. `list_projects` then shows the state `broken` and the reason.
Close the catalog in VS Code (or close the VS Code window) before the agent uses it. Do the same in the opposite direction.

## Questions of the Requality core

The server has no user. It gives these answers:

- To a question (for example, "Update the project to the current format?"): the first choice (OK, Yes).
- To an input box: the default value.
- To a file dialog: cancel.

`server_messages` shows all questions and answers.

## Options

| Option | Effect |
| --- | --- |
| `--storage <folder>` | The folder for caches, logs and settings. The default is `~/.requality-mcp`. |
| `REQUALITY_JAVA` | The Java command to use. |
| `REQUALITY_JAVA_OPTS` | The Java options. The default is `-Xmx2g`. |

The log files of the Requality core are in `<storage>/logs`. The server writes other diagnostic text to standard error.
