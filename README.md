# Masroof

A personal Android budget tracker. Everything stays on the phone: the app has
no internet permission and no server.

## Current stage: payment capture test

Before building the budget features, this build answers one question: what
does the phone actually show when a payment is made? It logs notifications
from Samsung Wallet, SMS apps, and any app whose message mentions money.

### Try it

1. Download `masroof.apk` from the **Latest build** release and install it.
2. Open Masroof and follow the two setup cards (notification access, battery).
   If Android shows "Restricted setting", open App info for Masroof, tap the
   menu (⋮), choose "Allow restricted settings", then grant access again.
3. Make a few payments: a Samsung Wallet tap, a physical card, an online one.
4. Check that each shows up in the list. If one is missing, turn on
   "Capture every app" and try again.
5. Tap **Export** to save the log as a JSON file.

### Roadmap

1. Capture test (this build)
2. Budgets: periods with a custom start day, categories, manual expenses
3. Automatic expenses from captured notifications, merchant rules, review inbox
4. Alerts and period-to-period analysis
5. Backup export/import

## Build

    ./gradlew testDebugUnitTest assembleDebug

Requires JDK 17+ and the Android SDK (platform 35).
