===============================================================================
 FABRIC DEEP-DIVE COLLECTION SCRIPTS  (read-only, 1 session, 5 switches)
 Pairs with: MPU5_Fabric_Deepdive_Worksheet_Fillable.pdf
===============================================================================

WHAT THESE DO
  Sweep the VLAN 17 (WAVES) fabric across all five switches in one pass to
  answer: how do packets travel, is there an L2 loop, who is the STP root,
  what does ARP look like everywhere. Everything is show/ping/traceroute.
  NO config mode, NO write memory, NO clear, NO shutdown. Safe to paste.

WHY A DEEP DIVE (the structural reason)
  The Annex II Roof switch has TWO VLAN-17 uplinks:
     - Fiber  Gi0/1  -> ALT_EOC 9300 (172.17.9.3)
     - Cambium Fa0/5 -> BLDG 602      (172.17.9.76)
  Both are "spanning-tree guard none". The WaveRelay mesh bridges VLAN 17 a
  THIRD way. That is a real physical ring - RSTP must block one path. Also,
  THREE switches tie for VLAN-17 root at priority 24576. This session confirms
  the ring is stable (or finds the loop / flapping root / dirty trunk).

FILES
  02-fabric-collect-IOS15.ios     <- paste on IOS 15.2 switches
  03-fabric-collect-IOSXE16.ios   <- paste on IOS-XE 16.9 switches
  04-packet-path-trace.ios        <- paste on BLDG602 + ALT_EOC (the .7 dual-path)

WHICH SCRIPT ON WHICH SWITCH
  IOS 15.2 (use 02): Annex II Roof, Chancery 2960, RES 4 Relay
  IOS-XE 16.9 (use 03): BLDG 602 9300, ALT_EOC 9300 (AnnexII 3rd Flr), EOC SNW
  Path trace (04): BLDG 602 9300 AND ALT_EOC 9300 (also fine on any switch)

HOW TO RUN (per switch)
  1. In MobaXterm, open the SSH session for the switch.
  2. Turn ON session logging: right-click tab -> "Log session output to file".
  3. Make sure you are at the priv-exec prompt (ends in #).
  4. Open the matching .ios file in Notepad, Select All, Copy, paste into SSH.
     It self-runs (sets terminal length 0, adds timestamps, runs the shows).
  5. Let it finish. The ping/traceroute bursts in 04 take a few minutes each
     (the .7 burst is 200 packets; watch for a run of '!' = an up-window).
  6. Save the log. Repeat on the next switch.

SUGGESTED ORDER
  1. BLDG 602 9300   (03, then 04) - the cambium side + mesh entry
  2. ALT_EOC 9300    (03, then 04) - the fiber side of the Annex ring
  3. Annex II Roof   (02)          - the dual-uplink switch itself
  4. EOC SNW         (03)          - core / root contender
  5. Chancery 2960   (02)          - known-good control (.5)
  6. RES 4 Relay     (02)          - British relay leg

READING THE RESULTS -> the worksheet
  Section A : STP root + blocked ports per switch (all must agree on 1 root)
  Section B : loop/dup test (a MAC on 2 ports = loop; 1 IP/2 MACs = dup IP)
  Section C : ARP census (who resolves from which switch = the path)
  Section D : traceroute + the .7 burst from BOTH ring legs
  Section E : trunk/uplink health on the ring legs (CRC = BPDU drops)
  Section G : the determination matrix - what each result means

THE TWO MACS THE SCRIPTS TRACK
  0018.a601.2290 = .7 management MAC (the subject node)
  0018.a6e0.bf79 = the device on Annex Fa0/1 (NOT .7 mgmt - identify it)

SAFETY NOTE
  Read-only. The only traffic generated is ICMP echo (ping) and traceroute
  probes. No state on any switch or the fabric is changed.
