# Создаёт ключ подписи релизов CarScan и keystore.properties. Оба файла в .gitignore и в git не попадают.
# Запускать один раз: .\tools\create_release_key.ps1
# Потеряете ключ — обновления с GitHub перестанут вставать поверх установленной версии.
param([string]$Alias = "carscan")

$root = Split-Path $PSScriptRoot -Parent
$jks = Join-Path $root "carscan-release.jks"
if (Test-Path $jks) {
    Write-Error "Ключ уже существует: $jks. Новый создавать нельзя — иначе обновления не встанут поверх."
    exit 1
}

# keytool из Android Studio (встроенный JDK) или из JAVA_HOME.
# Папку студии берём из реестра: её могут установить на любой диск (например, G:\Android Studio)
$studioDirs = Get-ItemProperty `
    HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*, `
    HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*, `
    HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\* -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -like "*Android Studio*" -and $_.DisplayIcon } |
    ForEach-Object { Split-Path (Split-Path ($_.DisplayIcon -replace '"|,.*$', '') -Parent) -Parent }
$candidates = @($studioDirs | ForEach-Object { "$_\jbr\bin\keytool.exe" }) + @(
    "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
    "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe",
    "$env:JAVA_HOME\bin\keytool.exe"
)
$keytool = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $keytool) {
    Write-Error "Не найден keytool. Установите Android Studio или задайте JAVA_HOME."
    exit 1
}

$bytes = New-Object byte[] 24
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = ([Convert]::ToBase64String($bytes)) -replace '[+/=]', 'x'

& $keytool -genkeypair -keystore $jks -alias $Alias -keyalg RSA -keysize 4096 -validity 36500 `
    -storepass $password -keypass $password -dname "CN=CarScan, O=CarScan"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

@(
    "storeFile=carscan-release.jks",
    "storePassword=$password",
    "keyAlias=$Alias",
    "keyPassword=$password"
) | Set-Content -Encoding ascii (Join-Path $root "keystore.properties")

[Convert]::ToBase64String([IO.File]::ReadAllBytes($jks)) | Set-Content -Encoding ascii "$jks.base64.txt"

Write-Host ""
Write-Host "Готово. GitHub → репозиторий → Settings → Secrets and variables → Actions → New repository secret:"
Write-Host "  CARSCAN_KEYSTORE_BASE64   = всё содержимое файла $jks.base64.txt"
Write-Host "  CARSCAN_KEYSTORE_PASSWORD = $password"
Write-Host "  CARSCAN_KEY_ALIAS         = $Alias"
Write-Host "  CARSCAN_KEY_PASSWORD      = $password"
Write-Host ""
Write-Host "Сохраните копию carscan-release.jks и keystore.properties в надёжном месте (не в git)."
