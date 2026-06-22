const express = require('express');
const { db } = require('../database');
const router = express.Router();

router.get('/', (req, res) => {
  res.json(
    db.prepare('SELECT id as vehicle_id, registration as plate, brand, fuel FROM vehicles ORDER BY registration').all()
  );
});

router.post('/', (req, res) => {
  const { registration, brand, fuel } = req.body;
  if (!registration) return res.status(400).json({ error: 'Brak rejestracji' });
  try {
    const r = db.prepare('INSERT OR IGNORE INTO vehicles (registration, brand, fuel) VALUES (?,?,?)').run(
      registration.trim().toUpperCase(), brand || '', (fuel || 'ON').toUpperCase()
    );
    res.json({ id: r.lastInsertRowid || null });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.delete('/:id', (req, res) => {
  db.prepare('DELETE FROM vehicles WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

// Aktualna trasa pojazdu
router.get('/:id/route/active/events', (req, res) => {
  const order = db.prepare(
    'SELECT * FROM orders WHERE vehicle_id = ? ORDER BY created_at DESC LIMIT 1'
  ).get(req.params.id);
  if (!order) return res.json({ route_number: '', events: [] });
  res.json({ route_number: order.route_number || '', events: jp(order.events_json, []) });
});

router.post('/:id/refresh', (req, res) => res.json({ ok: true }));

// Przypisz trasę do pojazdu (dashboard admin)
router.post('/:id/orders', (req, res) => {
  const { route_number, events } = req.body;
  db.prepare('INSERT INTO orders (vehicle_id, route_number, events_json) VALUES (?,?,?)').run(
    Number(req.params.id), route_number || '', JSON.stringify(events || [])
  );
  res.json({ ok: true });
});

// Lista wszystkich tras
router.get('/:id/orders', (req, res) => {
  res.json(
    db.prepare('SELECT * FROM orders WHERE vehicle_id = ? ORDER BY created_at DESC').all(req.params.id)
      .map(o => ({ ...o, events: jp(o.events_json, []) }))
  );
});

function jp(s, fb) { try { return JSON.parse(s || ''); } catch { return fb; } }

module.exports = router;
