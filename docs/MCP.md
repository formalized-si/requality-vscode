# Requality MCP server

The Requality MCP server is Requality without a window.
An AI agent on your computer (for example, Claude Code, Codex or Cursor) uses it to read and change Requality catalogs.
The server uses the Model Context Protocol (MCP) on standard input and standard output.

The server contains the same Requality core as the VS Code extension (Requality branch `unstable`).
It opens catalogs of all formats. A catalog in the format of an older Requality must be converted first
(refer to [Catalogs of an older Requality](#catalogs-of-an-older-requality)).

## Install

On macOS and Linux:

```
curl -fsSL https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.sh | sh
```

On Windows (PowerShell):

```
irm https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.ps1 | iex
```

The script does these steps:

1. It finds the archive for your platform in the newest release on GitHub.
   If that release has no archive, it uses the newest release that has one. This release can be a pre-release.
   `REQUALITY_MCP_VERSION=1.5.0` selects a release. `REQUALITY_MCP_PRERELEASE=1` also accepts the newest pre-release.
2. It downloads the archive and compares its SHA-256 with the value that GitHub gives.
   If the release gives no SHA-256, the script stops. `REQUALITY_MCP_INSECURE=1` installs without the check.
   If the GitHub CLI (`gh`) is installed and signed in, the script also verifies the build provenance attestation.
3. It unpacks the archive in the installation folder and runs `requality-mcp --version`.
   If the server does not start, the installed version stays.
4. It installs the server and removes the other versions that the script installed.
   It does not remove other folders.
5. It shows the command to connect Claude Code.

| | macOS and Linux | Windows |
| --- | --- | --- |
| Installation folder (`REQUALITY_MCP_HOME`) | `~/.local/share/requality-mcp` | `%LOCALAPPDATA%\Programs\requality-mcp` |
| Command | `~/.local/bin/requality-mcp` (a link, `REQUALITY_MCP_BIN`) | `requality-mcp.cmd` in the installation folder |

The archives have build provenance attestations. To examine an archive that you downloaded, use the GitHub CLI:

```
gh attestation verify requality-mcp-1.5.0-linux-x64.tar.gz -R formalized-si/requality-vscode
```

The scripts do not support Alpine Linux (musl). On Alpine, use the archive without a Java runtime.

On Windows, the Java runtime cannot start from a folder whose name has letters outside the code page of the system
(for example, Cyrillic letters on an English Windows). The installer then shows a warning.
Set `REQUALITY_MCP_HOME` to another folder, for example `C:\requality-mcp`.

**To install by hand:**

1. Download the archive for your platform from the GitHub releases: `requality-mcp-<version>-<platform>`.
   This archive contains a Java runtime.
   The archive `requality-mcp-<version>.zip` does not contain a Java runtime. It needs Java 17 or later.
2. Extract the archive.

The command is `bin/requality-mcp` (Windows: `bin\requality-mcp.cmd`).

**To build from the source code:** install Node.js 22 and Java 17 or later, then run these commands:

```
git clone https://github.com/formalized-si/requality-vscode.git
cd requality-vscode
npm ci
npm run build:server
```

The command is `dist/server/bin/requality-mcp`.

**The VS Code extension** contains the same command: `<extension folder>/dist/server/bin/requality-mcp`.
It uses the Java runtime of the extension.

**To examine the installation,** run `requality-mcp --version`. Then connect an agent and ask it:
"List the Requality projects". The agent calls `list_projects` and shows your catalogs.

**To uninstall,** remove the installed version, the `current` link and the command link. The installer shows the
exact command.
The server keeps its data in `~/.requality-mcp` (Windows: `%USERPROFILE%\.requality-mcp`), apart from the installation.
Remove that folder too if you do not need the histories of the changes and the backups.

## Start

The agent starts the server. You do not start it yourself.
Give the folders that contain your Requality catalogs as the arguments:

```
requality-mcp /path/to/catalogs
```

Without an argument, the server uses the current folder. It does not start without an argument in the home folder or
in the root folder: the agent could then use all files there.
The server finds the catalogs in the folders and in their subfolders.

| Option | Effect |
| --- | --- |
| `--allow <folder>` | The agent can also import files from this folder and write reports into it. You can give this option more than once. |
| `--convert` | Convert each catalog in an old format when it opens, without a backup. Without this option, `convert_project` converts a catalog after a backup. |
| `--release-after <minutes>` | Release a catalog that no tool used for this time. Another session of the agent or VS Code can then open it. The next call that needs the project loads it again. |
| `--storage <folder>` | The folder for caches, logs, the histories of the changes and the backups. The default is `~/.requality-mcp`. |
| `--version` | Show the version. |
| `--help` | Show the options. |

| Environment variable | Effect |
| --- | --- |
| `REQUALITY_JAVA` | The Java command to use. |
| `REQUALITY_JAVA_OPTS` | The Java options. The default is `-Xmx2g`. |

The log files of the Requality core are in `<storage>/logs`. The server writes other diagnostic text to standard error.

## Connect an agent

Use the full path of the command.

**Claude Code**

```
claude mcp add requality -- /path/to/requality-mcp /path/to/catalogs
```

On Windows, start the `.cmd` command through `cmd /c`:

```
claude mcp add requality -- cmd /c "C:\path\to\requality-mcp.cmd" C:\path\to\catalogs
```

**Codex** (`~/.codex/config.toml`)

```toml
[mcp_servers.requality]
command = "/path/to/requality-mcp"
args = ["/path/to/catalogs"]
```

**Cursor, Claude Desktop and other agents with an `mcpServers` file**

```json
{
  "mcpServers": {
    "requality": {
      "command": "/path/to/requality-mcp",
      "args": ["/path/to/catalogs"]
    }
  }
}
```

On Windows, use `"command": "cmd"` and `"args": ["/c", "C:\\path\\to\\requality-mcp.cmd", "C:\\path\\to\\catalogs"]`.

## Tools

The tools follow the jobs of a requirements engineer.

| Tool | Operation |
| --- | --- |
| `list_projects` | Lists the catalogs and their state: loading, ready, broken, closed, released or needs_conversion. |
| `get_tree` | Shows the nodes under a node, to a given depth. |
| `get_node` | Shows one node: id, name, type, description, attributes, links, broken links, document locations, problems and children. |
| `search_nodes` | Finds nodes by text, by an attribute value or by type. A document location shows its text, its document and its requirements. |
| `import_document` | Imports an HTML, XHTML, XML, DOC, DOCX, ODT, RTF, PDF or TXT file as a document. |
| `read_document` | Shows a document paragraph by paragraph: the heading level, the text and the coverage by requirements. A paragraph is covered when each of its sentences is covered. The filter `uncovered` shows the paragraphs with text that no requirement covers, and these sentences. |
| `apply_changes` | Makes a list of changes in one step: create, update, delete, move, link, unlink, locate (mark document text for a requirement), unlocate and resolve (close a review item). All changes are made, or none. `dry_run` examines the changes and makes none. A delete names the links of other nodes that it breaks. A `locate` that is made again changes nothing. |
| `update_document_version` | Imports the new version of a document and moves the requirement locations to it, sentence by sentence, in one step. Tells for each requirement if its text is unchanged, changed or lost. Lists the new sentences that no requirement marks. |
| `trace_coverage` | Shows the traceability: requirements without locations or test purposes, links that resolve to no node, checker problems, document coverage and open review items. |
| `test_coverage` | Shows the coverage of the requirements by tests from a coverage file of the test runs, and the test failures. |
| `review_changes` | Shows the changes of the agent, also of earlier sessions. With a change number, it shows the values before and after. |
| `revert_change` | Reverts a change of `review_changes`, also a change of an earlier session. |
| `undo`, `redo` | Undoes or redoes the newest change of the agent in this session. |
| `list_report_templates`, `generate_report` | Generate a report from a template, or from saved report settings, into a new or empty folder. The coverage report takes the test coverage from a coverage file. |
| `create_project`, `open_project`, `close_project` | Create an empty catalog; open a closed or broken catalog, or load a catalog again from its files; close a catalog. |
| `convert_project` | Converts a catalog of an older Requality after a backup. |
| `server_messages` | Shows the messages of the Requality core and the answers of the server. |

Address a node with its path (for example, `/Requirements/A/B`) or with its UUID.
Give the project name with each call. `list_projects` gives the names.

### Typical jobs

- **Requirements from a specification.** `import_document`, then `read_document` with the filter `uncovered`.
  The agent writes the requirements with `apply_changes` (operations `create` and `locate`).
  It repeats until `read_document` shows no uncovered paragraph that contains a requirement.
  A `locate` quote can have other quotation marks, dashes and letter case than the document.
  If the quote does not occur, the error shows the most similar sentence.
- **A new version of a specification.** `update_document_version` with the old document and the new file.
  The import and the update are one change. By default (`detach_old`), the requirements then trace only to the new
  version. The old document stays as the history.
  The result flags a changed requirement when its modal words (shall, must, may, not) or its numbers changed.
  A requirement whose text stayed but whose paragraph changed is "changed" too, with the sentences that came into or
  went out of the paragraph. A short marked text (a number, a word) is found again only in a similar paragraph.
  The agent examines the changed and the lost requirements and the new statements.
  It corrects the requirements with `apply_changes`.
  The operation `resolve` closes the review items of an update that a person made in the Eclipse or VS Code interface.
- **Traceability.** `trace_coverage` tells which requirements have no test purposes or no document text.
  `test_coverage` tells which requirements the tests cover, from a coverage file (below).
- **Review.** `review_changes` shows each change of the agent. Refer to [Changes, undo and revert](#changes-undo-and-revert).

### Coverage files

`test_coverage` reads the XML file that a coverage source of Requality reads:

```xml
<coverage>
  <reqcoverage qid="/Requirements/A/B">
    <covered_by uri="tests/test_b.py" label="test_b" hits="1"/>
  </reqcoverage>
  <error name="b_fails" testuri="tests/test_b.py">
    <violates qid="/Requirements/A/B"/>
    <description format="plain">The value is too large</description>
  </error>
</coverage>
```

A `qid` is the path, the UUID or the user-visible id of a requirement.
`generate_report` with the template `com.unitesk.requality.reports.coverage` and `coverage_file` makes the coverage
report for a person.

## Prompts

The server also gives prompts: requests for the typical jobs.
Claude Code shows them as commands, for example `/mcp__requality__requirements_from_document`.

| Prompt | Job |
| --- | --- |
| `requirements_from_document` | Make requirements from a specification file or document. |
| `update_specification` | Bring the requirements up to date with a new version of a specification. The agent asks you before it deletes a requirement with test purposes, children or links. |
| `coverage_audit` | Find the traceability gaps and propose fixes, without changes. |
| `review_agent_changes` | Show the changes of the agent and how to revert them. |

## Changes, undo and revert

Each call of `apply_changes`, `import_document`, `update_document_version` and `revert_change` is one change.
A change is one transaction of the catalog: all its operations are made, or none.

The server keeps a history of the changes for each catalog in `<storage>/journals/<key of the catalog folder>/`.
For each change, the history has the state of each node that the change touched, before and after the change:
the attributes, the parent, the id and the resources. The history stays after the session ends.
The server removes the states of the changes older than one year; these changes cannot be reverted then.
Only the user can read the storage folder that the server makes. The backups keep the three newest copies of each
catalog.

- `undo` and `redo` operate on the changes of the agent in this session, newest first.
  `undo` does not undo a change that a person made in VS Code.
- `revert_change` reverts any change, also a change of an earlier session.
  The nodes of the change become as they were before it. A deleted node comes back with its UUID,
  so the links and the locations that refer to it are correct again.
- If a node of the change was changed later (by the agent, or by a person in VS Code),
  `revert_change` does not revert. The error tells which nodes changed.
  `dry_run` shows what a revert does. `force` reverts and loses the later changes of these nodes.
- A revert is also a change. A revert of a revert makes the first change again.
- After undo, redo, a revert or a failed change, the server indexes the links to the nodes again.

## Files that the agent can use

The agent can import files and write reports only in these folders:

- the folders of the catalogs (the arguments of `requality-mcp`);
- the folders of the `--allow` options.

The paths must be absolute. The server imports only document files
(HTML, XHTML, XML, DOC, DOCX, ODT, RTF, PDF and TXT) and reads only XML coverage files.
`generate_report` writes only into a new or empty folder.

The import of an HTML document copies the files that its pictures, scripts and style sheets refer to, and downloads a
style sheet from a URL. The server refuses an HTML document that refers to a URL or to a file outside these folders.
The error names the references. Remove them (or put the files next to the document) and import again.

The server reads XML files without a DOCTYPE, so an XML file cannot read other files (no external entities). The
server uses Apache Tika 3.3, which does not read external entities in PDF forms.

The server imports DOC, DOCX, ODT, RTF and PDF with Apache Tika, which is part of the server.
It does not need LibreOffice.

The headless server starts no HTTP or WebSocket server. (The VS Code extension starts them for the web editors.
They accept connections from this computer only.)

## Catalogs of an older Requality

A catalog in the format of an older Requality has the state `needs_conversion`.
The server does not change it until you allow it:

- `convert_project` copies the catalog (without its caches) to `<storage>/backups/<name>-<time>`.
  Then it converts the catalog and opens it.
- With the option `--convert`, the server converts each catalog in an old format when it opens it, without a backup.

Older versions of Requality cannot open a converted catalog.

## Files changed on disk

A person can change the files of a catalog while the server runs, for example with `git pull`.
The server does not write over such a change. A change of the agent that touches a changed file is refused, and the
error names the nodes. `open_project` then loads the catalog again from its files.

## One process for each catalog

Only one Requality process can open a catalog at a time. If VS Code shows a catalog, the MCP server does not open it.
`list_projects` then shows the state `broken` and the reason.

- Close the catalog in VS Code (or close the VS Code window). The server loads it when the other process releases it
  within 45 seconds; else the agent calls `open_project` to load it.
- `close_project` releases a catalog for VS Code or for another session of the agent.
- With `--release-after <minutes>`, the server releases a catalog that no tool used for this time.

The locks are in `~/.requality/locks`. They operate for the processes of one user on one computer.

## Questions of the Requality core

The server has no user. It gives these answers:

- To a message with one button: that button (OK).
- To a question with a choice: cancel. The only exception is the conversion question of a catalog that you allowed
  (`convert_project` or `--convert`).
- To an input box and to a file dialog: cancel. A dialog closes.

`server_messages` shows all messages and answers.

## Not supported without a window

These features of Requality need the VS Code extension (or the Eclipse plug-in):

- ReqIF import and export. The core exports into a DOORS ReqIF model that exists already (it cannot make an empty
  one), and the wizards need a mapping that a person selects.
- The editing of types, attribute definitions, project settings and checker rules.
- Copy and paste, reuse of nodes, and mass operations.
- A comparison of a catalog with a Git revision.
- The creation of node types other than Requirement, TestPurpose, Comment, TextNode, DocFolder and ReportFolder.
  `import_document` makes documents.

The text search finds a phrase only when the phrase has no formatting in it.
On Windows, the folders in the arguments must have only letters of the code page of the system (a limit of the Java
launcher).
The statement words of `update_document_version` are English and Russian
(shall, must, should, required, will; должен, обязан, необходимо, следует, требуется).
