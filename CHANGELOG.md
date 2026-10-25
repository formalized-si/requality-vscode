# Changelog

## 1.5.0 (pre-release)

The port now uses the branch `unstable` of Requality (commit `e3f807bd4`, 2026-10-23).
Release 1.4 does not get changes after 2026-06-10. The upstream developers make all changes in `unstable`.

New: the Requality MCP server, Requality without a window for AI agents (`requality-mcp`, refer to `docs/MCP.md`).
The tools follow the jobs of a requirements engineer:

- requirements from a specification: `import_document`, `read_document`, `apply_changes`;
- a new version of a specification: `update_document_version` (imports the new version and moves the locations
  sentence by sentence, in one change; flags changed modal words and numbers);
- coverage and traceability: `trace_coverage`, and `test_coverage` from a coverage file of the test runs;
- a review of the changes of the agent: `review_changes`, `undo`, `redo`, and `revert_change` for a change of any
  session (the nodes come back with their UUIDs; a revert stops when the nodes were changed later, and names the later
  changes and the links it would break); `compare_with_revision` shows the changes since a Git revision;
- ReqIF: `import_reqif` imports the requirements of a ReqIF file (pictures and OLE objects are left out);
- project settings: `get_project_settings` and `change_project_settings` (project attributes, enumerations, mandatory
  attributes, checker rules, id prefix, default link, active node templates);
- `apply_changes` makes all node types of the New menu (also virtual nodes), copies subtrees, and changes many nodes
  at once (`update_all`, `rename_attribute`); a dry run reports every operation that fails.

Prompts give these jobs as commands of the agent. The GitHub release has archives with a Java runtime and build
provenance attestations. The VS Code extension contains the same server.

- The agent can import files and write reports only in the folders of the catalogs and in the `--allow` folders.
  An HTML document that refers to a URL or to a file outside these folders is refused.
- The server converts a catalog of an older Requality only after a backup (`convert_project`), or with `--convert`.
- The server loads a catalog again when its files changed on disk (for example by `git pull`), and does not write over
  such files. Two sessions take turns with a catalog: an idle session releases it when the other one waits for it.
  The history of the changes is safe when two servers share the storage folder.
- The JavaScript of a catalog (checker rules, formulas) runs in a sandbox in the MCP server, and report templates
  cannot run programs (FreeMarker 2.3.34 with the safer class resolver). Jetty 9.4.58; Jersey and Jackson are gone.
- When the client can ask the user (MCP elicitation), the agent can use a file outside the folders of the server after
  the user allows its folder.
- The HTTP and WebSocket servers of the web editors accept connections from this computer only, and the WebSocket
  servers only from the pages of the editors (Origin). The headless server starts none.
- Apache Tika 3.3.3 and POI 5.5.1 replace Tika 2.7.0 (XXE in the XFA forms of a PDF: CVE-2025-54988,
  CVE-2025-66516). The coverage files are read without a DOCTYPE.
- The packages use a pinned Temurin JRE (`scripts/jre-lock.json`, its SHA-256 checked).
- Fixes of the core: a requirement of a converted catalog can be deleted; an undo that removes a document with its
  locations does not fail; the report export does not copy files outside the report folder; a second WebSocket server
  for more than four web editors starts.
- The plug-in finds its report templates and web editors also in a folder whose path has a space.
- Only one Requality process opens a catalog: a catalog that VS Code shows is not open in the MCP server, and the
  opposite.
- The name field of the Properties view has the experimental check box **Ignore**.
- The Requality Explorer sorts the projects by the project names in `.project` files, not by the folder names.
- The **Edit** menu does not have **Find Next** and **Find Previous**. These items do not operate in Requality (#13947).
- **Select Image** in the web editors copies the image to a temporary folder of the node. The image shows immediately.
- The report templates fit large images to the page width if the report has the attribute `fitImages`.
- The search bar of the web editors gets the focus when it opens.
  **Ctrl+F** (**Cmd+F** on macOS) opens it, **Shift+Enter** finds the previous match.
- The editor of list attributes can edit HTML items as text.
- The reports have new titles (`DocumentTitle`, `DocumentSubTitle`). You can cancel more report generation steps.
- The upstream core has fixes for deadlocks, data races of checkers, duplicated UUIDs, conversion of old formats and
  projects loaded from Git revisions.
- Fix of the core (also in `unstable`): the conversion of an old catalog keeps the attributes that are copies from
  nodes outside the catalog. Before, the conversion of the ETSI TS103096 catalog stopped with an error.

## 1.4.0 (preview)

The first release. It is a port of the Requality Eclipse plug-in, release 1.4.

- The Requality core and all 320 classes of the Eclipse plug-in run in a Java server.
- The Requality Explorer shows the view of the plug-in. A setting selects a native VS Code tree.
- The views, editors, dialogs, wizards, preference pages and property pages are the Eclipse ones.
- The packages for Windows, Linux and macOS contain a Java 21 runtime.
  The package for other platforms uses the Java of the system (Java 17 or later).
- VS Code 1.95 or later.
