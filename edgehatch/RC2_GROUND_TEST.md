# EdgeHatch — RC2 Bodentest-Matrix

Versioniertes Hardware-Abnahmeprotokoll (Teil des `edgehatch/`-Baums). Enthält
keine vorausgefüllten Ergebnisse; wird auf der Hardware ausgefüllt und dann an
Claude/Codex zurückgegeben.

- **Commit unter Test:** _(EdgeHatch-Rebrand-Commit-ID hier eintragen)_, App `app.edgehatch.launcher`
- **Grundregel:** Nur am **Boden**, **keine Motoren/Propeller**, DJI Fly im
  Vordergrund. **Kein Flugtest**, bevor Overlay-Einblendung und -Abschaltung
  eindeutig beherrscht sind.
- **Installation:** Debug- oder lokal signierte Release-APK aus `edgehatch/`
  (`./gradlew assembleDebug`), per Seitlade-Weg auf die RC 2. Danach in EdgeHatch:
  Overlay-Berechtigung erteilen, Griffseite/-position/-größe/-transparenz
  wählen, gewünschte Apps (inkl. DJI Fly) auswählen, „Edge handle" aktivieren.

## Konfigurationskopf (je Durchlauf ausfüllen)

| Feld | Wert |
|---|---|
| Gerät | DJI RC 2 |
| RC-OS / Android-API | _(z. B. Android 10 / API 29 — on-device bestätigen)_ |
| DJI-Fly-Version | _(…)_ |
| EdgeHatch-Version | 1.0.0 _(Rebrand-Commit-ID s. o.)_ |
| Griff-Konfiguration | Seite _(L/R)_ / Position _(%)_ / Breite _(dp)_ / Höhe _(dp)_ / Transparenz _(%)_ / Trigger _(Tap/Swipe/beide)_ |
| Ausgewählte Apps | _(…)_ |

## Testfälle

Für jeden Fall: **Ausgangszustand → Schritte → Soll → Ist → Rücksetzpfad**.
Ergebnis: ✅ / ❌ / ⚠️ + Notiz.

| # | Testfall | Reproduzierbare Schritte | Soll | Ist | Rücksetzpfad |
|---|---|---|---|---|---|
| 1 | **Overlay-Persistenz nach Reboot** (autoStart an) | 1) autoStart aktivieren. 2) RC 2 neu starten. 3) DJI Fly öffnen. | Griff erscheint nach Kaltstart automatisch am Rand. | | Edge deaktivieren / autoStart aus |
| 2 | **Overlay-Persistenz nach Kaltstart** (App vorher beendet) | 1) App aus Recents entfernen. 2) DJI Fly öffnen. | FGS bleibt/kehrt zurück, Griff bleibt erreichbar. | | Notification „Stop" |
| 3 | **Randgeste über DJI Fly** | 1) DJI Fly im Vordergrund. 2) Griff antippen bzw. inward swipen. | Panel öffnet über DJI Fly, listet gewählte Apps. | | Scrim tippen / Back |
| 4 | **Touch-Durchleitung außerhalb des Griffs** | 1) DJI Fly aktiv. 2) Bedienelemente **neben** dem Griff bedienen. | Alle DJI-Fly-Eingaben außerhalb des schmalen Griffs kommen an; keine Interzeption. | | — |
| 5 | **Kein Fehltrigger bei Vertikalzug** | 1) Langer vertikaler Wisch am Rand (leichter inward-Drift). | Panel öffnet **nicht** (Swipe nur bei horizontaler Dominanz). | | — |
| 6 | **App-Start + Rückkehr zu DJI Fly** | 1) Panel öffnen, App wählen. 2) App nutzen. 3) Zurück zu DJI Fly. | Gewählte App startet (NEW_TASK); Rückkehr zu DJI Fly unproblematisch; Griff wieder da. | | Recents / Home |
| 7 | **Live-Parameter ohne Neustart** | 1) In der App Seite/Position/Breite/Höhe/Transparenz/Trigger ändern. 2) Apps ändern (Panel offen). | Ruhender Griff aktualisiert sich sofort (kein Neustart), nur bei Seitenwechsel Remove/Add; offenes Panel aktualisiert Liste. | | Werte zurückstellen |
| 8 | **Orientierung / Vollbild** | 1) DJI Fly in Quer/Immersive. | Griff/Panel korrekt positioniert, nicht hinter Notch/Systemleisten, keine Layout-Brüche. | | — |
| 9 | **Störungsfreier DJI-Fly-Notfallpfad** | 1) In EdgeHatch „Edge handle" ausschalten **oder** Notification „Stop". | Alle Overlay-Views verschwinden **sofort**; DJI Fly voll bedienbar, keine Reste. | | — |
| 10 | **Speicher-/Akkuverhalten** | 1) 15–30 min mit aktivem Griff + DJI Fly beobachten. | Kein spürbarer Akku-/Speicher-Ausreißer; FGS stabil. | | — |
| 11 | **Ungültiges/fehlendes App-Paket** | 1) Eine gewählte App deinstallieren. 2) Panel öffnen. | Deinstallierte App fehlt fail-closed in der Liste; kein Crash. | | Auswahl anpassen |
| 12 | **Berechtigung entzogen** | 1) Overlay-Recht in den Systemeinstellungen entziehen. 2) App/Boot. | Kein Crash; Griff verschwindet; App fordert Recht erneut an; Boot-Start unterbleibt sauber. | | Recht neu erteilen |

## Hinweis (separates Projekt)

Ein optionaler `BootReceiver exported=false`-Reboot-Nachweis eines **anderen**,
unabhängigen Projekts kann bei gleicher RC-2-Testsitzung mit erfasst werden; er
wird dort protokolliert und gehört nicht zu dieser EdgeHatch-Abnahme.

## Ergebnis-Zusammenfassung

- Bestanden: __/12
- Blocker: _(…)_
- Empfehlung: _(Freigabe zum Flugtest / Nacharbeit)_
