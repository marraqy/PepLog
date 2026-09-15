# PepLog 0.6.6 — simpler protocol workflows

## Editing scheduled items

- **Save item** and **End item after today** stay in a fixed action bar while the form scrolls. Save is the primary action; ending an item has a distinct color and confirmation.
- The header contains the title and an accessible close button.
- The end-item confirmation explains that future occurrences are removed, history is preserved, and unsaved edits are discarded.
- The protocol editor also keeps its save action in a fixed bar.

## Recording occurrences in Today and Protocols

- **Done as planned** records the amount shown on the button in one tap.
- **Adjust amount / note** expands fields within the agenda item, starting with the planned amount in the selected unit (mg or mcg).
- Adjustments retain amount validation, blend breakdowns, and the draft if saving fails.
- **Skip** uses the same compact confirmation in both agendas, with an optional note.
- Actions are disabled while saving. Completion remains limited to eligible occurrences; future dates cannot be marked done.

The database and backup format remain compatible. This release uses version 0.6.6 and Android version code 15.

## Validation

- 25 JVM tests passed.
- 15 Android tests passed on an Android 15/API 35 emulator using the signed release APK.
- Debug lint: no errors; 17 pre-existing warnings. Release build and vital lint passed.
- Coverage includes planned completion, inline mg/mcg adjustments, invalid amounts, failed-save drafts, fixed editor actions, blends, history, backup, reminders, and date changes.
- No physical-device testing was performed.
