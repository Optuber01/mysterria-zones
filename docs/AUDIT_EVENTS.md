# MysterriaZones audit events

Rows go through the shaded audit client to this server's `mysterria-audit-spool` directory; the optional audit engine ingests them. `businessId` is the zone name, `actorId` the staff (or bypassing) player, `targetId` the affected player for banish events. Success rows are emitted only after the zone YAML write succeeds; a failed write or delete emits the same type as `FAILED` with reason `persist_failed`. Risk `NORMAL` and privacy `STAFF_RESTRICTED` unless noted.

| Event type | Outcome(s) | Key facts |
| --- | --- | --- |
| `mysterria-zones.zone.created` | `COMMITTED`, `FAILED` | pos1 as `world`/`x`/`y`/`z`, pos2 as `pos2_world`/`pos2_x`/`pos2_y`/`pos2_z` |
| `mysterria-zones.zone.updated` | `COMMITTED`, `FAILED` | `/zone setdisplay`, `setenter`, `setexit`; `field`, `previous`, `value`; enter/exit message rows are `GAMEPLAY_INPUT` |
| `mysterria-zones.zone.config.updated` | `COMMITTED`, `FAILED` | `/zone toggle`, `priority`; `field`, `previous`, `value` |
| `mysterria-zones.zone.deleted` | `COMMITTED`, `FAILED` | `banished_count`, `banished_players` (sorted UUIDs, cut at a whole UUID within 1024 chars), `banished_players_truncated` |
| `mysterria-zones.zone.banished` | `COMMITTED`, `DENIED`, `FAILED` | `DENIED` with reason `already_banished` |
| `mysterria-zones.zone.unbanished` | `COMMITTED`, `FAILED` | target is the unbanished player |
| `mysterria-zones.zone.bypass_used` | `OBSERVED` (risk `HIGH`) | `myzones.bypass` skipped protection; `action` (`block_break`, `block_place`, `block_interact`, `ability_use`), `world`/`x`/`y`/`z` of the block (or the player for abilities); the zone is the protection zone the 1-second tracking task last placed the player in, so no row is written when the player stands outside one; one row per player per zone per 5 minutes, at most 1024 tracked pairs |
| `mysterria-zones.zone.banish_ejected` | `OBSERVED` | a banished player was teleported out of the zone; actor and target are the ejected player; `trigger` (`tracking` or `banish_command`), `world`/`x`/`y`/`z` where they stood, `to_world`/`to_x`/`to_y`/`to_z` where they were sent; tracking rows are limited to one per player per zone per minute |

Every row carries `zone`, `world`, `min_x`..`max_z`, `protection` and `priority`. Metadata is capped at 32 keys and 256 characters per text value (`banished_players`: 1024).

Main thread: no lookup is added for logging. The bypass permission is checked before the zone lookup, as without auditing, so bypass rows take the zone from the tracking task instead of looking it up; the block or player position is read only when a row is written. Ejection rows check their rate limit before reading the player's position. Dropped: bypass rows for protected blocks the player is not standing in (the block's own zone was a lookup only for the row).
