# Upstream Requality sources

Everything in this directory comes from the official Requality repository and is
distributed under the Apache License 2.0 (`LICENSE.txt`).

| Item | Value |
| --- | --- |
| Repository | `https://forge.ispras.ru/git/reqdb.git` |
| Branch | `release_1_4` |
| Commit | `58e3d525c` (2026-06-10, "fixes read-only flag override") |
| CKEditor | `https://forge.ispras.ru/repo/ckeditor/zips/ckeditor-4.16.zip`, SHA-256 `619dedb80e91bd378cc7fbf31e525ed7f016c6db79342f64eb951c99a4ba5580`, extracted over `jstools/` at build time exactly like the upstream `extractCkEditor` Gradle task |

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
