#requires -Version 5.1
<#
  中国象棋一键联机（Windows）

  用法（在本目录打开 PowerShell 或双击 start-online.cmd）：
    .\start-online.cmd          启动本地服务 + 公网隧道，输出可直接发给对方的链接
    .\start-online.cmd status   查看当前状态
    .\start-online.cmd stop     停止服务和隧道

  说明：
    - 服务是本机的 Spring Boot jar，公网地址由 Cloudflare 临时隧道生成；
    - 只要脚本启动的进程还在、电脑不关机，链接就持续有效，没有冷启动；
    - 每次重新执行 start 会生成新的公网地址（Cloudflare 临时隧道特性）。
#>
param(
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'start',
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$JarPath = Join-Path $Root 'target\chinese-chess-1.0.0.jar'
$ToolDir = Join-Path $env:LOCALAPPDATA 'XiangqiTools'
$LogDir = Join-Path $ToolDir 'logs'
New-Item -ItemType Directory -Force $ToolDir | Out-Null
New-Item -ItemType Directory -Force $LogDir | Out-Null

$ServerPidFile = Join-Path $ToolDir 'server.pid'
$TunnelPidFile = Join-Path $ToolDir 'tunnel.pid'
$ServerLog = Join-Path $LogDir 'server.log'
$ServerErrLog = Join-Path $LogDir 'server.err.log'
$TunnelLog = Join-Path $LogDir 'tunnel.err.log'
$TunnelOutLog = Join-Path $LogDir 'tunnel.out.log'
$Cloudflared = Join-Path $ToolDir 'cloudflared.exe'
$TunnelUrlPattern = 'https://[a-z0-9-]+\.trycloudflare\.com'

function Get-SavedPid([string]$File) {
    if (-not (Test-Path $File)) { return 0 }
    $text = (Get-Content $File -ErrorAction SilentlyContinue | Select-Object -First 1)
    $pidValue = 0
    if ([int]::TryParse($text, [ref]$pidValue)) { return $pidValue }
    return 0
}

function Test-ProcessAlive([int]$ProcessId) {
    if ($ProcessId -le 0) { return $false }
    return [bool](Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Test-LocalServer {
    try {
        $response = Invoke-WebRequest "http://127.0.0.1:$Port/" -UseBasicParsing -TimeoutSec 3
        return $response.Content -match 'roomEntry'
    } catch {
        return $false
    }
}

function Test-PublicUrl([string]$Url) {
    if ([string]::IsNullOrWhiteSpace($Url)) { return $false }
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        $code = & $curl.Source -s -o NUL -w '%{http_code}' --max-time 15 $Url 2>$null
        return $code -eq '200'
    }
    try {
        return (Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 15).StatusCode -eq 200
    } catch {
        return $false
    }
}

function Get-TunnelUrl {
    if (-not (Test-Path $TunnelLog)) { return $null }
    $match = Select-String -Path $TunnelLog -Pattern $TunnelUrlPattern -AllMatches |
        Select-Object -Last 1
    if ($match) { return $match.Matches[0].Value }
    return $null
}

function Wait-Server([int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-LocalServer) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Ensure-Cloudflared {
    if (Test-Path $Cloudflared) { return }
    Write-Host '首次运行，正在下载 cloudflared（约 30MB）...'
    $urls = @(
        'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe',
        'https://ghproxy.net/https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe'
    )
    $lastError = $null
    foreach ($url in $urls) {
        try {
            Invoke-WebRequest -Uri $url -OutFile $Cloudflared -UseBasicParsing -TimeoutSec 180
            if ((Test-Path $Cloudflared) -and (Get-Item $Cloudflared).Length -gt 5MB) {
                Write-Host 'cloudflared 下载完成。'
                return
            }
        } catch {
            $lastError = $_.Exception.Message
        }
    }
    throw "cloudflared 下载失败：$lastError"
}

function Test-NeedsBuild {
    if (-not (Test-Path $JarPath)) { return $true }
    $jarTime = (Get-Item $JarPath).LastWriteTimeUtc
    $newer = Get-ChildItem (Join-Path $Root 'src'), (Join-Path $Root 'frontend') -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTimeUtc -gt $jarTime } |
        Select-Object -First 1
    return [bool]$newer
}

function Stop-ByPidFile([string]$File, [string]$Name) {
    $processId = Get-SavedPid $File
    if ($processId -gt 0) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            Write-Host "已停止$Name（PID $processId）"
        }
    }
    Remove-Item $File -ErrorAction SilentlyContinue
}

function Start-ServerIfNeeded {
    if (Test-LocalServer) {
        Write-Host "服务已在运行：http://127.0.0.1:$Port"
        return
    }

    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listeners) {
        throw "端口 $Port 已被其他程序占用，请先关闭占用该端口的程序。"
    }

    if (Test-NeedsBuild) {
        $mvn = Get-Command mvn -ErrorAction SilentlyContinue
        if (-not $mvn) {
            throw "未找到 Maven（mvn）。请先执行 mvn -DskipTests package 生成 jar，或安装 Maven。"
        }
        Write-Host '检测到源码有更新，正在构建 jar（首次可能较慢）...'
        Push-Location $Root
        & $mvn.Source -q -DskipTests package
        $buildCode = $LASTEXITCODE
        Pop-Location
        if ($buildCode -ne 0) { throw 'Maven 构建失败，请查看上方输出。' }
    }

    $java = (Get-Command java -ErrorAction SilentlyContinue).Source
    if (-not $java) { throw '未找到 java，请安装 JDK 17 并加入 PATH。' }

    Write-Host '正在启动游戏服务器...'
    $process = Start-Process -FilePath $java -ArgumentList @('-jar', $JarPath) -WorkingDirectory $Root `
        -RedirectStandardOutput $ServerLog -RedirectStandardError $ServerErrLog `
        -WindowStyle Hidden -PassThru
    Set-Content -Path $ServerPidFile -Value $process.Id

    if (-not (Wait-Server)) {
        throw "服务器启动失败，请查看日志：$ServerLog / $ServerErrLog"
    }
    Write-Host "服务已就绪：http://127.0.0.1:$Port"
}

function Start-TunnelIfNeeded {
    $name = "ChinaChessTunnel$([Guid]::NewGuid().ToString('N').Substring(0,8))"

    $oldPid = Get-SavedPid $TunnelPidFile
    $oldUrl = Get-TunnelUrl
    if ((Test-ProcessAlive $oldPid) -and $oldUrl -and (Test-PublicUrl $oldUrl)) {
        Write-Host "公网隧道已在运行：$oldUrl"
        return $oldUrl
    }

    if (Test-ProcessAlive $oldPid) {
        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $TunnelPidFile -ErrorAction SilentlyContinue

    Ensure-Cloudflared
    Write-Host '正在建立公网隧道...'
    $process = Start-Process -FilePath $Cloudflared `
        -ArgumentList @('tunnel', '--url', "http://127.0.0.1:$Port", '--no-autoupdate') `
        -RedirectStandardOutput $TunnelOutLog -RedirectStandardError $TunnelLog `
        -WindowStyle Hidden -PassThru
    Set-Content -Path $TunnelPidFile -Value $process.Id

    $url = $null
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        $url = Get-TunnelUrl
        if ($url) { break }
        if (-not (Test-ProcessAlive $process.Id)) {
            throw "隧道进程提前退出，请查看日志：$TunnelLog"
        }
        Start-Sleep -Seconds 1
    }
    if (-not $url) { throw "获取公网地址超时，请查看日志：$TunnelLog" }

    for ($i = 0; $i -lt 10; $i++) {
        if (Test-PublicUrl $url) { return $url }
        Start-Sleep -Seconds 1
    }
    Write-Host '公网地址已生成，但首次访问还没通过，稍等几秒再刷新即可。'
    return $url
}

function Show-Status {
    if (Test-LocalServer) {
        Write-Host "本地服务：正常  http://127.0.0.1:$Port"
    } else {
        Write-Host '本地服务：未运行'
    }
    $url = Get-TunnelUrl
    if ($url -and (Test-PublicUrl $url)) {
        Write-Host "公网隧道：正常  $url"
    } elseif ($url) {
        Write-Host "公网隧道：有地址但暂时访问失败  $url"
    } else {
        Write-Host '公网隧道：未运行'
    }
}

try {
    switch ($Action) {
        'start' {
            Start-ServerIfNeeded | Out-Null
            $url = Start-TunnelIfNeeded
            try { Set-Clipboard -Value $url } catch { }

            Write-Host ''
            Write-Host '============================================' -ForegroundColor Green
            Write-Host ' 联机对弈已就绪' -ForegroundColor Green
            Write-Host " 公网地址：$url" -ForegroundColor Cyan
            Write-Host " 本机地址：http://127.0.0.1:$Port"
            Write-Host ' 使用：两人打开公网地址，一人创建房间，另一人输入房间号或点邀请链接'
            Write-Host ' 地址已复制到剪贴板，并已在浏览器打开' -ForegroundColor DarkGray
            Write-Host '============================================' -ForegroundColor Green
            Write-Host " 停止：$($MyInvocation.MyCommand.Name) stop" -ForegroundColor DarkGray

            try { Start-Process $url } catch { }
        }
        'stop' {
            Stop-ByPidFile $TunnelPidFile '公网隧道'
            Stop-ByPidFile $ServerPidFile '游戏服务器'
        }
        'status' {
            Show-Status
        }
    }
} catch {
    Write-Host ''
    Write-Host "错误：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
