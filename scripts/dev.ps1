param(
    [ValidateSet('check', 'desktop', 'ui', 'build-desktop', 'build-backend', 'backend')]
    [string]$Action = 'check'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$wrapper = Join-Path $projectRoot 'kotlin.bat'

Push-Location $projectRoot
try {
    switch ($Action) {
        'check' {
            & $wrapper --version
            if ($LASTEXITCODE -ne 0) { throw 'Kotlin toolchain check failed.' }
            foreach ($command in @('git', 'node')) {
                if (!(Get-Command $command -ErrorAction SilentlyContinue)) {
                    throw "Missing command: $command"
                }
                & $command --version
                if ($LASTEXITCODE -ne 0) { throw "$command check failed." }
            }
            if (!(Get-Command pnpm -ErrorAction SilentlyContinue)) { throw 'Missing command: pnpm' }
            Push-Location 'backend/ui'
            try {
                & pnpm --version
                if ($LASTEXITCODE -ne 0) { throw 'pnpm check failed.' }
            } finally { Pop-Location }
            if (!(Test-Path 'backend/ui/node_modules/.modules.yaml')) {
                throw 'Frontend dependencies missing. Run pnpm install --frozen-lockfile in backend/ui.'
            }
            Write-Host 'Desktop and frontend tools are ready. Database connectivity is checked separately.'
        }
        'desktop' {
            & $wrapper run -m jvm-app -- --home (Join-Path $projectRoot '.local/harness')
            if ($LASTEXITCODE -ne 0) { throw 'Desktop startup failed.' }
        }
        'ui' {
            Push-Location 'backend/ui'
            try {
                & pnpm dev:antdv-next -- --host 127.0.0.1
                if ($LASTEXITCODE -ne 0) { throw 'Frontend startup failed.' }
            } finally { Pop-Location }
        }
        'build-desktop' {
            & $wrapper build -m jvm-app
            if ($LASTEXITCODE -ne 0) { throw 'Desktop build failed.' }
        }
        'build-backend' {
            & $wrapper build -m server
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
        }
        'backend' {
            $configPath = Join-Path $projectRoot '.local/backend.env.json'
            if (!(Test-Path $configPath)) {
                throw 'Configure .local/backend.env.json using scripts/backend.env.example.json first.'
            }
            $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
            foreach ($required in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'REDIS_HOST')) {
                $value = $config.$required
                if ([string]::IsNullOrWhiteSpace($value) -or $value -match 'REPLACE_ME') {
                    throw "Configure $required in .local/backend.env.json."
                }
            }
            $allowed = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'REDIS_HOST', 'REDIS_PORT',
                'REDIS_DATABASE', 'REDIS_PASSWORD', 'SERVER_PORT', 'SPRING_PROFILES_ACTIVE')
            $previous = @{}
            try {
                foreach ($property in $config.PSObject.Properties) {
                    if ($property.Name -notin $allowed) { throw "Unsupported setting: $($property.Name)" }
                    $previous[$property.Name] = [Environment]::GetEnvironmentVariable($property.Name, 'Process')
                    [Environment]::SetEnvironmentVariable($property.Name, [string]$property.Value, 'Process')
                }
                & $wrapper run -m server
                if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed.' }
            } finally {
                foreach ($name in $previous.Keys) {
                    [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
                }
            }
        }
    }
} finally { Pop-Location }
