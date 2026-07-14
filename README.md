# Simple SSH Tunnel

Android app for creating SSH tunnels with port forwarding support.

## Features

- SSH tunnel management with password or private key authentication
- Multiple port forwarding configurations
- Encrypted credential storage using Android Keystore
- Foreground service for persistent connections
- Real-time network statistics

## Requirements

- Android 8.0+ (API 26)
- SSH server access

## Building

```bash
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/`.

## Usage

1. Add a new tunnel with your SSH server details
2. Configure port mappings (local port → remote port)
3. Tap Connect to establish the tunnel
4. Access forwarded services via localhost on your device

## Security

Credentials are encrypted using AES-256-GCM with keys stored in Android Keystore.
