# Join Messages Feature Design

**Date:** 2026-03-09
**Branch:** `join-messages`
**Approach:** Extend `CustomPlaceholder` system with a new `message` component

---

## Summary

Add a `join-messages.yml` config file that lets server admins define custom player join messages using RoseChat's existing condition and placeholder system. When a player joins, the vanilla join message is suppressed and a custom message — resolved per joining player's rank/permissions via PAPI conditions — is broadcast to all online players through RoseChat's format pipeline.

---

## Config Format (`join-messages.yml`)

Each top-level key is a join message entry ID. Sub-keys mirror `custom-placeholders.yml` structure (`text`, `hover`, `click` all supported). A new `message` sub-key defines the broadcast content.

```yaml
# join-messages.yml

join-message:
  # Optional: inline text component — usable as a custom placeholder in other formats.
  text:
    condition: "%luckperms_in_group%"
    default: "%luckperms_prefix%"

  # The message broadcast to all online players when a player joins.
  # Supports conditions, custom placeholders ({prefix}, {player}, etc.), and PAPI.
  message:
    condition: "%vault_rank%"
    mod:
      - "{prefix}{player} has come to the rescue"
    admin:
      - "The famous {prefix}{player} has joined"
    default:
      - "{prefix}{player} &ejoined the server"
```

- Multiple top-level entries are supported — all fire on join.
- If no `message` key is present in an entry, that entry is skipped during broadcast.
- If no `default` is set and the condition doesn't match, nothing is sent (consistent with existing behavior).

---

## Architecture

### New Files

| File | Purpose |
|------|---------|
| `manager/JoinMessageManager.java` | Loads `join-messages.yml`, stores entries as `CustomPlaceholder` objects |
| `src/main/resources/join-messages.yml` | Default config shipped with the plugin |

### Modified Files

| File | Change |
|------|--------|
| `listener/PlayerListener.java` | New `onPlayerJoin` handler at `NORMAL` priority for join message broadcast |
| `RoseChat.java` | Register `JoinMessageManager` with the plugin |

### No Changes Needed

- `CustomPlaceholder.java` — the `message` key is stored in the existing `placeholders` map under `"message"`, retrieved with `get("message").parseToStringList()`. No subclass required.
- `PlaceholderCondition.java` — `parseToStringList()` already handles list-valued conditions (same as `hover`).
- `PlaceholderManager.java` — join messages live in their own manager; no changes needed here.

---

## Data Flow

1. **Server start / reload** → `JoinMessageManager.reload()` reads `join-messages.yml`, builds `Map<String, CustomPlaceholder>`.
2. **Player joins server** → `PlayerListener.onPlayerJoin(PlayerJoinEvent)` fires at `NORMAL` priority.
3. Vanilla join message suppressed: `event.setJoinMessage(null)`.
4. For each entry in `JoinMessageManager`:
   - Skip if no `message` component present.
   - Resolve `message` condition against the joining player → get the template string for their rank.
   - For each online player (viewer): parse template via `RoseChatAPI.parse(joiningPlayer, viewer, template)`.
   - Send parsed result to viewer.

---

## Key Decisions

- **Event priority `NORMAL`** — allows other plugins at `HIGH`/`HIGHEST` to still override if needed.
- **Per-viewer parsing** — each online player receives a version of the message rendered for them (e.g., relational placeholders work correctly).
- **No new data types** — `message` is stored and parsed identically to `hover` (list of strings with conditions). Zero new parsing infrastructure required.
- **Vanilla suppression** — `event.setJoinMessage(null)` instead of empty string, to avoid sending a blank line.
- **Multiple entries** — all entries in `join-messages.yml` fire on join, enabling layered messages (e.g., a base message + a rank-specific announcement).
