# Playtest Notes

Write down what you find while you play, without leaving the game. `/note <text>` adds a timestamped line to a Markdown file on the server. Each line records who wrote it, the world, the position and any `#tags`. Each file starts with a table of the loaded plugins and their versions (the built-in Hytale ones are counted on one line), so you always know which build you were testing.

Hytale server 0.7.3. MIT licensed. No dependencies.

## Install

1. Put `PlaytestNotes-1.0.0.jar` in the server's `mods` folder. For singleplayer, use `%APPDATA%\Hytale\UserData\Mods\`.
2. Start the server (or open the world). The mod creates its folder, `mods/Hledjolf_PlaytestNotes/`, with `notes/settings.json` inside.
3. Type `/note it works #idea`.

The notes are in `mods/Hledjolf_PlaytestNotes/notes/`, one file per day and world, for example `2026-10-21_default.md`. In singleplayer, that's inside the world's save folder: `%APPDATA%\Hytale\UserData\Saves\<world>\mods\Hledjolf_PlaytestNotes\notes\`.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/note <text>` | See `WhoCanNote` | Adds one line to today's notes file. Words like `#bug` that match a known tag become tags; other `#words` stay in the text. |
| `/notes` | Anyone | Shows the last 5 lines of the current file in chat. |
| `/notes <n>` | Anyone | Shows the last `n` lines (1 to 20). |
| `/notes mark <label>` | See `WhoCanNote` | Writes a `## <label>` heading, to split a session into parts. |
| `/notes file` | Operators | Prints the full path of the current file. |

- `/note` also works from the server console. Console notes have no position, and the player shows as `console`.
- A note longer than `MaxNoteLength` is refused, not cut short.
- A second note within `CooldownSeconds` is refused. That guards against pressing Enter twice.
- Hytale's command parser reads every command line first, so two things in typed text change: a backslash starts an escape (type `\\` for one `\`), and square brackets get spaces around them (`[a]` arrives as `[ a ]`).

## In chat

The file is Markdown; chat shows plain text. `/note` answers with the line minus its formatting, and `/notes` strips it the same way (a `## heading` shows as `[heading]`, and the plugin table is skipped):

```
Noted. 14:05:32 Alex @ default (412, 71, -88): the bridge texture flickers at dusk #art
```

Only the formatting from the line format is removed. What you typed (`2*3`, `snake_case`) stays as written.

## A notes file

```markdown
# Playtest notes: default, 2026-10-21

Server 0.7.3 · started 14:02:11 · notes by Alex

| Plugin | Version |
| --- | --- |
| Hledjolf:PlaytestNotes | 1.0.0 |
| ModSettingsManager:ModSettingsManager | 1.0.0 |
| + 212 built-in Hytale plugins | 0.7.3 |

- 14:05:32 **Alex** @ default (412, 71, -88): the bridge texture flickers at dusk #art
## Boss fight
- 14:09:03 **Alex** @ default (530, 64, -12): boss heals too fast #balance
- 14:09:40 *settings changed: MaxNoteLength 280 -> 500 (by MSM)*
```

- A file starts with the first note of a server session, and a new one starts when the file name changes (a new date, or a note from another world). If the file already exists from an earlier session, the new session is added below a `---` line.
- Files are UTF-8 with `\n` line endings. Each line is written and flushed at once, so nothing is lost if the server stops.
- World names are made safe for file names: `<>:"/\|?*` and control characters become `_`. Letters in any language are kept.

## Settings: `notes/settings.json`

The file is in the mod's data folder. The first start writes only the most-used keys. Every other key below works when you add it; until then its default applies.

| Key | Default | What it does |
| --- | --- | --- |
| `Enabled` | `true` | When off, `/note` answers "Playtest Notes is turned off." |
| `OutputFolder` | `"notes"` | Where the files go: absolute, or relative to the data folder. On Windows, write backslashes doubled in JSON: `"D:\\Playtests"`. |
| `FileNameTemplate` | `"{date}_{world}.md"` | Tokens `{date}`, `{world}`, `{server}`, `{player}` (the session's first noter). Must end in `.md` or `.txt`. |
| `LineTemplate` | (long, see below) | Tokens `{time}`, `{player}`, `{world}`, `{x}`, `{y}`, `{z}`, `{text}`, `{tags}`. Anything else is written as is. |
| `TimeFormat` | `"HH:mm:ss"` | One of `HH:mm`, `HH:mm:ss`, `h:mm a`, `h:mm:ss a`, `ISO-8601`, `epoch-ms`. |
| `UseUtc` | `false` | Dates and times in UTC instead of the server's time zone. |
| `CoordinatePrecision` | `0` | Decimal places for positions, 0 to 3. Coordinates are rounded down, so 0 gives the block. |
| `MaxNoteLength` | `280` | The longest note, in characters (20 to 2000). |
| `CooldownSeconds` | `0.5` | The shortest time between two notes from one player (0 to 10). |
| `IncludeModList` | `true` | Start each file with the plugin table. |
| `ModListIncludeBuiltIn` | `false` | List every built-in `Hytale:*` plugin in the table. Off: one line gives their count. |
| `EchoToChat` | `"self"` | Who sees "Noted." and the line: `none`, `self`, `operators` or `everyone`. |
| `WhoCanNote` | `"everyone"` | `everyone`; `permission` (operators, plus players with `playtestnotes.write`); or `operators`. The console can always write. |
| `CommandName` | `"note"` | The note command's name, without the slash. The list command is this plus `s`. Takes effect after a restart. |
| `Tags` | `["bug", "idea", "balance", "art", "perf", "ui"]` | The words after `#` that count as tags. |
| `Debug.HookDelayMillis` | `0` | For testing only: makes the live-apply hook wait this long (0 to 5000). Set it inside a `"Debug": { }` object. |

Default `LineTemplate`: `"- {time} **{player}** @ {world} ({x}, {y}, {z}): {text} {tags}"`

A value of the wrong type or out of range is replaced by its default, with a line in the server log saying so. The mod never rewrites `notes/settings.json` after it first creates it, so your edits are never overwritten.

`state.json`, next to the `notes` folder, keeps a count of notes and the last file written. It isn't a setting; leave it alone.

## Works with Mod Settings Manager

Playtest Notes doesn't need [Mod Settings Manager](https://github.com/Hledjolf/ModSettingsManager), but if it is installed, every setting above shows up in its `/modsettings` window, with labels, sections, ranges and dropdowns, and changes apply at once without a restart. Only `CommandName` waits for a restart. `Tags` is shown read-only there; edit it in the file.

If you set `OutputFolder` to a folder the server can't write to, the change is refused and notes keep going to the old folder. The server log has a line with the reason and a stack trace. Mod Settings Manager 1.0.0 shows that refusal as a "restart needed" banner, but a restart won't help: fix the folder instead. (For mod authors: the live-apply hook throws on purpose here, so the reason reaches the log. Don't change it to `return false`.)

## Permissions

| Node | Meaning |
| --- | --- |
| `playtestnotes.write` | Lets a player write notes when `WhoCanNote` is `permission`. |

Operators always pass. Grant the node with `/perm user add <uuid> playtestnotes.write`.

## Project layout

`core/` is game-agnostic plain Java: note formatting, file names, templates, settings and cooldowns. `adapters/hytale/` is the Hytale mod; it builds into a single jar with `core/` merged in. Versions for other games can be added as further folders under `adapters/`.

## Building from source

Java 25 and the Hytale server jar are needed. `./gradlew release` builds `build/release/PlaytestNotes-1.0.0.jar` and the `.zip`. Set the server jar's path as `hytaleServerJar=<path>` in `~/.gradle/gradle.properties`.

## License

MIT, copyright (c) 2026 Hledjolf. See [LICENSE](LICENSE).
