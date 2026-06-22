const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { db } = require('../database');

const router = express.Router();
const INSP_DIR = path.join(__dirname, '..', 'uploads', 'inspections');

const storage = multer.diskStorage({
  destination: (req, file, cb) => { fs.mkdirSync(INSP_DIR, { recursive: true }); cb(null, INSP_DIR); },
  filename: (req, file, cb) => cb(null, `${Date.now()}_${file.fieldname}${path.extname(file.originalname) || '.jpg'}`)
});
const upload = multer({ storage, limits: { fileSize: 30 * 1024 * 1024 } });

const PHOTO_FIELDS = ['photo_left','photo_front','photo_right','photo_back','photo_inside','receipt_photo','photo']
  .map(n => ({ name: n, maxCount: 1 }));

router.post('/', upload.fields(PHOTO_FIELDS), (req, res) => {
  try {
    const payloadStr = req.body.payload;
    if (!payloadStr) return res.status(400).json({ error: 'Brak pola payload' });

    const payload = JSON.parse(payloadStr);
    const clientId = payload.client_id || `auto_${Date.now()}`;
    const driverId = req.driver?.id || null;
    const driverName = req.driver?.name || payload.driver_name || '';

    const dup = db.prepare('SELECT id FROM inspections WHERE client_id = ?').get(clientId);
    if (dup) return res.json({ id: dup.id, duplicate: true });

    // Paragony i wydatki → tabela expenses
    if (payload.type === 'receipt' || payload.type === 'expense') {
      const photoField = payload.type === 'expense' ? 'photo' : 'receipt_photo';
      const photo = req.files?.[photoField]?.[0];
      const r = db.prepare(`
        INSERT INTO expenses (client_id, driver_id, driver_name, registration, type, amount, currency, description,
          purpose, payment_method, payment_card, timestamp_ms, photo_filename, photo_dir)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
      `).run(
        clientId, driverId, driverName,
        payload.registration || '',
        payload.type, payload.amount || '', payload.currency || '',
        payload.description || '',
        payload.purpose || payload.description || '',
        payload.payment_method || '',
        payload.payment_card || '',
        payload.timestamp_ms || Date.now(),
        photo?.filename || null, 'inspections'
      );
      return res.json({ id: r.lastInsertRowid });
    }

    const r = db.prepare(`
      INSERT INTO inspections (client_id, driver_id, driver_name, registration, trailer_registration, fuel, timestamp_ms, checklist_json, inspection_json, road_card_json)
      VALUES (?,?,?,?,?,?,?,?,?,?)
    `).run(
      clientId, driverId, driverName,
      payload.registration || '', payload.trailer_registration || '', payload.fuel || '',
      payload.timestamp_ms || Date.now(),
      JSON.stringify(payload.checklist || []),
      JSON.stringify(payload.inspection || {}),
      JSON.stringify(payload.road_card || {})
    );
    const inspId = r.lastInsertRowid;

    for (const field of ['photo_left','photo_front','photo_right','photo_back','photo_inside']) {
      const file = req.files?.[field]?.[0];
      if (file) db.prepare('INSERT INTO inspection_photos (inspection_id, field, filename) VALUES (?,?,?)').run(inspId, field, file.filename);
    }

    res.json({ id: inspId });
  } catch (e) {
    console.error('POST /api/inspections', e);
    res.status(500).json({ error: e.message });
  }
});

router.get('/', (req, res) => {
  const rows = db.prepare(`
    SELECT i.*, d.name as dn FROM inspections i
    LEFT JOIN drivers d ON d.id = i.driver_id
    ORDER BY i.created_at DESC LIMIT 300
  `).all();

  res.json(rows.map(r => ({
    ...r,
    driver_name: r.dn || r.driver_name,
    checklist:  jp(r.checklist_json, []),
    inspection: jp(r.inspection_json, {}),
    road_card:  jp(r.road_card_json, {}),
    photos: db.prepare('SELECT field, filename FROM inspection_photos WHERE inspection_id = ?').all(r.id)
      .map(p => ({ field: p.field, url: `/uploads/inspections/${p.filename}` }))
  })));
});

router.get('/:id', (req, res) => {
  const r = db.prepare('SELECT * FROM inspections WHERE id = ?').get(req.params.id);
  if (!r) return res.status(404).json({ error: 'Nie znaleziono' });
  const photos = db.prepare('SELECT field, filename FROM inspection_photos WHERE inspection_id = ?').all(r.id);
  res.json({
    ...r,
    checklist:  jp(r.checklist_json, []),
    inspection: jp(r.inspection_json, {}),
    road_card:  jp(r.road_card_json, {}),
    photos: photos.map(p => ({ field: p.field, url: `/uploads/inspections/${p.filename}` }))
  });
});

function jp(s, fb) { try { return JSON.parse(s || ''); } catch { return fb; } }

module.exports = router;
