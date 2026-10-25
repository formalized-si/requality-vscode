# Requality MCP server

The Requality MCP server is Requality without a window.
An AI agent on your computer (for example, Claude Code, Codex or Cursor) uses it to read and change Requality catalogs.
The server uses the Model Context Protocol (MCP) on standard input and standard output.

The server contains the same Requality core as the VS Code extension (Requality branch `unstable`).
It opens catalogs of all formats. It converts a catalog in an old format to the current format when it opens the catalog.

## Install

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
| `update_document_version` | Moves the requirement locations of a document to its new version. Tells for each requirement if its text is unchanged, changed or lost. |
| `trace_coverage` | Shows the traceability: requirements without locations or test purposes, broken links, checker problems, document coverage and open review items. |
| `review_changes` | Shows the changes of the agent in this session, with the values before and after. |
| `undo`, `redo` | Undoes or redoes the last change. |
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
  The agent examines the requirements with changed or lost text and corrects them with `apply_changes`.
  It closes the review items of the update with the operation `resolve`.
- **Traceability questions.** `trace_coverage` tells which requirements have no test purposes or no document text.
- **Review.** `review_changes` shows each change of the agent. `undo` reverts the last change.
  The journal is also in `<storage>/mcp-journal.jsonl`.

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
