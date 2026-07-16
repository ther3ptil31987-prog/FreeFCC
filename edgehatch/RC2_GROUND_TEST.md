# EdgeHatch — RC2 Bodentest-Matrix

Versioniertes Hardware-Abnahmeprotokoll (Teil des EdgeHatch-Baums). Enthält keine
vorausgefüllten Ergebnisse; wird auf der Hardware ausgefüllt und dann
zurückgegeben.

- **Version unter Test:** EdgeHatch 1.1.0 (S-014, Accessibility-Overlay-Pfad). Commit-ID beim Commit eintragen.
- **Grundregel:** Nur am **Boden**, **keine Motoren/Propeller**, DJI Fly im
  Vordergrund. **Kein Flugtest**, bevor Overlay-Einblendung **und** sofortige
  -Abschaltung (Fall 11) eindeutig beherrscht sind.

## Gerätekontext (DJI RC 2)

Die RC 2 ist gesperrt: kein adb, DJI Fly ist De-facto-Home, Android-Settings sind
regulär nicht erreichbar, und `SYSTEM_ALERT_WINDOW` lässt sich praktisch nicht
erteilen. EdgeHatch bietet daher zwei Zeichen-Mechanismen:

- **Accessibility-Modus (Primärpfad RC 2):** hochprivilegierter
  Kompatibilitätsmodus, zeichnet den Griff via `TYPE_ACCESSIBILITY_OVERLAY` —
  **ohne** Overlay-Recht. Aktivierung über die Bedienungshilfen. Der Dienst liest
  **keinen** Bildschirm, wertet keine Events aus, dispatcht keine Gesten.
- **Overlay-Modus (Fallback):** klassischer Vordergrunddienst mit
  `SYSTEM_ALERT_WINDOW` (falls das Recht doch erteilt werden kann).

Nur **ein** Pfad zeichnet gleichzeitig (Accessibility hat Vorrang) — nie zwei
Griffe.

## Installation (RC 2)

Das Aktivieren des Accessibility-Dienstes verlangt, dass EdgeHatch von einem
Installer installiert wurde, der es einer **vertrauenswürdigen Quelle**
zuschreibt (sonst „unbekannte Quelle" → Sperre). **Woher ein solcher Installer
kommt, ist nicht Teil dieses Projekts; EdgeHatch liefert/empfiehlt keinen.** Nur
einen bereits vertrauenswürdigen Installer verwenden (z. B. gerätemitgeliefert
oder ein selbst aus auditiertem Quellcode gebauter). **Keinen** Installer
unbekannter Herkunft seitlich laden.

> Provenienz-Hinweis (ehrlich): Der RC-2-Nachweis dieses Protokolls lief über ein
> weiterverteiltes `com.android.packageinstaller`-APK mit **selbstsigniertem
> „DJI"-Freitext-Cert unbelegter Herkunft** (Paketnamens-Kollision mit dem
> System-Installer möglich) — ein **provenance-offener Testpfad**, ausreichend
> zum Funktionsnachweis, aber **keine** Empfehlung zur Installation/Verteilung.

1. EdgeHatch mit einem vertrauenswürdigen Installer installieren.
2. In EdgeHatch: Accessibility-Modus **oder** Overlay-Recht aktivieren, Griff
   konfigurieren, Apps (inkl. DJI Fly) wählen, „Edge handle" einschalten.

## Konfigurationskopf (je Durchlauf ausfüllen)

| Feld | Wert |
|---|---|
| Gerät | DJI RC 2 |
| RC-OS / Android-API | _(on-device bestätigen)_ |
| DJI-Fly-Version | _(…)_ |
| EdgeHatch-Version | 1.1.0 _(Commit-ID s. o.)_ |
| Zeichen-Pfad | _(Accessibility / Overlay)_ |
| Installationsweg | _(vertrauenswürdiger Installer / sonstig)_ |
| Griff-Konfiguration | Seite _(L/R)_ / Position _(%)_ / Breite _(dp)_ / Höhe _(dp)_ / Transparenz _(%)_ / Trigger _(Tap/Swipe/beide)_ |
| Ausgewählte Apps | _(…)_ |

## Testfälle

Für jeden Fall: **Ausgangszustand → Schritte → Soll → Ist → Rücksetzpfad**.
Ergebnis: ✅ / ❌ / ⚠️ + Notiz.

| # | Testfall | Reproduzierbare Schritte | Soll | Ist | Rücksetzpfad |
|---|---|---|---|---|---|
| 1 | **Accessibility-Aktivierung** | 1) EdgeHatch via vertrauenswürdigem Installer installiert. 2) „Accessibility mode" → Bedienungshilfen → EdgeHatch aktivieren. 3) „Edge handle" an. | Dienst lässt sich **ohne** „eingeschränkt/nicht verfügbar"-Sperre aktivieren; Griff erscheint am Rand. | | Accessibility-Dienst aus |
| 2 | **Accessibility-Entzug** | 1) Griff aktiv. 2) Bedienungshilfen → EdgeHatch deaktivieren. | Griff/Views verschwinden **sofort**, kein Crash; kein Rest-Overlay. | | Dienst neu aktivieren |
| 3 | **Overlay-Fallback** (falls Recht erteilbar) | 1) „Berechtigung erteilen" → Overlay-Recht. 2) Accessibility **aus**. 3) „Edge handle" an. | Griff zeichnet via Vordergrunddienst. | | Recht entziehen / Edge aus |
| 4 | **Beide Rechte gleichzeitig** | 1) Accessibility **an** UND Overlay-Recht erteilt. 2) Griff aktiv. | **Genau ein** Griff (Accessibility-Pfad gewinnt); **kein** Doppel-Griff; kein FGS-Griff daneben. | | — |
| 5 | **Randgriff + Panel über DJI Fly** | 1) DJI Fly im Vordergrund. 2) Griff antippen/inward swipen. | Panel öffnet über DJI Fly, listet gewählte Apps; **kein Crash**. | | Scrim tippen / Back |
| 6 | **Touch-Durchleitung außerhalb des Griffs** | 1) DJI Fly aktiv. 2) Bedienelemente **neben** dem Griff bedienen. | Alle DJI-Fly-Eingaben außerhalb des schmalen Griffs kommen an; keine Interzeption. | | — |
| 7 | **Kein Fehltrigger bei Vertikalzug** | 1) Langer vertikaler Wisch am Rand. | Panel öffnet **nicht** (Swipe nur bei horizontaler Dominanz). | | — |
| 8 | **App-Start + Rückkehr zu DJI Fly** | 1) Panel öffnen, App wählen. 2) Nutzen. 3) Zurück zu DJI Fly. | Gewählte App startet (NEW_TASK); Rückkehr unproblematisch; Griff wieder da. | | Recents / Home |
| 9 | **Live-Parameter ohne Neustart** | 1) Seite/Position/Breite/Höhe/Transparenz/Trigger ändern. 2) Apps ändern (Panel offen). | Ruhender Griff aktualisiert sich sofort (nur Seitenwechsel = Remove/Add); offenes Panel aktualisiert Liste. Gilt für den aktiven Pfad. | | Werte zurückstellen |
| 10 | **Orientierung / Vollbild** | 1) DJI Fly in Quer/Immersive. | Griff/Panel korrekt positioniert, nicht hinter Notch/Systemleisten. | | — |
| 11 | **Sofortiges Disable (Notfallpfad)** | 1) „Edge handle" ausschalten **oder** (Overlay-Pfad) Notification „Stop" **oder** Accessibility-Dienst aus. | **Alle** Overlay-Views verschwinden **sofort**; DJI Fly voll bedienbar, keine Reste. | | — |
| 12 | **Reboot-Semantik (pfadabhängig)** | 1a) **Accessibility-Modus**, Dienst aktiviert lassen → RC 2 neu starten. 1b) **Overlay-Modus** mit/ohne „Start after reboot" → neu starten. | 1a) Griff kehrt **automatisch** zurück, solange der Dienst aktiviert bleibt (unabhängig von „Start after reboot"). 1b) Griff kehrt nur bei „Start after reboot" **an** zurück. | | Dienst deaktivieren / Edge aus |
| 13 | **Speicher-/Akkuverhalten** | 1) 15–30 min mit aktivem Griff + DJI Fly beobachten. | Kein spürbarer Akku-/Speicher-Ausreißer; stabil. | | „Unrestricted battery" prüfen |
| 14 | **Ungültiges/fehlendes App-Paket** | 1) Eine gewählte App deinstallieren. 2) Panel öffnen. | Deinstallierte App fehlt fail-closed in der Liste; **kein Crash**. | | Auswahl anpassen |
| 15 | **Recht/Dienst entzogen im Betrieb** | 1) Overlay-Recht **oder** Accessibility-Dienst im laufenden Betrieb entziehen. | Kein Crash; Griff verschwindet; App fordert das Recht ggf. erneut an; keine Doppel-Zeichnung nach Reconnect. | | Recht/Dienst neu erteilen |

## Hinweis (separates Projekt)

Ein optionaler `BootReceiver exported=false`-Reboot-Nachweis eines **anderen**,
unabhängigen Projekts kann bei gleicher RC-2-Testsitzung mit erfasst werden; er
gehört nicht zu dieser EdgeHatch-Abnahme.

## Ergebnis-Zusammenfassung

- Bestanden: __/15
- Blocker: _(…)_
- Empfehlung: _(Freigabe zum Flugtest / Nacharbeit)_
