# Context bar and command box

Two additions of the MadameLabs fork: chips that say where the focused terminal is, and a
floating box to type a command in.

## Context chips

The context chips show, for the focused pane:

- **Machine**: `user@host` from the last OSC 7 a remote shell sent, else the destination of an
  `ssh` running in front, else `tlnix` for this phone.
- **Folder**: the last folder of the path. For a program in front of the shell (an editor, a
  coding agent) it is that program's own folder, so a `cd` inside it shows.
- **Chat**: while a coding agent runs in the pane, the name of its conversation, from the
  `ml_chat` variable or the terminal title.
- **Limits**: while a coding agent runs, the 5-hour and weekly usage in percent, red above 80%,
  with the time the 5-hour limit renews and the day the weekly one does.

They stand in one of two places:

- **In the status bar**, in the clock's place, when **Settings → Terminal → Status bar → Show clock** is
  off.
- **Above the extra keys**, with the window chips beside them and the chat on a row of its own,
  when **Settings → Terminal → Status bar → Context chips above the keys** is on. The status bar is put away while they stand there.

The chips refresh every two seconds, less often in lazy mode, and not at all while nothing shows
them.

### Feeding the chat and the limits

A program sets them with iTerm2's user variables, one escape per value, the value in base64:

```sh
printf '\033]1337;SetUserVar=%s=%s\007' ml_5h "$(printf 42 | base64)"
```

| Variable | What it holds |
|---|---|
| `ml_chat` | The conversation's name |
| `ml_5h` | The 5-hour usage, a percent |
| `ml_5h_reset` | When the 5-hour limit renews, `HH:MM` |
| `ml_semana` | The weekly usage, a percent |
| `ml_semana_reset` | The day the weekly limit renews |

A coding agent's status line script is the usual place to send them. Inside tmux, wrap each
escape in tmux's passthrough.

## Command box

The command box is a place to compose a command before it reaches the shell: edit it with the
system keyboard's spelling and voice, then send it with Enter.

- **Below the key pages** (the default): a text row under the extra keys.
- **Floating**: with **Settings → Terminal → Command box → Floating command box** on, the
  **Toggle command box** key opens it as a box you can move and resize. It has its own glass card
  in Appearance, and its own text size and colours on the same page.

In the floating box:

- the arrows key moves the cursor by swiping, and its corners are Home, Page Up, Page Down and End;
- sliding along the space bar moves the cursor;
- Send swipes up for a new line;
- Cut, Copy, Paste, Clipboard history and Dictate sit along its edge.

Add the key to the extra keys as `tool:terminal.toggle_command_box`, or bind it as a chord
([Custom keybindings](Custom_Keybindings.md)).
