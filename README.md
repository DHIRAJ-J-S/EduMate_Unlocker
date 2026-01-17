# EduMate Unlocker

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)
![Version](https://img.shields.io/badge/Version-1.0-orange)

[![Download APK](https://img.shields.io/badge/Download-Latest%20APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/DHIRAJ-J-S/EduMate_Unlocker/releases/latest/download/EduMate_Unlocker.apk)

A native Android wrapper that makes the Sairam EduMate student portal usable on mobile devices.

---

## 🤔 The Problem

### New Student Portal

The new EduMate student portal (used by SIT and SEC colleges) completely blocks mobile access. When you visit the site on a phone, you see:

> *"This website is optimized for access on desktop and laptop computers only. Mobile access is currently not supported."*

No login page, no functionality — just a block screen.

### Old EduMate Portal

The old EduMate site shows both "Student" and "Staff" login options. Staff login works normally, but when you click "Student", instead of showing username and password fields, it displays a button that redirects you to the new EduMate site — which then blocks you anyway.

---

## 💡 The Solution

This app is built with Kotlin and uses Android WebView to load the EduMate portal. It applies JavaScript and CSS injections to bypass restrictions and fix the login issues.

### For the New Site

| Technique | What it does |
|-----------|--------------|
| **Desktop UA Spoofing** | Sends a Chrome/Windows user-agent so the site thinks you're on a desktop |
| **JavaScript Injection** | Overrides browser properties (`screen.width`, `navigator.platform`, `matchMedia`) to pass all desktop detection checks |
| **CSS Injection** | Widens login input fields for easier mobile use |

### For the Old Site (Legacy Mode)

| Technique | What it does |
|-----------|--------------|
| **Redirect Bypass** | Directly loads the old EduMate URL |
| **Login Fix** | Injects JavaScript that changes the "Staff" radio button value to "Student", enabling student login with username/password fields on the old portal |

---

## ✨ Features

- 🏫 **Dual College Support** — Switch between SIT and SEC portals
- 📜 **Legacy Mode** — Access the old EduMate portal with working student login
- 📥 **File Downloads** — Download PDFs, images, and documents with Open/Share options
- 📤 **File Uploads** — Native file picker for uploading assignments
- 🧹 **Clear Cache** — One-tap cache clearing for login issues

---

## 🐛 Known Bugs

| Issue | Workaround |
|-------|------------|
| Some documents cannot be downloaded or previewed in the new EduMate | Enable **Legacy Mode** (Old EduMate) from the settings gear icon |

---

## 📱 Screenshots

<!-- Add your screenshots here -->
<!-- ![Home](screenshots/home.png) -->
<!-- ![Settings](screenshots/settings.png) -->

---

## 🚀 Installation

### Download & Install

1. **Download the APK**
   
   [![Download Latest](https://img.shields.io/badge/Download-Latest%20Release-blue?style=for-the-badge&logo=github)](https://github.com/DHIRAJ-J-S/EduMate_Unlocker/releases/latest)
   
   Or directly download: [EduMate_Unlocker.apk](https://github.com/DHIRAJ-J-S/EduMate_Unlocker/releases/latest/download/EduMate_Unlocker.apk)

2. **Enable Unknown Sources** (if prompted)
   - Go to Settings → Security → Enable "Install from unknown sources"
   - Or just tap "Settings" when Android prompts you

3. **Install the APK**
   - Open the downloaded file
   - Tap "Install"

### Tips

- 📱 **Landscape mode** works best for the full desktop experience
- ⚙️ Tap the **gear icon** to switch colleges, enable legacy mode, or clear cache

---

## 🏗️ Building from Source

```bash
# Clone the repo
git clone https://github.com/DHIRAJ-J-S/EduMate_Unlocker.git

# Open in Android Studio
# Build > Build Bundle(s) / APK(s) > Build APK(s)
```

---

## ⚠️ Disclaimer

This app is an unofficial wrapper and is not affiliated with Sairam Institutions. It simply provides a better way to access the existing student portal on Android devices. Your login credentials are sent directly to the official EduMate servers — this app does not collect or store any personal data.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <i>Cooked up with ☕</i>
</p>
