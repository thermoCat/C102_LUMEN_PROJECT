param(
    [Parameter(Mandatory = $true)]
    [string]$BastionKeyPath,

    [string]$BastionUser = "lumen102",
    [string]$BastionHost = "15.164.101.122",
    [int]$LocalPort = 16379
)

ssh -i $BastionKeyPath -N -L "${LocalPort}:redis-stg.ssafyapp.com:6379" "${BastionUser}@${BastionHost}"
