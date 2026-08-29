# Wireless Call Receiver — Google Play and GitHub Pages Links

Canonical production base URL: `https://smfarukhasan.github.io/wireless_call_receiver/`

Android application ID: `com.khubsoja.wirelesscallreceiver`

## Public URLs

| Purpose | URL | Where to use it |
|---|---|---|
| App website | https://smfarukhasan.github.io/wireless_call_receiver/ | Google Play Store listing website field |
| Privacy Policy | https://smfarukhasan.github.io/wireless_call_receiver/privacy.html | Google Play Console > Policy and programs > App content > Privacy policy |
| Terms and Conditions | https://smfarukhasan.github.io/wireless_call_receiver/terms.html | Store listing, in-app policy reference, and support responses |
| Accessibility Statement | https://smfarukhasan.github.io/wireless_call_receiver/accessibility.html | AccessibilityService declaration support material and store listing |
| Support | https://smfarukhasan.github.io/wireless_call_receiver/support.html | Google Play Store listing support website and user support |
| Reviewer Guide | https://smfarukhasan.github.io/wireless_call_receiver/review-guide.html | Play Console review instructions and supplemental declaration material |
| GitHub support issues | https://github.com/smfarukhasan/wireless_call_receiver/issues | Public support mechanism; users must not post sensitive information |

## GitHub Pages activation

1. Push the `landing_page/` folder and `.github/workflows/pages.yml` to the repository's `main` branch.
2. Open GitHub repository **Settings > Pages**.
3. Under **Build and deployment > Source**, select **GitHub Actions**.
4. Open the repository **Actions** tab and confirm the **Deploy landing page to GitHub Pages** workflow succeeds.
5. Open every URL above in a private browser window before entering it in Play Console.

The workflow deploys only `landing_page/`; the folder is intentionally not ignored by Git.

## Play Console declarations and recommended entries

### Privacy policy and Data safety

- Privacy policy URL: use the exact Privacy Policy URL above.
- Developer collection: **No data collected by the developer** based on the current code, because sensitive processing stays on the device and the app has no Internet permission or third-party collection SDK.
- Data sharing: **No data shared with third parties** based on the current code.
- Account creation: **No**.
- Account deletion URL: **Not applicable** while the app does not allow account creation and the developer holds no user data. Do not enter a fake deletion URL.
- Keep the Play Console Data safety answers synchronized with every future SDK, network, logging, backup, or data-handling change.

### AccessibilityService declaration

- Declare the app as an accessibility tool whose primary purpose is to assist blind and visually impaired users.
- Disability served: **Vision**. Add another category only if the app and store listing genuinely serve that group.
- Core feature: Bluetooth hardware control for answering or ending supported calls without locating an on-screen control.
- Describe on-device access accurately: supported call notifications and call-interface text, labels, view identifiers, window structure, configured hardware-key events, and targeted call actions.
- Include both manual hardware-button behavior and the user-enabled deterministic Auto-Answer feature.
- Demonstrate both **Not Now** and **Agree and Continue** paths in the review video.
- Supplemental public page: use the Accessibility Statement URL above.

### Foreground service declaration

- Declared type: `connectedDevice`.
- Feature description: immediate, user-visible Bluetooth hardware monitoring while the Master Service is enabled and a Bluetooth call-control session is active.
- User impact if interrupted: headset button events may not reach the app in time to answer or end the active call.
- Demonstrate enabling the Master Service, the persistent notification, a hardware call action, and stopping monitoring.

### Other App content items

- Ads: **No**, while no advertising SDK or ad content is present.
- App access: **All functionality is available without login or special credentials**. Provide the reviewer steps from the Reviewer Guide.
- Target audience: choose the real intended age groups. The current policies state the app is not specifically directed to children under 13.
- Content rating: complete the questionnaire using actual call-control and accessibility behavior.
- Store listing: prominently describe the app as an accessibility tool for blind and visually impaired users, and disclose supported call apps and compatibility limitations.

## External items that still require the developer

These cannot be generated as working public URLs from the repository alone:

- AccessibilityService declaration video URL: `TODO — upload an unlisted review video and paste its URL here and in Play Console.`
- Foreground service declaration video URL: `TODO — the same video may be used only if it clearly demonstrates the foreground-service flow required by the form.`
- Store support email: `TODO — enter a verified email address controlled by the developer in Play Console. Do not publish an invented address.`

## Official policy references used

- AccessibilityService API: https://support.google.com/googleplay/android-developer/answer/10964491
- Prominent disclosure and consent: https://support.google.com/googleplay/android-developer/answer/11150561
- User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- App review preparation: https://support.google.com/googleplay/android-developer/answer/9859455
- Foreground service declarations: https://support.google.com/googleplay/android-developer/answer/13392821
- GitHub Pages custom workflows: https://docs.github.com/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages
