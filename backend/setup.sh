#!/bin/bash
set -e

echo "=== Vehicle Inspection Backend – Setup ==="

# Node.js (jeśli brak)
if ! command -v node &>/dev/null; then
  echo "Instaluję Node.js 20..."
  curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
  sudo apt-get install -y nodejs
fi

echo "Node: $(node -v), npm: $(npm -v)"

# PM2
if ! command -v pm2 &>/dev/null; then
  echo "Instaluję PM2..."
  sudo npm install -g pm2
fi

# Zależności
npm install

# .env
if [ ! -f .env ]; then
  cp .env.example .env
  # Wygeneruj losowy JWT secret
  SECRET=$(node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
  sed -i "s/zmien_mnie_na_dlugi_losowy_ciag_znakow_abc123/$SECRET/" .env
  echo "✅ .env utworzony z losowym JWT_SECRET"
  echo "⚠️  Zmień ADMIN_PASS i ADMIN_KEY w pliku .env!"
fi

# Katalogi uploadów
mkdir -p uploads/inspections uploads/expenses

# Uruchom przez PM2
pm2 start server.js --name vi-backend || pm2 restart vi-backend
pm2 save
pm2 startup 2>/dev/null | tail -1 | bash 2>/dev/null || true

echo ""
echo "=== Gotowe! ==="
echo "  Serwer: http://localhost:3000/"
echo "  Dashboard: http://localhost:3000/dashboard/"
echo "  Login: admin / Admin1234!  (zmień w .env)"
echo ""
echo "  Aby dodać kierowcę:"
echo "  curl -X POST http://localhost:3000/api/auth/register \\"
echo "    -H 'X-Admin-Key: ADMIN_SECRET_CHANGE_ME' \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"username\":\"jan\",\"password\":\"Haslo123!\",\"name\":\"Jan Kowalski\"}'"
