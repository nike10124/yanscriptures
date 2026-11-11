# 用本机已保存的 GitHub 凭据创建 Release 并上传 jar（不打印令牌）
$ErrorActionPreference = "Stop"
$repo = "nike10124/yanscriptures"
$tag = "v0.1.0"
$jar = "D:\MCModDev\forge-mdk\build\libs\yanscriptures-0.1.0.jar"

$out = "protocol=https`nhost=github.com`n`n" | & "C:\Program Files\Git\cmd\git.exe" credential fill
$tok = ($out -split "`n" | Where-Object { $_ -match "^password=" }) -replace "^password=", ""
if (-not $tok) { throw "no credential" }

$headers = @{
    Authorization          = "token $tok"
    Accept                 = "application/vnd.github+json"
    "User-Agent"           = "dsh-release-script"
    "X-GitHub-Api-Version" = "2022-11-28"
}

$notes = Get-Content "D:\MCModDev\forge-mdk\changelog.txt" -Raw -Encoding utf8
$body = @{
    tag_name = $tag
    name     = "0.1.0 初版"
    body     = $notes
    draft    = $false
    prerelease = $false
} | ConvertTo-Json -Depth 4

$rel = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Method Post -Headers $headers -Body $body -ContentType "application/json; charset=utf-8"
Write-Output ("release created: id=" + $rel.id + " url=" + $rel.html_url)

$up = Invoke-RestMethod -Uri ("https://uploads.github.com/repos/$repo/releases/" + $rel.id + "/assets?name=" + [IO.Path]::GetFileName($jar)) `
    -Method Post -Headers $headers -ContentType "application/java-archive" -InFile $jar
Write-Output ("asset uploaded: " + $up.name + " " + $up.size + " bytes")
