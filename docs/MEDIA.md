# Refresh the repository and website demo

`npm run media` records the actual VS Code extension on a disposable copy of the ETSI TS 103 096 test catalogue.
It produces `media/readme/overview.gif` and four screenshots used by the README. The Formalized project page uses
the same five files; its other project illustrations are unrelated to Requality.

Requirements: Node.js, Java, FFmpeg (including `ffprobe`), an environment where the VS Code application can start,
and the built server/test catalogue. If needed, prepare them with `npm ci`, `npm run build:server` and
`npm run test:server`.

To record and update both existing checkouts in one run, execute this from the Requality repository in a normal
Terminal window (set the path to your existing Formalized checkout):

```sh
REQUALITY_MEDIA_SITE="$HOME/Documents/ChatGPT/requality-vscode/formalized-site" npm run media
```

Omit `REQUALITY_MEDIA_SITE` to update only the repository. `VSCODE_TEST_VERSION=1.106.0` selects a specific VS Code
version; the default is `stable`. The site destination must already contain `.openai/hosting.json` and
`dist/projects/requality-vscode/assets/`; the command never creates a new Site.

The recording shows the explorer, expanded requirements, requirement properties, context menu, Document Editor
and UniEditor. It creates all frames and encodes the output in a temporary directory, then verifies the GIF's size,
frame count and duration before replacing the five files. Capture or encoding failure leaves the existing media
untouched. The printed temporary path retains the frames for diagnosis on failure.

After recording, inspect the full GIF and all four screenshots: readable content, no loading dialogs or error
notifications, and the expected document/editor views. Confirm that the five files in the two checkouts match.
The command also updates the website media provenance in `assets/README.txt`.

Commit/push the repository media and publish the existing Formalized Site through its Sites workflow. Copying the
files into its local checkout does not publish the live website.
