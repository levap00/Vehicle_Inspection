require('dotenv').config({ path: require('path').join(__dirname, '.env') });

const express = require('express');
const path = require('path');
const fs = require('fs');
const { initDb } = require('./database');
const authMiddleware = require('./middleware/auth');

const app = express();

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Pliki statyczne
fs.mkdirSync(path.join(__dirname, 'uploads', 'inspections'), { recursive: true });
fs.mkdirSync(path.join(__dirname, 'uploads', 'expenses'), { recursive: true });
fs.mkdirSync(path.join(__dirname, 'uploads', 'contacts'), { recursive: true });
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.use('/dashboard', express.static(path.join(__dirname, 'dashboard')));
app.get('/dashboard', (req, res) =>
  res.sendFile(path.join(__dirname, 'dashboard', 'index.html'))
);
app.get('/', (req, res) => res.redirect('/dashboard'));

// API – auth (bez JWT)
app.use('/api/auth', require('./routes/auth'));

// API – chronione JWT lub X-Api-Key
app.use('/api/vehicles',    authMiddleware, require('./routes/vehicles'));
app.use('/api/inspections', authMiddleware, require('./routes/inspections'));
app.use('/api/road-cards',  authMiddleware, require('./routes/road_cards'));
app.use('/api/expenses',    authMiddleware, require('./routes/expenses'));
app.use('/api/contacts',    authMiddleware, require('./routes/contacts'));
app.use('/api/drivers',     authMiddleware, require('./routes/drivers'));

app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ error: err.message });
});

initDb();

const PORT = process.env.PORT || 3000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`\n🚛 VehicleInspection backend on port ${PORT}`);
  console.log(`   Dashboard: http://localhost:${PORT}/dashboard/`);
  console.log(`   API:       http://localhost:${PORT}/api/\n`);
});
