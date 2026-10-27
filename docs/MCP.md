# Requality MCP server

The Requality MCP server is Requality without a window.
An AI agent on your computer (for example, Claude Code, Codex or Cursor) uses it to read and change Requality catalogs.
The server uses the Model Context Protocol (MCP) on standard input and standard output.

The server contains the same Requality core as the VS Code extension (Requality branch `unstable`).
It opens catalogs of all formats. A catalog in the format of an older Requality must be converted first
(refer to [Catalogs of an older Requality](#catalogs-of-an-older-requality)).

## Install

The installers and the archives come with the releases from version 1.5.0. Before that release is published, build
the server from the source code (refer to "To build from the source code" below).

On macOS and Linux:

```
curl -fsSL https://raw.githubusercontent.com/formalized-si/requality-vscode/main/scripts/install-mcp.sh -o install-mcp.sh
sh install-mcp.sh
```

(`curl ... | sh` also installs, but when the download fails, it shows no error.)

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
   Then it verifies the build provenance attestation with the GitHub CLI (`gh` 2.68 or later, signed in with
   `gh auth login`): the release workflow of the repository made the archive from the tag of the release. The SHA-256
   comes from the same release, so only the attestation shows who made the archive. Without `gh`, the script stops.
   `REQUALITY_MCP_UNVERIFIED=1` installs without the attestation (the SHA-256 is still checked).
3. It unpacks the archive in the installation folder and runs `requality-mcp --version`.
   If the server does not start, the installed version stays.
4. It installs the server and removes the other versions that the script installed.
   It does not remove other folders, and it does not remove a version that a running server uses
   (a later install removes it).
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

**The VS Code extension** contains the same command from version 1.5.0: `<extension folder>/dist/server/bin/requality-mcp`.
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
| `--locale <language>` | The language of the messages and reports of Requality. The default is `en`; `ru` gives Russian. |
| `--locks <folder>` | The folder of the catalog locks. The default is `~/.requality/locks`. Give a folder that the server can write when the home folder is read-only. |
| `--release-after <minutes>` | Release a catalog that no tool used for this time. The next call that needs the project loads it again. Without this option, the server releases a catalog only when another session or VS Code waits for it (refer to [One process for each catalog](#one-process-for-each-catalog)). |
| `--storage <folder>` | The folder for caches, logs, the histories of the changes and the backups. The default is `~/.requality-mcp`. |
| `--version` | Show the version. |
| `--help` | Show the options. |

| Environment variable | Effect |
| --- | --- |
| `REQUALITY_JAVA` | The Java command to use. |
| `REQUALITY_JAVA_OPTS` | The Java options. The default is `-Xmx2g`. |

The log files of the Requality core are in `<storage>/logs`. The server writes other diagnostic text to standard error.

## Connect an agent

**From the VS Code extension:** run **Requality: Connect AI Agent (MCP)...**, choose the agent, then review
and copy the generated configuration. It uses the Java runtime selected by the extension, its bundled server
and every open local workspace folder. Merge the entry with any existing servers in the agent's settings.
Close the catalog in Requality before asking the agent to open it. If you move or upgrade the extension and
the old path no longer exists, generate the configuration again. For a path independent of VS Code updates,
use the standalone installation below.

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
| `list_projects` | Lists the catalogs and their state: loading, ready, broken (with the error), closed, locked (another process has it), released or needs_conversion. |
| `get_tree` | Shows the nodes under a node, to a given depth. |
| `get_node` | Shows one node: id, name, type, description, attributes, links, broken links, document locations, problems and children. |
| `search_nodes` | Finds nodes by text, by an attribute value or by type. Give an attribute by its name in the editor (`name`, `id`, `description`, `type`) or by its key. An unknown attribute is an error. A document location shows its text, its document and its requirements. |
| `import_document` | Imports an HTML, XHTML, XML, DOC, DOCX, ODT, RTF, PDF or TXT file as a document. A document without text is not imported. The numbered headings of a PDF (for example "2.1 Pressure") and the titles of its outline become headings. |
| `import_reqif` | Imports the requirements of one specification of a ReqIF file (refer to [ReqIF files](#reqif-files)). |
| `read_document` | Shows a document paragraph by paragraph: the heading level, the table, row and column of a paragraph in a table, the alternative text of its pictures, the text and the coverage by requirements. A paragraph is covered when each of its sentences is covered. The filter `uncovered` shows the paragraphs with text that no requirement covers, and these sentences. An older version of a document names its new version (`superseded_by`). |
| `apply_changes` | Makes a list of changes in one step (refer to [Operations of apply_changes](#operations-of-apply_changes)). All changes are made, or none. `dry_run` examines the changes, makes none and reports each operation that fails. The result is short: a location by its UUID and paragraph, the warnings together; with more than 50 operations, the counts, the named nodes and the first results (`verbose` gives all). |
| `update_document_version` | Imports the new version of a document and moves the requirement locations to it, sentence by sentence, in one step. Refer to [A new version of a specification](#a-new-version-of-a-specification). |
| `trace_coverage` | Shows the traceability: requirements without locations or test purposes, links that resolve to no node, checker problems, document coverage and open review items. An older version of a document is named, not counted. |
| `test_coverage` | Shows the coverage of the requirements by tests from a coverage file of the test runs (`coverage_file`), and the test failures. |
| `review_changes` | Shows the changes of the agent, also of earlier sessions. With a change number, it shows the values before and after, a page at a time (`offset`, `limit`). |
| `compare_with_revision` | Shows the nodes that differ from a Git revision of the catalog (refer to [Comparison with a Git revision](#comparison-with-a-git-revision)). |
| `revert_change` | Reverts a change of `review_changes`, also a change of an earlier session. |
| `undo`, `redo` | Undoes or redoes the newest change of the agent in this session. With `project`, only when that change is of the project. |
| `get_project_settings`, `change_project_settings` | Show and change the settings of a project (refer to [Project settings](#project-settings)). |
| `list_report_templates`, `generate_report` | Generate a report from a template, or from saved report settings, into a new or empty folder outside the catalogs. `save_as` saves the template, the root and the parameters as report settings. The templates have English names (`name_original` is the name in Requality). The coverage report takes the test coverage from a coverage file. The template `com.unitesk.requality.reports.em` shows the marked text of each requirement in its document, for a person who checks the markup. |
| `create_project`, `open_project`, `close_project` | Create an empty catalog; open a closed or broken catalog, or load a catalog again from its files; close a catalog. |
| `convert_project` | Converts a catalog of an older Requality after a backup. |
| `server_messages` | Shows the messages of the Requality core and the answers of the server. |

Address a node with its path (for example, `/Requirements/A/B`), its UUID, or its id alone (for example, `LIM_04`)
when only one node of the project has this id (two nodes with the id give an error). The user-visible id with the id
prefix of the project (for example, `PUMP-LIM_04`) is also correct.
Give the project name with each call. `list_projects` gives the names.

### Operations of apply_changes

| Operation | Keys | Operation |
| --- | --- | --- |
| `create` | parent, type, id, name, description, attributes, target, template, as | Makes a node. The types: Requirement (the default), TestPurpose, Comment, TextNode, DocFolder, ReportFolder, VirtualNode, InstancesFolder and RestrictedFolder. A VirtualNode needs `target`: the node that it reuses (in a requirement, or as an instance in an InstancesFolder). `template` names a node template of `/Templates`, or `none`. A node with the name or the description of a sibling gets a warning. |
| `update` | node, id, name, description, attributes, as | Changes a node. An attribute value `null` removes the attribute. An attribute of an enumeration takes only the values of the enumeration. |
| `update_all` | under, attributes, type, where_attribute, where_value | Sets or removes attributes of all the nodes of a subtree (of a type, with an attribute value). |
| `rename_attribute` | under, attribute, new_name, value_type | Gives an attribute of the nodes of a subtree another name, or converts its values to another type (string, integer, number, boolean). A value that is not of the new type (`abc` as an integer) stops the change. |
| `delete` | node, recursive | Deletes a node. A node with nodes under it needs `recursive` true. The result counts the nodes and names the links of other nodes that the delete breaks. |
| `move` | node, parent, after | Moves a node. |
| `copy` | node, parent, as | Copies a node and the nodes under it (new UUIDs, the next free ids). |
| `link`, `unlink` | from, to, attribute | Adds or removes a link. `<project>::<path>` is a node of another project. |
| `locate` | requirement, document, quote, paragraph, occurrence, start, end, as | Marks document text for a requirement. A quote can have other white space, quotation marks, dashes and letter case than the document. The same text again changes nothing. A text that another requirement marks too, and a document that has a newer version, get a warning. |
| `unlocate` | location, requirement | Removes a location. |
| `resolve` | item, status | Closes a review item of `trace_coverage`. |

`as` gives a node a name. A later operation of the same call refers to it as `$name`.

### Typical jobs

- **Requirements from a specification.** `import_document`, then `read_document` with the filter `uncovered`.
  The agent writes the requirements with `apply_changes` (operations `create` and `locate`).
  It repeats until `read_document` shows no uncovered paragraph that contains a requirement.
  A `locate` quote can have other quotation marks, dashes and letter case than the document.
  If the quote does not occur, the error shows the most similar sentence.
- **A new version of a specification.** Refer to [A new version of a specification](#a-new-version-of-a-specification).
- **Traceability.** `trace_coverage` tells which requirements have no test purposes or no document text.
  `test_coverage` tells which requirements the tests cover, from a coverage file (below).
- **Review.** `review_changes` shows each change of the agent. `compare_with_revision` shows all the changes since a
  commit. Refer to [Changes, undo and revert](#changes-undo-and-revert).

### A new version of a specification

`update_document_version` takes the old document and the new file (or a document that you imported already).
The new file can have the same name as the old one. The import and the update are one change.

The result has these parts, the most important first:

1. `summary`: the numbers of requirements by status and the number of new sentences.
2. `new_sentences`: the sentences of the new version that the old version does not have and no requirement marks.
   `statement` is true when a sentence has a requirement word (shall, must, should, required, will; должен, обязан,
   необходимо, следует, требуется).
3. `requirements`: the requirements to examine, lost first.
   - `lost`: no similar sentence. `suggestions` gives the most similar sentences.
   - `changed`: the location moved to the most similar sentence (in a section like the old one). `words_removed` and
     `words_added` show the words that changed. `modality_changed` tells that shall, must, may or not changed.
     `numbers_changed` tells that a number changed. A clause number at the start of the new sentence is not part of the
     location. Unlocate the new location if it is wrong.
   - `context_changed`: the text is the same, but sentences came into or went out of its paragraph
     (`paragraph_added`, `paragraph_removed`), or it is in another section (`section_changed`).

   A text that occurs more than once keeps its own copy: a copy that changed does not take the place of a copy that
   stayed. Each location gives its paragraph `p` in the new version.

The unchanged requirements are counted, not listed (`include_unchanged` lists them). Each list has at most `limit`
items (default 50); `review_changes` with the change number and `offset` gives the next ones.

By default (`detach_old`), the requirements then trace only to the new version. The old document stays as the history
and names the new version (`superseded_by`). The new version names the old one (`previous_version`).
An update of a document that has a new version already is refused: update the new version, or revert the update.
A location of an older catalog without character ranges is found by its text.
The operation `resolve` closes the review items of an update that a person made in the Eclipse or VS Code interface.

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

A `qid` is the path or the UUID of a requirement, or its user-visible id (with the id prefix of the project).
`generate_report` with the template `com.unitesk.requality.reports.coverage` and `coverage_file` makes the coverage
report for a person.

## Project settings

`get_project_settings` shows the settings of a project, as the project properties of Requality keep them.
`change_project_settings` changes them in one change (one undo step, which `revert_change` can revert).
Give only the settings that change:

| Setting | Value |
| --- | --- |
| `attributes` | Project attributes, which all nodes inherit: name → `{"type": ..., "value": ...}`, or `null` to remove one. The types are string, integer, number, boolean, list, html and `enum:<enumeration>`. |
| `enums` | Enumerations: name → `{"type": string, integer or number, "values": [value or {"value", "comment"}]}`, or `null` to remove one. The server does not remove an enumeration or a value that nodes use. |
| `required_attributes` | Mandatory attributes by node type: type → `[{"attribute", "default"}]`, or `null`. The nodes of the type get the attributes that they do not have, with the default value. |
| `checker_rules` | All checker rules: `[{"name", "formula"}]`. A formula is JavaScript that is true for a correct node. It sees the node as `one`: `one.attr(name)` (a user attribute, or `%Name`, `%Id`, `%Description`, `%Type`; null when absent), `one.attrs()`, `one.getParent()`, `one.getChildren()`, `one.getLinked(attribute)` and `one.getQID()`. Example: `one.attr('priority') != null`. A formula that does not compile, or that fails on the first requirements of the project, is refused. |
| `checkers` | `{"user_visible_ids": true or false, "defined_terms": true or false}`. |
| `id_prefix`, `default_link_attribute` | Text. |
| `active_templates` | The active node template of a node type: type → template id of `/Templates`, or `null`. |

The JavaScript of a catalog (checker rules, formulas of attributes and of report settings) runs in a sandbox in the
MCP server. It can use text, numbers, collections, dates and the nodes of the tree. It cannot use files, programs,
the network or the classes of Java.

## ReqIF files

`import_reqif` imports the requirements of one specification of a ReqIF file (`.reqif` or `.xml`, the exchange format
of DOORS, Polarion and other tools) into `/Requirements`, in one change:

- The structure of the specification becomes the tree of the requirements.
- `ReqIF.Text` becomes the description; `ReqIF.Name` or `ReqIF.ChapterName` becomes the name.
- The other attributes keep their names. An enumeration becomes an enumeration of the project.
- A value that the file does not give stays empty (or takes the `DEFAULT-VALUE` of its attribute).
- Pictures and OLE objects of the texts are left out; their text stays. The result counts them.

If the file has more than one specification, give one with `specification` (its name or its identifier).
Import into an empty project (`create_project`). To import a new version of the file, give `replace`: the
requirements that are in the file (by `ReqIF.ForeignID`, else by the identifier of the object) are updated and keep
their UUIDs, test purposes and other nodes. The requirements that are not in the file go; the result lists them.
A test purpose goes only with `force`. `revert_change` brings everything back.

The export into ReqIF is not supported: the core exports only into a DOORS ReqIF model that exists already.

## Comparison with a Git revision

`compare_with_revision` compares the node files of a catalog with a Git revision (a commit, a branch or a tag; the
default is `HEAD`). The catalog must be in a Git repository, and Git must be installed. The result lists the added,
the deleted and the changed nodes. A changed node shows the attributes that changed (name, description, locations
and user attributes) with the values before and after, and the old path of a moved or renamed node.
It shows all changes since the revision: the changes of the agent, of VS Code, of a colleague and of other sessions.
Use it to review the catalog before a commit or a pull request. `under` limits the comparison to a subtree.

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
The server removes the changes older than one year from the history: they cannot be reverted then, and
`review_changes` does not list them.
Only the user can read the storage folder that the server makes. The backups keep the three newest copies of each
catalog.

- `undo` and `redo` operate on the changes of the agent in this session, newest first.
  `undo` does not undo a change that a person made in VS Code.
- A failed change or a dry run that deletes, moves or renames existing nodes discards the uncommitted transaction
  and reloads the catalog. Its files stay untouched. The response reports this reload. Earlier session undo/redo steps
  end there; the persistent history and `revert_change` remain available.
- `revert_change` reverts any change, also a change of an earlier session.
  The nodes of the change become as they were before it. A deleted node comes back with its UUID,
  so the links and the locations that refer to it are correct again.
- If a node of the change was changed later (by the agent, or by a person in VS Code), or if another node links to
  a node that the revert deletes, `revert_change` does not revert. The error tells which nodes changed and which
  later changes of the agent changed them: revert these first, newest first.
  `dry_run` shows what a revert does. `force` reverts and loses the later changes of these nodes; the result names the
  links that break.
- The result of a revert counts the nodes that it restored, recreated and deleted, and names the first 20
  (`verbose` names all).
- A revert is also a change. A revert of a revert makes the first change again.
- After undo, redo, a revert or a failed change, the server indexes the links to the nodes again.
- The undo steps end when the server loads a catalog again (`open_project`, a change on disk, a catalog that another
  session used). `revert_change` still operates.

Two servers can use the same storage folder (for example, two sessions of the agent on the same catalogs, one after
the other): they share the history. Each change has its own number.

## Files that the agent can use

The agent can import files and write reports only in these folders:

- the folders of the catalogs (the arguments of `requality-mcp`);
- the folders of the `--allow` options;
- a folder that you allow when the agent needs it. If the client can ask you (MCP elicitation, for example Claude
  Code), the server asks before it uses a file outside these folders. It asks for the folder of the file, never for
  the home folder, a folder above it, a top folder or a hidden folder (such as `~/.ssh`). The folder stays allowed
  until the server stops.

The paths must be absolute. The server imports only document files
(HTML, XHTML, XML, DOC, DOCX, ODT, RTF, PDF and TXT) and reads only XML coverage files.
`generate_report` writes only into a new or empty folder, and not into a catalog. The coverage of a report comes only
from a coverage file in these folders: the server does not read the coverage source of saved report settings when it
is a URL or a file outside the folders, and `apply_changes` does not change it.

The import of an HTML document copies the files that its pictures, scripts and style sheets refer to, and downloads a
style sheet from a URL. The server refuses an HTML document that refers to a URL or to a file outside these folders
(with each way that the importer reads a path). The error names the references. Remove them (or put the files next to
the document) and import again.

A report template cannot make Java objects that run programs (FreeMarker `?new` of `Execute` and similar classes)
and cannot use `?api`. This also applies to the templates of a catalog (`.templates`).

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
Before a call on a project, the server compares the node files with the tree it has (at most once in 2 seconds).
When a file has a node that the tree does not have, the server loads the project again, and the result of the call
has a `notice`. The files that the server writes itself are not a change on disk.

The server does not write over a change on disk. A change of the agent that touches a changed file is refused (for
example, when the file changed in the last 2 seconds), and the error names the nodes. `open_project` then loads the
catalog again from its files.

## One process for each catalog

Only one Requality process can open a catalog at a time. If VS Code or another session of the agent has a catalog,
`list_projects` shows the state `locked`.

- A call on a locked project waits for the catalog and asks the other process to release it.
  Another MCP server releases a catalog that no tool used for 20 seconds. VS Code does not release a catalog: close
  the catalog in VS Code (or close the VS Code window). If the catalog does not come in 45 seconds, the call tells it.
- `close_project` releases a catalog at once.
- With `--release-after <minutes>`, the server also releases a catalog that no tool used for this time.
- The server does not convert a catalog while another process can open it: the catalog stays locked during and after
  a conversion.

The locks are in `~/.requality/locks` (`--locks` gives another folder). They operate for the processes of one user
on one computer. If the server cannot write the folder of the locks, it opens the catalogs without a lock and does not
change or convert them; `list_projects` shows a warning. Give a folder that it can write with `--locks`.
The folder of the locks must be on a local disk: the locks of a network file system (NFS, SMB, a Windows drive in WSL)
may not keep another process out. `list_projects` shows a warning for such a folder.

## Questions of the Requality core

The server has no user. It gives these answers:

- To a message with one button: that button (OK).
- To a question with a choice: cancel. The only exception is the conversion question of a catalog that you allowed
  (`convert_project` or `--convert`).
- To an input box and to a file dialog: cancel. A dialog closes.

`server_messages` shows all messages and answers.

## Not supported without a window

These features of Requality need the VS Code extension (or the Eclipse plug-in):

- The export into ReqIF (refer to [ReqIF files](#reqif-files)).
- The pictures and OLE objects of a ReqIF import.
- The mass operations of the files of DOORS (the import of DOORS ids, their renames, the explosion of headers). The
  other mass operations are `update_all` and `rename_attribute`.
- The builder switch "Enable Checkers" of Eclipse. The checkers of the server run with `trace_coverage` (`refresh`).
- Fix Locations Descriptions and Change transferred status (commands of the Eclipse explorer).

Requality has no test generator of its own (its extension point is for other plug-ins).

The text search finds a phrase only when the phrase has no formatting in it.
On Windows, the folders in the arguments must have only letters of the code page of the system (a limit of the Java
launcher).
The statement words of `update_document_version` are English and Russian
(shall, must, should, required, will; должен, обязан, необходимо, следует, требуется).
