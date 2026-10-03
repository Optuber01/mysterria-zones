# myzones Enhanced Zone System

## Overview
The myzones plugin has been enhanced to support multiple protected zones with modern Adventure API components, YAML persistence, and advanced zone management features.

## New Features

### 1. Multiple Protected Zones
- Create and manage multiple named protected zones
- Each zone can have different settings and priorities
- Zones are saved to and loaded from `zones.yml` file

### 2. Modern Adventure API Integration
- Beautiful title displays when entering/exiting zones
- Configurable entry and exit messages with color codes
- Sound effects for zone transitions
- Component-based text rendering for better performance

### 3. Zone Management Commands
New `/zone` command with comprehensive subcommands and tab completion:

#### Basic Zone Operations
- `/zone pos1` - Set first position for zone creation
- `/zone pos2` - Set second position for zone creation
- `/zone create <name>` - Create a new zone with the selected area
- `/zone delete <name>` - Delete an existing zone
- `/zone list` - List all zones with their status
- `/zone info <name>` - Display detailed zone information

#### Zone Configuration
- `/zone setenter <name> <message>` - Set custom entry message (supports color codes with &)
- `/zone setexit <name> <message>` - Set custom exit message (supports color codes with &)
- `/zone setdisplay <name> <display_name>` - Set custom display name for zone title (supports color codes with &)
- `/zone toggle <name>` - Enable/disable protection for a zone
- `/zone priority <name> <number>` - Set zone priority (higher = more important)

#### Zone Visualization
- `/zone visualize <name>` - Show zone boundaries with particles

### 4. Zone Priority System
- Higher priority zones take precedence when areas overlap
- Useful for creating nested protection areas
- Priority determines which zone's settings apply in overlapping areas

### 5. YAML Configuration
Zones are automatically saved to `zones.yml` with the following structure:
```yaml
zones:
  example_zone:
    name: "example_zone"
    displayName: "&b&lSafe Haven"
    world: "world"
    minX: 100
    minY: 64
    minZ: 100
    maxX: 200
    maxY: 100
    maxZ: 200
    enterMessage: "&aWelcome to &eExample Zone&a!"
    exitMessage: "&cYou left &eExample Zone&c!"
    protection: true
    priority: 1
```

### 6. Zone Entry/Exit Detection
- Automatic detection when players enter or leave zones
- Displays title with customizable display name and entry/exit messages
- Plays appropriate sound effects
- Handles zone transitions smoothly

## Permissions
- `myzones.zone` - Access to zone commands
- `myzones.bypass` - Bypass zone protection (inherited from original system)

## Zone Protection Features
All original protection features work with the new zone system:
- Block breaking/placing (with automatic restoration)
- PvP protection
- Explosion protection
- Block physics/growth prevention
- Entity protection (armor stands, etc.)
- Interaction blocking (except lecterns and crafting tables)

## Legacy Command Support
The old `/myzones` and `/gr` commands are now aliases that redirect to the new zone system:
- `/myzones pos1` - Set first position (same as `/zone pos1`)
- `/myzones pos2` - Set second position (same as `/zone pos2`)
- `/myzones define [name]` - Create a zone (default name: "main")
- `/myzones clear` - Delete the "main" zone

## Usage Examples

### Creating Your First Zone
1. Stand at one corner: `/zone pos1`
2. Stand at opposite corner: `/zone pos2`
3. Create the zone: `/zone create spawn`
4. Set a fancy display name: `/zone setdisplay spawn &b&l✦ Safe Haven ✦`
5. Customize entry message: `/zone setenter spawn &a&lWelcome to the safe zone!`
6. Customize exit message: `/zone setexit spawn &c&lLeaving safe zone - be careful!`

### Managing Multiple Zones
1. Create a PvP zone: `/zone create pvp_arena`
2. Set higher priority: `/zone priority pvp_arena 5`
3. Disable protection for PvP: `/zone toggle pvp_arena`
4. Create a safe zone inside: `/zone create safe_room`
5. Keep protection enabled with higher priority: `/zone priority safe_room 10`

### Administrative Tasks
- List all zones: `/zone list`
- Check zone details: `/zone info spawn`
- Visualize boundaries: `/zone visualize spawn`
- Remove unused zone: `/zone delete old_zone`

## Technical Notes
- Zone tracking runs every second (20 ticks) for smooth detection
- Players are automatically removed from tracking when they quit
- Zone data is saved on plugin disable and zone updates
- All messages support Minecraft color codes using `&` prefix
- Particle visualization uses lime-colored dust particles at zone edges