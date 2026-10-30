# BillWise

Offline Android invoice generator for freelancers and small sellers.

## Features

- Business profile with company logo and company signature (drawn or uploaded)
- Saved clients and items, automatic totals
- GST on/off toggle with breakup (invoice format only — no GST return filing)
- Premium tabular PDF invoices with QR code, remarks, and signature
- Paid/Unpaid tracking, duplicate, share via WhatsApp, save to Downloads
- Fully offline: no account, no server, all data stays on the device

## Tech

- Kotlin, Room, PdfDocument, ZXing (QR codes)
- JDK 17, Gradle 8.7, Android SDK 35

## Build

```bash
gradle assembleRelease
```

Notes:

- The QR code library (`com.google.zxing:core:3.5.3`) comes from Maven
  Central — no vendored repository is needed.
- Launcher icon PNG files are intentionally not included (binary files).
  Before building, add your own `ic_launcher.png` and
  `ic_launcher_round.png` files under `app/src/main/res/mipmap-*/`.
- For a signed release build, create your own `keystore.properties` next
  to this README with `storeFile`, `storePassword`, `keyAlias`, and
  `keyPassword`. Never commit signing files or passwords.

## Privacy

The app requests no internet permission. Invoices, clients, and settings
never leave the device.
