# AITSM Windows collector

The production portal provides a one-click, self-contained Windows x64 installer. It enrolls once over HTTPS, installs `AITSMCollector` as an automatic Windows service, stores its collector credential with Windows DPAPI (`LocalMachine`), restricts the installation folder to SYSTEM and local Administrators, and begins reporting every 60 seconds. No inbound firewall rule, copied token, PowerShell command, .NET installation, or scheduled task is required.

## Download and install

### Administrator: company/LAN collector

1. Open **Settings → Windows Collector Installer**.
2. Enter a descriptive collector and site name, choose **Company / LAN**, and select **Download collector**.
3. Within 10 minutes, run the downloaded EXE and approve the Windows administrator prompt. Do not rename the file before running it.
4. Confirm the collector changes from **Never connected** to **Online** in Settings.

An administrator may instead choose **This computer only**. Only a Company/LAN collector receives discovery work and observes authorized LAN peers. Peer records contain only observable address, MAC, hostname, and reachability; they do not claim hardware telemetry.

### End user: this computer only

1. Open the end-user dashboard and find **Protect This Computer**.
2. Select **Download collector**, run the EXE within 10 minutes, and approve the Windows administrator prompt.
3. Confirm the device appears as **Online**.

End-user installers are always forced to `SELF` scope by the server, even if a request is modified. A self-device collector can report exactly one private-address host with its own authenticated hardware inventory and resource telemetry. It cannot poll LAN discovery commands or submit peer devices.

Each download contains a high-entropy, single-use enrollment code in its generated filename. The server stores only its SHA-256 hash. Redemption returns the long-lived collector token once to the installer; the token is then DPAPI-encrypted on that Windows machine. Never share an unredeemed installer. Generate a new one if it expires or is renamed.

## Health, repair, revocation, and uninstall

- **Online**: a valid report was received in the last 2 minutes.
- **Stale**: the most recent report is 2–10 minutes old.
- **Offline**: no valid report was received for more than 10 minutes.
- **Never connected**: enrollment exists but no report has arrived.
- **Revoked**: the server will reject further reports for that credential.

Running the same installer again repairs a partially completed local service installation by reusing the already protected local credential. To replace a revoked enrollment, uninstall locally first and download a new installer.

Administrators can revoke all managed collectors in Settings. End users can view and revoke only collectors that they enrolled for themselves. Revocation is immediate and does not erase historical inventory under the normal data-retention policy.

To remove the local service, run an elevated command from the installed binary:

```powershell
"C:\ProgramData\AITSMCollector\AITSMCollector.exe" --uninstall
```

Then revoke the matching portal entry. The encrypted credential is deleted locally. The service log is at `C:\ProgramData\AITSMCollector\collector.log`; it is capped by rotation and never intentionally logs the token.

## Network and troubleshooting

- Allow outbound TCP 443 to the AITSM production hostname. No inbound port or router change is required.
- Company/LAN discovery is restricted to the private CIDRs configured in **Settings → Asset Detection Configuration**.
- HTTP 401 in the local log means the credential was revoked or rejected. Revoke/uninstall it and download a replacement.
- If the EXE says the enrollment is expired or already used, download a fresh installer.
- Windows may display an unknown-publisher warning until the organization applies an Authenticode code-signing certificate. Only use the installer downloaded while signed in to the trusted AITSM portal.
- A browser refresh does not make a collector online; state is based only on the server receipt time of authenticated reports.

## Build and release

The installer source is in `collector/windows/installer`. Build the production Windows x64 resource with:

```powershell
dotnet publish collector/windows/installer/AITSMCollectorSetup.csproj -c Release -r win-x64 --self-contained true -o collector/windows/installer/publish
Copy-Item collector/windows/installer/publish/AITSMCollectorSetup.exe src/main/resources/collector/AITSMCollectorSetup.exe -Force
```

The packaged EXE is served only after an authenticated, CSRF-protected request whose enrollment code belongs to the requesting account. Production deployment must apply `V008__collector_self_service_enrollment.sql` before starting the new application revision.
