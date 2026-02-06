# AGENTS.md - Coding Guidelines for TopDownView Mod

Minecraft Forge 1.20.1 mod with Mixin integration for top-down camera view.

## Build Commands

```bash
./gradlew build                    # Build mod JAR
./gradlew runClient                # Run client
./gradlew runServer                # Run server
./gradlew runGameTestServer        # Run GameTest server
./gradlew test                     # Run all tests
./gradlew test --tests "com.example.examplemod.ClassName"      # Single test class
./gradlew test --tests "com.example.examplemod.ClassName.method" # Single test method
./gradlew clean                    # Clean build
./gradlew compileJava              # Compile only
./gradlew processResources         # Process resources
./gradlew genIntellijRuns          # Generate IDEA configs
./gradlew runData                  # Generate assets
```

## Code Style

### Imports
- Order: Java stdlib → Minecraft/Forge → project classes
- No wildcard imports (except static constants)
- No unused imports

### Formatting
- 4 spaces indent, LF line endings
- Max 120 chars per line
- Braces: same line for class/method, new line for control structures
- Always use braces for control structures

### Naming
- Classes: PascalCase (`CameraMixin`, `ModState`)
- Methods/fields: camelCase (`isTopDownView`, `cameraDistance`)
- Constants: UPPER_SNAKE_CASE (`MODID`, `MIN_CAMERA_DISTANCE`)
- Mixin classes: suffix with `Mixin`
- State classes: suffix with `State`

### Types
- Use `var` only when type is obvious
- Prefer primitives over boxed types
- Use `final` for parameters/locals when not reassigned
- Use Minecraft's `Mth` instead of `Math`

### Comments
- Japanese for implementation comments
- Javadoc for public APIs
- Explain "why", not "what"

## Architecture

### Package Structure
```
com.example.examplemod/
├── TopDownViewMod.java          # Main mod class
├── Config.java                   # Forge config
├── state/                        # State management
│   ├── ModState.java             # Facade for all states
│   ├── CameraState.java          # Camera position/rotation
│   ├── TimeState.java            # Time tracking
│   └── ModStatus.java            # Status flags
├── client/                       # Client-side code
│   ├── ClientForgeEvents.java   # State holder (AtomicBoolean/AtomicReference)
│   ├── ClientModBusEvents.java  # Mod bus handlers
│   ├── CameraController.java    # Camera events
│   ├── InputHandler.java        # Key/mouse input
│   ├── MovementController.java  # Movement mapping
│   ├── MouseRaycast.java        # Mouse raycasting
│   └── [other handlers...]
├── mixin/                        # Mixin classes
│   ├── CameraMixin.java          # Camera setup injection
│   ├── GameRendererMixin.java    # Rendering injection
│   ├── MouseHandlerMixin.java    # Mouse input modification
│   └── EntityAccessor.java       # Entity field access
└── api/
    └── MinecraftClientAccessor.java
```

### State Management Pattern
```java
// Utility class with singleton instances
public final class ModState {
    public static final CameraState CAMERA = CameraState.INSTANCE;
    public static final TimeState TIME = TimeState.INSTANCE;
    
    private ModState() { throw new AssertionError(); }
}

// Thread-safe state holder
public final class ClientForgeEvents {
    private static final AtomicBoolean IS_TOP_DOWN_VIEW = new AtomicBoolean(false);
    private static final AtomicReference<Double> CAMERA_DISTANCE = new AtomicReference<>(15.0);
    
    public static boolean isTopDownView() { return IS_TOP_DOWN_VIEW.get(); }
    
    public static void setTopDownView(boolean enabled) {
        boolean oldValue = IS_TOP_DOWN_VIEW.getAndSet(enabled);
        if (oldValue != enabled) { notifyStateChange(...); }
    }
}
```

### Event Handling
```java
@Mod.EventBusSubscriber(modid = TopDownViewMod.MODID, value = Dist.CLIENT)
public final class CameraController {
    private CameraController() { throw new AssertionError("ユーティリティクラス"); }
    
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) { ... }
}
```

### Mixin Pattern
```java
@Mixin(value = Camera.class, priority = 1000)
public abstract class CameraMixin {
    @Shadow public abstract void setPosition(Vec3 pos);
    
    @Inject(method = "setup(...)", at = @At("TAIL"))
    private void onSetup(BlockGetter level, Entity entity, ..., CallbackInfo ci) {
        if (!ClientForgeEvents.isTopDownView()) return;
        // Modify camera
    }
}
```

## Error Handling

- Early returns for guard clauses
- Log with `LogUtils.getLogger()`
- Validate null checks for Minecraft/level
- No empty catch blocks
- Specific exception types

## Testing

- Manual: `./gradlew runClient`
- Test with/without Embeddium
- Verify camera with blocks between player/camera
- Test mouse rotation and movement mapping

## Dependencies

- Minecraft: 1.20.1
- Forge: 47.4.10+
- Mixin: 0.8.5
- Java: 17
- Optional: Embeddium (runtime)

## Agent Instructions

- Always respond in Japanese
- Remove unused code (don't comment out)
- Delete unnecessary files proactively
- Think step-by-step before implementing
- Confirm before changing existing systems
- Run `./gradlew build` after significant changes
- Keep mixins minimal, delegate to regular classes