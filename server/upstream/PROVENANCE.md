# Upstream Requality sources

Everything in this directory comes from the official Requality repository and is
distributed under the Apache License 2.0 (`LICENSE.txt`).

| Item | Value |
| --- | --- |
| Repository | `https://forge.ispras.ru/git/reqdb.git` |
| Branch | `unstable` |
| Commit | `e3f807bd4` (2026-10-23, "fix checkers to be called on project close") |
| CKEditor | `https://repono.ispras.ru/repo/ckeditor/zips/ckeditor-4.16.zip`, SHA-256 `619dedb80e91bd378cc7fbf31e525ed7f016c6db79342f64eb951c99a4ba5580`, extracted over `jstools/` at build time exactly like the upstream `extractCkEditor` Gradle task |

## Layout

| Path | Upstream path | Use |
| --- | --- | --- |
| `core/java` | `src/core/java` | Compiled into the Requality server. Eclipse-independent core: model, storage, attributes, checkers, reports, ReqIF, document processing, HTTP/WebSocket services for the web editors. |
| `jstools` | `jstools` | Original web editors (UniEditor, ModuleEditor, document markup, report viewer) served by the core. |
| `templates`, `templatesStore` | same | Built-in FreeMarker report templates. |
| `icons` | `icons` | Node type and action icons used by the VS Code views. |
| `docs` | `docs` | Original user manual, opened by the Help command. |
| `resources` | `resources` | Localization bundles of the core messages (`localization/localization_en.properties`, `_ru`; on the server class path) and the Windows registry fixes of the plug-in (`regfix/*.reg`). |
| `eclipse-reference` | `src/requality/java`, `plugin.xml`, `META-INF/MANIFEST.MF` | **Not compiled.** The Eclipse UI layer that the VS Code extension replaces; kept as the reference for the 1:1 port. |

## Local modifications

Every change to upstream code is marked with a `VSCODE-PATCH` comment and listed here.

| File | Change | Reason |
| --- | --- | --- |
| `core/java/com/unitesk/requality/core/storage/FilesTracker.java` | Null guard in `open(Object)` | `dbsa` is null while a project is reopened during storage-format conversion (upstream NPE, reproduced on the public ETSI TS103096 catalog). |
| `core/java/com/unitesk/requality/core/service/EditorsSocketService.java` | `nodeReferenceUUID` for `loadNodeByUUID` / `loadNodeTreeByUUID` | The UniEditor page of a requirement whose location addresses a document element by its path (`/Documents/<doc>/<uuid>`, catalogs converted from an older format) asked for that node; `UUID.fromString` threw, the message was lost and the page never initialized (reproduced on the public ETSI TS103096 catalog). |
| `core/java/com/unitesk/requality/core/tree/TreeLogic.java` | `canBeDeleted` returns false for a node without a work parent | The explorer's selection record of a selected project is its root node (RequalityCNF); while a part with a node selection is active, the node delete handler (DeleteSelection) asks `canBeDeleted` about it, which dereferenced the missing parent of the root (upstream NPE, logged; the root cannot be deleted). |
| `core/java/com/unitesk/requality/core/attribute/AttributeStorage.java` | `getAD` keeps the stored attribute when its origin node is missing | Old catalogs have attributes copied from nodes that are not in the catalog (22 `_type` attributes in the public ETSI TS103096 catalog). `getAD` removed such an attribute from the map that the storage caches. Since `unstable` the core loads a node without a type, the format conversion then failed with an NPE in `ResourceStorage.saveStorageAttributes` (shown in the conversion message), and a save would remove `_type` from the file. |
| `core/java/com/unitesk/requality/core/service/ResourcesProvider.java` | The HTTP server of the web editors listens on 127.0.0.1 only; its path token comes from `SecureRandom`; no server dump and no token in the output and the logs | The server listened on every network interface, and the 12-character path token was its only access check. The token was printed to standard error (which an MCP client keeps in its log) and written to the log files. |
| `core/java/com/unitesk/requality/core/service/WSServer.java` | The WebSocket servers listen on 127.0.0.1 only | The same as the HTTP server: the web editors run on this computer. |
| `core/java/com/unitesk/requality/core/service/PortUtils.java` | `testPortIsFree` also binds 127.0.0.1 without `SO_REUSEADDR` | With the servers on 127.0.0.1, the wildcard test of a port (with `SO_REUSEADDR`, the default on macOS) passed for the port of a running server, and the next WebSocket server (one for every four web editors) could not start. |
| `core/java/com/unitesk/requality/core/api/DefaultAPISource.java` | `init` and `destroy` start and stop no HTTP or WebSocket server when the system property `requality.noServers` is true | The headless MCP server (`requality-mcp`) shows no web editors, so it opens no network port. |
| `core/java/com/unitesk/requality/core/operation/CommonRemoveLocation.java` | `locationUUID` reads a location reference in the old path form (`/Documents/a.xhtml/<uuid>`) | `UUID.fromString` threw "UUID string too large", so a requirement of a converted catalog could not be deleted (reproduced on the public ETSI TS103096 catalog). |
| `core/java/com/unitesk/requality/core/operation/LocationEventsHandler.java` | Null checks for the document of a location, a deleted document and a missing requirement | An undo that removes a document with its locations put a null document into a `ConcurrentLinkedQueue` (NPE), and the undo failed. |
| `core/java/com/unitesk/requality/core/operation/ReportExportHandler.java` | `inside`: an image path with `../` that leaves the report folder or the export folder is not copied | A description with `<img src="../../file">` made the export read a file outside the report and write it outside the export folder. |
| `core/java/com/unitesk/requality/core/transaction/TransactionStorage.java` | `getTouchedNodes` | The MCP server compares the states of these nodes before and after a transaction, to revert an agent change later with the node UUIDs. |
| `core/java/com/unitesk/requality/core/transaction/UndoableTreeDBOperation.java` | `getTransaction` | The MCP server knows which agent change an undo step is by its transaction, not by its label. |
