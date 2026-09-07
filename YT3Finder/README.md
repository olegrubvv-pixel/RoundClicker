# YT3 Finder

Native Android scanner for three-character YouTube handle candidates.

Modes:
- Basic: all valid 3-character Latin/digit/separator combinations.
- International Pretty: mixed queue of visually patterned handles across writing systems used by YouTube-supported languages.

The app performs a self-test on known occupied handles before scanning, runs scanning in an Android foreground service, saves progress, and never converts a network error into a green result.
