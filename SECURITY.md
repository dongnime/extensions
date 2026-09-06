# Security Policy

The Dongnime project takes the security of our extension modules, build pipelines, and users seriously.

---

## Supported Versions

Only the latest commit on the `master` branch and extensions distributed through the official `repo` index are currently supported for security updates.

| Version / Branch | Supported | Notes |
| :--- | :---: | :--- |
| `master` (Latest) | ✅ | Active development & current fixes |
| `repo` (Published APKs) | ✅ | Latest built extension artifacts |
| Older releases / tags | ❌ | Users should update via the in-app extension manager |

---

## Reporting a Vulnerability

If you discover a security vulnerability, **please do NOT report it via a public GitHub issue**.

### How to Report

Please report vulnerabilities privately through:
- **[GitHub Private Vulnerability Reporting](../../security/advisories/new)** on the Security tab of this repository.

### What to Include

To help us triage and resolve the issue quickly, please include:
1. **Description**: A clear description of the vulnerability and its potential impact.
2. **Proof of Concept**: Step-by-step instructions or minimal reproduction steps.
3. **Suggested Fix**: Remediation suggestions or patches if available.

### Scope

- **In-Scope**:
  - Remote code execution (RCE) or arbitrary file manipulation in build scripts or CI/CD pipelines.
  - Insecure handling or exposure of signing keys, tokens, or credentials.
  - Unsafe deserialization or memory safety issues in native or helper libraries.
- **Out-of-Scope**:
  - Downtime or stream playback failures caused by third-party hosting servers (please open a regular [Bug Report](../../issues/new/choose) instead).
  - Normal HTML layout changes by target websites.
  - Denial of Service (DoS) attacks on third-party web services.

---

## Response & Disclosure Process

- **Acknowledgement**: We aim to acknowledge receipt of valid reports within **24 to 48 hours**.
- **Assessment**: We will investigate and coordinate a patch in a private security advisory.
- **Resolution & Release**: Once resolved, we will publish the fix and credit the reporter (if desired) via release notes.

---

## Kebijakan Keamanan (Bahasa Indonesia)

Jika Anda menemukan kerentanan atau celah keamanan pada repositori Dongnime:
1. Mohon untuk **tidak** melaporkannya di issue publik.
2. Laporkan secara privat melalui tab **[Security Advisories](../../security/advisories/new)**.
3. Sertakan deskripsi masalah, langkah reproduksi (*Proof of Concept*), dan estimasi dampaknya.
4. Tim kami akan meninjau dan merespons laporan dalam kurun waktu **24–48 jam**.
