param(
    [Parameter(Mandatory = $true)]
    [string]$BastionKeyPath,

    [string]$BastionUser = "lumen102",
    [string]$BastionHost = "15.164.101.122",
    [int]$LocalPort = 13306
)

ssh -i $BastionKeyPath -N -L "${LocalPort}:rds-stg.ssafyapp.com:3306" "${BastionUser}@${BastionHost}"
