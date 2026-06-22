const express = require('express');
const bcrypt = require('bcryptjs');
const { db } = require('../database');
const router = express.Router();

function requireAdmin(req, res, next) {
  if (req.driver?.role !== 'admin') return res.status(403).json({ error: 'Wymagane uprawnienia admina' });
  next();
}

router.get('/', requireAdmin, (req, res) => {
  res.json(db.prepare('SELECT id, username, name, role, created_at FROM drivers ORDER BY name').all());
});

router.post('/', requireAdmin, (req, res) => {
  const { username, password, name, role } = req.body;
  if (!username || !password || !name) return res.status(400).json({ error: 'username, password i name są wymagane' });
  try {
    const hash = bcrypt.hashSync(password, 10);
    const r = db.prepare('INSERT INTO drivers (username, password_hash, name, role) VALUES (?,?,?,?)').run(
      username.trim().toLowerCase(), hash, name, role === 'admin' ? 'admin' : 'driver'
    );
    res.json({ id: r.lastInsertRowid, username: username.trim().toLowerCase(), name });
  } catch (e) {
    if (e.message.includes('UNIQUE')) return res.status(409).json({ error: 'Login już istnieje' });
    res.status(500).json({ error: e.message });
  }
});

router.put('/:id/password', requireAdmin, (req, res) => {
  const { password } = req.body;
  if (!password || password.length < 6) return res.status(400).json({ error: 'Hasło min. 6 znaków' });
  const hash = bcrypt.hashSync(password, 10);
  db.prepare('UPDATE drivers SET password_hash = ? WHERE id = ?').run(hash, req.params.id);
  res.json({ ok: true });
});

router.delete('/:id', requireAdmin, (req, res) => {
  if (Number(req.params.id) === req.driver.id) return res.status(400).json({ error: 'Nie możesz usunąć siebie' });
  db.prepare('DELETE FROM drivers WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

module.exports = router;
