#!/bin/bash

# Ensure gateway is running
echo "Checking if gateway is reachable..."
if ! curl -s -f http://localhost:8080/actuator/health > /dev/null; then
    echo "Gateway is not reachable at http://localhost:8080. Start it first with 'docker compose up'."
    exit 1
fi

echo "Triggering seed..."
curl -X POST -H "Authorization: Bearer admin-secret-key" -v http://localhost:8080/admin/seed

echo "Seed complete. Open http://localhost:8080/dashboard to view the operator console."
