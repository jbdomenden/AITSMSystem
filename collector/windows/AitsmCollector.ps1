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

function Format-InventoryBytes([long]$Value) {
  if ($Value -lt 0) { return $null }
  if ($Value -ge 1TB) { return "$([Math]::Round($Value / 1TB, 2)) TB" }
  if ($Value -ge 1GB) { return "$([Math]::Round($Value / 1GB, 2)) GB" }
  if ($Value -ge 1MB) { return "$([Math]::Round($Value / 1MB, 2)) MB" }
  return "$Value bytes"
}

function Get-HostInventory($Os, $Disks) {
  $computer = Get-CimInstance Win32_ComputerSystem
  $product = Get-CimInstance Win32_ComputerSystemProduct
  $processors = @(Get-CimInstance Win32_Processor)
  $memoryModules = @(Get-CimInstance Win32_PhysicalMemory -ErrorAction SilentlyContinue)
  $videoControllers = @(Get-CimInstance Win32_VideoController -ErrorAction SilentlyContinue)

  $processorNames = @($processors | ForEach-Object { ([string]$_.Name).Trim() } | Where-Object { $_ } | Sort-Object -Unique)
  $maxClockMhz = ($processors | Measure-Object -Property MaxClockSpeed -Maximum).Maximum
  $processorSpeed = if ($maxClockMhz) { "$([Math]::Round([double]$maxClockMhz / 1000, 2)) GHz" } else { $null }
  $ramSpeeds = @($memoryModules | ForEach-Object { if ($_.ConfiguredClockSpeed) { "$($_.ConfiguredClockSpeed) MHz" } } | Where-Object { $_ } | Sort-Object -Unique)
  $gpuNames = @($videoControllers | ForEach-Object { $_.Name } | Where-Object { $_ } | Sort-Object -Unique)
  $gpuBytes = [long](($videoControllers | Where-Object { $_.AdapterRAM -gt 0 } | Measure-Object -Property AdapterRAM -Sum).Sum)
  $storageBreakdown = @($Disks | ForEach-Object {
    $total = [long]$_.Size
    $free = [long]$_.FreeSpace
    [ordered]@{
      drive = $_.DeviceID
      total = Format-InventoryBytes $total
      used = Format-InventoryBytes ([Math]::Max([long]0, $total - $free))
      free = Format-InventoryBytes $free
    }
  }) | ConvertTo-Json -Compress -Depth 4

  $deviceUuid = [string]$product.UUID
  if ([string]::IsNullOrWhiteSpace($deviceUuid) -or $deviceUuid -match '^0{8}-0{4}-0{4}-0{4}-0{12}$') { $deviceUuid = $null }
  $fullDeviceName = if ($computer.PartOfDomain -and -not [string]::IsNullOrWhiteSpace([string]$computer.Domain)) {
    "$($computer.DNSHostName).$($computer.Domain)"
  } else { [string]$computer.DNSHostName }
  $installedOn = if ($os.InstallDate) { ([DateTime]$os.InstallDate).ToString('o') } else { $null }

  return [ordered]@{
    fullDeviceName = $fullDeviceName
    manufacturer = [string]$computer.Manufacturer
    model = [string]$computer.Model
    processorName = ($processorNames -join '; ')
    processorSpeed = $processorSpeed
    installedRam = Format-InventoryBytes ([long]$computer.TotalPhysicalMemory)
    usableRam = Format-InventoryBytes ([long]$os.TotalVisibleMemorySize * 1KB)
    ramSpeed = ($ramSpeeds -join '; ')
    gpuName = ($gpuNames -join '; ')
    gpuMemory = if ($gpuBytes -gt 0) { Format-InventoryBytes $gpuBytes } else { $null }
    storageTotal = $null
    storageUsed = $null
    storageFree = $null
    storageBreakdownJson = $storageBreakdown
    systemType = [string]$computer.SystemType
    osName = [string]$os.Caption
    osEdition = $null
    osVersion = [string]$os.Version
    osBuild = [string]$os.BuildNumber
    installedOn = $installedOn
    domainOrWorkgroup = [string]$computer.Domain
    deviceUuid = $deviceUuid
    productId = [string]$os.SerialNumber
    penTouchSupport = $null
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
  $hostInventory = Get-HostInventory $os $disks
  $hostInventory.storageTotal = Format-InventoryBytes $storageTotal
  $hostInventory.storageUsed = Format-InventoryBytes ([Math]::Max([long]0, [long]($storageTotal - $storageFree)))
  $hostInventory.storageFree = Format-InventoryBytes $storageFree
  $hostDevice = [ordered]@{
    deviceKey = "host:$($env:COMPUTERNAME.ToLowerInvariant())"; hostname = $env:COMPUTERNAME
    ipAddress = $primary.IPAddress; macAddress = $adapter.MacAddress; osDetails = "$($os.Caption) $($os.Version)"
    reachable = $true; telemetryAvailable = $true; cpuUsagePercent = [Math]::Round([double]$cpu, 2)
    memoryUsagePercent = [Math]::Round($memoryPct, 2); storageTotalBytes = $storageTotal
    storageUsedBytes = [Math]::Max([long]0, [long]($storageTotal - $storageFree)); measurementAt = $now
    inventory = $hostInventory
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
      inventory = $null
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
      measuredAt = [DateTime]::UtcNow.ToString('o'); devices = @(Get-CollectorDevices $cidrs $true)
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
