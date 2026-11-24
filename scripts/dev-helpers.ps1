# Helpers for manual API testing in Windows PowerShell 5.1.
# Load once per terminal tab (note the leading dot and space):
#     . .\scripts\dev-helpers.ps1
# Contains no secrets: passwords are asked for on screen.

$global:ApiBase = "http://localhost:8088"

# Prints "<status code> <body>" for any response, including 4xx and 5xx errors.
#   Call-Api GET /api/products
#   Call-Api POST /api/orders '{"shippingAddress":"x","items":[]}' $token
function Call-Api {
    param($Method, $Path, $Body = $null, $Token = $null)
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    try {
        $r = Invoke-WebRequest -Method $Method -Uri "$global:ApiBase$Path" -Headers $headers `
            -ContentType "application/json" -Body $Body -UseBasicParsing
        "$([int]$r.StatusCode) $($r.Content)"
    } catch {
        "$([int]$_.Exception.Response.StatusCode) $($_.ErrorDetails.Message)"
    }
}

# Logs in and returns the JWT. Asks for the password on screen unless -Password is given.
#   $a = Get-Token admin@clickandtap.com
#   $t = Get-Token test1@example.com password123
function Get-Token {
    param([Parameter(Mandatory)][string]$Email, [string]$Password)
    if (-not $Password) {
        $secure = Read-Host "Password for $Email" -AsSecureString
        $Password = [System.Net.NetworkCredential]::new("", $secure).Password
    }
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$global:ApiBase/api/auth/login" -ContentType "application/json" -Body $body).token
}

# Creates the throwaway customer used in the manual tests (ignores the error if it already exists).
function New-TestCustomer {
    param([string]$Email = "test1@example.com", [string]$Password = "password123")
    $body = @{ name = "Test"; email = $Email; password = $Password } | ConvertTo-Json
    try {
        Invoke-RestMethod -Method Post -Uri "$global:ApiBase/api/auth/register" -ContentType "application/json" -Body $body | Out-Null
    } catch { }
}

function Get-StockOf {
    param([Parameter(Mandatory)]$ProductId)
    (Invoke-RestMethod "$global:ApiBase/api/products/$ProductId").stock
}

# Fires $Count orders of 1 unit at the same time and prints how many got each status code.
#   Test-ConcurrentOrders -Token $t -ProductId 1 -Count 10
function Test-ConcurrentOrders {
    param([Parameter(Mandatory)]$Token, $ProductId = 1, [int]$Count = 10)
    $jobs = 1..$Count | ForEach-Object {
        Start-Job -ArgumentList $Token, $ProductId, $global:ApiBase -ScriptBlock {
            param($tok, $prodId, $base)
            $body = '{"shippingAddress":"Calle 1","items":[{"productId":' + $prodId + ',"quantity":1}]}'
            try {
                (Invoke-WebRequest -Method Post -Uri "$base/api/orders" -Headers @{ Authorization = "Bearer $tok" } `
                    -ContentType "application/json" -Body $body -UseBasicParsing).StatusCode
            } catch {
                [int]$_.Exception.Response.StatusCode
            }
        }
    }
    $jobs | Wait-Job | Receive-Job | Group-Object | Select-Object Name, Count
    $jobs | Remove-Job
}
