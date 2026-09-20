param(
    [string]$HostName = '127.0.0.1',
    [int]$Port = 25576,
    [string]$Password = 'mineai',
    [string[]]$Commands
)

function Read-Exact {
    param($Stream, [byte[]]$Buffer, [int]$Count)
    $offset = 0
    while ($offset -lt $Count) {
        $read = $Stream.Read($Buffer, $offset, $Count - $offset)
        if ($read -le 0) { throw "Connection closed while reading" }
        $offset += $read
    }
}

function Send-RconPacket {
    param($Stream, [int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    $length = 4 + 4 + $bodyBytes.Length + 2
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    $bw.Write([int]$length)
    $bw.Write([int]$Id)
    $bw.Write([int]$Type)
    $bw.Write($bodyBytes)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Flush()
    $bytes = $ms.ToArray()
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()
}

function Read-RconPacket {
    param($Stream)
    $lenBuf = New-Object byte[] 4
    Read-Exact $Stream $lenBuf 4
    $length = [System.BitConverter]::ToInt32($lenBuf, 0)
    $buf = New-Object byte[] $length
    Read-Exact $Stream $buf $length
    $id = [System.BitConverter]::ToInt32($buf, 0)
    $type = [System.BitConverter]::ToInt32($buf, 4)
    $bodyLen = $length - 10
    $body = if ($bodyLen -gt 0) { [System.Text.Encoding]::ASCII.GetString($buf, 8, $bodyLen) } else { '' }
    return [pscustomobject]@{ Id = $id; Type = $type; Body = $body }
}

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($HostName, $Port)
$stream = $client.GetStream()
$stream.ReadTimeout = 20000

Send-RconPacket $stream 1 3 $Password
$auth = Read-RconPacket $stream
if ($auth.Type -ne 2) {
    Write-Output "AUTH_FAILED type=$($auth.Type)"
    $client.Close()
    exit 1
}
Write-Output "AUTH_OK"

$requestId = 2
foreach ($cmd in $Commands) {
    Send-RconPacket $stream $requestId 2 $cmd
    $response = Read-RconPacket $stream
    Write-Output ">>> $cmd"
    Write-Output $response.Body
    $requestId++
}

$client.Close()
