# JARVIS Android

A starter real Android JARVIS assistant.

## Included
- Futuristic dark JARVIS interface
- Speech recognition
- Text-to-speech replies
- Open YouTube / Chrome / Settings
- Flashlight control
- Volume up/down
- Optional Accessibility Service for deeper phone navigation

## Build
1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Connect an Android phone with USB debugging enabled, or use an emulator.
4. Run the `app` configuration.
5. Grant microphone permission.
6. Tap ENABLE PHONE CONTROL and enable JARVIS under Accessibility.

## Important Android limitation
A normal app cannot silently control every part of Android. Android protects calls, messages, passwords, permissions, banking apps, and other sensitive operations. The Accessibility Service can automate many UI actions only after the user explicitly enables it.

## Extending commands
Add more cases inside `handleCommand()` in `MainActivity.kt`.

For example:
- open WhatsApp
- open calculator
- open camera
- take the user to a settings page
- accessibility click actions

A true always-listening wake word such as "Hey JARVIS" needs an additional wake-word engine/foreground-service design and must follow Android microphone/background restrictions.
