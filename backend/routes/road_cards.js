const express = require('express');
const { db } = require('../database');
const router = express.Router();

router.post('/', (req, res) => {
  try {
    const payload = req.body;
    const driverId = req.driver?.id || null;
    const driverName = req.driver?.name || payload.driver_name || '';
    const rc = payload.road_card || {};

    const r = db.prepare(`
      INSERT INTO road_cards (driver_id, driver_name, registration, card_number, trailer_registration, fuel, timestamp_ms,
        odometer_start, odometer_end, departure_datetime, return_datetime, total_distance_km, loaded_km_total,
        empty_km_total, fuel_start, fuel_end, points_json)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    `).run(
      driverId,
      driverName,
      payload.registration || rc.tractor_registration || '',
      rc.card_number || '',
      rc.trailer_registration || payload.trailer_registration || '',
      payload.fuel || '',
      payload.timestamp_ms || Date.now(),
      rc.odometer_start || '',
      rc.odometer_end || '',
      rc.departure_datetime || '',
      rc.return_datetime || '',
      rc.total_distance_km || '',
      rc.loaded_km_total || '',
      rc.empty_km_total || '',
      rc.fuel_start || '',
      rc.fuel_end || '',
      JSON.stringify(rc.points || [])
    );
    res.json({ id: r.lastInsertRowid });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.get('/', (req, res) => {
  const rows = db.prepare(`
    SELECT rc.*, d.name as dn FROM road_cards rc
    LEFT JOIN drivers d ON d.id = rc.driver_id
    ORDER BY rc.created_at DESC LIMIT 300
  `).all();

  res.json(rows.map(r => ({
    ...r,
    driver_name: r.dn || r.driver_name,
    points: jp(r.points_json, [])
  })));
});

function jp(s, fb) { try { return JSON.parse(s || ''); } catch { return fb; } }

module.exports = router;
