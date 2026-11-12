# Majin effect controller

Register the controller once during mod initialization:

```java
ServerTickEvents.END_SERVER_TICK.register(MajinEffectController.INSTANCE::tick);
```

Commands, keybinds, or a custom dummy entity can drive the state machine:

```java
var fx = MajinEffectController.INSTANCE;
fx.startCharge(player); // hold
fx.cancelCharge(player); // smooth fade
fx.release(player); // blast and terrain pass
```

The controller is server-side and uses vanilla particles. The explosion damage is applied once per second as 30% of each target's current maximum health via `setHealth`, bypassing armor and potion resistance. Terrain is processed in bounded batches to avoid a single-tick stall.
