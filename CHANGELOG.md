# Changelog

## 1.0.0 (2026-10-21), Hytale 0.7.3

First release.

- **`/note <text>`** adds a timestamped line to a Markdown file: the player, the world, the position and any known `#tags`. It works from the server console too.
- **`/notes`** shows the last lines in chat (`/notes <n>` for 1 to 20). `/notes mark <label>` writes a heading. `/notes file` (operators) prints the file's path.
- **Each file starts with a header**: the server build, the start time, the first noter, and a table of every loaded plugin and its version.
- **One file per day and world** by default, named from a template. Append-only, UTF-8, flushed after each line. World names are made safe for file names.
- **Settings** in `notes/settings.json`: output folder (absolute paths and Windows paths work), file name and line templates, six time formats, UTC, coordinate decimals, note length limit, cooldown, plugin table on or off, chat echo, who may write notes, the command's name and the tag list. Missing keys use their defaults; the file is never rewritten after first run.
- **Permission** `playtestnotes.write`, for `WhoCanNote: permission`. Operators always pass.
- **Works with Mod Settings Manager** when it's installed (no dependency): a sidecar gives every setting a label, section, range or dropdown, and the live-apply hook applies changes without a restart. A settings change made that way is written into the current notes file. An output folder that can't be written is refused, and notes keep going to the old one.
- **`state.json`** (note count and last file) is versioned from the start (`"Version": 1`).

Not in 1.0: screenshots attached to notes (a server can't take them), a notes window, uploading or syncing notes, and per-player files.
