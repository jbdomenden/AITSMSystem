param(
  [Parameter(Mandatory = $true)][string]$ConfigPath,
  [switch]$Once
)

$ErrorActionPreference = 'Stop'
$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
if (-not $config.BaseUrl.StartsWith('https://')) { throw 'BaseUrl must use HTTPS.' }
if ([string]::IsNullOrWhiteSpace($config.Token)) { throw 'Collector token is required.' }
$baseUrl = $config.BaseUrl.TrimEnd('/')
$headers = @{ Authorization = "Bearer $($config.Token)" }
$configuredInterval = if ($null -eq $config.IntervalSeconds) { 60 } else { [int]$config.IntervalSeconds }
$interval = [Math]::Max(30, $configuredInterval)

function Test-IpInCidr([string]$Ip, [string]$Cidr) {
  try {
    $parts = $Cidr.Split('/')
    $address = [Net.IPAddress]::Parse($Ip).GetAddressBytes()
    $network = [Net.IPAddress]::Parse($parts[0]).GetAddressBytes()
    if ($address.Length -ne 4 -or $network.Length -ne 4) { return $false }
    $prefix = [int]$parts[1]
    for ($i = 0; $i -lt 4; $i++) {
      $bits = [Math]::Min(8, [Math]::Max(0, $prefix - ($i * 8)))
      $mask = if ($bits -eq 0) { 0 } else { (0xff -shl (8 - $bits)) -band 0xff }
      if (($address[$i] -band $mask) -ne ($network[$i] -band $mask)) { return $false }
    }
    return $true
  } catch { return $false }
}

function Get-DiscoveryCommand {
  try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/monitoring/collector/commands" -Headers $headers -Method Get -UseBasicParsing
    if ($response.StatusCode -eq 204 -or [string]::IsNullOrWhiteSpace($response.Content)) { return $null }
    return $response.Content | ConvertFrom-Json
  } catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 204) { return $null }
    throw
  }
}

function Get-CollectorDevices($Cidrs, [bool]$IncludePeers) {
  $now = [DateTime]::UtcNow.ToString('o')
  $primary = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $candidateIp = $_.IPAddress
    $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and
    [bool]($Cidrs | Where-Object { Test-IpInCidr $candidateIp $_ } | Select-Object -First 1)
  } | Select-Object -First 1
  if (-not $primary) { throw 'No local IPv4 address matches the configured private CIDR ranges.' }
  $adapter = Get-NetAdapter -InterfaceIndex $primary.InterfaceIndex -ErrorAction SilentlyContinue
  $os = Get-CimInstance Win32_OperatingSystem
  $cpu = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average
  $memoryUsed = [double]($os.TotalVisibleMemorySize - $os.FreePhysicalMemory)
  $memoryPct = if ($os.TotalVisibleMemorySize) { 100 * $memoryUsed / [double]$os.TotalVisibleMemorySize } else { 0 }
  $disks = Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3"
  $storageTotal = [long](($disks | Measure-Object -Property Size -Sum).Sum)
  $storageFree = [long](($disks | Measure-Object -Property FreeSpace -Sum).Sum)
  $hostDevice = [ordered]@{
    deviceKey = "host:$($env:COMPUTERNAME.ToLowerInvariant())"; hostname = $env:COMPUTERNAME
    ipAddress = $primary.IPAddress; macAddress = $adapter.MacAddress; osDetails = "$($os.Caption) $($os.Version)"
    reachable = $true; telemetryAvailable = $true; cpuUsagePercent = [Math]::Round([double]$cpu, 2)
    memoryUsagePercent = [Math]::Round($memoryPct, 2); storageTotalBytes = $storageTotal
    storageUsedBytes = [Math]::Max([long]0, [long]($storageTotal - $storageFree)); measurementAt = $now
  }
  $peers = if ($IncludePeers) { Get-NetNeighbor -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object {
    $candidateIp = $_.IPAddress
    $_.State -notin @('Unreachable', 'Incomplete') -and $_.IPAddress -ne $primary.IPAddress -and
    [bool]($Cidrs | Where-Object { Test-IpInCidr $candidateIp $_ } | Select-Object -First 1)
  } | Sort-Object IPAddress -Unique | ForEach-Object {
    $peerName = try { [Net.Dns]::GetHostEntry($_.IPAddress).HostName } catch { 'LAN peer' }
    [ordered]@{
      deviceKey = "peer:$($_.IPAddress)"; hostname = $peerName
      ipAddress = $_.IPAddress; macAddress = $_.LinkLayerAddress; osDetails = $null
      reachable = $true; telemetryAvailable = $false; cpuUsagePercent = $null; memoryUsagePercent = $null
      storageTotalBytes = $null; storageUsedBytes = $null; measurementAt = $now
    }
  } } else { @() }
  return @($hostDevice) + @($peers)
}

do {
  try {
    $command = Get-DiscoveryCommand
    $cidrs = if ($command) { @($command.privateCidrs) } else { @($config.PrivateCidrs) }
    if (-not $cidrs -or $cidrs.Count -eq 0) { throw 'No private CIDR ranges are configured.' }
    $payload = @{
      measuredAt = [DateTime]::UtcNow.ToString('o'); devices = @(Get-CollectorDevices $cidrs ([bool]$command))
      discoveryRequestId = if ($command) { $command.requestId } else { $null }; discoveryError = $null
    } | ConvertTo-Json -Depth 8
    $result = Invoke-RestMethod -Uri "$baseUrl/api/monitoring/collector/reports" -Headers $headers -Method Post -ContentType 'application/json' -Body $payload
    Write-Host "Report accepted at $($result.receivedAt); devices: $($result.devicesAccepted)."
  } catch {
    $serverDetails = $_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($serverDetails) -and $_.Exception.Response) {
      try {
        $reader = [IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $serverDetails = $reader.ReadToEnd()
        $reader.Dispose()
      } catch { $serverDetails = $null }
    }
    $detailSuffix = if ([string]::IsNullOrWhiteSpace($serverDetails)) { '' } else { " Server response: $serverDetails" }
    Write-Error "Collector cycle failed: $($_.Exception.Message)$detailSuffix"
  }
  if (-not $Once) { Start-Sleep -Seconds $interval }
} while (-not $Once)
