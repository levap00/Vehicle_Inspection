const jwt = require('jsonwebtoken');
const JWT_SECRET = process.env.JWT_SECRET || 'vi_jwt_secret_change_me';

module.exports = function auth(req, res, next) {
  // Bearer JWT token
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    try {
      req.driver = jwt.verify(authHeader.slice(7), JWT_SECRET);
      return next();
    } catch {
      return res.status(401).json({ error: 'Token wygasł lub jest nieprawidłowy' });
    }
  }

  // Stary klucz API (backward compat podczas migracji)
  const key = req.headers['x-api-key'];
  if (key && key === (process.env.API_KEY || 'TEST123')) {
    req.driver = { id: 1, username: 'admin', name: 'Admin', role: 'admin' };
    return next();
  }

  return res.status(401).json({ error: 'Brak autoryzacji – wymagany token' });
};
