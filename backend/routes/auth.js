const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { db } = require('../database');

const router = express.Router();
const JWT_SECRET = process.env.JWT_SECRET || 'vi_jwt_secret_change_me';

router.post('/login', (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Podaj login i hasło' });
  }

  const driver = db.prepare('SELECT * FROM drivers WHERE username = ?').get(username.trim().toLowerCase());
  if (!driver || !bcrypt.compareSync(password, driver.password_hash)) {
    return res.status(401).json({ error: 'Nieprawidłowy login lub hasło' });
  }

  const token = jwt.sign(
    { id: driver.id, username: driver.username, name: driver.name, role: driver.role },
    JWT_SECRET,
    { expiresIn: '30d' }
  );

  res.json({ token, driver_name: driver.name, driver_id: driver.id, role: driver.role });
});

// Tworzenie konta przez admina (klucz X-Admin-Key lub JWT admina)
router.post('/register', (req, res) => {
  const adminKey = req.headers['x-admin-key'];
  const authHeader = req.headers.authorization;

  let isAuthorized = adminKey && adminKey === (process.env.ADMIN_KEY || 'ADMIN_SECRET_CHANGE_ME');

  if (!isAuthorized && authHeader && authHeader.startsWith('Bearer ')) {
    try {
      const payload = jwt.verify(authHeader.slice(7), JWT_SECRET);
      isAuthorized = payload.role === 'admin';
    } catch {}
  }

  if (!isAuthorized) return res.status(403).json({ error: 'Brak uprawnień' });

  const { username, password, name, role } = req.body;
  if (!username || !password || !name) {
    return res.status(400).json({ error: 'Wymagane pola: username, password, name' });
  }

  try {
    const hash = bcrypt.hashSync(password, 10);
    const r = db.prepare(
      'INSERT INTO drivers (username, password_hash, name, role) VALUES (?,?,?,?)'
    ).run(username.trim().toLowerCase(), hash, name, role === 'admin' ? 'admin' : 'driver');
    res.json({ id: r.lastInsertRowid, username: username.trim().toLowerCase(), name });
  } catch (e) {
    if (e.message.includes('UNIQUE')) return res.status(409).json({ error: 'Login już istnieje' });
    res.status(500).json({ error: e.message });
  }
});

module.exports = router;
