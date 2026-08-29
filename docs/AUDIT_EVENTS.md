# MysterriaZones audit events

MysterriaZones emits best-effort, staff-restricted events through the optional
COI `MysterriaAudit` service. Events are emitted only after the authoritative
zone map and YAML file operation succeeds. If the service is not installed or
emission fails, zone commands and persistence continue unchanged.

## Event catalog

| Event type | Authoritative operation | Subject/target |
| --- | --- | --- |
| `mysterria-zones.zone.created` | New zone YAML written and registered | Actor is the staff creator |
| `mysterria-zones.zone.updated` | Zone update persisted (reserved for manager callers) | Actor is the staff editor |
| `mysterria-zones.zone.deleted` | Zone YAML removed and zone unregistered | Actor is the staff deleter |
| `mysterria-zones.zone.banished` | Banishment persisted in the zone YAML | Target is the banished player |
| `mysterria-zones.zone.unbanished` | Unbanishment persisted in the zone YAML | Target is the unbanished player |
| `mysterria-zones.zone.config.updated` | Protected/admin zone setting persisted | Actor is the staff editor |

The stable audit `businessId` is the zone name. Each emission receives a new
correlation UUID because these commands are single-step operations. `actorId`
is the staff player UUID and banish events also set `targetId` to the affected
player UUID. All successful mutations use `COMMITTED`; failed or rejected
operations are not emitted.

## Metadata

Every event includes bounded zone context: `zone`, `world`, `min_x`, `min_y`,
`min_z`, `max_x`, `max_y`, `max_z`, `protection`, and `priority`. Configuration
events additionally include `field`, `previous`, and `value` when available.
Metadata is capped to 32 keys and 256 characters per textual value. No chat
content, coordinates beyond the zone bounds, or player names are recorded.
