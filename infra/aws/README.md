# SmartCane AWS Tunnels

This directory contains tunnel and relay helpers for AWS managed services used by
the SmartCane backend.

## Local developer tunnels

These PowerShell scripts open SSH local-forwards from a developer PC to the
legacy bastion host:

- `tunnel-postgres.ps1`
- `tunnel-mysql.ps1`
- `tunnel-redis.ps1`

They are useful for local testing, but Kubernetes pods cannot use a tunnel that
only exists on a developer laptop.

## Shared relay for k3s workers

In the current infrastructure, only node A can reach PostgreSQL, MySQL, and
Redis directly. To let backend pods on worker nodes B/C reuse that route, run a
shared TCP relay on node A and point the backend config at node A's Tailscale IP.

Files for that flow:

- `shared-egress-relays.sh`
- `shared-egress-relays.service.example`

Default shared relay ports on node A:

- PostgreSQL: `15432`
- MySQL: `13306`
- Redis: `16379`

Default node A Tailscale IP used by the backend manifests:

- `100.85.219.60`

## Example rollout

1. Copy `shared-egress-relays.sh` and `shared-egress-relays.service.example` to node A.
2. Install `socat` on node A.
3. Start the systemd service.
4. Apply the backend ConfigMap so pods use node A as the managed-service gateway.

## Important note about S3

This relay setup covers PostgreSQL, MySQL, and Redis only.

If worker nodes also cannot reach S3 directly, upload/download paths may still
need one of these follow-up options:

- pin S3-using backend workloads to node A
- add an HTTP/HTTPS proxy on node A
- introduce a proper egress/NAT path for worker nodes
