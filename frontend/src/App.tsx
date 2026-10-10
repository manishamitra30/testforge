import { BrowserRouter, Routes, Route, Link, useNavigate } from 'react-router-dom';
import { Register } from './pages/Register';
import { Login } from './pages/Login';

function Dashboard() {
const navigate = useNavigate();
const userData = localStorage.getItem('user');
const user = userData ? JSON.parse(userData) : null;

const handleLogout = () => {
localStorage.removeItem('user');
navigate('/login');
};

return (
<div className="min-h-screen bg-slate-900 text-white flex flex-col items-center justify-center space-y-4">
  <h1 className="text-4xl font-bold">TestForge Dashboard</h1>
  {user ? (
    <div className="text-center space-y-3 p-6 bg-slate-800 rounded-lg border border-slate-700 shadow-lg">
      <p className="text-xl">Logged in as: <span className="font-semibold text-blue-400">{user.username}</span></p>
      <p className="text-sm text-slate-400">{user.email}</p>
      <button onClick={handleLogout} className="px-4 py-2 bg-rose-600 hover:bg-rose-500 rounded font-semibold text-sm transition-colors">
        Sign Out
      </button>
    </div>
  ) : (
    <div className="space-x-4">
      <Link to="/login" className="px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded font-semibold transition-colors">
        Sign In
      </Link>
      <Link to="/register" className="px-4 py-2 bg-slate-700 hover:bg-slate-600 rounded font-semibold transition-colors">
        Register
      </Link>
    </div>
  )}
</div>
);
}

export function App() {
return (
<BrowserRouter>
  <Routes>
    <Route path="/" element={<Dashboard />} />
    <Route path="/login" element={<Login />} />
    <Route path="/register" element={<Register />} />
  </Routes>
</BrowserRouter>
);
}

export default App;