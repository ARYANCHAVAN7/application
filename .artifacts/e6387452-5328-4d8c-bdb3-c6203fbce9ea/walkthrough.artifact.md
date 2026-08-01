# Walkthrough - Fixing Windows File Locking Errors

I have updated your project configuration to prevent Gradle from locking files in the `build` directory, which was causing your "Access Denied" errors on Windows.

## Changes Made

### Configuration
I modified [gradle.properties](file:///C:/Users/APURVA%20RAKSHAK/OneDrive/ドキュメント/GitHub/application/gradle.properties) with the following settings:
- **`org.gradle.vfs.watch=false`**: Disables the Virtual File System watcher, which is the most common cause of locked files on Windows.
- **`org.gradle.configuration-cache=false`**: Temporarily disabled the configuration cache to ensure a clean state for the next build.

## How to Fix the Current Error

Because Android Studio is currently holding onto the files, I cannot "clean" the project from my side. To see the changes on your phone, you **MUST** follow these steps:

1.  **Close Android Studio** completely.
2.  **Restart Android Studio**.
3.  Go to the top menu: **Build > Clean Project**.
4.  Then: **Build > Rebuild Project**.
5.  Click the green **Run** button to launch on your Vivo phone.

Restarting the IDE is the only way to release the "lock" that Windows has placed on your `build` folder. Once you do this, the "Access Denied" error will disappear, and you'll be able to see the new light theme!
