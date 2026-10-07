# Security policy

## Supported versions

Security fixes are shipped in the latest stable patch of the current minor release. At present,
that is the 0.2.x series. Update to its newest patch before reporting a problem.

## Reporting a vulnerability

Please report suspected vulnerabilities privately through
[GitHub's vulnerability reporting page](https://github.com/tedtedted/jazzclub/security/advisories/new).
If private reporting is unavailable, email the maintainer at ted@tedredington.com. Include the
version, operating system, affected behavior, reproduction steps and expected impact. Use dummy
credentials and omit real Pandora/Last.fm passwords, session keys, auth tokens and signed audio
URLs. Please wait for coordination with the maintainer before public disclosure.

Reports are reviewed by the project maintainer; there is no guaranteed response time or bounty.
Confirmed fixes are released as patch versions, with advisories when appropriate. Dependency
alerts and automated update PRs help identify vulnerable dependencies, but do not replace reports
about jazzclub's own behavior.

## Local trust boundaries

The config and state directories should be private. `password_command` and `event_command` run
programs with your permissions, and anyone who can write to the control FIFO can control the
player and invoke account settings. Only configure scripts and paths you trust. The README
explains password-manager integration and file permissions.
