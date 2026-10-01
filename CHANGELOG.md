# TE Update v1.6

This update introduces a new Traffic Engine interface, restores missing curb blocks, removes the DragonLib dependency and improves rendering, performance and server-side behavior.

---

# 🆕 Added:

### 🛣️ Restored Curb Blocks

- **Asphalt Curb & Concrete Curb:** Restored full curb blocks alongside their existing slope variants.

### 🎨 New Traffic Engine Interface

- Replaced DragonLib-based menus with Traffic Engine’s own interface.
- Redesigned the **Paint Brush**, **Traffic Sign Workbench**, **Traffic Light Configuration**, scheduling and display menus.
- Added clearer selection indicators, improved tooltips and a modern color picker with **HEX input** and color presets.

---

### 🚥 Traffic Light Improvements

- Improved selected lamp-color indicators.
- Switching from **2 lamps to 3 lamps** now selects **Red, Yellow and Green**.
- Traffic-light ID input now accepts **1–100**.
- Improved controller list spacing, scrolling and color-coded timing labels.
- Automatic red-light durations now display whole seconds without unnecessary decimal places.
- Automatic red-light durations appear as calculated text; manual durations remain editable fields.
- Corrected label alignment and preview-panel borders.

### 🖌️ Paint Brush & Sign Workbench

- Made the Paint Brush menu more compact and improved the pattern-selection layout.
- Removed paint-percentage labels from the menu and item tooltip.
- Removed the pattern counter from the selection menu.
- Corrected pattern names and category assignments.
- Replaced clipped workbench labels with clear icons and descriptive tooltips.
- Added a **green checkmark** for saving and a **red cross** for cancelling.
- Improved preview positioning and color-selection controls.
- Fixed switching back to built-in signs after selecting a custom catalogue entry.
- Restored missing workbench panels and inventory-slot frames on **1.21.1**.

### 🛠️ Rendering & Gameplay Fixes

- Fixed invisible paint colors on the Paint Brush in **1.21.1**.
- Fixed blurred menus and previews, including the dark strip behind the Paint Brush menu.
- Pedestrian, bicycle and arrow lamps now use normal lighting for their backgrounds while keeping their symbols illuminated.
- Painted asphalt slopes now retain the same surface texture as their full-block counterparts.
- Matched LED and traffic-information display rear textures to the traffic sign post.
- New LED and traffic-information displays now default to **white**.
- Improved server-side Paint Bucket updates and network error handling.

### 🧹 Cleanup & Organization

- **DragonLib is no longer required.**
- Removed obsolete interfaces and unused assets.
- Removed the **Color Palette** and its workbench slot.
- Removed **Salt** and the **Bitumen Block**; **Road Salt** remains available.
- Removed broken and unwanted sign variants, including **PARE**, **ALTO**, the Japanese stop sign and text-based yield variants.
- Moved **STOP** and **DUR** alongside the yield signs.
- Reorganized the creative inventory, grouping related tools, curbs, slopes, lamps and displays.

### ⚡ Performance & Compatibility

- Optimized LED and traffic-information display color pickers to reduce menu rendering overhead.
- Simplified and cached scissor-barrier shapes to reduce excessive break particles and related FPS drops.
- Applied the updates across **1.20.1 Forge/Fabric** and **1.21.1 NeoForge/Fabric**.
- Preserved existing saved settings and data formats during the interface and dependency migration.
