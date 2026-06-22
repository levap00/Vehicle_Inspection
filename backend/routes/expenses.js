const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { db } = require('../database');

const router = express.Router();
const EXP_DIR = path.join(__dirname, '..', 'uploads', 'expenses');

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    fs.mkdirSync(EXP_DIR, { recursive: true });
    cb(null, EXP_DIR);
  },
  filename: (req, file, cb) => cb(null, `${Date.now()}_${file.fieldname}${path.extname(file.originalname) || '.jpg'}`)
});
const upload = multer({ storage, limits: { fileSize: 30 * 1024 * 1024 } });

router.post('/', upload.single('photo'), (req, res) => {
  try {
    const payloadStr = req.body.payload;
    const payload = payloadStr ? JSON.parse(payloadStr) : req.body;
    const clientId = payload.client_id || `exp_${Date.now()}`;

    const dup = db.prepare('SELECT id FROM expenses WHERE client_id = ?').get(clientId);
    if (dup) return res.json({ id: dup.id, duplicate: true });

    const r = db.prepare(`
      INSERT INTO expenses (client_id, driver_id, driver_name, registration, type, amount, currency,
        description, purpose, payment_method, payment_card, timestamp_ms, photo_filename, photo_dir)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    `).run(
      clientId,
      req.driver?.id || null,
      req.driver?.name || payload.driver_name || '',
      payload.registration || '',
      payload.type || 'other',
      payload.amount || '',
      payload.currency || '',
      payload.description || '',
      payload.purpose || payload.description || '',
      payload.payment_method || '',
      payload.payment_card || '',
      payload.timestamp_ms || Date.now(),
      req.file?.filename || null,
      'expenses'
    );
    res.json({ id: r.lastInsertRowid });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.get('/', (req, res) => {
  const rows = db.prepare(`
    SELECT e.*, d.name as dn FROM expenses e
    LEFT JOIN drivers d ON d.id = e.driver_id
    ORDER BY e.created_at DESC LIMIT 300
  `).all();

  res.json(rows.map(e => ({
    ...e,
    driver_name: e.dn || e.driver_name,
    photo_url: e.photo_filename
      ? `/uploads/${e.photo_dir || 'expenses'}/${e.photo_filename}`
      : null
  })));
});

module.exports = router;
