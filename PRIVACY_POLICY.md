# Privacy Policy for BT Receive Call

**Effective Date:** August 9, 2026  
**Last Updated:** August 9, 2026

## 1. Introduction
"BT Receive Call" is an offline accessibility utility application designed specifically to assist visually impaired individuals in receiving incoming standard phone calls and social media calls (WhatsApp, Facebook Messenger, Telegram, Viber, etc.) using hardware buttons on connected Bluetooth devices (neckbands, headphones, earphones).

We are deeply committed to protecting user privacy and ensuring full compliance with Google Play Store Developer Policies, including the **Google Play Accessibility API Policy** and **Bluetooth Connection Guidelines**.

---

## 2. Zero Data Collection & Storage
- **No Personal Data Collection:** BT Receive Call does **NOT** collect, store, record, transmit, or share any personal information, audio recordings, contact details, call logs, text contents, or device identifiers.
- **100% Offline Operation:** The application operates completely offline and contains **ZERO** internet permissions (`android.permission.INTERNET` is neither requested nor included in the application manifest).

---

## 3. Use of Android Accessibility Service
BT Receive Call utilizes Android's `AccessibilityService` API strictly for the following limited accessibility purpose:
- **Purpose:** To detect when an incoming call window (ringing screen) is actively displayed by standard phone dialers or supported social media calling apps (WhatsApp, Messenger, Telegram), and to simulate a single tap/click on the "Answer" or "Accept" call button when the user presses a hardware key on their connected Bluetooth headset.
- **Data Protection Guarantee:**
  - The Accessibility Service reads window events **ONLY** when a whitelisted calling app package is active.
  - The Accessibility Service **NEVER** reads, inspects, or logs keystrokes, messages, screen contents, or notifications outside of active incoming call windows.
  - No accessibility data is ever saved locally or transmitted anywhere.

---

## 4. Bluetooth & Background Execution Compliance
- **Bluetooth Connection:** The application uses Bluetooth permissions (`BLUETOOTH_CONNECT`) solely to detect whether a Bluetooth audio device is connected.
- **Zero Battery Drain & Foreground Restrictions:** Background monitoring services execute **ONLY** when a Bluetooth device is actively connected and the Master Switch is enabled. If Bluetooth is turned off or disconnected, all background services automatically stop.
- **Battery Optimization:** Users may optionally grant battery optimization exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) to prevent Android from prematurely terminating the service while their Bluetooth headset is connected.

---

## 5. Security & Isolation
- The Accessibility Service is protected by the `android.permission.BIND_ACCESSIBILITY_SERVICE` permission, preventing unauthorized third-party apps from accessing or interacting with the service.
- The app contains no third-party SDKs, analytics tracking, advertising frameworks, or external code dependencies.

---

## 6. Children's Privacy
BT Receive Call does not collect any data from anyone, including children under the age of 13.

---

## 7. Changes to This Privacy Policy
We may update this Privacy Policy from time to time. Any changes will be reflected in this document with an updated effective date.

---

## 8. Contact Us
If you have any questions or concerns regarding this Privacy Policy, please contact us via the official developer support channels.
