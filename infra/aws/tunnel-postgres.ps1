param(
    [Parameter(Mandatory = $true)]
    [string]$BastionKeyPath,

    [string]$BastionUser = "lumen102",
    [string]$BastionHost = "15.164.101.122",
    [int]$LocalPort = 15432
)

ssh -i $BastionKeyPath -N -L "${LocalPort}:rds-postgres-stg.ssafyapp.com:5432" "${BastionUser}@${BastionHost}"
