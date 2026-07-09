# Vaults 🛡️

Vaults is a **deterministic mobile money PIN manager** for Android. It eliminates the need to memorize or store multiple PINs by securely deriving unique, service-specific PINs from a single master passphrase.

## 🚀 What is Vaults?

In an era of multiple digital services, many users resort to reusing weak PINs or writing them down. Vaults solves this by using high-grade cryptography to compute your PINs on the fly. 

**Zero Storage:** Vaults never saves your PINs. If your phone is stolen or compromised, there is no "vault" of secrets to be cracked.

## ✨ Key Features

- **Deterministic Derivation:** PINs are derived mathematically from your passphrase + salt. The same input always produces the same PIN, but no PINs are stored on the device.
- **Service-Specific PINs:** Generate unique PINs for:
  - **Mobile Money:** M-Pesa, Tigo Pesa, Airtel Money, MTN MoMo, Orange Money, etc.
  - **Digital Wallets:** PayPal, Venmo, Cash App, Chipper Cash, etc.
  - **Banking Apps:** CRDB SimBanking, NMB Mkononi, NBC Kiganjani, Kuda, etc.
  - **Cards:** ATM cards, Visa, and Mastercard.
- **Multi-Layered Security:**
  - **Rust Cryptography:** Core logic built in Rust for memory safety and performance.
  - **2FA (TOTP):** Integrated Time-based One-Time Passwords for vault access.
  - **Biometric Auth:** Optional fingerprint/face unlock for quick access.
  - **SECURE Window:** Prevents screenshots and screen recording of sensitive areas.
- **Cloud Sync & Backup:** Optional encrypted backup to Google Drive with automated periodic sync.
- **Modern UI:** Built with Jetpack Compose, featuring Material 3 design and customizable themes.

## 🛠️ Tech Stack

- **Android:** Kotlin, Jetpack Compose, Material 3, Navigation, WorkManager.
- **Core (Rust):** PBKDF2 (600,000 iterations), HKDF, SHA-256, HMAC, TOTP.
- **Integration:** [UniFFI](https://github.com/mozilla/uniffi-rs) for seamless communication between Kotlin and Rust.
- **Security:** Android EncryptedSharedPreferences and Keystore for hardware-backed key protection.

## 🏗️ Project Structure

The project is split into two main components:

- **`app/`**: The Android application layer, containing the UI, storage management, and background workers.
- **`core/`**: The Rust-based cryptographic engine.
  - `vaults-core`: The library containing derivation logic and UniFFI bindings.
  - `vaults-cli`: A command-line interface for interacting with the vault on desktops.

## 🚦 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer).
- Rust toolchain installed (for building the core).
- Android NDK configured.

### Building
1. Clone the repository.
2. Build the Rust core:
   ```bash
   cd core
   # Use the provided scripts or cargo to build for specific targets
   ```
3. Sync the project with Gradle in Android Studio.
4. Run the `app` module on your device or emulator.

---

*Vaults: Security through derivation, not storage.*
