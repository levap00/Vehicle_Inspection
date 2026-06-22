const Database = require('better-sqlite3');
const bcrypt = require('bcryptjs');
const path = require('path');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'vi.db');
const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

function ensureColumn(table, column, definition) {
  const exists = db.prepare(`PRAGMA table_info(${table})`).all().some(c => c.name === column);
  if (!exists) db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
}

function initDb() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS drivers (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      name TEXT NOT NULL,
      role TEXT NOT NULL DEFAULT 'driver',
      created_at INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS vehicles (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      registration TEXT UNIQUE NOT NULL,
      brand TEXT DEFAULT '',
      fuel TEXT DEFAULT 'ON',
      created_at INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS inspections (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      client_id TEXT UNIQUE,
      driver_id INTEGER REFERENCES drivers(id),
      driver_name TEXT DEFAULT '',
      registration TEXT DEFAULT '',
      trailer_registration TEXT DEFAULT '',
      fuel TEXT DEFAULT '',
      timestamp_ms INTEGER,
      checklist_json TEXT DEFAULT '[]',
      inspection_json TEXT DEFAULT '{}',
      road_card_json TEXT DEFAULT '{}',
      created_at INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS inspection_photos (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      inspection_id INTEGER REFERENCES inspections(id),
      field TEXT,
      filename TEXT,
      created_at INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS road_cards (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      driver_id INTEGER REFERENCES drivers(id),
      driver_name TEXT DEFAULT '',
      registration TEXT DEFAULT '',
      card_number TEXT DEFAULT '',
      trailer_registration TEXT DEFAULT '',
      fuel TEXT DEFAULT '',
      timestamp_ms INTEGER,
      odometer_start TEXT DEFAULT '',
      odometer_end TEXT DEFAULT '',
      departure_datetime TEXT DEFAULT '',
      return_datetime TEXT DEFAULT '',
      total_distance_km TEXT DEFAULT '',
      loaded_km_total TEXT DEFAULT '',
      empty_km_total TEXT DEFAULT '',
      fuel_start TEXT DEFAULT '',
      fuel_end TEXT DEFAULT '',
      points_json TEXT DEFAULT '[]',
      created_at INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS expenses (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      client_id TEXT UNIQUE,
      driver_id INTEGER REFERENCES drivers(id),
      driver_name TEXT DEFAULT '',
      registration TEXT DEFAULT '',
      type TEXT DEFAULT 'other',
      amount TEXT DEFAULT '',
      currency TEXT DEFAULT '',
      description TEXT DEFAULT '',
      purpose TEXT DEFAULT '',
      payment_method TEXT DEFAULT '',
      payment_card TEXT DEFAULT '',
      timestamp_ms INTEGER,
      photo_filename TEXT,
      photo_dir TEXT DEFAULT 'expenses',
      created_at INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS contacts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      role TEXT DEFAULT '',
      phone TEXT DEFAULT '',
      email TEXT DEFAULT '',
      photo_url TEXT DEFAULT '',
      created_at INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS orders (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      vehicle_id INTEGER,
      route_number TEXT DEFAULT '',
      events_json TEXT DEFAULT '[]',
      created_at INTEGER DEFAULT (strftime('%s','now'))
    );
  `);
  ensureColumn('inspections', 'trailer_registration', "TEXT DEFAULT ''");
  ensureColumn('road_cards', 'card_number', "TEXT DEFAULT ''");
  ensureColumn('road_cards', 'trailer_registration', "TEXT DEFAULT ''");
  ensureColumn('road_cards', 'departure_datetime', "TEXT DEFAULT ''");
  ensureColumn('road_cards', 'return_datetime', "TEXT DEFAULT ''");
  ensureColumn('road_cards', 'total_distance_km', "TEXT DEFAULT ''");
  ensureColumn('road_cards', 'loaded_km_total', "TEXT DEFAULT ''");
  ensureColumn('road_cards', 'empty_km_total', "TEXT DEFAULT ''");
  ensureColumn('expenses', 'currency', "TEXT DEFAULT ''");
  ensureColumn('expenses', 'purpose', "TEXT DEFAULT ''");
  ensureColumn('expenses', 'payment_method', "TEXT DEFAULT ''");
  ensureColumn('expenses', 'payment_card', "TEXT DEFAULT ''");
  ensureColumn('contacts', 'photo_url', "TEXT DEFAULT ''");

  // DomyĹ›lne konto admin
  const admin = db.prepare("SELECT id FROM drivers WHERE username = 'admin'").get();
  if (!admin) {
    const hash = bcrypt.hashSync(process.env.ADMIN_PASS || 'Admin1234!', 10);
    db.prepare(
      "INSERT INTO drivers (username, password_hash, name, role) VALUES ('admin',?,'Administrator','admin')"
    ).run(hash);
    console.log('âś… Konto admin utworzone: admin / ' + (process.env.ADMIN_PASS || 'Admin1234!'));
  }

  // PrzykĹ‚adowe kontakty (tylko jeĹ›li tabela pusta)
  const cnt = db.prepare('SELECT COUNT(*) as c FROM contacts').get().c;
  if (cnt === 0) {
    const ins = db.prepare('INSERT INTO contacts (name, role, phone, email) VALUES (?,?,?,?)');
    [
      ['Jan Kowalski',    'Spedytor',          '+48 123 456 789', 'j.kowalski@firma.pl'],
      ['Anna Nowak',      'Sekretariat',        '+48 987 654 321', 'a.nowak@firma.pl'],
      ['Tomasz WiĹ›niewski','DziaĹ‚ IT',          '+48 111 222 333', 'it@firma.pl'],
      ['Zbigniew Krawczyk','Serwis techniczny', '+48 444 555 666', 'serwis@firma.pl'],
    ].forEach(r => ins.run(...r));
    console.log('âś… PrzykĹ‚adowe kontakty dodane');
  }
}

module.exports = { db, initDb };

