---
sidebar_position: 10
title: "TUI Customization and Theming"
description: "Explains the four TUI customization layers (config, theme, renderer, runtime) - theme lookup order, file-level override semantics of tui.json, the difference between --theme and /theme, and how useAlternateScreen affects the runtime backend."
tags: [concept]
---

# TUI Customization and Theming

The TUI today is not a "black box where you can only change colors", but it is not yet a plugin-based UI platform either.

The most accurate way to understand it:

- The usage layer already has a theme / config mechanism that ships as implemented
- The Java layer still retains renderer / runtime / factory level extension points
- But these extensions are still code-injection-style customization, not a frontend plugin marketplace

---

## 1. Start with the TUI assembly chain

TUI-related assembly currently follows roughly this chain:

```text
CodeCommand -> CodingCliSessionRunner
  -> TuiConfigManager.load(...)
  -> TuiConfigManager.resolveTheme(...)
  -> DefaultCodingCliTuiFactory.create(...)
  -> TuiSessionView
  -> AppendOnlyTuiRuntime 或 AnsiTuiRuntime
```

The most important points here:

- theme, config, renderer, and runtime are assembled in layers
- A single `TUI` object does not do everything itself

This is why you can change each of these separately:

- Configuration
- Theme
- Renderer
- Runtime behavior

---

## 2. Customization points you can use right now

The two layers most directly usable:

- theme
- `tui.json`

### 2.1 Theme override via CLI startup flags

```text
--theme <name>
```

This value comes from `CodeCommandOptionsParser`, and is then read in by `TuiConfigManager.load(overrideTheme)`.

A key detail:

- `--theme` is only an in-memory override for this launch
- It is not automatically persisted to `tui.json`

### 2.2 In-session `/theme`

The `/theme` command currently supports:

```text
/theme
/theme <name>
```

When you run `/theme <name>`, `CodingCliSessionRunner.applyTheme(...)` calls:

- `TuiConfigManager.switchTheme(themeName)`

And this method will:

1. Validate whether the theme exists
2. Read the current configuration
3. Switch `config.theme` to the new theme
4. Save to the workspace `tui.json`

So `/theme` is a persistent switch, not a one-time preview.

---

## 3. The theme lookup order is not arbitrary

The lookup order in `TuiConfigManager.resolveTheme(name)` is currently very clear:

1. `<workspace>/.ai4j/themes/<name>.json`
2. `~/.ai4j/themes/<name>.json`
3. Built-in resource `/io/github/lnyocly/ai4j/tui/themes/<name>.json`
4. If still not found and it is not `default`, fall back to `default`
5. As a final fallback, use the code-generated default theme

So the current theme priority is:

- workspace custom
- home custom
- built-in
- hardcoded fallback

This means a team can perfectly well:

- Put a repo-specific theme in the repository

Without affecting other TUI usage scenarios in the user's home directory.

---

## 4. The config file has two layers, but no field-level merge

The currently supported config file locations are:

- `<workspace>/.ai4j/tui.json`
- `~/.ai4j/tui.json`

Many people instinctively assume:

- The home config provides defaults
- The workspace config overrides only individual fields

But the current `TuiConfigManager.merge(base, override)` is not a field-level merge.

Its behavior is closer to:

- If the workspace config exists, use the workspace config directly
- Otherwise, fall back to the home config

So the current semantics are:

- File-level override

Rather than:

- Field-level cascade merge

This is critical, because it means you cannot assume:

- `showFooter=false` is set in home
- Only `theme=ocean` is written in workspace
- `showFooter=false` will still be preserved in the end

The current implementation does not guarantee this kind of field-level inheritance.

---

## 5. What `tui.json` actually controls today

The core fields of `TuiConfig` currently include:

- `theme`
- `denseMode`
- `showTimestamps`
- `showFooter`
- `maxEvents`
- `useAlternateScreen`

Meanwhile `TuiConfigManager.normalize(config)` guarantees:

- An empty theme falls back to `default`
- When `maxEvents <= 0`, it falls back to `10`

So this is not "pass any value through as-is" either; there are minimal normalization rules.

---

## 6. Built-in themes are just the starting point, not the whole story

The built-in theme names in `TuiConfigManager` currently are:

- `default`
- `amber`
- `ocean`
- `matrix`
- `github-dark`
- `github-light`

But `listThemeNames()` merges three sources:

1. Built-in names
2. Custom themes in the home directory
3. Custom themes in the workspace directory

This is why `/theme` does not necessarily list only built-in themes.

It lists user-defined and repo-defined themes together.

---

## 7. Which fields in a theme file really matter

`TuiTheme` is filled with a large number of defaults by `TuiConfigManager.normalize(theme, fallbackName)`.

The key fields currently include:

- `brand`
- `accent`
- `success`
- `warning`
- `danger`
- `text`
- `muted`
- `panelBorder`
- `panelTitle`
- `badgeForeground`
- `codeBackground`
- `codeBorder`
- `codeText`
- `codeKeyword`
- `codeString`
- `codeComment`
- `codeNumber`

This shows that a theme is more than "primary color + secondary color".

It already covers:

- transcript
- panel
- badge
- code block
- syntax highlight

If you only change one or two fields, the other fields will continue to use the normalized default values.

---

## 8. What `DefaultCodingCliTuiFactory` actually decides

This is currently the development-layer entry point most worth reading directly.

It does four things in `create(...)`:

1. Read `TuiConfig`
2. Resolve `TuiTheme`
3. Construct `TuiSessionView` as the renderer
4. Select the runtime based on the terminal and `useAlternateScreen`

In other words, it does not just "produce a TUI object" — it decides:

- Which configuration to use
- Which theme to use
- Which renderer to use
- Which runtime backend to use

---

## 9. Why it forks between `AppendOnlyTuiRuntime` and `AnsiTuiRuntime`

The current runtime selection rules are:

- If `useAlternateScreen=false` and the terminal is `JlineTerminalIO`
  - Use `AppendOnlyTuiRuntime`
- Otherwise
  - Use `AnsiTuiRuntime`

This means `useAlternateScreen` is not a purely visual preference.

It directly affects the choice of runtime backend.

In engineering terms, you can understand it this way:

### `AppendOnlyTuiRuntime`

Closer to:

- Append-style terminal output
- More friendly to JLINE terminals

### `AnsiTuiRuntime`

Closer to:

- A full screen refresh model with a renderer
- A unified fallback path for alternate screen or non-JLINE terminals

So do not think of `useAlternateScreen` as "just changing how the terminal clears the screen".

---

## 10. What actually gets updated when `/theme` switches

`CodingCliSessionRunner.applyTheme(...)` currently:

1. Switches via `TuiConfigManager.switchTheme(...)`
2. Re-runs `resolveTheme(...)`
3. If it is a `JlineShellTerminalIO`, updates the shell terminal's theme styler
4. If there is currently a TUI renderer, also calls `tuiRenderer.updateTheme(config, theme)`
5. Refreshes the current session's output prompt

This shows that a theme switch does not "take effect the next time the TUI is opened".

It immediately affects:

- The shell transcript style
- The theme used by the renderer
- Subsequent TUI rendering

---

## 11. What each deeper extension point is suited for

### Changing color scheme, brand style, code highlight colors

Prefer to change:

- `TuiTheme`

### Changing display density, timestamps, footer, event count

Prefer to change:

- `TuiConfig`

### Changing layout, status bar structure, message panel rendering

Prefer to change:

- `TuiRenderer`
- The current default implementation is `TuiSessionView`

### Changing screen refresh mode, alternate screen strategy, runtime interaction shell

Prefer to change:

- `TuiRuntime`
- Or the higher-level `CodingCliTuiFactory`

Do not mix changes across these four layers; otherwise it becomes very hard to tell whether a given behavior comes from configuration, theme, renderer, or runtime.

---

## 12. What the current boundary is

The extension boundary of the current TUI can be summarized in one sentence:

- Configurable out of the box, but not yet a plugin ecosystem

That is:

- theme and `tui.json` are already first-class usage-layer capabilities
- `CodingCliTuiFactory`, `TuiRenderer`, and `TuiRuntime` are development-layer extension points
- There is not yet a system where "a user downloads a UI plugin package and can hot-swap it"

So if you want to do deep customization, the current expectation should still be:

- Wire in a custom implementation at the Java layer

---

## 13. The five most common pitfalls

### 13.1 Assuming home and workspace `tui.json` do a field-level merge

They do not; when the workspace file exists it is closer to a full overwrite.

### 13.2 Assuming `--theme` is automatically persisted

It is only a startup-time override; it is not saved.

### 13.3 Assuming `useAlternateScreen` is just a visual switch

It directly changes the runtime backend selection.

### 13.4 Changing only the theme but expecting the layout to change too

Layout belongs to the renderer layer, not the theme layer.

### 13.5 Changing only the renderer while ignoring terminal backend differences

The interaction models of `AppendOnlyTuiRuntime` and `AnsiTuiRuntime` are not identical.

---

## 14. The key takeaways from this page

- TUI customization today is split into four layers: config, theme, renderer, runtime
- Theme lookup order is workspace > home > built-in > default fallback
- `tui.json` today is closer to a file-level override, not a field-level merge
- `--theme` is a one-time override; only `/theme` persists back to the workspace config
- `useAlternateScreen` affects the runtime backend, not just the visual mode

---

## 15. Further reading

1. [CLI / TUI Usage Guide](/docs/coding-agent/cli-and-tui)
2. [Command Reference](/docs/coding-agent/command-reference)
3. [Runtime Architecture](/docs/coding-agent/runtime-architecture)
