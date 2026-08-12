# Remove Amethyst X account data

Amethyst X uses two types of accounts:

- Offline accounts, which exist only on your device
- Microsoft accounts, which sign in to Mojang

## Removing an account from the launcher

1. On the home screen, tap the account shown at the top to open **Who's playing?**
2. **Long press** the account you want to remove.
3. Confirm when asked.

All data the launcher stored for that account is removed immediately. Removal is behind a long
press and a confirmation on purpose, because it cannot be undone.

## What is stored, and where

Account data is kept on your device only, under the launcher's own storage. It is not shared with
any third party, with the obvious exception of Microsoft and Mojang when you sign in to a Microsoft
account, which is what makes it possible to join servers and wear a skin.

## Removing your data from Microsoft

Removing an account here removes it from this launcher. It does not touch anything Microsoft holds.
To close a Microsoft account entirely, go to <https://aka.ms/CloseAccount>.

## Removing everything

Uninstalling Amethyst X removes every account it stored. Worlds, versions and runtimes live in
`Android/data/org.angelauramc.amethyst/files` and can be cleared from Android's own app storage
settings, or deleted with the app.
