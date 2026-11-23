<#
.SYNOPSIS
    Starts the Local Java Microservices Platform via Docker Compose or Kubernetes with
    per-folder Git-diff incremental builds and fast container spin-up.

.PARAMETER Mode
    Deployment mode: "k8s" (default) or "docker".

.PARAMETER SkipBuild
    Skips all Maven and Docker build steps (uses existing artifacts/images).

.PARAMETER ForceBuild
    Forces rebuilding all 7 microservices regardless of Git diff state.

.PARAMETER NoBrowser
    Prevents automatically opening the Control Center dashboard in the default browser.
#>

param(
    [Parameter(Position=0)]
    [Alias("m")]
    [ValidateSet("docker", "k8s", "kubernetes", "Kubernetes", "Docker", "K8s", IgnoreCase=$true)]
    [string]$Mode = "k8s",

    [switch]$SkipBuild,
    [switch]$ForceBuild,
    [switch]$NoBrowser,
    [switch]$EnableAuth
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Get-Item "$PSScriptRoot\..").FullName
Set-Location $ProjectRoot

$ModeLower = $Mode.ToLower()
$IsK8s = ($ModeLower -in @("k8s", "kubernetes"))
$TargetModeDisplay = if ($IsK8s) { "KUBERNETES" } else { "DOCKER COMPOSE" }
$StateFile = Join-Path $ProjectRoot ".build-state.json"

if ($EnableAuth) {
    $env:SECURITY_ENABLED = "true"
} else {
    $env:SECURITY_ENABLED = "false"
}

function Write-Step {
    param([string]$Message)
    Write-Host "`n[+] $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[!] $Message" -ForegroundColor Yellow
}

function Write-Err {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Get-StringSha256 {
    param([string]$Text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        $hashBytes = $sha.ComputeHash($bytes)
        return ([System.BitConverter]::ToString($hashBytes)).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Get-FolderGitSignature {
    param(
        [string]$RelativePath,
        [bool]$IsGitRepo
    )

    $fullTarget = Join-Path $ProjectRoot $RelativePath
    $hasOwnGit = (Test-Path -LiteralPath (Join-Path $fullTarget ".git"))

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($hasOwnGit) {
            $lastCommit = (git -C $fullTarget rev-parse HEAD 2>$null) -join ""
            $diffOutput = (git -C $fullTarget -c core.safecrlf=false diff HEAD 2>$null) -join "`n"
            $untrackedFiles = @(git -C $fullTarget ls-files --others --exclude-standard 2>$null)
            $baseDir = $fullTarget
        } elseif ($IsGitRepo) {
            $lastCommit = (git -C $ProjectRoot log -n 1 --format=%H -- $RelativePath 2>$null) -join ""
            $diffOutput = (git -C $ProjectRoot -c core.safecrlf=false diff HEAD -- $RelativePath 2>$null) -join "`n"
            $untrackedFiles = @(git -C $ProjectRoot ls-files --others --exclude-standard -- $RelativePath 2>$null)
            $baseDir = $ProjectRoot
        } else {
            return "no-git-" + (Get-Date -Format "yyyyMMddHHmmssfff")
        }
    } finally {
        $ErrorActionPreference = $prevEap
    }

    $untrackedBuilder = New-Object System.Text.StringBuilder
    foreach ($relFile in ($untrackedFiles | Sort-Object)) {
        if ([string]::IsNullOrWhiteSpace($relFile)) { continue }
        $fullPath = Join-Path $baseDir $relFile
        if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
            $fileHash = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash
            [void]$untrackedBuilder.AppendLine("$relFile=$fileHash")
        }
    }

    $combined = "commit:$lastCommit`ndiff:$diffOutput`nuntracked:$($untrackedBuilder.ToString())"
    return Get-StringSha256 -Text $combined
}

function Load-BuildState {
    if (-not (Test-Path -LiteralPath $StateFile)) {
        return @{}
    }
    try {
        $raw = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        $table = @{}
        if ($null -ne $raw) {
            foreach ($prop in $raw.PSObject.Properties) {
                $table[$prop.Name] = [string]$prop.Value
            }
        }
        return $table
    } catch {
        return @{}
    }
}

function Save-BuildState {
    param([hashtable]$State)
    $State | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $StateFile -Encoding ASCII
}

function Test-DockerImageExists {
    param([string]$ImageTag)
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        docker image inspect $ImageTag 2>$null | Out-Null
        return ($LASTEXITCODE -eq 0)
    } finally {
        $ErrorActionPreference = $prevEap
    }
}

Write-Host @"
========================================================================
 Local Microservices Control Center & Platform Launcher
 Mode: $TargetModeDisplay | Root: $ProjectRoot
========================================================================
"@ -ForegroundColor DarkCyan

# -----------------------------------------------------------------------------
# 1. Check Prerequisites
# -----------------------------------------------------------------------------
Write-Step "Checking local environment prerequisites..."

try {
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker daemon not responding."
    }
    Write-Success "Docker daemon is active and responsive."
} catch {
    Write-Warn "Docker daemon is not responsive. Attempting automatic WSL2 recovery..."
    try {
        & wsl.exe --shutdown | Out-Null
        Start-Sleep -Seconds 2
        & wsl.exe -d docker-desktop -e echo "WSL wake" | Out-Null
        if (Test-Path "C:\Program Files\Docker\Docker\Docker Desktop.exe") {
            Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
            Write-Host "Waiting 15 seconds for Docker Desktop to boot..." -ForegroundColor Gray
            Start-Sleep -Seconds 15
        }
        docker info 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Docker recovery failed."
        }
        Write-Success "Docker daemon successfully recovered and active."
    } catch {
        Write-Err "Docker daemon is not running! Please start Docker Desktop and retry."
        exit 1
    }
}

$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $null = java -version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Java runtime detected."
    } else {
        Write-Warn "Java not found on PATH; relying on local Maven wrapper / containers."
    }
} finally {
    $ErrorActionPreference = $prevEap
}

# -----------------------------------------------------------------------------
# 2. Per-Folder Git-Diff Change Detection
# -----------------------------------------------------------------------------
$allServices = @(
    @{ Name = "api-gateway";          Module = "services/api-gateway";          Jar = "api-gateway-0.0.1-SNAPSHOT.jar" },
    @{ Name = "order-service";        Module = "services/order-service";        Jar = "order-service-0.0.1-SNAPSHOT.jar" },
    @{ Name = "inventory-service";    Module = "services/inventory-service";    Jar = "inventory-service-0.0.1-SNAPSHOT.jar" },
    @{ Name = "notification-service"; Module = "services/notification-service"; Jar = "notification-service-0.0.1-SNAPSHOT.jar" },
    @{ Name = "payment-service";      Module = "services/payment-service";      Jar = "payment-service-0.0.1-SNAPSHOT.jar" },
    @{ Name = "product-service";      Module = "services/product-service";      Jar = "product-service-0.0.1-SNAPSHOT.jar" },
    @{ Name = "control-center";       Module = "services/control-center";       Jar = "control-center-0.0.1-SNAPSHOT.jar" }
)

$isGitRepo = $false
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $gitCheck = (git -C $ProjectRoot rev-parse --is-inside-work-tree 2>$null)
    if ($LASTEXITCODE -eq 0 -and "$gitCheck".Trim() -eq "true") {
        $isGitRepo = $true
    }
} finally {
    $ErrorActionPreference = $prevEap
}

$previousState = Load-BuildState
$currentSignatures = @{}

$dirtyMavenServices = New-Object System.Collections.ArrayList
$dirtyDockerServices = New-Object System.Collections.ArrayList
$serviceReasons = @{}

if ($SkipBuild) {
    Write-Warn "Skipping build change detection (-SkipBuild specified). Using existing JARs and Docker images."
} else {
    Write-Step "Scanning per-folder Git diffs to determine changed services..."

    $sharedPaths = @("pom.xml", "docker/Dockerfile", "common")
    $sharedChanged = $false
    $sharedChangedList = New-Object System.Collections.ArrayList

    foreach ($sp in $sharedPaths) {
        $sig = Get-FolderGitSignature -RelativePath $sp -IsGitRepo $isGitRepo
        $currentSignatures[$sp] = $sig
        if (-not $previousState.ContainsKey($sp) -or $previousState[$sp] -ne $sig) {
            $sharedChanged = $true
            [void]$sharedChangedList.Add($sp)
        }
    }

    $k8sSig = Get-FolderGitSignature -RelativePath "k8s" -IsGitRepo $isGitRepo
    $currentSignatures["k8s"] = $k8sSig
    $k8sChanged = (-not $previousState.ContainsKey("k8s") -or $previousState["k8s"] -ne $k8sSig)

    foreach ($svc in $allServices) {
        $svcPath = $svc.Module
        $sig = Get-FolderGitSignature -RelativePath $svcPath -IsGitRepo $isGitRepo
        $currentSignatures[$svcPath] = $sig

        $jarPath = Join-Path $ProjectRoot "$svcPath\target\$($svc.Jar)"
        $jarExists = Test-Path -LiteralPath $jarPath
        $imgExists = Test-DockerImageExists -ImageTag "$($svc.Name):latest"

        $reasons = New-Object System.Collections.ArrayList
        $needsMaven = $false
        $needsDocker = $false

        if ($ForceBuild) {
            [void]$reasons.Add("-ForceBuild")
            $needsMaven = $true
            $needsDocker = $true
        } else {
            if ($sharedChanged) {
                $onlyDockerfile = ($sharedChangedList.Count -eq 1 -and $sharedChangedList[0] -eq "docker/Dockerfile")
                [void]$reasons.Add("shared changed: $($sharedChangedList -join ', ')")
                if (-not $onlyDockerfile) {
                    $needsMaven = $true
                }
                $needsDocker = $true
            }
            if (-not $previousState.ContainsKey($svcPath) -or $previousState[$svcPath] -ne $sig) {
                [void]$reasons.Add("git diff in $svcPath")
                $needsMaven = $true
                $needsDocker = $true
            }
            if (-not $jarExists) {
                [void]$reasons.Add("missing JAR")
                $needsMaven = $true
                $needsDocker = $true
            }
            if ($IsK8s -and -not $imgExists) {
                [void]$reasons.Add("missing image $($svc.Name):latest")
                $needsDocker = $true
            }
        }

        if ($needsMaven) { [void]$dirtyMavenServices.Add($svc) }
        if ($needsDocker) { [void]$dirtyDockerServices.Add($svc) }

        if ($reasons.Count -gt 0) {
            $serviceReasons[$svc.Name] = $reasons -join "; "
            Write-Host ("  [REBUILD] {0,-22} ({1})" -f $svc.Name, $serviceReasons[$svc.Name]) -ForegroundColor Yellow
        } else {
            Write-Host ("  [SKIP]    {0,-22} (unchanged)" -f $svc.Name) -ForegroundColor DarkGray
        }
    }

    if ($k8sChanged) {
        Write-Host "  [APPLY]   k8s/ manifests changed" -ForegroundColor Cyan
    }
}

# -----------------------------------------------------------------------------
# 3. Targeted Parallel Maven Build (Changed Modules Only, No Clean)
# -----------------------------------------------------------------------------
if (-not $SkipBuild) {
    if ($dirtyMavenServices.Count -gt 0) {
        $javaCandidates = @()
        if ($env:JAVA_HOME) {
            $javaCandidates += Join-Path $env:JAVA_HOME "bin\java.exe"
        }
        $pathJava = Get-Command java -ErrorAction SilentlyContinue
        if ($pathJava) {
            $javaCandidates += $pathJava.Source
        }

        $selectedJava = $null
        foreach ($candidate in ($javaCandidates | Select-Object -Unique)) {
            if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
            $javaVersionOutput = (& $candidate -version 2>&1 | Out-String)
            if ($javaVersionOutput -match 'version "(\d+)' -and [int]$matches[1] -ge 25) {
                $selectedJava = $candidate
                break
            }
        }
        if (-not $selectedJava) {
            Write-Err "A Java 25 or newer JDK is required to build the services."
            exit 1
        }

        $selectedJavaHome = Split-Path -Parent (Split-Path -Parent $selectedJava)
        if (-not (Test-Path -LiteralPath (Join-Path $selectedJavaHome "bin\javac.exe") -PathType Leaf)) {
            Write-Err "The selected Java runtime does not include javac; install a Java 25 or newer JDK."
            exit 1
        }
        $env:JAVA_HOME = $selectedJavaHome

        $moduleList = ($dirtyMavenServices | ForEach-Object { $_.Module }) -join ","
        Write-Step "Packaging $($dirtyMavenServices.Count) changed module(s) in parallel (-T 1C -pl $moduleList -am)..."
        & "$ProjectRoot\mvnw.cmd" -T 1C package -DskipTests -pl $moduleList -am
        if ($LASTEXITCODE -ne 0) {
            Write-Err "Maven packaging failed. Please resolve build errors before starting."
            exit 1
        }
        Write-Success "Changed microservice JARs packaged successfully."
    } else {
        Write-Success "All microservice JARs are up to date. Skipped Maven build (0s)."
    }
}

# -----------------------------------------------------------------------------
# 4. Deploy Platform (Docker Compose or Kubernetes)
# -----------------------------------------------------------------------------
$DashboardUrl = "http://localhost:8090"

if (-not $IsK8s) {
    Write-Step "Starting infrastructure and microservices with Docker Compose..."
    if ($dirtyDockerServices.Count -gt 0 -or $ForceBuild) {
        docker compose -f "$ProjectRoot\docker\docker-compose.yml" up -d --build
    } else {
        docker compose -f "$ProjectRoot\docker\docker-compose.yml" up -d
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Err "docker compose up failed."
        exit 1
    }
    if ($dirtyDockerServices.Count -gt 0) {
        docker image prune -f | Out-Null
        docker builder prune -f --keep-storage=500MB | Out-Null
    }
    Write-Success "Docker containers launched in background."

} else {
    Write-Step "Checking Kubernetes cluster connectivity..."
    try {
        kubectl cluster-info 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Kubernetes cluster unreachable."
        }
        $nodes = kubectl get nodes --no-headers 2>&1 | Out-String
        Write-Success "Kubernetes cluster connected! Active nodes:`n$nodes"
    } catch {
        Write-Err "Unable to connect to a local Kubernetes cluster!
Please ensure Kubernetes is enabled in Docker Desktop (Settings -> Kubernetes -> Enable),
or start a local cluster via 'minikube start' or 'kind create cluster'.
To use Docker Compose instead, run:
    .\start.ps1 -Mode docker"
        exit 1
    }

    if (-not $SkipBuild -and $dirtyDockerServices.Count -gt 0) {
        Write-Step "Building $($dirtyDockerServices.Count) changed layered container image(s) in parallel (max concurrency 4)..."
        $maxParallel = 4
        $runningJobs = New-Object System.Collections.ArrayList
        $buildFailed = $false

        foreach ($svc in $dirtyDockerServices) {
            while ($runningJobs.Count -ge $maxParallel) {
                $completed = Wait-Job -Job $runningJobs -Any
                $res = Receive-Job -Job $completed
                if ($completed.State -ne "Completed" -or ($res -is [hashtable] -and $res.ExitCode -ne 0)) {
                    $buildFailed = $true
                    Write-Err "Docker build failed for $($res.Name):`n$($res.Output)"
                } else {
                    Write-Host "  [BUILT]   $($res.Name):latest ($($res.DurationSec)s)" -ForegroundColor Green
                }
                Remove-Job -Job $completed -Force
                [void]$runningJobs.Remove($completed)
            }

            $job = Start-Job -ScriptBlock {
                param($root, $svcName, $svcModule, $svcJar)
                $sw = [System.Diagnostics.Stopwatch]::StartNew()
                $out = docker build -t "${svcName}:latest" -f "$root\docker\Dockerfile" `
                    --build-arg "SERVICE_MODULE=$svcModule" `
                    --build-arg "APP_JAR=$svcJar" "$root" 2>&1 | Out-String
                $code = $LASTEXITCODE
                $sw.Stop()
                return @{
                    Name        = $svcName
                    ExitCode    = $code
                    Output      = $out
                    DurationSec = [Math]::Round($sw.Elapsed.TotalSeconds, 1)
                }
            } -ArgumentList $ProjectRoot, $svc.Name, $svc.Module, $svc.Jar
            [void]$runningJobs.Add($job)
        }

        while ($runningJobs.Count -gt 0) {
            $completed = Wait-Job -Job $runningJobs -Any
            $res = Receive-Job -Job $completed
            if ($completed.State -ne "Completed" -or ($res -is [hashtable] -and $res.ExitCode -ne 0)) {
                $buildFailed = $true
                Write-Err "Docker build failed for $($res.Name):`n$($res.Output)"
            } else {
                Write-Host "  [BUILT]   $($res.Name):latest ($($res.DurationSec)s)" -ForegroundColor Green
            }
            Remove-Job -Job $completed -Force
            [void]$runningJobs.Remove($completed)
        }

        if ($buildFailed) {
            Write-Err "One or more Docker image builds failed. Aborting deployment."
            exit 1
        }

        $isKindCluster = (docker ps -q -f name=desktop-control-plane)
        if ($isKindCluster) {
            Write-Step "Loading $($dirtyDockerServices.Count) rebuilt image(s) into desktop-control-plane..."
            foreach ($svc in $dirtyDockerServices) {
                Write-Host "  -> Loading $($svc.Name):latest..." -ForegroundColor DarkGray
                $tarFile = Join-Path $ProjectRoot "$($svc.Name).tar"
                docker save -o $tarFile "$($svc.Name):latest"
                docker cp $tarFile "desktop-control-plane:/root/$($svc.Name).tar"
                docker exec desktop-control-plane ctr -n k8s.io images import "/root/$($svc.Name).tar" | Out-Null
                docker exec desktop-control-plane rm "/root/$($svc.Name).tar" | Out-Null
                Remove-Item $tarFile -Force -ErrorAction SilentlyContinue
            }
        }

        Write-Step "Cleaning up dangling Docker images and stale build cache..."
        docker image prune -f | Out-Null
        docker builder prune -f --keep-storage=500MB | Out-Null
        Write-Success "Docker cleanup complete."
    } elseif (-not $SkipBuild) {
        Write-Success "All 7 container images are up to date. Skipped Docker builds (0s)."
    }

    Write-Step "Applying Kubernetes manifests via Kustomize..."
    kubectl apply -k "$ProjectRoot\k8s"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "kubectl apply failed."
        exit 1
    }

    if ($EnableAuth) {
        kubectl set env deployment/api-gateway SECURITY_ENABLED=true -n microservices-demo 2>$null | Out-Null
    } else {
        kubectl set env deployment/api-gateway SECURITY_ENABLED=false -n microservices-demo 2>$null | Out-Null
    }

    if (-not $SkipBuild -and $dirtyDockerServices.Count -gt 0) {
        Write-Step "Restarting only changed microservice deployment(s) ($($dirtyDockerServices.Count))..."
        foreach ($svc in $dirtyDockerServices) {
            Write-Host "  -> Rolling out $($svc.Name)..." -ForegroundColor Gray
            kubectl rollout restart "deployment/$($svc.Name)" -n microservices-demo 2>$null | Out-Null
        }
    }

    # Persist updated per-folder Git signatures right after successful build/apply
    if (-not $SkipBuild -and $currentSignatures.Count -gt 0) {
        $mergedState = @{}
        foreach ($k in $previousState.Keys) { $mergedState[$k] = $previousState[$k] }
        foreach ($k in $currentSignatures.Keys) { $mergedState[$k] = $currentSignatures[$k] }
        Save-BuildState -State $mergedState
    }

    Write-Step "Waiting for control-center rollout in namespace 'microservices-demo'..."
    kubectl rollout status deployment/control-center -n microservices-demo --timeout=90s

    Write-Step "Configuring local development port-forwarding for all microservices & infrastructure..."
    & "$ProjectRoot\scripts\open-k8s-ports.ps1" -Namespace "microservices-demo"

    $DashboardUrl = "http://localhost:8090"
    Write-Success "All local Kubernetes service ports mapped to localhost."
}

# Persist build state for Docker Compose mode as well
if (-not $IsK8s -and -not $SkipBuild -and $currentSignatures.Count -gt 0) {
    $mergedState = @{}
    foreach ($k in $previousState.Keys) { $mergedState[$k] = $previousState[$k] }
    foreach ($k in $currentSignatures.Keys) { $mergedState[$k] = $currentSignatures[$k] }
    Save-BuildState -State $mergedState
}

# -----------------------------------------------------------------------------
# 5. Wait for Control Center Dashboard Health
# -----------------------------------------------------------------------------
Write-Step "Waiting for Control Center & Dashboard to become ready at $DashboardUrl..."
$maxAttempts = 30
$attempt = 0
$isHealthy = $false

while ($attempt -lt $maxAttempts -and -not $isHealthy) {
    $attempt++
    try {
        $response = Invoke-WebRequest -Uri "$DashboardUrl/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            $isHealthy = $true
            break
        }
    } catch {
        # Retry
    }
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 2
}
Write-Host ""

if ($isHealthy) {
    Write-Success "Control Center is HEALTHY and ready!"
} else {
    Write-Warn "Control Center health probe did not return 200 within timeout. Services may still be initializing."
}

# -----------------------------------------------------------------------------
# 6. Service Directory Summary & Dashboard Launch
# -----------------------------------------------------------------------------
Write-Host @"

========================================================================
 PLATFORM RUNNING - SERVICE ENDPOINTS & ACCESS ($TargetModeDisplay)
========================================================================
 Control Center Dashboard: $DashboardUrl (and http://localhost:30090 in K8s)
 API Gateway:              http://localhost:8080 (NodePort: 30080 in K8s)
 Order Service:            http://localhost:8081
 Inventory Service:        http://localhost:8082
 Notification Service:     http://localhost:8083
 Payment Service:          http://localhost:8084
 Product Service:          http://localhost:8085
 Keycloak IAM:             http://localhost:8088 (NodePort: 30088 in K8s)
 LocalStack AWS Emulator:  http://localhost:4566
 Apache Kafka:             localhost:9092 (UI: http://localhost:8093)
 Redis Cache:              localhost:6379 (UI: http://localhost:8092)
 PostgreSQL Databases:     Order (5432), Product (5433), Payment (5434) (UI: http://localhost:8091)
========================================================================
"@ -ForegroundColor Green

if (-not $NoBrowser) {
    Write-Step "Opening Control Center Dashboard in your default browser..."
    Start-Process $DashboardUrl
}

Write-Success "Platform startup complete."
