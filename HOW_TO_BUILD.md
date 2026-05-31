# Reply AI - Build Instructions

## What this app does
- Shows a floating ✨ bubble over ANY app (including Twitter/X)
- When you open Twitter, it auto-reads the tweet text using Accessibility Service
- Tap the bubble → select tone + recipient → tap Generate → get 3 human replies instantly
- Tap "Copy Reply" → paste it into Twitter's reply box

## Requirements to build
- PC/Mac/Linux with Android Studio installed
- Java 8+
- Android phone with USB debugging enabled

## Build Steps

### Option A - Android Studio (easiest)
1. Download/unzip this project folder
2. Open Android Studio → "Open an existing project" → select the ReplyAI folder
3. Wait for Gradle sync to finish
4. Click Build → Generate Signed Bundle/APK → APK
5. Install the APK on your phone

### Option B - Command line
```bash
cd ReplyAI
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

### Option C - Build online FREE (no PC needed)
1. Go to https://github.com and create an account
2. Create a new repository, upload all these files
3. Go to https://appetize.io OR use GitHub Actions to build
4. OR: send the folder to someone with Android Studio

## After installing - Setup Steps
1. Open Reply AI app
2. **Step 1**: Enter your Anthropic API key (get free at console.anthropic.com)
3. **Step 2**: Grant "Display over other apps" permission
4. **Step 3**: Enable "Reply AI Screen Reader" in Accessibility Settings
5. Tap "Start Floating Bubble"
6. Open Twitter/X — tap any tweet to go to reply screen
7. Tap the ✨ bubble — tweet text is auto-filled!
8. Pick tone (Casual/Playful/etc) → tap Generate → copy reply → paste in Twitter

## How tweet auto-reading works
The Accessibility Service watches Twitter/X screen and picks up the longest
text block visible (the tweet content). This does NOT log or store any data.
It only reads text that's already visible on your screen.

## Speed
Uses claude-haiku (fastest Claude model) — replies generate in ~1-2 seconds.
