# First-Project

## MPU5 Fabric Deep-Dive — VLAN 17 (WAVES) collection bundle

A one-session, **read-only** sweep of the VLAN 17 fabric across five switches to
settle packet paths, STP/root, ARP, and L2 loops. The collection scripts pair
with a fillable worksheet you transcribe the results into.

Structural reason for the deep dive: the Annex II Roof switch has **two** VLAN-17
uplinks (Fiber `Gi0/1` → ALT_EOC 9300, Cambium `Fa0/5` → BLDG 602), both
`spanning-tree guard none`, and the WaveRelay mesh bridges VLAN 17 a third way —
a real physical ring that RSTP must block. Three switches also tie for VLAN-17
root at priority 24576. This session confirms the ring is stable or finds the
loop / flapping root / dirty trunk.

### Contents

| File | Purpose |
| --- | --- |
| `MPU5_Fabric_Deepdive_Worksheet_Fillable.pdf` | Fillable worksheet (3 pages, 78 fields) — transcribe results here |
| `MPU5_Fabric_Scripts/README_fabric.txt` | Run instructions, switch order, and how to read the results |
| `MPU5_Fabric_Scripts/02-fabric-collect-IOS15.ios` | Paste on IOS 15.2 switches |
| `MPU5_Fabric_Scripts/03-fabric-collect-IOSXE16.ios` | Paste on IOS-XE 16.9 switches |
| `MPU5_Fabric_Scripts/04-packet-path-trace.ios` | Paste on BLDG602 + ALT_EOC (the .7 dual-path burst) |

### Which script on which switch

| Switch | OS | Script |
| --- | --- | --- |
| Annex II Roof, Chancery 2960, RES 4 Relay | IOS 15.2 | `02` |
| BLDG 602 9300, ALT_EOC 9300, EOC SNW | IOS-XE 16.9 | `03` |
| BLDG 602 9300 **and** ALT_EOC 9300 | either | `04` (after 03) |

See `MPU5_Fabric_Scripts/README_fabric.txt` for the full run order and the
worksheet's Section A–G mapping.

### Safety

Every command is `show` / `ping` / `traceroute` plus session-only `terminal`
settings. No config mode, no `write`, no `clear`, no `shutdown`, no `debug`.
The only traffic generated is ICMP echo and traceroute probes. Nothing on any
switch or the fabric is changed.

## Offline PowerShell module stager

`Stage-NetworkModules.ps1` — run on an **internet-connected** machine to
download PowerShell troubleshooting modules (with dependencies) from the
PowerShell Gallery into a folder you can copy to USB and sideload onto an
offline / air-gapped server. It also drops `INSTALL_OFFLINE.txt` (step-by-step
install notes) and `MANIFEST.txt` into the folder, and zips the lot.

Modules staged by default (all open-source, all valid PSGallery IDs):
`Posh-SSH` (SSH/SFTP automation — script the switch collection), `PoshSNMP`
(native SNMP get/walk), and `PSNetAddressing` (subnet/CIDR math, IP target
lists).

```powershell
.\Stage-NetworkModules.ps1                          # stage to .\OfflineModules
.\Stage-NetworkModules.ps1 -OutPath D:\usb\PSModules # stage to a chosen path
```

Downloads files only — it does **not** install anything on the staging machine
(uses `Save-Module`, current-user scope for the NuGet bootstrap).

### Validation

These files were reviewed before being committed:

- **Read-only confirmed** — every executable line uses only `show`, `terminal`,
  `ping`, or `traceroute`; no state-changing commands present.
- **Paste-safe encoding** — pure ASCII (no smart quotes / em-dashes / BOM), so
  nothing breaks when pasted into a CLI. CRLF line endings are kept on purpose
  for the Notepad → MobaXterm paste workflow.
- **Command syntax** validated for both IOS 15.2 (2960-class) and IOS-XE 16.9
  (Catalyst 9300).
- **One fix applied:** the session-reset line in `02` and `03` was
  `terminal exec prompt none`, which is not a valid IOS/IOS-XE keyword (it would
  error and leave timestamps on). Corrected to `terminal no exec prompt
  timestamp`, the documented disable form.

`Stage-NetworkModules.ps1` was validated separately (parsed with the PowerShell
AST — 0 syntax errors — and run end-to-end). Three fixes were applied:

- **Blank-manifest bug** — `Format-Table | Out-String` collapses to empty when
  the console width is unset (`-1`), as in any non-interactive run (scheduled
  task, CI, `-File`, piped output), so `MANIFEST.txt` and the summary came out
  empty. Pinned to `Out-String -Width 4096` so they always render.
- **`-OutPath` default** hardened to fall back to the current directory when
  `$PSScriptRoot` is empty (e.g. dot-sourced / run as selected lines).
- **`Save-Module`** now pins `-Repository PSGallery` to match the documented
  intent. Module IDs (`Posh-SSH`, `PoshSNMP`, `PSNetAddressing`) confirmed
  present on the Gallery.
