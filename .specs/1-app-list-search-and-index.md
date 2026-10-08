# 1 — Search and letter index in the app list

## Task

Make it quick to find an app in the settings list: a search field above the list, letter
section headers, and an A–Z rail on the right to jump between sections.

## Why

The list is every launchable app, sorted alphabetically. Finding one meant scrolling
through all of them, with no way to jump ahead.

## How

All in `MainActivity.AppList` (Compose):

- **Search** — an `OutlinedTextField` filters by label, case-insensitively, on every
  keystroke. It matches app names only. Matching package names too gave odd results
  ("ch" matched Google via `googlequicksearchbox`). The list scrolls to the top when the
  query changes, and an empty result shows a short "No apps match" line.
- **Sections** — each `AppEntry` gets a `section`: its first letter with accents removed
  (NFD) and upper-cased, or `#` for digits, symbols and emoji. The list is sorted with a
  locale-aware `Collator`, with `#` last, then grouped by section. Each group starts with a
  `stickyHeader`.
- **Letter rail** — `SectionIndex` lists only the sections that are present, so it shrinks
  to match the search results. Tapping or dragging scrolls to that section's header. Each
  header's list index is worked out from the group sizes. The section at the top of the
  list is highlighted, and the rail is hidden when there is only one section.
- Icons are converted to an `ImageBitmap` once, when apps are loaded, rather than on every
  recomposition, so typing in the search field stays smooth.

**Left out on purpose** — fuzzy matching, pinning marked apps to the top.

**Verified** — on the API 36 emulator: headers and rail render, tapping a rail letter
jumps there, and search filters the list and its sections.
