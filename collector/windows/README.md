# AITSM Windows LAN collector

The collector runs inside the target LAN and sends data outbound over HTTPS. The Cloud Run service never scans the private network. LAN peers report only observable address, MAC, hostname, and reachability data; detailed CPU, memory, storage, and OS telemetry is reported only for the Windows computer running the collector.

## Enroll and configure

1. As an AITSM administrator, create a collector with `POST /api/monitoring/collectors` using JSON such as `{"siteId":"manila-office","name":"LAN collector 1"}`. The response contains the raw token once. Store it securely; the server stores only its SHA-256 hash.
2. Copy `collector.config.example.json` to a restricted folder as `collector.config.json`. Set the production HTTPS URL, paste the token, and list only private CIDR ranges that this collector is authorized to observe.
3. Restrict the configuration ACL to the service account: `icacls collector.config.json /inheritance:r /grant:r "SYSTEM:R" "Administrators:R"`.

Never commit or email the populated configuration. Revoke a compromised collector with `DELETE /api/monitoring/collectors/{collectorId}` and enroll a replacement.

## Start, verify, and stop

Run once from an elevated PowerShell prompt:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\AitsmCollector.ps1 -ConfigPath .\collector.config.json -Once
```

Successful output includes the server-recorded `receivedAt` timestamp and accepted device count. Confirm the collector shows Online and that Last data received matches the report time in AITSM. For continuous collection, omit `-Once`; stop with Ctrl+C. For unattended use, create a Windows Scheduled Task under a dedicated least-privilege account.

## Permissions, firewall, and troubleshooting

- Permit outbound TCP 443 to the AITSM hostname. No inbound firewall rule or port forwarding is required.
- `Get-NetNeighbor`, `Get-NetIPAddress`, and CIM queries must be available. Some adapter/MAC details require an elevated shell.
- Discovery is limited to the server-provided private CIDRs. Configure Windows/network firewalls to allow the local observation methods your organization approves; devices that do not answer still never receive fabricated telemetry.
- HTTP 401 means the token is missing, revoked, or incorrect. HTTP 429 means the collector is reporting too frequently. HTTP 400 means a payload value or address is outside the allowed range.
- An Offline or Stale collector retains the last valid server receipt timestamp; refreshing the browser does not change it.

## Safe uninstall

Stop the process or delete its Scheduled Task, revoke the collector in AITSM, then securely delete `collector.config.json` and the script directory. Historical device reports remain available according to the application’s data-retention policy.
