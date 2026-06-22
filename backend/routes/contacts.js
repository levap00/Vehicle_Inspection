const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { db } = require('../database');

const router = express.Router();
const CONTACTS_DIR = path.join(__dirname, '..', 'uploads', 'contacts');

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    fs.mkdirSync(CONTACTS_DIR, { recursive: true });
    cb(null, CONTACTS_DIR);
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname) || '.jpg';
    cb(null, `${Date.now()}_contact_${req.params.id || 'new'}${ext}`);
  }
});
const upload = multer({ storage, limits: { fileSize: 10 * 1024 * 1024 } });

router.get('/', (req, res) => {
  res.json(db.prepare('SELECT * FROM contacts ORDER BY role, name').all());
});

router.post('/', (req, res) => {
  const { name, role, phone, email, photo_url } = req.body;
  if (!name) return res.status(400).json({ error: 'Brak pola name' });
  const r = db.prepare('INSERT INTO contacts (name, role, phone, email, photo_url) VALUES (?,?,?,?,?)')
    .run(name, role || '', phone || '', email || '', photo_url || '');
  res.json({
    id: r.lastInsertRowid,
    name,
    role: role || '',
    phone: phone || '',
    email: email || '',
    photo_url: photo_url || ''
  });
});

router.put('/:id', (req, res) => {
  const { name, role, phone, email, photo_url } = req.body;
  db.prepare('UPDATE contacts SET name=?, role=?, phone=?, email=?, photo_url=? WHERE id=?')
    .run(name, role || '', phone || '', email || '', photo_url || '', req.params.id);
  res.json({ ok: true });
});

router.post('/:id/photo', upload.single('photo'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'Brak pliku photo' });
  const photoUrl = `/uploads/contacts/${req.file.filename}`;
  db.prepare('UPDATE contacts SET photo_url=? WHERE id=?').run(photoUrl, req.params.id);
  res.json({ ok: true, photo_url: photoUrl });
});

router.delete('/:id', (req, res) => {
  db.prepare('DELETE FROM contacts WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

module.exports = router;
