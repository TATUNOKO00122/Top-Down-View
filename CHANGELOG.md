# Changelog

## [1.2.0] - 2025-02-25

### Fixed
- **Ambient Sound Fix**: Fixed ambient sounds (biome sounds, cave sounds, etc.) not being audible in top-down view mode
  - The sound listener position is now correctly set to the player position instead of the camera position
  - This prevents sounds from being culled due to excessive distance from the camera

### Technical Changes
- Added `SoundSystemMixin.java`: Redirects camera position, look vector, and up vector in `SoundEngine.updateSource()` and `Listener.getListenerPosition()` in `SoundEngine.play()`
- Added `ClientLevelSoundMixin.java`: Redirects camera position in `ClientLevel.playSound()`

---

## [1.1.0] - 2025-02-25

### Added
- **Internationalization Support**: Added translation keys for config screen
  - Japanese (`ja_jp.json`) and English (`en_us.json`) language files
  - Config screen title and slider labels are now translatable

### Changed
- **State Management Refactoring**: Unified state management into `CameraState` (Single Source of Truth)
- **Code Cleanup**: 
  - Removed backward compatibility methods from `MouseRaycast`
  - Moved reflection initialization to static initializer blocks
  - Consolidated constants into `MathConstants`
  - Package renamed from `com.example.examplemod` to `com.topdownview`

---

## [1.0.0] - Initial Release

### Features
- Top-down camera view mode (bird's eye view)
- Mouse-controlled camera rotation
- WASD movement mapped to camera orientation
- Block culling optimization for distant blocks
- Configuration GUI accessible via Mod Menu or keybind
- Configurable culling range and height threshold
