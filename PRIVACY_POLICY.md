# Privacy Policy

**AetherNet Browser — version 0.0.1**
Last updated: 18 September 2026

> **Draft.** AetherNet Browser is an unreleased proof of concept. This document describes what the
> current build does; it has not been reviewed by a lawyer and must be replaced before any public
> release.

## What stays on your device

Your browsing stays on your phone. Tabs, history, bookmarks, downloads, site permissions, user
scripts and settings are stored locally and are never uploaded by the app. There is no analytics SDK,
no crash reporting service and no advertising SDK in the build.

Clearing this data in the app, or uninstalling it, removes it.

## What leaves your device

**Websites you visit.** Normal browsing traffic goes to the sites you open, exactly as any browser.
Those sites receive your IP address and whatever they log themselves.

**Your search engine.** What you type in the address bar goes to the search engine you picked during
setup, and search suggestions are requested from it as you type. You can change or disable this in
Settings.

**Site icons.** Icons for sites on the home page and in lists are fetched from the site itself, and
from DuckDuckGo's icon service when the site has none.

**Filter lists.** The content blocker and website blocker download their lists from their publishers.

**Update checks.** The app asks GitHub whether a newer build exists. This sends your IP address to
GitHub and nothing else.

## The VPN

The built-in VPN is optional and off until you turn it on. When you use it:

- The app signs in to the VPN service as a guest. Doing so sends your device's Android ID, an
  attestation token from Google Play, and basic device information (model, Android version, app
  version) to the VPN service, which uses them to check the app is genuine and to hold your session.
- While connected, this app's browsing travels through the VPN server you chose, so websites see that
  server's IP address rather than yours. The VPN operator can see the connection itself.
- **Only AetherNet's own traffic** goes through the tunnel. Other apps on your phone keep their normal
  connection.
- Turning the VPN off ends the session.

The VPN service in this build is operated by a third party under a separate agreement, and its own
handling of connection data is governed by that operator.

## Children

This app is not directed at children.

## Contact

Questions about this build: raise an issue on the project's GitHub repository.
