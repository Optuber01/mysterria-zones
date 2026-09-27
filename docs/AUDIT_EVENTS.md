# MysterriaZones audit events

MysterriaZones emits best-effort, staff-restricted events through its shaded neutral audit client after the authoritative zone map and YAML operation succeeds. Audit failures do not change zone commands or persistence.

The optional per-server audit engine owns SQLite and local staff searches. Each producer writes to its own bounded spool directory even when the engine is absent. Existing gameplay dependencies remain separate from audit transport.

## Event catalog

| Event type | Authoritative operation | Subject/target |
| --- | --- | --- |
| `mysterria-zones.zone.created` | New zone YAML written and registered | Actor is the staff creator |
| `mysterria-zones.zone.updated` | Display name, enter message or exit message persisted (`/zone setdisplay`, `setenter`, `setexit`) | Actor is the staff editor |
| `mysterria-zones.zone.deleted` | Zone YAML removed and zone unregistered | Actor is the staff deleter |
| `mysterria-zones.zone.banished` | Banishment persisted in the zone YAML (`COMMITTED`), or a banish request for an already-banished player (`DENIED`, reason `already_banished`) | Target is the banished player |
| `mysterria-zones.zone.unbanished` | Unbanishment persisted in the zone YAML | Target is the unbanished player |
| `mysterria-zones.zone.config.updated` | Protection toggle or priority persisted (`/zone toggle`, `priority`) | Actor is the staff editor |
| `mysterria-zones.zone.bypass_used` | `myzones.bypass` skipped a protection check in a protected zone (`OBSERVED`, risk `HIGH`) | Actor is the bypassing player |

The stable audit `businessId` is the zone name. Each emission receives a new
correlation UUID because these commands are single-step operations. `actorId`
is the staff player UUID and banish events also set `targetId` to the affected
player UUID. All successful mutations use `COMMITTED` and are emitted only
after the zone YAML write succeeds. When the YAML write (or, for
`zone.deleted`, the file delete) fails for a staff create, update, config
update, delete, banish or unbanish, the same event type is emitted as `FAILED`
with audit reason and `reason` metadata `persist_failed`; the zone map is left
unchanged. The only rejected operation that is emitted is a banish of an
already-banished player (`DENIED`, audit reason and `reason` metadata
`already_banished`).

Risk is `NORMAL` except for `zone.bypass_used`, which is `HIGH`. Privacy is
`STAFF_RESTRICTED`, except for `zone.updated` rows for `enter_message` and
`exit_message`: those carry staff-typed message text in `previous`/`value`
and are classified `GAMEPLAY_INPUT`.

`zone.bypass_used` is emitted when a player with `myzones.bypass` performs an
action that protection would otherwise have blocked or reverted in a
protected zone. `action` is one of `block_break`, `block_place`,
`block_interact` or `ability_use` (the last only when CircleOfImagination is
loaded). Rows are rate-limited in memory to one per player per zone per five
minutes, and the limit resets on restart. The rate-limit map holds at most
1024 player/zone pairs; the oldest pair is evicted first.

## Metadata

Every event includes bounded zone context: `zone`, `world`, `min_x`, `min_y`,
`min_z`, `max_x`, `max_y`, `max_z`, `protection`, and `priority`.
`zone.updated` and `zone.config.updated` also include `field`, `previous` and
`value` when available. `zone.created` adds the selected positions: pos1 as
`world`, `x`, `y`, `z` and pos2 as `pos2_world`, `pos2_x`, `pos2_y`, `pos2_z`.
`zone.bypass_used` adds `action` and the position of
the protected block or player as `world`, `x`, `y`, `z`. `zone.deleted`
snapshots the removed zone: its bounds come from the standard zone context,
plus `banished_count`, `banished_players` (comma-separated sorted UUIDs, cut at
a whole-UUID boundary within 1024 characters), and
`banished_players_truncated`.
Metadata is capped to 32 keys and 256 characters per textual value. The one
exception is `banished_players`, which is capped at 1024. No chat content or
player names are recorded.

## Overlap policy

Zone YAML remains required live configuration. Routine successful save output is debug-level; domain change events remain actor-aware. Failure diagnostics are retained.
