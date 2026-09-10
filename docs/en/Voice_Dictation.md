# Voice dictation

The built-in terminal keyboard can dictate. Swipe the microphone gesture, speak, and the text is
written into the terminal session you started from.

Out of the box, that gesture hands the job to whatever speech recognizer Android offers. Configure a
Groq key and the launcher does the dictation itself, which is what makes the rest of this page
possible: you choose the transcription service, and you can have the text corrected, shortened,
decorated with emoji, or turned into a shell command before it reaches your prompt.

Nothing here runs a command for you. Terminal mode writes command text and stops; you read it and
press Enter yourself, or you do not.

## Quick start

1. Open **Settings ▸ Keyboard & input ▸ Voice and post-processing**.
2. Turn on **Enable voice post-processing**.
3. Paste a [Groq API key](https://console.groq.com/keys) into **Groq API key**.
4. Swipe the Enter key toward the microphone and speak. Long-press it instead to pick a mode first.

Until both the switch and the key are set, the gesture keeps its original behaviour. A feature you
have not configured does not take the gesture away from you.

## The gesture

| Gesture | What happens |
| --- | --- |
| Swipe to the microphone | Records straight away, using whichever mode is currently selected |
| Long-press the microphone | Opens the panel so you can choose a mode, then record |

The panel shows the state as it goes — recording, transcribing, applying the mode, inserting — and
**Cancel** abandons the recording without writing anything.

## Modes

A mode transforms the transcript before it is inserted. With none selected you get the raw
transcript, which is always available no matter how the rest is configured.

| Mode | What it does |
| --- | --- |
| **Correct** | Removes hesitations, repetitions and false starts; fixes punctuation and spelling |
| **Shorten** | Says the same thing in fewer words, without dropping anything you said |
| **Emoji** | Adds fitting emoji, sparingly |
| **Terminal** | Turns a spoken intent into a command line, as text |

Correct and Shorten are alternatives to each other. Emoji rides along with either of them, or on its
own. Terminal takes the whole transformation and switches itself off once it has produced a command,
so the next thing you say is not turned into a command by accident.

If the transformation fails — no connection, a rejected key, an empty answer — **you still get the
raw transcript**, along with a note that the transformation did not happen. Losing what you said is
never the price of an optional rewrite.

## Settings

**Enable voice post-processing** decides whether any of this is on. With it off, the mode chips do
not appear at all.

**Groq API key** is write-only: it never shows you what is stored, only whether something is. It is
encrypted with a key held in the Android Keystore and kept in a preferences file of its own, so a
settings backup or a debug dump of the ordinary preferences cannot carry it out.

**Speech model** and **Text model** name the Groq models used to transcribe and to transform.
`whisper-large-v3-turbo` and `llama-3.3-70b-versatile` are the defaults. Any model your account can
reach is accepted, including the agentic `groq/compound` systems.

**Dictation language** is the language you speak, `pt` by default.

**Creativity** is the sampling temperature for the transformation, from 0 to 1. Low is what you
want: these are rewriting tasks, not writing tasks.

**Prompts** are the instructions sent for each mode, and every one of them is editable. Leave a
field empty and the default is used — the field's summary shows you the text that will actually be
sent. The defaults follow your device language, because an instruction and the speech it transforms
should be in the same language.

**Show terminal mode** hides the terminal chip if you would rather not have it within reach.

**Custom vocabulary** is one term per line: names, commands, product words whose spelling you want
respected. It is a spelling reference and nothing more. A term you did not say is never inserted
because it appears in this list.

## What leaves the device, and what does not

Dictating sends your audio to Groq, and using a mode sends the transcript there too. That is the
trade you are making; if it is not one you want, leave post-processing off and the platform
recognizer keeps working as before.

On the device itself:

- The recording is written to the app's private cache and deleted when the dictation finishes, is
  cancelled, or fails. A recording left behind by a process that was killed is removed before the
  next one starts.
- The key, the audio and the transcript are never written to the log.
- The text is inserted into the session that was active when you started speaking. If that session
  is gone by the time the text is ready, it is discarded rather than written somewhere else.

## When something goes wrong

| Message | What it means |
| --- | --- |
| Set a Groq key in voice settings first | Post-processing is on but no key is stored |
| The Groq key was rejected | The key is wrong, revoked, or belongs to another account |
| Groq rate limit or quota reached | Your account is out of allowance for now |
| No connection to the transcription service | The request did not reach Groq |
| Nothing was understood in the recording | Groq returned an empty transcript |
| Microphone unavailable or permission denied | Grant the microphone permission, or another app holds it |
| The original session is gone; nothing was inserted | The terminal you dictated into was closed |
| Transformation failed; the raw transcript was inserted | Transcription worked, the mode did not |

Messages name a category and never quote what you said, the request, or the key.
