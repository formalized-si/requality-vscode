# Changelog

## 1.5.0 (pre-release)

The port now uses the branch `unstable` of Requality (commit `e3f807bd4`, 2026-10-23).
Release 1.4 does not get changes after 2026-06-10. The upstream developers make all changes in `unstable`.

New: the Requality MCP server, Requality without a window for AI agents (`requality-mcp`, refer to `docs/MCP.md`).
An agent reads the catalogs, finds nodes, creates and changes requirements, links and document locations,
imports documents, runs the checkers and generates reports. The GitHub release has archives with a Java runtime.
The VS Code extension contains the same server.

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
