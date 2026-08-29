# Privacy Policy for Wireless Call Receiver

**Effective date:** August 29, 2026
**Last updated:** August 29, 2026

## 1. Scope and developer contact

This Privacy Policy applies to the Wireless Call Receiver Android application. Wireless Call Receiver is an accessibility tool designed primarily to help blind and visually impaired users answer or end supported cellular and internet calls using configured Bluetooth hardware controls.

The project maintainer can be contacted through the [Wireless Call Receiver support page](https://smfarukhasan.github.io/wireless_call_receiver/support.html) or [GitHub Issues](https://github.com/smfarukhasan/wireless_call_receiver/issues).

## 2. Data the app accesses on your device

The app processes only the information needed for features you choose to enable:

- **Bluetooth connection information:** whether a paired Bluetooth audio device is connected and its device name or alias.
- **Call notification information:** the originating package name, notification category, call-action labels, and temporary Android `PendingIntent` action tokens for supported calling apps.
- **Supported call-interface information:** visible text, content descriptions, view identifiers, and window structure used to locate an Answer or End Call control in a supported call interface.
- **Hardware-control events:** configured headset, media, call, or volume key events used to trigger call controls.
- **Local settings:** language, master-service state, screen-state preferences, and auto-answer preferences.

The app does not request or access contacts, call history, SMS, precise or approximate location, microphone recordings, camera content, photos, videos, documents, advertising identifiers, or account credentials.

## 3. How the information is used

The information above is processed on the device to:

- detect whether a supported Bluetooth headset is connected;
- detect a supported incoming or active call;
- invoke a supported app's native Answer or End Call action;
- perform a targeted accessibility click or tap on a supported call control;
- answer a cellular call through Android Telecom when the user grants the Answer Phone Calls permission; and
- run deterministic auto-answer behavior only when the user explicitly enables Auto-Answer.

Accessibility window inspection is restricted to the supported phone and internet-calling packages included by the app. Notification processing is disabled while the app's Master Service is off.

## 4. Collection, transmission, and sharing

The developer does not collect, sell, rent, share, or transmit personal or sensitive user data. Wireless Call Receiver does not declare the Android Internet permission and contains no advertising or analytics SDK.

Call-related information and temporary action tokens are processed in memory and are not sent to developer-controlled servers. Diagnostic logs avoid notification action text and call-interface text.

## 5. Permissions and sensitive APIs

Each capability is requested separately and may be declined:

- **Nearby Bluetooth Devices (`BLUETOOTH_CONNECT`):** detects connection state and displays the connected device name.
- **Notifications (`POST_NOTIFICATIONS`):** displays the foreground monitoring status notification on supported Android versions.
- **Answer Phone Calls (`ANSWER_PHONE_CALLS`):** enables Android Telecom's cellular call-answer fallback. The app does not request Call Log permissions.
- **AccessibilityService:** observes supported call notifications and interfaces, configured hardware keys, and performs narrow answer or end-call actions. If Auto-Answer is enabled, the same deterministic action may occur automatically after a supported incoming call is detected.
- **Notification Listener:** locally inspects supported call-style notifications and their native action controls for phone and supported internet-calling apps.
- **Connected-device foreground service:** keeps user-visible Bluetooth call-control monitoring active only while the feature is enabled.

The app shows a separate in-app disclosure with **Agree and Continue** and **Not Now** choices before opening Accessibility or Notification Access settings. Access can be revoked at any time in Android Settings.

## 6. Local storage, Android backup, retention, and deletion

The app stores preferences locally in Android private app storage. Depending on device and Android backup settings, these preferences may be included in an Android system backup or device-to-device transfer. The developer does not receive or control those backups.

Temporary call information is kept only as long as needed for the current call state and is cleared when the call ends, the service stops, or the process ends. Local preferences remain until they are cleared by the user or the app is uninstalled.

Wireless Call Receiver does not create user accounts and the developer does not hold user data. Users can delete local data through **Android Settings > Apps > Wireless Call Receiver > Storage > Clear data**, or by uninstalling the app.

## 7. Security

Android restricts binding to the Accessibility and Notification Listener services using system permissions. The app minimizes processing to supported calling packages and keeps sensitive processing on the device. No method of software operation is guaranteed to be completely error-free, but the app is designed to minimize data access and retention.

## 8. Children's privacy

Wireless Call Receiver is not specifically directed to children under 13 and does not knowingly collect children's personal data. The app's primary audience is users who need its accessibility call-control functionality.

## 9. Changes to this policy

Material changes will be published on the public policy page with a revised effective or last-updated date. The policy URL will remain publicly accessible.

## 10. Contact

For privacy questions or requests, use the [support page](https://smfarukhasan.github.io/wireless_call_receiver/support.html) or open a private-data-free support request through [GitHub Issues](https://github.com/smfarukhasan/wireless_call_receiver/issues). Do not include phone numbers, notification content, or other sensitive information in a public issue.
